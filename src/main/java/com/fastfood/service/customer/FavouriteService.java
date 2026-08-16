package com.fastfood.service.customer;

import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.JdbcSupport;
import com.fastfood.dao.customer.FavouriteDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.model.entity.Favourite;
import com.fastfood.model.entity.Product;
import com.fastfood.service.Tx;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Món quen của khách — đánh dấu ngay trên thực đơn, kèm ghi chú riêng.
 * <p>
 * <b>Món ngừng bán vẫn đánh dấu được.</b> Khách quen một món đang tạm hết hàng thì việc họ muốn
 * nhớ nó lại càng có lý — đó chính là món họ sẽ hỏi khi quay lại. Màn hình nói rõ món nào đang
 * không bán được, còn chốt chặn thật vẫn nằm ở giỏ hàng lúc thêm món.
 * <p>
 * <b>Xoá hẳn khỏi bảng.</b> Đây là dữ liệu riêng của khách, không dính tiền, không bản ghi nào
 * trỏ tới — giữ lại một dòng đã bỏ đánh dấu chỉ để nó không bao giờ được đọc nữa.
 * <p>
 * Không ghi nhật ký thao tác, cùng lý do.
 */
public class FavouriteService {

    private static final int MAX_NOTE_LENGTH = 255;

    private final FavouriteDAO favouriteDAO = new FavouriteDAO();
    private final ProductDAO productDAO = new ProductDAO();

    // ============================================================ đọc

    public List<Favourite> listOf(int customerId) {
        return Tx.read(con -> favouriteDAO.findByCustomer(con, customerId));
    }

    /**
     * Mã các món khách đã đánh dấu — dùng để tô dấu trên lưới thực đơn.
     * <p>
     * Khách chưa đăng nhập thì trả về tập rỗng chứ không ném lỗi: thực đơn là trang công khai,
     * và một người xem không đăng nhập là chuyện bình thường chứ không phải lỗi.
     */
    public Set<Integer> favouriteProductIds(Integer customerId) {
        if (customerId == null) {
            return Collections.emptySet();
        }
        return Tx.read(con -> favouriteDAO.productIdsOf(con, customerId));
    }

    public Favourite findOwn(int favouriteId, int customerId) {
        return Tx.read(con -> requireOwn(con, favouriteId, customerId));
    }

    // ============================================================ ghi

    /**
     * Đánh dấu một món.
     * <p>
     * Mỗi khách một dấu cho một món, và điều đó do {@code UQ_Fav_customer_prod} bảo đảm chứ
     * không phải một lượt đọc trước: bấm đúp trên điện thoại là hai yêu cầu gần như cùng lúc.
     */
    public Favourite add(int customerId, int productId, String note) {
        String text = optionalNote(note);
        LocalDateTime now = DateTimeUtil.now();
        try {
            return Tx.write(con -> {
                Product product = productDAO.findById(con, productId);
                if (product == null) {
                    throw new NotFoundException("Không tìm thấy món ăn.");
                }
                Favourite fav = new Favourite();
                fav.setCustomerId(customerId);
                fav.setProductId(productId);
                fav.setNote(text);
                fav.setCreatedAt(now);
                favouriteDAO.insert(con, fav);
                return fav;
            });
        } catch (RuntimeException e) {
            if (!JdbcSupport.isUniqueViolation(e)) {
                throw e;
            }
            throw new BusinessException("Món này đã có trong danh sách món quen của bạn.");
        }
    }

    public void updateNote(int favouriteId, int customerId, String note) {
        String text = optionalNote(note);
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            requireOwn(con, favouriteId, customerId);
            favouriteDAO.updateNote(con, favouriteId, customerId, text, now);
        });
    }

    public void remove(int favouriteId, int customerId) {
        Tx.writeVoid(con -> {
            requireOwn(con, favouriteId, customerId);
            favouriteDAO.delete(con, favouriteId, customerId);
        });
    }

    /**
     * Bỏ đánh dấu từ lưới thực đơn, nơi chỉ có mã món trong tay.
     * <p>
     * Bỏ một món chưa từng đánh dấu <b>không</b> bị coi là lỗi: người dùng bấm nút "bỏ" thì điều
     * họ muốn là món đó không còn trong danh sách, và sau lệnh này thì đúng như vậy. Báo lỗi ở
     * đây chỉ làm phiền người vừa bấm đúp.
     */
    public void removeByProduct(int customerId, int productId) {
        Tx.writeVoid(con -> favouriteDAO.deleteByProduct(con, customerId, productId));
    }

    // ============================================================ dùng chung

    private Favourite requireOwn(Connection con, int favouriteId, int customerId)
            throws SQLException {
        Favourite fav = favouriteDAO.findById(con, favouriteId);
        if (fav == null) {
            throw new NotFoundException("Không tìm thấy món quen này.");
        }
        if (fav.getCustomerId() != customerId) {
            throw new BusinessException("Đây là món quen của tài khoản khác.");
        }
        return fav;
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
