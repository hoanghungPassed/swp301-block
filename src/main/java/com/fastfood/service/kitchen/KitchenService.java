package com.fastfood.service.kitchen;

import com.fastfood.common.constant.AuditAction;
import com.fastfood.common.constant.IssueType;
import com.fastfood.common.constant.OrderStatus;
import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.kitchen.KitchenIssueDAO;
import com.fastfood.dao.shared.OrderDAO;
import com.fastfood.dao.shared.OrderItemDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.model.dto.KdsItemView;
import com.fastfood.model.entity.KitchenIssue;
import com.fastfood.model.dto.Page;
import com.fastfood.model.entity.Order;
import com.fastfood.model.entity.OrderItem;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;
import com.fastfood.service.shared.OrderCoreService;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Nghiệp vụ bếp.
 * <p>
 * Bếp chỉ nhìn thấy món của những đơn đã được đưa xuống. Đơn đặt trước đã thanh toán nhưng
 * chưa tới giờ nằm ngoài tầm nhìn của bếp — đó chính là cơ chế giữ cho món không bị làm sớm.
 * <p>
 * Bếp cũng không biết gì về tiền: không thấy trạng thái thanh toán, không đổi được giờ hẹn.
 */
public class KitchenService {

    private final OrderItemDAO orderItemDAO = new OrderItemDAO();
    /** Chỉ để đọc lại đơn khi cần giải thích vì sao một thao tác bị từ chối. */
    private final OrderDAO orderDAO = new OrderDAO();
    private final KitchenIssueDAO issueDAO = new KitchenIssueDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final OrderCoreService orderCore = new OrderCoreService();
    private final AuditService auditService = new AuditService();

    /** Hàng chờ: món chưa ai nhận, sắp theo mức độ gấp. */
    public List<KdsItemView> waitingQueue() {
        List<OrderItem> items = Tx.read(orderItemDAO::findWaitingQueue);
        List<KdsItemView> views = new ArrayList<>(items.size());
        for (OrderItem item : items) {
            views.add(new KdsItemView(item));
        }
        return views;
    }

    /** Việc mà đầu bếp đang làm dở. */
    public List<KdsItemView> myTasks(int userId) {
        List<OrderItem> items = Tx.read(con -> orderItemDAO.findMyTasks(con, userId));
        List<KdsItemView> views = new ArrayList<>(items.size());
        for (OrderItem item : items) {
            views.add(new KdsItemView(item));
        }
        return views;
    }

    /**
     * Mọi món đang nằm trong bếp lúc này, bất kể ai làm — dùng cho ô chọn món ở màn báo sự cố.
     * <p>
     * Không lọc theo người đang đăng nhập: sự cố là chuyện của cả bếp, và người phát hiện món
     * cháy thường không phải người đang đứng nấu nó.
     */
    public List<KdsItemView> itemsInKitchen() {
        List<OrderItem> items = Tx.read(orderItemDAO::findInKitchen);
        List<KdsItemView> views = new ArrayList<>(items.size());
        for (OrderItem item : items) {
            views.add(new KdsItemView(item));
        }
        return views;
    }

    /**
     * Một trang món đã hoàn thành, mới nhất trước.
     *
     * @param assignedTo lọc theo người làm; truyền 0 để xem của cả bếp
     */
    public Page<OrderItem> recentReady(int pageNo, int assignedTo) {
        int page = Page.safePage(pageNo);
        int offset = Page.offset(page, Page.SIZE);
        return Tx.read(con -> new Page<>(
                orderItemDAO.findReadyPage(con, assignedTo, offset, Page.SIZE),
                page, Page.SIZE,
                orderItemDAO.countReady(con, assignedTo)));
    }

    public OrderItem findItem(int orderItemId) {
        OrderItem item = Tx.read(con -> orderItemDAO.findById(con, orderItemId));
        if (item == null) {
            throw new NotFoundException("Không tìm thấy món cần chế biến.");
        }
        return item;
    }

