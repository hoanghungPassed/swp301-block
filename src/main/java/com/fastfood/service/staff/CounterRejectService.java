package com.fastfood.service.staff;

import com.fastfood.common.constant.Constants.AuditAction;
import com.fastfood.common.constant.Constants.IssueType;
import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.kitchen.KitchenIssueDAO;
import com.fastfood.dao.shared.OrderItemDAO;
import com.fastfood.model.entity.OperationEntities.KitchenIssue;
import com.fastfood.model.entity.OrderEntities.OrderItem;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class CounterRejectService {

    private final KitchenIssueDAO issueDAO = new KitchenIssueDAO();
    private final OrderItemDAO orderItemDAO = new OrderItemDAO();
    private final AuditService auditService = new AuditService();

    public List<KitchenIssue> openRejects() {
        return Tx.read(issueDAO::findOpen).stream()
                .filter(i -> IssueType.COUNTER_REJECT.name().equals(i.getIssueType()))
                .collect(Collectors.toList());
    }

    public KitchenIssue findById(int issueId) {
        KitchenIssue issue = Tx.read(con -> issueDAO.findById(con, issueId));
        if (issue == null) {
            throw new NotFoundException("Không tìm thấy phiếu từ chối.");
        }
        return issue;
    }

    public KitchenIssue reject(int orderItemId, int cashierId, String reason) {
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty()) {
            throw new ValidationException("Vui lòng nhập lý do từ chối để bếp biết cần sửa gì.");
        }
        LocalDateTime now = DateTimeUtil.now();

        return Tx.write(con -> {
            OrderItem item = orderItemDAO.findById(con, orderItemId);
            if (item == null) {
                throw new NotFoundException("Không tìm thấy món.");
            }
            if (item.getHandedOverAt() == null) {
                throw new BusinessException("Bếp chưa bàn giao món này ra quầy.");
            }
            if (item.isReceived()) {
                throw new BusinessException("Món này đã được nhận rồi, không từ chối được nữa.");
            }

            if (orderItemDAO.returnToKitchen(con, orderItemId) == 0) {
                throw new BusinessException("Món này vừa được nhận tại quầy nên không trả về bếp "
                        + "được nữa. Vui lòng tải lại màn hình.");
            }

            KitchenIssue issue = new KitchenIssue();
            issue.setOrderItemId(orderItemId);
            issue.setCreatedBy(cashierId);
            issue.setIssueType(IssueType.COUNTER_REJECT.name());
            issue.setDescription(text);
            issue.setStatus("OPEN");
            issue.setCreatedAt(now);
            issueDAO.insert(con, issue);

            auditService.log(con, cashierId, "ORDER_ITEM", orderItemId,
                    AuditAction.ISSUE_OPENED, "AT_COUNTER", IssueType.COUNTER_REJECT.name());
            return issue;
        });
    }

    public void updateReason(int issueId, int cashierId, String reason) {
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty()) {
            throw new ValidationException("Vui lòng nhập lý do từ chối.");
        }
        Tx.writeVoid(con -> {
            KitchenIssue before = requireOwnOpen(con, issueId, cashierId);
            issueDAO.updateDescription(con, issueId, cashierId, text);
            auditService.log(con, cashierId, "KITCHEN_ISSUE", issueId,
                    AuditAction.ISSUE_UPDATED, before.getDescription(), text);
        });
    }

    public void cancel(int issueId, int cashierId) {
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            requireOwnOpen(con, issueId, cashierId);
            int changed = issueDAO.cancel(con, issueId, cashierId, now);
            if (changed == 0) {
                throw new BusinessException("Phiếu này vừa được xử lý, không thu hồi được nữa.");
            }
            auditService.log(con, cashierId, "KITCHEN_ISSUE", issueId,
                    AuditAction.ISSUE_CANCELLED, "OPEN", "CANCELLED");
        });
    }

    private KitchenIssue requireOwnOpen(Connection con, int issueId, int cashierId)
            throws SQLException {
        KitchenIssue issue = issueDAO.findById(con, issueId);
        if (issue == null) {
            throw new NotFoundException("Không tìm thấy phiếu từ chối.");
        }
        if (!IssueType.COUNTER_REJECT.name().equals(issue.getIssueType())) {
            throw new BusinessException("Đây là sự cố do bếp báo, xử lý ở màn hình bếp.");
        }
        if (issue.getCreatedBy() != cashierId) {
            throw new BusinessException("Chỉ người đã lập phiếu này mới sửa hoặc thu hồi được.");
        }
        if (!issue.isOpen()) {
            throw new BusinessException("Phiếu đã khép lại, không sửa hay thu hồi được nữa.");
        }
        return issue;
    }
}
