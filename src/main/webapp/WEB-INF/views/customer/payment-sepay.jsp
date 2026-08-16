<c:set var="pageTitle" value="Chuyển khoản thanh toán" />
<c:set var="mainClass" value="container narrow" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <div class="card">
    <h1>Quét mã để thanh toán</h1>
    <p class="muted small">
      Đơn hàng #${orderId} · Mở ứng dụng ngân hàng, quét mã bên dưới rồi xác nhận chuyển khoản.
      Trang này tự cập nhật ngay khi cửa hàng nhận được tiền, bạn không cần bấm gì thêm.
    </p>

    <div class="qr-box mt">
      <%-- Mã QR do SePay dựng sẵn, đã điền đủ số tài khoản, số tiền và nội dung. Đặt kích
           thước cố định để chỗ dành cho ảnh không co giãn trong lúc ảnh đang tải — mã nhảy
           vị trí ngay khi khách vừa đưa điện thoại lên là đủ để phải quét lại từ đầu. --%>
      <img src="${qrImageUrl}" alt="Mã QR chuyển khoản đơn hàng #${orderId}"
           width="280" height="280">
    </div>

    <div class="mt mb">
      <div class="total-line"><span class="muted">Ngân hàng</span><span><c:out value="${bank}"/></span></div>
      <div class="total-line">
        <span class="muted">Số tài khoản</span>
        <span class="mono"><c:out value="${accountNumber}"/></span>
      </div>
      <c:if test="${not empty accountName}">
        <div class="total-line"><span class="muted">Chủ tài khoản</span><span><c:out value="${accountName}"/></span></div>
      </c:if>
      <div class="total-line">
        <span class="muted">Nội dung chuyển khoản</span>
        <span class="mono"><strong><c:out value="${transferContent}"/></strong></span>
      </div>
      <div class="total-line grand">
        <span>Số tiền</span><span>${ff:money(payment.amount)}</span>
      </div>
    </div>

    <%-- Hai điều kiện duy nhất khiến một lần chuyển khoản đúng bị hệ thống bỏ sót. Nói trước
         ở đây rẻ hơn rất nhiều so với đối soát bằng tay sau đó. --%>
    <div class="alert alert-warn">
      <strong>Giữ nguyên nội dung <span class="mono"><c:out value="${transferContent}"/></span>
      và đúng số tiền.</strong>
      Đây là hai thứ duy nhất cho hệ thống biết khoản tiền này là của đơn nào. Sai một trong hai
      thì tiền vẫn tới cửa hàng nhưng đơn không tự xác nhận được, và phải chờ nhân viên đối
      chiếu bằng tay.
    </div>

    <p class="small muted">
      Đơn được giữ trong ${expiryMinutes} phút kể từ lúc đặt. Quá thời gian đó mà chưa nhận được
      tiền thì đơn hết hiệu lực và suất được trả lại cho khách khác — lúc ấy bạn đặt đơn mới.
    </p>

    <div class="stack mt">
      <a class="btn btn-block" href="${ctx}/order/track?orderId=${orderId}">
        Để sau — quay lại đơn hàng
      </a>
    </div>
  </div>

  <noscript>
    <div class="alert alert-info">
      Trình duyệt đang tắt JavaScript nên trang không tự cập nhật. Sau khi chuyển khoản xong,
      bấm <a href="${ctx}/order/track?orderId=${orderId}">xem đơn hàng</a> để kiểm tra trạng thái.
    </div>
  </noscript>

  <%-- Dấu hiệu để app.js hỏi lại trạng thái đơn: lần báo có tiền đi thẳng từ SePay tới máy
       chủ, không qua trình duyệt này, nên không hỏi thì màn hình đứng yên mãi. --%>
  <div id="payment-watch" hidden
       data-endpoint="${ctx}/api/order/status?orderId=${orderId}"
       data-redirect="${ctx}/order/track?orderId=${orderId}"></div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
