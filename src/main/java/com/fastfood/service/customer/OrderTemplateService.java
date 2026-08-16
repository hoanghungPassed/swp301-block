package com.fastfood.service.customer;

import com.fastfood.common.constant.BusinessRule;
import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.JdbcSupport;
import com.fastfood.dao.customer.CartDAO;
import com.fastfood.dao.customer.OrderTemplateDAO;
import com.fastfood.dao.shared.OrderDAO;
import com.fastfood.dao.shared.OrderItemDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.model.dto.TemplateApplyResult;
import com.fastfood.model.entity.Order;
import com.fastfood.model.entity.OrderItem;
import com.fastfood.model.entity.OrderTemplate;
import com.fastfood.model.entity.OrderTemplateItem;
import com.fastfood.model.entity.Product;
import com.fastfood.service.Tx;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Mẫu đặt nhanh của khách — lưu một đơn cũ lại rồi lần sau nạp thẳng vào giỏ.
 * <p>
 * <b>Mẫu không lưu giá.</b> Chỉ mã món và số lượng. Giá đọc mới ở mọi chỗ hiển thị và ở lúc nạp,
 * đúng nguyên tắc đã ghi trong {@link CartService}: giá chỉ được chốt lại vào lúc đặt hàng.
 * <p>
 * <b>Nạp mẫu phải nói ra món nào bị bỏ.</b> Món trong mẫu có thể đã ngừng bán từ lần lưu tới
 * giờ. Nạp thiếu trong im lặng là kiểu hỏng tệ nhất ở đây: khách đi tiếp tới bước chọn giờ đến
 * lấy rồi mới thấy giỏ ít hơn mình tưởng, mà lúc đó không còn manh mối nào để biết vì sao. Cách
 * xử lý giống hệt {@code CartService.removeUnavailable} — làm được phần nào thì làm, rồi nói rõ
 * phần còn lại.
 * <p>
 * Xoá hẳn khỏi bảng, cùng lý do với giỏ hàng: đây là bản nháp của khách, không dính tiền và
 * không bản ghi nào trỏ tới.
 */
public class OrderTemplateService {

    /** Quá số này thì khối mẫu trên trang lịch sử đơn dài hơn chính lịch sử. */
    private static final int MAX_TEMPLATES_PER_CUSTOMER = 10;
    private static final int MAX_NAME_LENGTH = 100;

    private final OrderTemplateDAO templateDAO = new OrderTemplateDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final OrderItemDAO orderItemDAO = new OrderItemDAO();
    private final CartDAO cartDAO = new CartDAO();

    // ============================================================ đọc

    public List<OrderTemplate> listOf(int customerId) {
        return Tx.read(con -> templateDAO.findByCustomer(con, customerId));
    }

    public OrderTemplate findOwn(int templateId, int customerId) {
        return Tx.read(con -> requireOwn(con, templateId, customerId));
    }

    // ============================================================ tạo

    /**
     * Lưu một đơn đã đặt thành mẫu.
     * <p>
     * Đơn phải là của chính khách đó — điều kiện này kiểm ở đây chứ không tin vào mã đơn gửi lên
     * từ biểu mẫu, nếu không thì đổi một con số trong trang là đọc được món của người khác.
     */
    public OrderTemplate saveFromOrder(int customerId, int orderId, String name) {
        String ten = requireName(name);
        LocalDateTime now = DateTimeUtil.now();

        try {
            return Tx.write(con -> {
                Order order = orderDAO.findById(con, orderId);
                if (order == null || order.getCustomerId() == null
                        || order.getCustomerId() != customerId) {
                    throw new NotFoundException("Không tìm thấy đơn hàng.");
                }
                List<OrderItem> items = orderItemDAO.findByOrder(con, orderId);
                if (items.isEmpty()) {
                    throw new BusinessException("Đơn này không có món nào để lưu thành mẫu.");
                }
                requireRoom(con, customerId);

                OrderTemplate template = newTemplate(con, customerId, ten, now);
                for (OrderItem item : items) {
                    templateDAO.addItem(con, template.getTemplateId(),
                            item.getProductId(), item.getQuantity());
                }
                template.setItems(templateDAO.findItems(con, template.getTemplateId()));
                return template;
            });
        } catch (RuntimeException e) {
            throw asFriendly(e, ten);
        }
    }

