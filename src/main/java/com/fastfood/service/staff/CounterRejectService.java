package com.fastfood.service.staff;

import com.fastfood.common.constant.AuditAction;
import com.fastfood.common.constant.IssueType;
import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.kitchen.KitchenIssueDAO;
import com.fastfood.dao.shared.OrderItemDAO;
import com.fastfood.model.entity.KitchenIssue;
import com.fastfood.model.entity.OrderItem;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Thu ngân từ chối nhận món bếp đưa ra quầy — sai món, nguội, thiếu phần.
 * <p>
 * <b>Vá một đường đi còn thiếu.</b> Trước đây {@code StaffOrderService.receiveAtCounter} chỉ có
 * một lối: <i>nhận</i>. Thu ngân phát hiện món có vấn đề thì không có nút nào để trả về bếp —
 * họ buộc phải nhận rồi đi nói miệng, và chuyện đó không để lại dấu vết nào.
 * <p>
 * Dùng chung bảng {@code KitchenIssue} với sự cố bếp chứ không tạo bảng riêng: cùng là một món
 * có vấn đề, cùng vòng đời mở → xử lý xong, và cùng phải hiện lên cả hai màn hình. Khác nhau ở
 * người tạo, mà cột {@code created_by} đã ghi lại điều đó.
 * <p>
 * <b>Từ chối là một lần trả món, không chỉ là một tờ giấy.</b> Phiếu đi kèm việc xoá hai mốc
 * bàn giao trên dòng món, nên món quay lại danh sách "chờ bàn giao ra quầy" của chính người đã
 * nấu nó. Chỉ ghi phiếu mà để nguyên mốc bàn giao thì món kẹt ở giữa: bếp không còn thấy nó ở
 * danh sách việc nào, quầy vẫn thấy nó nằm chờ, và cả đơn không giao được cho khách cho tới khi
 * thu ngân bấm nhận đúng món mình vừa từ chối.
 */
public class CounterRejectService {

    private final KitchenIssueDAO issueDAO = new KitchenIssueDAO();
    private final OrderItemDAO orderItemDAO = new OrderItemDAO();
    private final AuditService auditService = new AuditService();

    /** Phiếu từ chối còn đang mở — hiện ở màn Quầy giao nhận. */
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

    /**
     * Từ chối nhận một món và trả nó về bếp.
     * <p>
     * Chỉ từ chối được món bếp đã bàn giao ra quầy mà quầy chưa nhận — đúng khoảng thời gian mà
     * món nằm chờ trên quầy. Món chưa bàn giao thì chưa tới tay thu ngân, món đã nhận rồi thì
     * phải xử lý bằng đường khác vì nó đã được ghi là đang ở quầy.
     * <p>
     * Hai việc nằm trong <b>một giao dịch</b>: trả món về bếp và lập phiếu. Tách ra thì một
     * bên hỏng để lại đúng tình trạng cần tránh — món về bếp mà không ai biết vì sao, hoặc
     * phiếu tố cáo một món vẫn đang nằm trên quầy.
     */
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

            // Trả món về bếp. Hai lần đọc ở trên là để nói đúng lý do; chốt chặn thật nằm ở
            // điều kiện trong câu lệnh này — quầy có hai người, và người kia có thể vừa bấm
            // nhận đúng món đang bị từ chối.
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

            // Giá trị cũ ghi "AT_COUNTER" chứ không để trống như sự cố bếp: dòng này vừa mở
            // phiếu vừa dời món khỏi quầy, và nhật ký phải đọc ra được cả chỗ món vừa rời đi.
            auditService.log(con, cashierId, "ORDER_ITEM", orderItemId,
                    AuditAction.ISSUE_OPENED, "AT_COUNTER", IssueType.COUNTER_REJECT.name());
            return issue;
        });
    }

    /** Sửa lý do — chỉ người đã lập phiếu, và chỉ khi phiếu còn mở. */
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

    /**
     * Thu hồi phiếu lập nhầm. Bản ghi giữ lại ở trạng thái đã huỷ chứ không xoá: nhật ký thao tác
     * đã trỏ về mã này — cùng cách làm với sự cố bếp.
     * <p>
     * Thu hồi phiếu <b>không</b> kéo món trở lại quầy: món đã thật sự nằm trong bếp rồi, và đường
     * ra vẫn là bếp bấm bàn giao lần nữa. Tự đặt lại mốc bàn giao ở đây là ghi vào dữ liệu một
     * việc chưa ai làm.
     */
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
