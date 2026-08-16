package com.fastfood.service.kitchen;

import com.fastfood.common.constant.AuditAction;
import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.JdbcSupport;
import com.fastfood.dao.kitchen.PrepTaskDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.model.entity.PrepTask;
import com.fastfood.model.entity.Product;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Kế hoạch chuẩn bị sẵn của bếp — hôm nay làm trước bao nhiêu phần mỗi món.
 * <p>
 * Đây là phần việc duy nhất của bếp <b>không bắt nguồn từ đơn hàng</b>: bếp đồ ăn nhanh làm sẵn
 * theo dự đoán chứ không đợi khách gọi. Vì vậy nó không đụng tới {@code OrderItem} và không
 * làm đổi trạng thái đơn nào — hoàn toàn tách khỏi luồng trong {@link KitchenService}.
 * <p>
 * Hiển thị ở màn hàng chờ <code>/kitchen/queue</code>, ngay cạnh danh sách món đang tới: đầu bếp
 * cần nhìn hai thứ cùng lúc mới quyết được nên làm sẵn thêm hay dừng lại.
 */
public class PrepService {

    /** Giới hạn khớp với {@code CK_Prep_planned} và {@code CK_Prep_done} trong lược đồ. */
    private static final int MAX_QTY = 999;

    private final PrepTaskDAO prepTaskDAO = new PrepTaskDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final AuditService auditService = new AuditService();

    // ============================================================ đọc

    /** Kế hoạch của một ngày. Món còn thiếu nhiều nhất xếp lên đầu. */
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

    // ============================================================ lập kế hoạch

    /**
     * Lập kế hoạch cho một món trong một ngày.
     * <p>
     * Món phải còn được bán: lập kế hoạch làm sẵn một món đã ngừng phục vụ là chuẩn bị nguyên
     * liệu cho thứ không ai đặt được.
     * <p>
     * Trùng món trong cùng ngày bị chặn bằng ràng buộc duy nhất chứ không bằng một lượt đọc
     * trước — hai đầu bếp bấm gần như cùng lúc thì cả hai đều đọc thấy "chưa có", và chỉ ràng
     * buộc ở cơ sở dữ liệu mới chặn được lần thứ hai.
     */
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

    // ============================================================ sửa

    /**
     * Sửa số dự kiến, số đã làm và ghi chú.
     * <p>
     * Số đã làm nằm chung một lượt sửa với số dự kiến vì trong bếp hai con số đó luôn được
     * nhìn cùng nhau: thấy làm hụt thì hoặc làm bù, hoặc hạ dự kiến xuống cho khớp thực tế.
     */
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

    /** Chốt dòng kế hoạch đã làm xong. Chốt rồi thì không sửa được nữa. */
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

    /**
     * Thu hồi dòng lập nhầm.
     * <p>
     * Chỉ người lập mới thu hồi được, cùng quy tắc với sự cố bếp. Bản ghi giữ lại ở trạng thái
     * đã thu hồi chứ không xoá khỏi bảng: nhật ký thao tác đã trỏ về mã này, xoá hẳn thì dòng
     * nhật ký đó chỉ vào chỗ trống.
     */
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

    // ============================================================ dùng chung

    /**
     * Đọc trước để báo lỗi cho đúng: câu lệnh cập nhật gộp cả điều kiện trạng thái vào chính
     * nó, nên khi trả về 0 dòng thì không phân biệt được là không tồn tại hay đã khép lại.
     */
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
