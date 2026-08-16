package com.fastfood.service.staff;

import com.fastfood.common.constant.BusinessRule;
import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.JdbcSupport;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.dao.staff.PosHoldDAO;
import com.fastfood.model.entity.PosHold;
import com.fastfood.model.entity.PosHoldItem;
import com.fastfood.model.entity.Product;
import com.fastfood.service.Tx;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phiếu treo tại quầy — cất giỏ hàng đang dở lại để phục vụ khách tiếp theo.
 * <p>
 * <b>Vì sao có bảng này trong khi giỏ POS cố ý không ghi xuống cơ sở dữ liệu.</b> Lý do cũ vẫn
 * đúng: ghi mọi lần bấm xuống thì mỗi khách vãng lai để lại một dòng rác. Nhưng treo đơn là một
 * thao tác <b>cố ý</b> — thu ngân phải đặt tên cho phiếu thì mới treo được. Bấm nhầm không sinh
 * ra bản ghi nào; chỉ khi có người chủ động treo mới có.
 * <p>
 * <b>Lấy phiếu ra là xoá phiếu.</b> Giữ lại thì cùng một phiếu bán được hai lần: người trực ca
 * sau mở màn hình vẫn thấy nó nằm đó và tưởng chưa ai tính tiền. Phiếu đã nạp vào giỏ thì nó
 * sống tiếp trong phiên làm việc, và muốn cất lại thì treo lần nữa.
 * <p>
 * Không ghi nhật ký thao tác, cùng lý do với ghi chú điều phối: chưa có đồng tiền nào đi qua và
 * không bản ghi nào trỏ về đây.
 */
public class PosHoldService {

    /** Quá số này thì khối phiếu treo trên màn bán hàng dài hơn cả thực đơn. */
    private static final int MAX_HOLDS_PER_CASHIER = 20;
    private static final int MAX_LABEL_LENGTH = 100;
    private static final int MAX_NOTE_LENGTH = 500;

    private final PosHoldDAO holdDAO = new PosHoldDAO();
    private final ProductDAO productDAO = new ProductDAO();

    // ============================================================ đọc

    public List<PosHold> myHolds(int cashierId) {
        return Tx.read(con -> holdDAO.findByCashier(con, cashierId));
    }

    public PosHold findOwn(int holdId, int cashierId) {
        return Tx.read(con -> requireOwn(con, holdId, cashierId));
    }

    // ============================================================ treo

    /**
     * Treo giỏ đang dở thành một phiếu.
     *
     * @param lines mã món và số lượng, lấy từ giỏ tạm trong phiên làm việc
     */
    public PosHold hold(int cashierId, String label, String note, Map<Integer, Integer> lines) {
        String name = requireLabel(label);
        String text = optionalNote(note);
        if (lines == null || lines.isEmpty()) {
            throw new ValidationException("Giỏ đang trống, không có gì để treo.");
        }
        for (Integer quantity : lines.values()) {
            requireSaneQuantity(quantity == null ? 0 : quantity);
        }
        LocalDateTime now = DateTimeUtil.now();

        try {
            return Tx.write(con -> {
                if (holdDAO.countByCashier(con, cashierId) >= MAX_HOLDS_PER_CASHIER) {
                    throw new BusinessException("Bạn đang treo " + MAX_HOLDS_PER_CASHIER
                            + " phiếu — nhiều nhất rồi. Hãy tính tiền hoặc bỏ bớt phiếu cũ.");
                }
                PosHold hold = new PosHold();
                hold.setCashierId(cashierId);
                hold.setLabel(name);
                hold.setNote(text);
                hold.setCreatedAt(now);
                holdDAO.insert(con, hold);

                for (Map.Entry<Integer, Integer> line : lines.entrySet()) {
                    // Kiểm món có thật trước khi ghi. Mã món gửi lên từ biểu mẫu nên không tin
                    // được; thiếu bước này thì lỗi khoá ngoại nổ ra dưới tầng dữ liệu.
                    requireProduct(con, line.getKey());
                    holdDAO.addItem(con, hold.getHoldId(), line.getKey(), line.getValue());
                }
                hold.setItems(holdDAO.findItems(con, hold.getHoldId()));
                return hold;
            });
        } catch (RuntimeException e) {
            if (!JdbcSupport.isUniqueViolation(e)) {
                throw e;
            }
            throw new BusinessException("Bạn đang có một phiếu treo tên \"" + name
                    + "\". Hãy đặt tên khác để không lẫn.");
        }
    }

    // ============================================================ sửa

    /** Đổi tên và ghi chú của phiếu. */
    public void rename(int holdId, int cashierId, String label, String note) {
        String name = requireLabel(label);
        String text = optionalNote(note);
        LocalDateTime now = DateTimeUtil.now();
        try {
            Tx.writeVoid(con -> {
                requireOwn(con, holdId, cashierId);
                holdDAO.updateHeader(con, holdId, cashierId, name, text, now);
            });
        } catch (RuntimeException e) {
            if (!JdbcSupport.isUniqueViolation(e)) {
                throw e;
            }
            throw new BusinessException("Bạn đang có một phiếu treo khác tên \"" + name + "\".");
        }
    }

