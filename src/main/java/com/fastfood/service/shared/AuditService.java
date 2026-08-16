package com.fastfood.service.shared;

import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.shared.AuditLogDAO;
import com.fastfood.model.dto.Page;
import com.fastfood.model.entity.AuditLog;
import com.fastfood.service.Tx;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ghi nhật ký thao tác.
 * <p>
 * Mọi phương thức đều nhận sẵn {@link Connection} để bản ghi nhật ký nằm cùng giao dịch
 * với thao tác nó mô tả. Nếu tách ra thì có thể xảy ra chuyện đơn bị huỷ mà nhật ký
 * không ghi được, hoặc ngược lại.
 */
public class AuditService {

    private static final Logger LOG = Logger.getLogger(AuditService.class.getName());

    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    public void log(Connection con, Integer actorId, String entityType, Object entityId,
                    String action, String oldValue, String newValue) throws SQLException {
        AuditLog log = new AuditLog();
        log.setActorId(actorId);
        log.setEntityType(entityType);
        log.setEntityId(String.valueOf(entityId));
        log.setAction(action);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setCreatedAt(DateTimeUtil.now());
        auditLogDAO.insert(con, log);
    }

    /** Thao tác do hệ thống tự thực hiện: bộ hẹn giờ, cổng thanh toán. */
    public void logSystem(Connection con, String entityType, Object entityId,
                          String action, String newValue) throws SQLException {
        log(con, null, entityType, entityId, action, null, newValue);
    }

    /**
     * Ghi nhật ký trong một giao dịch <b>riêng</b>, không dính tới giao dịch đang chạy.
     * <p>
     * Chỉ dùng cho đúng một nhóm tình huống: thao tác <b>bị từ chối</b>. Ghi nhật ký trong
     * cùng giao dịch với thao tác thất bại là tự xoá bằng chứng — thao tác hỏng thì giao dịch
     * bị huỷ, và bản ghi nhật ký bị huỷ theo, nên chuyện đáng ghi nhất lại là chuyện duy nhất
     * không để lại dấu vết. Đưa sai mã nhận hàng nhiều lần liên tiếp chính là thứ cần nhìn thấy.
     * <p>
     * Nuốt mọi lỗi: không ghi được nhật ký thì cũng không được che mất lỗi nghiệp vụ thật
     * mà nơi gọi đang chuẩn bị báo cho người dùng.
     */
    public void logRejected(Integer actorId, String entityType, Object entityId,
                            String action, String oldValue, String newValue) {
        try {
            Tx.writeVoid(con -> log(con, actorId, entityType, entityId, action, oldValue, newValue));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Khong ghi duoc nhat ky cho thao tac bi tu choi: " + action, e);
        }
    }

    public List<AuditLog> findByEntity(String entityType, Object entityId) {
        return Tx.read(con -> auditLogDAO.findByEntity(con, entityType, String.valueOf(entityId)));
    }

    /**
     * Một trang nhật ký khớp bộ lọc.
     * <p>
     * Lấy dữ liệu và đếm tổng trong cùng một kết nối: hai lần mở kết nối riêng thì giữa
     * chúng có thể có bản ghi mới chen vào, và khi đó số trang tính ra không khớp với
     * dữ liệu đang hiển thị.
     */
    public Page<AuditLog> search(String entityType, String action, LocalDateTime from,
                                 LocalDateTime to, int pageNo) {
        int page = Page.safePage(pageNo);
        int offset = Page.offset(page, Page.SIZE);
        return Tx.read(con -> new Page<>(
                auditLogDAO.search(con, entityType, action, from, to, offset, Page.SIZE),
                page, Page.SIZE,
                auditLogDAO.countSearch(con, entityType, action, from, to)));
    }

    /** Vài bản ghi mới nhất cho khung phụ, không phân trang. */
    public List<AuditLog> recent(String entityType, int limit) {
        return Tx.read(con -> auditLogDAO.search(con, entityType, null, null, null, 0, limit));
    }

    public List<String> distinctActions() {
        return Tx.read(auditLogDAO::distinctActions);
    }
}
