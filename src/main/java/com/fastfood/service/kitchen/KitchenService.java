package com.fastfood.service.kitchen;

import com.fastfood.common.constant.Constants.AuditAction;
import com.fastfood.common.constant.Constants.IssueType;
import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.kitchen.KitchenIssueDAO;
import com.fastfood.dao.shared.OrderDAO;
import com.fastfood.dao.shared.OrderItemDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.model.dto.Dtos.KdsItemView;
import com.fastfood.model.dto.Dtos.KdsOrderView;
import com.fastfood.model.entity.OperationEntities.KitchenIssue;
import com.fastfood.model.dto.Dtos.Page;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.model.entity.OrderEntities.OrderItem;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;
import com.fastfood.service.shared.OrderCoreService;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class KitchenService {

    private final OrderItemDAO orderItemDAO = new OrderItemDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final KitchenIssueDAO issueDAO = new KitchenIssueDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final OrderCoreService orderCore = new OrderCoreService();
    private final AuditService auditService = new AuditService();

    public List<KdsItemView> waitingQueue() {
        List<OrderItem> items = Tx.read(orderItemDAO::findWaitingQueue);
        List<KdsItemView> views = new ArrayList<>(items.size());
        for (OrderItem item : items) {
            views.add(new KdsItemView(item));
        }
        return views;
    }

    public List<KdsItemView> myTasks(int userId) {
        List<OrderItem> items = Tx.read(con -> orderItemDAO.findMyTasks(con, userId));
        List<KdsItemView> views = new ArrayList<>(items.size());
        for (OrderItem item : items) {
            views.add(new KdsItemView(item));
        }
        return views;
    }

    public List<KdsItemView> itemsInKitchen() {
        List<OrderItem> items = Tx.read(orderItemDAO::findInKitchen);
        List<KdsItemView> views = new ArrayList<>(items.size());
        for (OrderItem item : items) {
            views.add(new KdsItemView(item));
        }
        return views;
    }

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

    /*
     * Bếp làm việc theo đơn: nhận cả đơn, xong cả đơn, bàn giao cả đơn. Ba hàm dưới đây dựng
     * danh sách cho ba khối trên màn hình; phần thao tác lẻ từng món vẫn giữ nguyên bên dưới
     * và chỉ còn lối vào từ trang chi tiết món, dành cho ca hỏng một món giữa đơn.
     */

    public List<KdsOrderView> waitingOrders() {
        return KdsOrderView.group(Tx.read(orderItemDAO::findWaitingQueueOrders));
    }

    public List<KdsOrderView> myOrders(int userId) {
        return KdsOrderView.group(Tx.read(con -> orderItemDAO.findMyOrderItems(con, userId)));
    }

    public List<KdsOrderView> ordersAwaitingHandover(int userId) {
        return KdsOrderView.group(Tx.read(con -> orderItemDAO.findHandoverOrderItems(con, userId)));
    }

    /**
     * Người bếp đang giữ đơn, hoặc {@code null} nếu chưa ai nhận. Mỗi đơn chỉ một người, nên
     * món nào có tên người nhận cũng cho ra cùng một câu trả lời. Trả về nguyên món để nơi gọi
     * lấy được cả mã lẫn tên người đó.
     */
    public OrderItem holderOfOrder(int orderId) {
        return Tx.read(con -> holderOf(orderItemDAO.findByOrder(con, orderId)));
    }

    private static OrderItem holderOf(List<OrderItem> items) {
        for (OrderItem item : items) {
            if (item.getAssignedToUserId() != null) {
                return item;
            }
        }
        return null;
    }

    /**
     * Chặn người thứ hai chen vào một đơn đã có người bếp nhận. Phải gọi sau khi khoá đơn, vì
     * hai đầu bếp bấm cùng lúc thì chỉ khoá mới xếp được ai đọc trước ai đọc sau.
     */
    private static void requireNobodyElseHoldsOrder(List<OrderItem> items, int userId) {
        OrderItem holder = holderOf(items);
        if (holder != null && holder.getAssignedToUserId() != userId) {
            throw new BusinessException("Đơn này đang do "
                    + (holder.getAssignedToName() == null ? "người khác" : holder.getAssignedToName())
                    + " làm. Mỗi đơn chỉ một người bếp nhận.");
        }
    }

    /** Nhận trọn một đơn. Đơn đã có người bếp khác đụng vào thì từ chối, không nhận nửa vời. */
    public void claimOrder(int orderId, int userId) {
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            Order order = orderDAO.findById(con, orderId);
            if (order == null) {
                throw new NotFoundException("Không tìm thấy đơn hàng.");
            }
            orderCore.lockOrder(con, orderId);
            if (order.getReleasedToKdsAt() == null) {
                throw new BusinessException("Đơn này chưa tới lượt vào bếp nên chưa nhận được. "
                        + "Đơn đặt trước chỉ xuống bếp trước giờ hẹn của khách.");
            }

            List<OrderItem> items = orderItemDAO.findByOrder(con, orderId);
            requireNobodyElseHoldsOrder(items, userId);

            int claimed = 0;
            for (OrderItem item : items) {
                if (!"WAITING".equals(item.getItemStatus())) {
                    continue;
                }
                if (orderItemDAO.claim(con, item.getOrderItemId(), userId, now) == 1) {
                    auditService.log(con, userId, "ORDER_ITEM", item.getOrderItemId(),
                            AuditAction.ITEM_START, null, "PREPARING");
                    claimed++;
                }
            }
            if (claimed == 0) {
                /* Người khác chen vào đã bị chặn ở trên, nên hết món để nhận chỉ còn hai lý do:
                   chính tôi đã nhận rồi, hoặc đơn vừa rời khỏi bếp. */
                throw new BusinessException("Đơn này không còn món nào chờ nhận — "
                        + "có thể bạn đã nhận rồi hoặc đơn vừa bị huỷ.");
            }
            orderCore.recalculateStatus(con, orderId, now);
        });
    }

    /** Đánh dấu xong mọi món của đơn mà tôi đang làm. Trả về true nếu cả đơn đã sẵn sàng. */
    public boolean markOrderReady(int orderId, int userId) {
        LocalDateTime now = DateTimeUtil.now();
        return Tx.write(con -> {
            Order order = orderDAO.findById(con, orderId);
            if (order == null) {
                throw new NotFoundException("Không tìm thấy đơn hàng.");
            }
            if (!order.isActiveForKitchen()) {
                throw new BusinessException("Đơn #" + orderId + " đã kết thúc nên không đánh dấu "
                        + "được nữa. Vui lòng dừng chế biến.");
            }

            int done = 0;
            int waiting = 0;
            for (OrderItem item : orderItemDAO.findByOrder(con, orderId)) {
                if ("WAITING".equals(item.getItemStatus())) {
                    waiting++;
                    continue;
                }
                if (!"PREPARING".equals(item.getItemStatus())) {
                    continue;
                }
                if (orderItemDAO.markReady(con, item.getOrderItemId(), userId, now) == 1) {
                    auditService.log(con, userId, "ORDER_ITEM", item.getOrderItemId(),
                            AuditAction.ITEM_READY, "PREPARING", "READY");
                    done++;
                }
            }
            if (done == 0) {
                throw new BusinessException(waiting > 0
                        ? "Đơn còn " + waiting + " món chưa ai nhận. Bấm nhận nốt rồi mới đánh dấu xong."
                        : "Đơn này không còn món nào bạn đang làm.");
            }
            return orderCore.recalculateStatus(con, orderId, now);
        });
    }

    /** Đẩy cả đơn ra quầy. Đơn còn món chưa xong thì chưa đi được — thu ngân nhận một lần cho gọn. */
    public void handOverOrder(int orderId, int userId) {
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            List<OrderItem> items = orderItemDAO.findByOrder(con, orderId);
            if (items.isEmpty()) {
                throw new NotFoundException("Không tìm thấy đơn hàng.");
            }
            for (OrderItem item : items) {
                if ("WAITING".equals(item.getItemStatus()) || "PREPARING".equals(item.getItemStatus())) {
                    throw new BusinessException("Đơn còn món chưa làm xong nên chưa bàn giao được. "
                            + "Làm xong cả đơn rồi đưa ra quầy một lần.");
                }
            }

            int sent = 0;
            for (OrderItem item : items) {
                if (item.getHandedOverAt() != null) {
                    continue;
                }
                if (orderItemDAO.handOverToCounter(con, item.getOrderItemId(), userId, now) == 1) {
                    auditService.log(con, userId, "ORDER_ITEM", item.getOrderItemId(),
                            AuditAction.ITEM_HANDED_OVER, "READY", "AT_COUNTER");
                    sent++;
                }
            }
            if (sent == 0) {
                throw new BusinessException("Đơn này không còn món nào của bạn chờ bàn giao.");
            }
        });
    }

    /**
     * Nhận lẻ một món. Vẫn phải là đơn chưa ai đụng vào hoặc chính đơn tôi đang làm: nhận lẻ
     * một món của đơn người khác là hai người cùng nấu một đơn, mỗi người xong một nửa và
     * không ai bàn giao được trọn đơn ra quầy.
     */
    public void claim(int orderItemId, int userId) {
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            OrderItem item = orderItemDAO.findById(con, orderItemId);
            if (item == null) {
                throw new NotFoundException("Không tìm thấy món cần chế biến.");
            }
            orderCore.lockOrder(con, item.getOrderId());
            requireNobodyElseHoldsOrder(orderItemDAO.findByOrder(con, item.getOrderId()), userId);

            int changed = orderItemDAO.claim(con, orderItemId, userId, now);
            if (changed == 0) {
                Order order = orderDAO.findById(con, item.getOrderId());
                if (order == null || order.getReleasedToKdsAt() == null) {
                    throw new BusinessException("Đơn này chưa tới lượt vào bếp nên chưa nhận được. "
                            + "Đơn đặt trước chỉ xuống bếp trước giờ hẹn của khách.");
                }
                throw new BusinessException("Món này vừa được người khác nhận.");
            }
            auditService.log(con, userId, "ORDER_ITEM", orderItemId,
                    AuditAction.ITEM_START, null, "PREPARING");
            orderCore.recalculateStatus(con, item.getOrderId(), now);
        });
    }

    public boolean markReady(int orderItemId, int userId) {
        LocalDateTime now = DateTimeUtil.now();
        return Tx.write(con -> {
            OrderItem item = orderItemDAO.findById(con, orderItemId);
            if (item == null) {
                throw new NotFoundException("Không tìm thấy món.");
            }
            int changed = orderItemDAO.markReady(con, orderItemId, userId, now);
            if (changed == 0) {
                Order order = orderDAO.findById(con, item.getOrderId());
                if (order != null && !order.isActiveForKitchen()) {
                    throw new BusinessException("Đơn #" + item.getOrderId() + " đã kết thúc "
                            + "nên không đánh dấu món được nữa. Vui lòng dừng chế biến.");
                }
                throw new BusinessException("Chỉ người đang chế biến món này mới đánh dấu hoàn thành được.");
            }
            auditService.log(con, userId, "ORDER_ITEM", orderItemId,
                    AuditAction.ITEM_READY, "PREPARING", "READY");
            return orderCore.recalculateStatus(con, item.getOrderId(), now);
        });
    }

    public List<KdsItemView> awaitingHandover(int userId) {
        List<OrderItem> items = Tx.read(con -> orderItemDAO.findAwaitingHandover(con, userId));
        List<KdsItemView> views = new ArrayList<>(items.size());
        for (OrderItem item : items) {
            views.add(new KdsItemView(item));
        }
        return views;
    }

    public void handOverToCounter(int orderItemId, int userId) {
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            OrderItem item = orderItemDAO.findById(con, orderItemId);
            if (item == null) {
                throw new NotFoundException("Không tìm thấy món.");
            }
            int changed = orderItemDAO.handOverToCounter(con, orderItemId, userId, now);
            if (changed == 0) {
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

    public List<KitchenIssue> openIssuesOfOrder(int orderId) {
        return Tx.read(con -> issueDAO.findOpenByOrder(con, orderId));
    }

    public int countOpenIssues() {
        return Tx.read(issueDAO::countOpen);
    }

    public List<KitchenIssue> recentIssues(int limit) {
        return Tx.read(con -> issueDAO.findRecent(con, limit));
    }

    /** Sự cố đã khép lại trong số bản ghi gần nhất — hai màn quầy và bếp dùng chung. */
    public List<KitchenIssue> recentClosedIssues(int limit) {
        List<KitchenIssue> closed = new ArrayList<>();
        for (KitchenIssue issue : recentIssues(limit)) {
            if (!issue.isOpen()) {
                closed.add(issue);
            }
        }
        return closed;
    }

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