    /**
     * Đầu bếp nhận việc.
     * <p>
     * Điều kiện "món còn ở hàng chờ và chưa ai nhận" nằm ngay trong câu lệnh cập nhật.
     * Hai người cùng bấm nhận một món thì người thứ hai nhận về 0 dòng và được báo món
     * đã có người làm — thay vì ghi đè lên phân công của người trước.
     * <p>
     * Dòng đơn được khoá <b>trước</b> khi ghi, không phải sau. Khách huỷ đơn cũng khoá đúng
     * dòng này rồi mới đếm số món đã vào bếp (xem {@code CustomerOrderService.cancelByCustomer}).
     * Đảo thứ tự thì hai bên chạy song song trên cùng một đơn: cơ sở dữ liệu bật chế độ đọc
     * ảnh chụp nên lệnh đếm của bên huỷ không nhìn thấy món vừa được nhận, và kết quả là
     * đơn bị huỷ trong lúc bếp đã bắt đầu nấu.
     */
    public void claim(int orderItemId, int userId) {
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            OrderItem item = orderItemDAO.findById(con, orderItemId);
            if (item == null) {
                throw new NotFoundException("Không tìm thấy món cần chế biến.");
            }
            orderCore.lockOrder(con, item.getOrderId());

            int changed = orderItemDAO.claim(con, orderItemId, userId, now);
            if (changed == 0) {
                // Câu lệnh gộp bốn điều kiện nên 0 dòng không nói được lý do. Đọc lại đơn để
                // phân biệt hai chuyện khác hẳn nhau: món có người làm rồi, và món của một đơn
                // mà bếp lẽ ra chưa được nhìn thấy.
                Order order = orderDAO.findById(con, item.getOrderId());
                if (order == null || order.getReleasedToKdsAt() == null) {
                    throw new BusinessException("Đơn này chưa tới lượt vào bếp nên chưa nhận được. "
                            + "Đơn đặt trước chỉ xuống bếp trước giờ hẹn của khách.");
                }
                throw new BusinessException("Món này vừa được người khác nhận.");
            }
            auditService.log(con, userId, "ORDER_ITEM", orderItemId,
                    AuditAction.ITEM_START, null, "PREPARING");
            // Đơn chuyển sang đang chế biến khi món đầu tiên được nhận
            orderCore.recalculateStatus(con, item.getOrderId(), now);
        });
    }

    /**
     * Đánh dấu món đã xong.
     * <p>
     * Một dòng món là một việc nguyên khối: đặt 3 phần thì làm xong cả 3 mới đánh dấu,
     * không có trạng thái xong một phần.
     *
     * @return true nếu món này là món cuối cùng và cả đơn vừa chuyển sang sẵn sàng
     */
    public boolean markReady(int orderItemId, int userId) {
        LocalDateTime now = DateTimeUtil.now();
        return Tx.write(con -> {
            OrderItem item = orderItemDAO.findById(con, orderItemId);
            if (item == null) {
                throw new NotFoundException("Không tìm thấy món.");
            }
            int changed = orderItemDAO.markReady(con, orderItemId, userId, now);
            if (changed == 0) {
                // Đơn bị huỷ giữa lúc bếp đang nấu là chuyện có thật, và đầu bếp cần biết ngay
                // để dừng tay — nói "chỉ người đang chế biến mới đánh dấu được" ở tình huống đó
                // là sai và làm họ tưởng mình bấm nhầm nút.
                Order order = orderDAO.findById(con, item.getOrderId());
                if (order != null && !order.isActiveForKitchen()) {
                    throw new BusinessException("Đơn #" + item.getOrderId() + " đã "
                            + (OrderStatus.CANCELLED.name().equals(order.getOrderStatus())
                               ? "bị huỷ" : "kết thúc")
                            + " nên không đánh dấu món được nữa. Vui lòng dừng chế biến.");
                }
                throw new BusinessException("Chỉ người đang chế biến món này mới đánh dấu hoàn thành được.");
            }
            auditService.log(con, userId, "ORDER_ITEM", orderItemId,
                    AuditAction.ITEM_READY, "PREPARING", "READY");
            return orderCore.recalculateStatus(con, item.getOrderId(), now);
        });
    }

    /** Món đầu bếp đã làm xong nhưng chưa đưa ra quầy. */
    public List<KdsItemView> awaitingHandover(int userId) {
        List<OrderItem> items = Tx.read(con -> orderItemDAO.findAwaitingHandover(con, userId));
        List<KdsItemView> views = new ArrayList<>(items.size());
        for (OrderItem item : items) {
            views.add(new KdsItemView(item));
        }
        return views;
    }

    /**
     * Bếp đưa món ra quầy.
     * <p>
     * Tách khỏi lúc đánh dấu món xong chứ không gộp làm một: món xong là lúc tắt bếp, còn đưa
     * ra quầy là lúc rời tay đầu bếp. Giữa hai mốc đó món nằm trong bếp và chưa ai ngoài quầy
     * chịu trách nhiệm về nó — chính là khoảng mà trước đây không ai nhìn thấy, nên món để
     * quên trong bếp chỉ lộ ra khi khách hỏi.
     * <p>
     * Không đụng tới trạng thái đơn: đơn đã sẵn sàng từ lúc món cuối xong, việc bàn giao
     * không làm nó sẵn sàng thêm lần nữa.
     */
    public void handOverToCounter(int orderItemId, int userId) {
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            OrderItem item = orderItemDAO.findById(con, orderItemId);
            if (item == null) {
                throw new NotFoundException("Không tìm thấy món.");
            }
            int changed = orderItemDAO.handOverToCounter(con, orderItemId, userId, now);
            if (changed == 0) {
                // Ba lý do có thể dẫn tới 0 dòng; đọc lại để nói đúng lý do thay vì một câu
                // chung chung khiến đầu bếp bấm đi bấm lại.
                if (item.isHandedOver()) {
                    throw new BusinessException("Món này đã được bàn giao ra quầy rồi.");
                }
                if (!item.isReady()) {
                    throw new BusinessException("Món chưa làm xong nên chưa bàn giao được.");
                }
                throw new BusinessException("Chỉ người đã làm món này mới bàn giao được.");
            }
            auditService.log(con, userId, "ORDER_ITEM", orderItemId,
                    AuditAction.ITEM_HANDED_OVER, "READY", "AT_COUNTER");
        });
    }

    // ------------------------------------------------------------ sự cố bếp

    /**
     * Ghi nhận sự cố: hết nguyên liệu, món hỏng phải làm lại.
     * Sự cố chạy song song với trạng thái món — món đang làm lại vẫn giữ trạng thái
     * đang chế biến chứ không lùi về hàng chờ, để tránh việc bị người khác nhận lại.
     * <p>
     * Riêng <b>hết nguyên liệu</b> còn tắt luôn món đó trên thực đơn. Đây là chỗ duy nhất
     * bếp nói được với phần còn lại của hệ thống mà không cần ai chuyển lời: không tắt thì
     * khách tiếp theo vẫn đặt đúng món vừa hết, và cửa hàng lại nợ thêm một đơn không làm
     * được. Quản trị viên bật lại khi có hàng.
     * <p>
     * Sự cố <b>không</b> tự huỷ món hay tự huỷ đơn. Huỷ đơn là quyết định phải hỏi khách,
     * nên nó thuộc về thu ngân — xem {@code StaffOrderService.cancelByStaff}.
     */
    public void openIssue(int orderItemId, int userId, String issueType, String description) {
        if (issueType == null || issueType.isBlank()) {
            throw new ValidationException("Vui lòng chọn loại sự cố.");
        }
        final IssueType type;
        try {
            type = IssueType.from(issueType);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Loại sự cố không hợp lệ.");
        }
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            OrderItem item = orderItemDAO.findById(con, orderItemId);
            if (item == null) {
                throw new NotFoundException("Không tìm thấy món.");
            }
            KitchenIssue issue = new KitchenIssue();
            issue.setOrderItemId(orderItemId);
            issue.setCreatedBy(userId);
            issue.setIssueType(type.name());
            issue.setDescription(description);
            issue.setStatus("OPEN");
            issue.setCreatedAt(now);
            issueDAO.insert(con, issue);
            auditService.log(con, userId, "ORDER_ITEM", orderItemId,
                    AuditAction.ISSUE_OPENED, null, type.name());

            if (type == IssueType.OUT_OF_STOCK) {
                productDAO.toggleAvailability(con, item.getProductId(), false);
                auditService.log(con, userId, "PRODUCT", item.getProductId(),
                        AuditAction.PRODUCT_CHANGED, "AVAILABLE", "OUT_OF_STOCK");
            }
        });
    }

    /**
     * Sửa mô tả sự cố đang mở.
     * <p>
     * Chỉ sửa được mô tả, không sửa được loại sự cố. Đổi loại là đổi hệ quả — chuyển sang
     * hết nguyên liệu thì phải tắt món, chuyển ngược lại thì phải cân nhắc bật món — nên
     * việc đó đi qua đường thu hồi rồi báo lại, chứ không lặng lẽ đổi tại chỗ.
     */
    public void updateIssue(int issueId, int userId, String description) {
        Tx.writeVoid(con -> {
            KitchenIssue before = requireOwnOpenIssue(con, issueId, userId);
            int changed = issueDAO.updateDescription(con, issueId, userId, description);
            if (changed == 0) {
                throw new BusinessException("Sự cố này vừa được xử lý, không sửa được nữa.");
            }
            auditService.log(con, userId, "KITCHEN_ISSUE", issueId,
                    AuditAction.ISSUE_UPDATED, before.getDescription(), description);
        });
    }

    /**
     * Thu hồi sự cố báo nhầm.
     * <p>
     * Bản ghi được giữ lại ở trạng thái đã huỷ chứ không xoá khỏi bảng: nhật ký thao tác đã
     * ghi lần mở sự cố và trỏ về mã này, xoá hẳn thì dòng nhật ký đó chỉ vào chỗ trống.
     * <p>
     * Báo hết nguyên liệu đã tắt món trên thực đơn, nên thu hồi phải bật lại — nhưng chỉ khi
     * không còn báo hết nào khác đang mở cho cùng món đó.
     */
    public void cancelIssue(int issueId, int userId) {
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            KitchenIssue issue = requireOwnOpenIssue(con, issueId, userId);
            int changed = issueDAO.cancel(con, issueId, userId, now);
            if (changed == 0) {
                throw new BusinessException("Sự cố này vừa được xử lý, không thu hồi được nữa.");
            }
            auditService.log(con, userId, "KITCHEN_ISSUE", issueId,
                    AuditAction.ISSUE_CANCELLED, "OPEN", "CANCELLED");

            if (IssueType.OUT_OF_STOCK.name().equals(issue.getIssueType())) {
                OrderItem item = orderItemDAO.findById(con, issue.getOrderItemId());
                if (item != null
                        && issueDAO.countOpenOutOfStockForProduct(con, item.getProductId()) == 0) {
                    productDAO.toggleAvailability(con, item.getProductId(), true);
                    auditService.log(con, userId, "PRODUCT", item.getProductId(),
                            AuditAction.PRODUCT_CHANGED, "OUT_OF_STOCK", "AVAILABLE");
                }
            }
        });
    }

    /**
     * Sự cố phải còn đang mở và phải do chính người đang thao tác báo.
     * <p>
     * Đọc trước để báo lỗi cho đúng: câu lệnh cập nhật gộp cả hai điều kiện nên khi nó trả về
     * 0 dòng thì không biết là do người khác báo hay do vừa được xử lý xong.
     */
    private KitchenIssue requireOwnOpenIssue(Connection con, int issueId, int userId)
            throws SQLException {
        KitchenIssue issue = issueDAO.findById(con, issueId);
        if (issue == null) {
            throw new NotFoundException("Không tìm thấy sự cố.");
        }
        if (issue.getCreatedBy() != userId) {
            throw new BusinessException("Chỉ người đã báo sự cố này mới sửa hoặc thu hồi được.");
        }
        if (!issue.isOpen()) {
            throw new BusinessException("Sự cố đã khép lại, không sửa hay thu hồi được nữa.");
        }
        return issue;
    }

    public void resolveIssue(int issueId, int userId) {
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            int changed = issueDAO.resolve(con, issueId, now);
            if (changed == 0) {
                throw new BusinessException("Sự cố này đã được xử lý.");
            }
            auditService.log(con, userId, "KITCHEN_ISSUE", issueId,
                    AuditAction.ISSUE_RESOLVED, "OPEN", "RESOLVED");
        });
    }

    public List<KitchenIssue> openIssues() {
        return Tx.read(issueDAO::findOpen);
    }

    /**
     * Sự cố đang mở của một đơn — dùng ở màn hình chi tiết đơn của thu ngân.
     * <p>
     * Bếp phát hiện sự cố nhưng người phải trả lời khách lại là thu ngân, nên thông tin
     * buộc phải đi được sang bên đó. Trước đây nó dừng lại trong bếp và đơn kẹt vô hạn.
     */
    public List<KitchenIssue> openIssuesOfOrder(int orderId) {
        return Tx.read(con -> issueDAO.findOpenByOrder(con, orderId));
    }

    /** Số sự cố chưa xử lý, hiện thành cảnh báo trên màn hình điều phối. */
    public int countOpenIssues() {
        return Tx.read(issueDAO::countOpen);
    }

    public List<KitchenIssue> recentIssues(int limit) {
        return Tx.read(con -> issueDAO.findRecent(con, limit));
    }

    /** Một sự cố cụ thể — dùng để đổ sẵn vào biểu mẫu sửa. */
    public KitchenIssue findIssue(int issueId) {
        KitchenIssue issue = Tx.read(con -> issueDAO.findById(con, issueId));
        if (issue == null) {
            throw new NotFoundException("Không tìm thấy sự cố.");
        }
        return issue;
    }

    public List<KitchenIssue> issuesOfItem(int orderItemId) {
        return Tx.read(con -> issueDAO.findByOrderItem(con, orderItemId));
    }
}
