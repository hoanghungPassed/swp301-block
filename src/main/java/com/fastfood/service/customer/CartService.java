package com.fastfood.service.customer;

import com.fastfood.common.constant.BusinessRule;
import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.ValidationUtil;
import com.fastfood.dao.customer.CartDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.model.dto.CartView;
import com.fastfood.model.entity.CartItem;
import com.fastfood.model.entity.Product;
import com.fastfood.service.Tx;

import java.util.List;

/**
 * Giỏ hàng của khách đặt trước.
 * <p>
 * Giỏ chỉ là bản nháp: giá trong giỏ luôn đọc mới từ bảng món chứ không lưu lại,
 * nên khách nhìn thấy giá hiện hành ngay cả khi để giỏ qua đêm. Giá thật chỉ được
 * chốt và sao chép lại vào lúc đặt hàng.
 */
public class CartService {

    private final CartDAO cartDAO = new CartDAO();
    private final ProductDAO productDAO = new ProductDAO();

    public CartView getCart(int userId) {
        return Tx.read(con -> {
            int cartId = cartDAO.getOrCreateCartId(con, userId, DateTimeUtil.now());
            List<CartItem> items = cartDAO.findItems(con, cartId);
            CartView view = new CartView();
            view.setCartId(cartId);
            view.setItems(items);
            return view;
        });
    }

    /** Số món trên biểu tượng giỏ hàng ở thanh điều hướng. */
    public int countItems(int userId) {
        return Tx.read(con -> cartDAO.countItems(con, userId));
    }

    /**
     * Thêm món vào giỏ. Món đã có thì <b>cộng dồn</b> vào dòng cũ chứ không tạo dòng thứ hai.
     * <p>
     * Vì cộng dồn, giới hạn số lượng phải xét trên <b>tổng sau khi cộng</b>, không phải trên con
     * số của riêng lần bấm này. Xét lần bấm thì mỗi yêu cầu đều mang số 1 và đều hợp lệ, trong
     * khi dòng trong giỏ cứ thế lớn lên không có trần — bấm "thêm vào giỏ" đủ nhiều là vượt qua
     * đúng cái ngưỡng vừa dựng lên để chặn. Đường bán tại quầy đã xét theo tổng ngay từ đầu
     * ({@code PosServlet}), nên xét khác đi ở đây còn làm hai đường đặt hàng lệch nhau.
     * <p>
     * Đọc số đang có nằm trong cùng giao dịch với lúc ghi, để hai yêu cầu gần như cùng lúc không
     * cùng đọc thấy một con số cũ rồi cùng cộng thêm.
     */
    public void addProduct(int userId, int productId, int quantity) {
        ValidationUtil.requirePositive(quantity, "Số lượng");
        requireSaneQuantity(quantity);
        Tx.writeVoid(con -> {
            Product p = productDAO.findForCheckout(con, productId);
            if (p == null) {
                throw new NotFoundException("Không tìm thấy món ăn.");
            }
            if (!p.isOrderable()) {
                throw new BusinessException("Món \"" + p.getName() + "\" hiện không còn phục vụ.");
            }
            int cartId = cartDAO.getOrCreateCartId(con, userId, DateTimeUtil.now());
            requireSaneQuantity(cartDAO.quantityOf(con, cartId, productId) + quantity);
            cartDAO.addItem(con, cartId, productId, quantity);
            cartDAO.touch(con, cartId, DateTimeUtil.now());
        });
    }

    /**
     * Ô nhập trên giao diện đã giới hạn số lượng, nhưng đó chỉ là gợi ý cho người dùng — gửi
     * thẳng một con số khổng lồ lên vẫn được nhận. Chặn ở đây để thành tiền không vượt quá sức
     * chứa của cột tiền trong cơ sở dữ liệu và làm hỏng cả lượt đặt hàng.
     */
    private void requireSaneQuantity(int quantity) {
        if (quantity > BusinessRule.MAX_QUANTITY_PER_LINE) {
            throw new BusinessException("Mỗi món chỉ đặt được tối đa "
                    + BusinessRule.MAX_QUANTITY_PER_LINE
                    + " phần. Cần nhiều hơn, vui lòng liên hệ cửa hàng.");
        }
    }

    /** Đặt lại số lượng; về 0 thì bỏ món khỏi giỏ. */
    public void updateQuantity(int userId, int cartItemId, int quantity) {
        requireSaneQuantity(quantity);
        Tx.writeVoid(con -> {
            int cartId = cartDAO.getOrCreateCartId(con, userId, DateTimeUtil.now());
            if (quantity <= 0) {
                cartDAO.removeItem(con, cartId, cartItemId);
            } else {
                cartDAO.updateQuantity(con, cartId, cartItemId, quantity);
            }
            cartDAO.touch(con, cartId, DateTimeUtil.now());
        });
    }

    public void removeItem(int userId, int cartItemId) {
        Tx.writeVoid(con -> {
            int cartId = cartDAO.getOrCreateCartId(con, userId, DateTimeUtil.now());
            cartDAO.removeItem(con, cartId, cartItemId);
            cartDAO.touch(con, cartId, DateTimeUtil.now());
        });
    }

    /** Bỏ khỏi giỏ những món vừa hết hàng, để khách đi tiếp được mà không phải xoá từng dòng. */
    public int removeUnavailable(int userId) {
        return Tx.write(con -> {
            int cartId = cartDAO.getOrCreateCartId(con, userId, DateTimeUtil.now());
            int removed = 0;
            for (CartItem item : cartDAO.findItems(con, cartId)) {
                if (!item.isOrderable()) {
                    cartDAO.removeItem(con, cartId, item.getCartItemId());
                    removed++;
                }
            }
            cartDAO.touch(con, cartId, DateTimeUtil.now());
            return removed;
        });
    }
}