    /** Lưu chính giỏ hàng đang có thành mẫu, không cần đặt hàng trước. */
    public OrderTemplate saveFromCart(int customerId, String name) {
        String ten = requireName(name);
        LocalDateTime now = DateTimeUtil.now();

        try {
            return Tx.write(con -> {
                int cartId = cartDAO.getOrCreateCartId(con, customerId, now);
                var cartItems = cartDAO.findItems(con, cartId);
                if (cartItems.isEmpty()) {
                    throw new BusinessException("Giỏ hàng đang trống, không có gì để lưu thành mẫu.");
                }
                requireRoom(con, customerId);

                OrderTemplate template = newTemplate(con, customerId, ten, now);
                for (var item : cartItems) {
                    templateDAO.addItem(con, template.getTemplateId(),
                            item.getProductId(), item.getQuantity());
                }
                template.setItems(templateDAO.findItems(con, template.getTemplateId()));
                return template;
            });
        } catch (RuntimeException e) {
            throw asFriendly(e, ten);
        }
    }

    // ============================================================ sửa

    public void rename(int templateId, int customerId, String name) {
        String ten = requireName(name);
        LocalDateTime now = DateTimeUtil.now();
        try {
            Tx.writeVoid(con -> {
                requireOwn(con, templateId, customerId);
                templateDAO.rename(con, templateId, customerId, ten, now);
            });
        } catch (RuntimeException e) {
            throw asFriendly(e, ten);
        }
    }

