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

    public void logSystem(Connection con, String entityType, Object entityId,
                          String action, String newValue) throws SQLException {
        log(con, null, entityType, entityId, action, null, newValue);
    }

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

    public Page<AuditLog> search(String entityType, String action, LocalDateTime from,
                                 LocalDateTime to, int pageNo) {
        int page = Page.safePage(pageNo);
        int offset = Page.offset(page, Page.SIZE);
        return Tx.read(con -> new Page<>(
                auditLogDAO.search(con, entityType, action, from, to, offset, Page.SIZE),
                page, Page.SIZE,
                auditLogDAO.countSearch(con, entityType, action, from, to)));
    }

    public List<AuditLog> recent(String entityType, int limit) {
        return Tx.read(con -> auditLogDAO.search(con, entityType, null, null, null, 0, limit));
    }

    public List<String> distinctActions() {
        return Tx.read(auditLogDAO::distinctActions);
    }
}
