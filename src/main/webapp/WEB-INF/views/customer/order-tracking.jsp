<c:set var="pageTitle" value="Đơn hàng #${order.orderId}" /><c:set var="nav" value="orders" />
<c:set var="mainClass" value="container medium" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <p class="small mb"><a href="${ctx}/order/history">← Tất cả đơn của tôi</a></p>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <div class="card">
    <div class="row-between mb">
      <div>
        <h1>Đơn hàng #${order.orderId}</h1>
        <p class="muted small">Đặt lúc ${ff:dateTime(order.createdAt)}</p>
      </div>
      <span id="order-status-badge" class="${ff:orderStatusClass(order.orderStatus)}">
        ${ff:orderStatus(order.orderStatus)}
      </span>
    </div>

    <%-- Tiến trình đơn. Bước hiện tại tô đậm để khách biết đang ở đâu. --%>
    <c:if test="${order.orderStatus ne 'CANCELLED' and order.orderStatus ne 'EXPIRED'}">
      <%-- Danh sách có thứ tự chứ không phải các ô rời: tiến trình đơn vốn là một chuỗi
           các bước nối tiếp, và trình đọc màn hình cần biết đang ở bước mấy trên mấy. --%>
      <ol class="steps" aria-label="Tiến trình đơn hàng">
        <c:set var="s" value="${order.orderStatus}" />
        <li class="step ${s ne 'PENDING_PAYMENT' ? 'done' : 'current'}"
            ${s eq 'PENDING_PAYMENT' ? 'aria-current="step"' : ''}>Thanh toán</li>
        <li class="step ${s eq 'CONFIRMED' ? 'current' : (s eq 'PREPARING' or s eq 'READY' or s eq 'COMPLETED' ? 'done' : '')}"
            ${s eq 'CONFIRMED' ? 'aria-current="step"' : ''}>Đã xác nhận</li>
        <li class="step ${s eq 'PREPARING' ? 'current' : (s eq 'READY' or s eq 'COMPLETED' ? 'done' : '')}"
            ${s eq 'PREPARING' ? 'aria-current="step"' : ''}>Đang chế biến</li>
        <li class="step ${s eq 'READY' ? 'current' : (s eq 'COMPLETED' ? 'done' : '')}"
            ${s eq 'READY' ? 'aria-current="step"' : ''}>Sẵn sàng</li>
        <li class="step ${s eq 'COMPLETED' ? 'done' : ''}">Đã nhận</li>
      </ol>
    </c:if>

    <c:if test="${order.overdue}">
      <div class="alert alert-warn">
        Món đã sẵn sàng và đã quá giờ hẹn. Đơn vẫn được giữ tại quầy, bạn tới lấy bất cứ lúc nào.
      </div>
    </c:if>

    <c:if test="${order.orderStatus eq 'PENDING_PAYMENT'}">
      <div class="alert alert-warn">
        Đơn chưa được thanh toán nên cửa hàng chưa nhận. Thanh toán để giữ suất.
        <div class="mt">
          <a class="btn btn-primary btn-sm" href="${ctx}/payment/start?orderId=${order.orderId}">
            Thanh toán ngay
          </a>
        </div>
      </div>
    </c:if>

    <c:if test="${order.orderStatus eq 'EXPIRED'}">
      <div class="alert alert-error">
        Đơn đã hết hạn thanh toán và không còn hiệu lực. Bạn có thể đặt lại đơn mới.
      </div>
    </c:if>

    <div class="grid grid-2 mt">
      <div>
        <h3>Thông tin nhận hàng</h3>
        <div class="total-line"><span class="muted">Hình thức</span><span>${ff:orderSource(order.orderSource)}</span></div>
        <c:if test="${order.online}">
          <div class="total-line">
            <span class="muted">Giờ hẹn</span>
            <span><strong>${ff:dateTime(order.pickupTime)}</strong></span>
          </div>
          <c:if test="${order.orderStatus eq 'CONFIRMED' or order.orderStatus eq 'PREPARING'}">
            <div class="total-line">
              <span class="muted">Trạng thái bếp</span>
              <span>${ff:releaseState(order.releaseState)}</span>
            </div>
          </c:if>
        </c:if>
        <c:if test="${not empty order.readyAt}">
          <div class="total-line"><span class="muted">Sẵn sàng lúc</span><span>${ff:dateTime(order.readyAt)}</span></div>
        </c:if>
        <c:if test="${not empty order.pickedUpAt}">
          <div class="total-line"><span class="muted">Đã nhận lúc</span><span>${ff:dateTime(order.pickedUpAt)}</span></div>
        </c:if>
        <c:if test="${not empty order.latestPayment}">
          <div class="total-line">
            <span class="muted">Thanh toán</span>
            <span class="${ff:paymentStatusClass(order.latestPayment.paymentStatus)}">
              ${ff:paymentStatus(order.latestPayment.paymentStatus)}
            </span>
          </div>
        </c:if>
      </div>

      <div>
        <c:if test="${not empty order.pickupCode}">
          <h3>Mã nhận hàng</h3>
          <div class="pickup-code"><c:out value="${order.pickupCode}"/></div>
          <c:if test="${not empty qrDataUri}">
            <div class="qr-box"><img src="${qrDataUri}" alt="Mã QR nhận hàng" width="160" height="160"></div>
          </c:if>
          <p class="small muted">Đưa mã này cho nhân viên tại quầy để nhận món.</p>
        </c:if>
      </div>
    </div>
  </div>

  <div class="card pad0 table-wrap">
    <div class="card-head"><h2>Món đã đặt</h2></div>
    <table>
      <thead><tr><th scope="col">Món</th><th scope="col" class="center">SL</th><th scope="col" class="num">Đơn giá</th><th scope="col" class="num">Thành tiền</th><th scope="col" class="center">Bếp</th></tr></thead>
      <tbody>
        <c:forEach var="item" items="${order.items}">
          <tr>
            <td><c:out value="${item.productNameSnapshot}"/></td>
            <td class="center">${item.quantity}</td>
            <td class="num">${ff:money(item.unitPrice)}</td>
            <td class="num">${ff:money(item.lineTotal)}</td>
            <td class="center">
              <c:choose>
                <c:when test="${empty order.releasedToKdsAt}">
                  <span class="tag tag-muted">Chưa vào bếp</span>
                </c:when>
                <c:otherwise>
                  <span class="${ff:itemStatusClass(item.itemStatus)}">${ff:itemStatus(item.itemStatus)}</span>
                </c:otherwise>
              </c:choose>
            </td>
          </tr>
        </c:forEach>
      </tbody>
      <tfoot>
        <tr><td colspan="3"><strong>Tổng cộng</strong></td>
            <td class="num"><strong>${ff:money(order.totalAmount)}</strong></td><td></td></tr>
      </tfoot>
    </table>
  </div>

  <%-- Tin đã gửi cho khách về chính đơn này. Đặt ngay dưới danh sách món chứ không để riêng ở
       hộp thông báo: khi khách thắc mắc "sao đơn của tôi thành ra thế này", họ mở đơn ra xem
       chứ không đi tìm hộp thư, và câu trả lời — huỷ vì lý do gì, tiền đã hoàn chưa — nằm
       trong mấy dòng này. --%>
  <c:if test="${not empty notifications}">
    <div class="card pad0">
      <div class="card-head"><h2>Tin đã gửi cho bạn</h2></div>
      <ul class="notice-list">
        <c:forEach var="n" items="${notifications}">
          <li class="notice">
            <div class="notice-icon" aria-hidden="true">${ff:notificationIcon(n.eventType)}</div>
            <div class="grow">
              <div class="row-between">
                <span class="${ff:notificationEventClass(n.eventType)}">
                  ${ff:notificationEvent(n.eventType)}
                </span>
                <span class="small muted">${ff:dateTime(n.sentAt)}</span>
              </div>
              <p class="small mt-tight"><c:out value="${n.content}"/></p>
              <c:if test="${n.failed}">
                <div class="mt-tight"><span class="tag tag-red">Gửi tới email không thành công</span></div>
              </c:if>
            </div>
          </li>
        </c:forEach>
      </ul>
    </div>
  </c:if>

  <c:if test="${order.cancellable}">
    <div class="card">
      <h3>Huỷ đơn</h3>
      <p class="small muted mb">
        <c:choose>
          <c:when test="${order.orderStatus eq 'PENDING_PAYMENT'}">
            Đơn chưa thanh toán nên huỷ được ngay, bạn không bị trừ đồng nào. Huỷ xong là đặt
            đơn mới được — mỗi lúc chỉ giữ một đơn chờ thanh toán.
          </c:when>
          <c:otherwise>
            Bếp chưa nhận làm món nào của đơn này nên bạn vẫn huỷ được, tiền sẽ hoàn lại đầy đủ.
            Khi có đầu bếp bắt đầu chế biến thì không huỷ ở đây được nữa — lúc đó vui lòng liên hệ
            nhân viên tại quầy.
          </c:otherwise>
        </c:choose>
      </p>
      <form method="post" action="${ctx}/order/track"
            data-confirm="Huỷ đơn hàng này?">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <input type="hidden" name="orderId" value="${order.orderId}">
        <button type="submit" class="btn btn-danger">Huỷ đơn hàng</button>
      </form>
    </div>
  </c:if>

  <%-- Dấu hiệu để app.js tự cập nhật trạng thái, khách không phải bấm tải lại khi đang chờ món. --%>
  <c:if test="${order.orderStatus eq 'CONFIRMED' or order.orderStatus eq 'PREPARING'}">
    <noscript>
      <div class="alert alert-info">
        Trình duyệt đang tắt JavaScript nên trạng thái đơn không tự cập nhật.
        Bấm tải lại trang để xem đơn đã tới bước nào.
      </div>
    </noscript>
    <div id="order-watch" hidden
         data-endpoint="${ctx}/api/order/status?orderId=${order.orderId}"
         data-status="${order.orderStatus}"></div>
  </c:if>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
