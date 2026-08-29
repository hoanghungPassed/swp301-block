package com.fastfood.service.shared;

import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.shared.AuditLogDAO;
import com.fastfood.model.dto.Dtos.Page;
import com.fastfood.model.entity.OperationEntities.AuditLog;
import com.fastfood.service.Tx;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AuditService {

    private static final Logger LOG = Logger.getLogger(AuditService.class.getName());

    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    /** Ghi ai đã thay đổi đối tượng nào, giá trị trước/sau, trong cùng transaction nghiệp vụ. */
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

    /** Ghi thay đổi tự động như hết hạn thanh toán khi không có người dùng trực tiếp thao tác. */
    public void logSystem(Connection con, String entityType, Object entityId,
                          String action, String newValue) throws SQLException {
        log(con, null, entityType, entityId, action, null, newValue);
    }

    /** Cố lưu riêng một thao tác bị từ chối nhưng không để lỗi audit làm hỏng phản hồi chính. */
    public void logRejected(Integer actorId, String entityType, Object entityId,
                            String action, String oldValue, String newValue) {
        try {
            Tx.writeVoid(con -> log(con, actorId, entityType, entityId, action, oldValue, newValue));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Khong ghi duoc nhat ky cho thao tac bi tu choi: " + action, e);
        }
    }

    /** Lấy lịch sử biến động của một đơn, payment, review hoặc đối tượng cụ thể. */
    public List<AuditLog> findByEntity(String entityType, Object entityId) {
        return Tx.read(con -> auditLogDAO.findByEntity(con, entityType, String.valueOf(entityId)));
    }

    /** Tìm và phân trang nhật ký theo loại đối tượng, hành động và khoảng thời gian. */
    public Page<AuditLog> search(String entityType, String action, LocalDateTime from,
                                 LocalDateTime to, int pageNo) {
        int page = Page.safePage(pageNo);
        int offset = Page.offset(page, Page.SIZE);
        return Tx.read(con -> new Page<>(
                auditLogDAO.search(con, entityType, action, from, to, offset, Page.SIZE),
                page, Page.SIZE,
                auditLogDAO.countSearch(con, entityType, action, from, to)));
    }

    /** Lấy nhanh các thay đổi mới nhất của một loại đối tượng. */
    public List<AuditLog> recent(String entityType, int limit) {
        return Tx.read(con -> auditLogDAO.search(con, entityType, null, null, null, 0, limit));
    }

    /** Cấp danh sách loại hành động cho bộ lọc nhật ký. */
    public List<String> distinctActions() {
        return Tx.read(auditLogDAO::distinctActions);
    }
}
