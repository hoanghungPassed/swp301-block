package com.fastfood.service.kitchen;

import com.fastfood.common.constant.Constants.AuditAction;
import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.JdbcSupport;
import com.fastfood.dao.kitchen.PrepTaskDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.model.entity.OperationEntities.PrepTask;
import com.fastfood.model.entity.MenuEntities.Product;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class PrepService {

    private static final int MAX_QTY = 999;

    private final PrepTaskDAO prepTaskDAO = new PrepTaskDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final AuditService auditService = new AuditService();

    public List<PrepTask> planOf(LocalDate date) {
        LocalDate day = date == null ? DateTimeUtil.now().toLocalDate() : date;
        return Tx.read(con -> prepTaskDAO.findByDate(con, day));
    }

    public PrepTask findById(int prepTaskId) {
        PrepTask task = Tx.read(con -> prepTaskDAO.findById(con, prepTaskId));
        if (task == null) {
            throw new NotFoundException("Không tìm thấy dòng kế hoạch.");
        }
        return task;
    }

    public PrepTask plan(int productId, LocalDate prepDate, int plannedQty, String note, int userId) {
        requireSaneQty(plannedQty, "Số lượng dự kiến");
        LocalDateTime now = DateTimeUtil.now();
        LocalDate day = prepDate == null ? now.toLocalDate() : prepDate;
        if (day.isBefore(now.toLocalDate())) {
            throw new ValidationException("Không lập được kế hoạch cho ngày đã qua.");
        }

        try {
            return Tx.write(con -> {
                Product product = productDAO.findById(con, productId);
                if (product == null) {
                    throw new NotFoundException("Không tìm thấy món.");
                }
                if (!product.isOrderable()) {
                    throw new BusinessException("Món \"" + product.getName()
                            + "\" hiện không còn phục vụ nên không cần chuẩn bị sẵn.");
                }

                PrepTask task = new PrepTask();
                task.setProductId(productId);
                task.setPrepDate(day);
                task.setPlannedQty(plannedQty);
                task.setDoneQty(0);
                task.setNote(note);
                task.setCreatedBy(userId);
                task.setCreatedAt(now);
                task.setStatus("PLANNED");
                prepTaskDAO.insert(con, task);

                auditService.log(con, userId, "PREP_TASK", task.getPrepTaskId(),
                        AuditAction.PREP_PLANNED, null, product.getName() + " x" + plannedQty);
                task.setProductName(product.getName());
                return task;
            });
        } catch (RuntimeException e) {
            if (!JdbcSupport.isUniqueViolation(e)) {
                throw e;
            }
            throw new BusinessException("Món này đã có dòng kế hoạch cho ngày "
                    + DateTimeUtil.formatDate(day.atStartOfDay())
                    + ". Hãy sửa dòng đang có thay vì lập thêm.");
        }
    }

    public void update(int prepTaskId, int plannedQty, int doneQty, String note, int userId) {
        requireSaneQty(plannedQty, "Số lượng dự kiến");
        if (doneQty < 0 || doneQty > MAX_QTY) {
            throw new ValidationException("Số đã làm phải từ 0 đến " + MAX_QTY + ".");
        }
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            PrepTask before = requireEditable(con, prepTaskId);
            int changed = prepTaskDAO.update(con, prepTaskId, plannedQty, doneQty, note, now);
            if (changed == 0) {
                throw new BusinessException("Dòng kế hoạch này vừa được chốt hoặc thu hồi, không sửa được nữa.");
            }
            auditService.log(con, userId, "PREP_TASK", prepTaskId, AuditAction.PREP_UPDATED,
                    before.getPlannedQty() + "/" + before.getDoneQty(),
                    plannedQty + "/" + doneQty);
        });
    }

    public void markDone(int prepTaskId, int userId) {
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            requireEditable(con, prepTaskId);
            int changed = prepTaskDAO.markDone(con, prepTaskId, now);
            if (changed == 0) {
                throw new BusinessException("Dòng kế hoạch này vừa được chốt hoặc thu hồi.");
            }
            auditService.log(con, userId, "PREP_TASK", prepTaskId,
                    AuditAction.PREP_DONE, "PLANNED", "DONE");
        });
    }

    public void cancel(int prepTaskId, int userId) {
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            PrepTask task = requireEditable(con, prepTaskId);
            if (task.getCreatedBy() != userId) {
                throw new BusinessException("Chỉ người đã lập dòng này mới thu hồi được.");
            }
            int changed = prepTaskDAO.cancel(con, prepTaskId, userId, now);
            if (changed == 0) {
                throw new BusinessException("Dòng kế hoạch này vừa được chốt hoặc thu hồi.");
            }
            auditService.log(con, userId, "PREP_TASK", prepTaskId,
                    AuditAction.PREP_CANCELLED, "PLANNED", "CANCELLED");
        });
    }

    private PrepTask requireEditable(Connection con, int prepTaskId) throws SQLException {
        PrepTask task = prepTaskDAO.findById(con, prepTaskId);
        if (task == null) {
            throw new NotFoundException("Không tìm thấy dòng kế hoạch.");
        }
        if (!task.isPlanned()) {
            throw new BusinessException("Dòng kế hoạch đã khép lại, không sửa hay thu hồi được nữa.");
        }
        return task;
    }

    private void requireSaneQty(int qty, String label) {
        if (qty < 1 || qty > MAX_QTY) {
            throw new ValidationException(label + " phải từ 1 đến " + MAX_QTY + ".");
        }
    }
}
