<c:set var="pageTitle" value="Thông báo" /><c:set var="nav" value="notifications" />
<c:set var="mainClass" value="container medium" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head row-between">
    <div>
      <h1>Thông báo</h1>
      <p>Những gì đã xảy ra với đơn của bạn — xác nhận, món sẵn sàng, huỷ đơn và hoàn tiền.</p>
    </div>
    <%-- Nút chỉ hiện khi thật sự còn tin chưa đọc: bấm vào một nút không đổi được gì
         chỉ làm người dùng nghi ngờ là mình bấm hụt. --%>
    <c:if test="${not empty unreadNotifications and unreadNotifications > 0}">
      <form method="post" action="${ctx}/notifications">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <button type="submit" class="btn">Đánh dấu đã đọc hết (${unreadNotifications})</button>
      </form>
    </c:if>
  </div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <div class="card pad0">
    <c:choose>
      <c:when test="${pageData.emptyPage}">
        <div class="empty">
          <div class="icon" aria-hidden="true">🔔</div>
          Chưa có thông báo nào.
          <div class="small mt">Đặt đơn đầu tiên và bạn sẽ nhận được tin ngay khi cửa hàng
            xác nhận, rồi khi món làm xong.</div>
          <div class="mt"><a class="btn btn-primary" href="${ctx}/menu">Xem thực đơn</a></div>
        </div>
      </c:when>
      <c:otherwise>
        <ul class="notice-list">
          <c:forEach var="n" items="${pageData.items}">
            <li class="notice ${n.unread ? 'unread' : ''}">
              <div class="notice-icon" aria-hidden="true">${ff:notificationIcon(n.eventType)}</div>
              <div class="grow">
                <div class="row-between">
                  <div>
                    <span class="${ff:notificationEventClass(n.eventType)}">
                      ${ff:notificationEvent(n.eventType)}
                    </span>
                    <c:if test="${n.unread}">
                      <%-- Chữ chứ không phải chấm màu: một chấm đỏ không đọc lên được, nên
                           người dùng trình đọc màn hình không biết dòng nào là tin mới. --%>
                      <span class="tag tag-amber">Mới</span>
                    </c:if>
                  </div>
                  <span class="small muted">${ff:dateTime(n.sentAt)}</span>
                </div>
                <p class="small mt-tight"><c:out value="${n.content}"/></p>
                <div class="actions mt-tight">
                  <a class="btn btn-sm" href="${ctx}/order/track?orderId=${n.orderId}">
                    Xem đơn #${n.orderId}
                  </a>
                  <c:if test="${n.failed}">
                    <%-- Gửi hỏng thì tin này vẫn nằm đây, nhưng khách cần biết là hộp thư của
                         họ không có gì cả — nếu không, lần sau họ vẫn ngồi đợi thư. --%>
                    <span class="tag tag-red">Gửi tới email không thành công</span>
                  </c:if>
                </div>
              </div>
            </li>
          </c:forEach>
        </ul>
      </c:otherwise>
    </c:choose>
    <%@ include file="/WEB-INF/views/layout/pager.jspf" %>
  </div>

  <p class="small muted">
    Tin được giữ lại đầy đủ, kể cả của đơn đã đóng — đây là chỗ tra lại vì sao một đơn cũ bị
    huỷ và khoản tiền đã được hoàn hay chưa.
  </p>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