    /**
     * Đặt lại số lượng một dòng; về 0 thì bỏ dòng đó. Bỏ nốt dòng cuối thì xoá luôn mẫu — một
     * mẫu rỗng chỉ là cái tên nằm chiếm chỗ.
     */
    public void setQuantity(int templateId, int customerId, int productId, int quantity) {
        if (quantity > 0) {
            requireSaneQuantity(quantity);
        }
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            requireOwn(con, templateId, customerId);
            if (quantity <= 0) {
                templateDAO.removeItem(con, templateId, productId);
            } else {
                templateDAO.updateItemQuantity(con, templateId, productId, quantity);
            }
            if (templateDAO.findItems(con, templateId).isEmpty()) {
                templateDAO.delete(con, templateId, customerId);
            } else {
                templateDAO.touch(con, templateId, now);
            }
        });
    }

    public void addItem(int templateId, int customerId, int productId, int quantity) {
        requireSaneQuantity(quantity);
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            requireOwn(con, templateId, customerId);
            Product product = productDAO.findById(con, productId);
            if (product == null) {
                throw new NotFoundException("Không tìm thấy món ăn.");
            }
            templateDAO.addItem(con, templateId, productId, quantity);
            templateDAO.touch(con, templateId, now);
        });
    }

    public void delete(int templateId, int customerId) {
        Tx.writeVoid(con -> {
            requireOwn(con, templateId, customerId);
            templateDAO.delete(con, templateId, customerId);
        });
    }

    // ============================================================ nạp vào giỏ

    /**
     * Nạp mẫu vào giỏ hàng, bỏ qua món không còn phục vụ và <b>trả về tên những món đó</b>.
     * <p>
     * Mẫu <b>không</b> bị xoá sau khi nạp, khác hẳn phiếu treo ở quầy. Phiếu treo là một khách
     * đang đứng đợi, lấy ra một lần là xong; còn mẫu sinh ra để dùng đi dùng lại — xoá sau lần
     * nạp đầu tiên thì nó chỉ là một cách bấm "đặt lại" rườm rà.
     * <p>
     * Món đã có sẵn trong giỏ thì cộng dồn, do {@code UQ_CartItem} và cách viết của
     * {@code CartDAO.addItem} quyết định — nạp mẫu hai lần là gấp đôi số lượng, đúng như khi
     * bấm thêm món hai lần.
     */
    public TemplateApplyResult applyToCart(int templateId, int customerId) {
        LocalDateTime now = DateTimeUtil.now();
        return Tx.write(con -> {
            OrderTemplate template = requireOwn(con, templateId, customerId);
            TemplateApplyResult result = new TemplateApplyResult();
            int cartId = cartDAO.getOrCreateCartId(con, customerId, now);

            for (OrderTemplateItem item : template.getItems()) {
                // Đi qua đúng truy vấn mà giỏ hàng dùng lúc thêm món, thay vì tin vào cờ đã đọc
                // kèm danh sách: giữa lúc mở trang và lúc bấm nạp, bếp có thể vừa báo hết món.
                Product product = productDAO.findForCheckout(con, item.getProductId());
                if (product == null || !product.isOrderable()) {
                    result.skip(item.getProductName());
                    continue;
                }
                cartDAO.addItem(con, cartId, item.getProductId(), item.getQuantity());
                result.countAdded();
            }
            cartDAO.touch(con, cartId, now);

            if (!result.isAnythingAdded()) {
                throw new BusinessException("Mẫu \"" + template.getName()
                        + "\" hiện không còn món nào phục vụ được, nên chưa nạp được vào giỏ.");
            }
            return result;
        });
    }

    // ============================================================ dùng chung

    private OrderTemplate newTemplate(Connection con, int customerId, String name,
                                      LocalDateTime now) throws SQLException {
        OrderTemplate template = new OrderTemplate();
        template.setCustomerId(customerId);
        template.setName(name);
        template.setCreatedAt(now);
        templateDAO.insert(con, template);
        return template;
    }

    private void requireRoom(Connection con, int customerId) throws SQLException {
        if (templateDAO.countByCustomer(con, customerId) >= MAX_TEMPLATES_PER_CUSTOMER) {
            throw new BusinessException("Bạn đã lưu " + MAX_TEMPLATES_PER_CUSTOMER
                    + " mẫu — nhiều nhất rồi. Hãy xoá bớt mẫu cũ trước khi lưu thêm.");
        }
    }

    private OrderTemplate requireOwn(Connection con, int templateId, int customerId)
            throws SQLException {
        OrderTemplate template = templateDAO.findById(con, templateId);
        if (template == null) {
            throw new NotFoundException("Không tìm thấy mẫu đặt nhanh.");
        }
        if (template.getCustomerId() != customerId) {
            throw new BusinessException("Đây là mẫu của tài khoản khác.");
        }
        return template;
    }

    /** Trùng tên là lỗi người dùng sửa được, nên phải nói thành lời chứ không để lộ lỗi khoá. */
    private RuntimeException asFriendly(RuntimeException e, String name) {
        if (!JdbcSupport.isUniqueViolation(e)) {
            return e;
        }
        return new BusinessException("Bạn đã có một mẫu tên \"" + name
                + "\". Hãy đặt tên khác để hai mẫu không lẫn nhau.");
    }

    private String requireName(String name) {
        String ten = name == null ? "" : name.trim();
        if (ten.isEmpty()) {
            throw new ValidationException("Đặt tên cho mẫu để lần sau còn nhận ra, "
                    + "ví dụ \"Bữa trưa quen\".");
        }
        if (ten.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("Tên mẫu tối đa " + MAX_NAME_LENGTH + " ký tự.");
        }
        return ten;
    }

    private void requireSaneQuantity(int quantity) {
        if (quantity <= 0) {
            throw new ValidationException("Số lượng phải lớn hơn 0.");
        }
        if (quantity > BusinessRule.MAX_QUANTITY_PER_LINE) {
            throw new ValidationException("Mỗi món chỉ đặt được tối đa "
                    + BusinessRule.MAX_QUANTITY_PER_LINE + " phần.");
        }
    }
}