    /**
     * Đặt lại số lượng một dòng; về 0 thì bỏ dòng đó khỏi phiếu.
     * <p>
     * Bỏ dòng cuối cùng thì <b>xoá luôn phiếu</b>: một phiếu treo không còn món nào chỉ là một
     * cái tên nằm chiếm chỗ, và thu ngân sẽ phải bấm xoá thêm một lần nữa cho đúng thứ họ vừa
     * làm rồi.
     */
    public void setQuantity(int holdId, int cashierId, int productId, int quantity) {
        if (quantity > 0) {
            requireSaneQuantity(quantity);
        }
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            requireOwn(con, holdId, cashierId);
            if (quantity <= 0) {
                holdDAO.removeItem(con, holdId, productId);
            } else {
                holdDAO.updateItemQuantity(con, holdId, productId, quantity);
            }
            if (holdDAO.findItems(con, holdId).isEmpty()) {
                holdDAO.delete(con, holdId, cashierId);
            } else {
                holdDAO.touch(con, holdId, now);
            }
        });
    }

    /** Thêm món vào phiếu đang treo mà không cần lấy ra — khách gọi thêm qua điện thoại. */
    public void addItem(int holdId, int cashierId, int productId, int quantity) {
        requireSaneQuantity(quantity);
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            requireOwn(con, holdId, cashierId);
            requireProduct(con, productId);
            holdDAO.addItem(con, holdId, productId, quantity);
            holdDAO.touch(con, holdId, now);
        });
    }

    // ============================================================ lấy ra và bỏ

    /**
     * Lấy phiếu ra để tính tiền: trả về các dòng món rồi xoá phiếu.
     * <p>
     * Trả về theo thứ tự đã treo để phiếu tạm dựng lại giống hệt lúc cất đi.
     */
    public Map<Integer, Integer> resume(int holdId, int cashierId) {
        return Tx.write(con -> {
            PosHold hold = requireOwn(con, holdId, cashierId);
            Map<Integer, Integer> lines = new LinkedHashMap<>();
            for (PosHoldItem item : hold.getItems()) {
                lines.put(item.getProductId(), item.getQuantity());
            }
            holdDAO.delete(con, holdId, cashierId);
            return lines;
        });
    }

    /** Bỏ hẳn phiếu — khách đổi ý và bỏ đi. */
    public void discard(int holdId, int cashierId) {
        Tx.writeVoid(con -> {
            requireOwn(con, holdId, cashierId);
            holdDAO.delete(con, holdId, cashierId);
        });
    }

    // ============================================================ dùng chung

    /**
     * Đọc trước để báo lỗi cho đúng: các câu lệnh ghi đã gộp điều kiện "đúng người treo" vào
     * chính chúng, nên khi trả về 0 dòng thì không biết là phiếu không tồn tại hay của người
     * khác. Hai câu trả lời đó dẫn tới hai việc khác nhau cho người đang đứng ở quầy.
     */
    private PosHold requireOwn(Connection con, int holdId, int cashierId) throws SQLException {
        PosHold hold = holdDAO.findById(con, holdId);
        if (hold == null) {
            throw new NotFoundException("Không tìm thấy phiếu treo. Có thể ai đó vừa tính tiền phiếu này.");
        }
        if (hold.getCashierId() != cashierId) {
            throw new BusinessException("Đây là phiếu treo của thu ngân khác.");
        }
        return hold;
    }

    private Product requireProduct(Connection con, int productId) throws SQLException {
        Product product = productDAO.findById(con, productId);
        if (product == null) {
            throw new NotFoundException("Không tìm thấy món ăn.");
        }
        return product;
    }

    /**
     * Món hết hàng vẫn treo được, cố ý. Lúc treo có thể món còn, lúc lấy ra mới hết — chặn ở
     * đây không ngăn được chuyện đó, mà chỉ làm thu ngân không cất nổi giỏ đang dở. Cảnh báo
     * nằm ở màn hình ({@link PosHold#isAnyUnavailable}), còn chốt chặn thật vẫn là
     * {@code StaffOrderService.createPosOrder} lúc thu tiền.
     */
    private void requireSaneQuantity(int quantity) {
        if (quantity <= 0) {
            throw new ValidationException("Số lượng phải lớn hơn 0.");
        }
        if (quantity > BusinessRule.MAX_QUANTITY_PER_LINE) {
            throw new ValidationException("Mỗi món chỉ đặt được tối đa "
                    + BusinessRule.MAX_QUANTITY_PER_LINE + " phần.");
        }
    }

    private String requireLabel(String label) {
        String name = label == null ? "" : label.trim();
        if (name.isEmpty()) {
            throw new ValidationException("Đặt tên cho phiếu để lát nữa còn nhận ra, "
                    + "ví dụ \"Bàn 3\" hoặc \"Anh áo xanh\".");
        }
        if (name.length() > MAX_LABEL_LENGTH) {
            throw new ValidationException("Tên phiếu tối đa " + MAX_LABEL_LENGTH + " ký tự.");
        }
        return name;
    }

    private String optionalNote(String note) {
        String text = note == null ? "" : note.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > MAX_NOTE_LENGTH) {
            throw new ValidationException("Ghi chú tối đa " + MAX_NOTE_LENGTH + " ký tự.");
        }
        return text;
    }
}
