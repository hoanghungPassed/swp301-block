<c:set var="pageTitle" value="Thu tiền đơn #${order.orderId}" /><c:set var="nav" value="pos" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <p class="small"><a href="${ctx}/staff/pos">← Quay lại màn hình bán hàng</a></p>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <div class="grid grid-side">
    <div>
      <div class="card">
        <div class="row-between mb">
          <div>
            <h1>Khách quét mã để trả tiền</h1>
            <p class="small muted">Đơn #${order.orderId} · lập lúc ${ff:time(order.createdAt)}
              · cổng <c:out value="${gatewayName}"/></p>
          </div>
          <span class="tag tag-muted">Chưa xuống bếp</span>
        </div>

        <c:choose>
          <c:when test="${payment.paid}">
            <div class="alert alert-success" role="status">
              <strong>Cổng thanh toán đã báo nhận đủ tiền.</strong>
              Bấm <strong>Xong</strong> ở khung bên phải để đưa đơn xuống bếp.
            </div>
          </c:when>
          <c:when test="${not empty qrError}">
            <div class="alert alert-error" role="alert">
              <strong>Chưa sinh được mã QR.</strong> <c:out value="${qrError}"/>
              Hãy thu bằng tiền mặt. Nếu khách bỏ đi, cứ để đơn đấy —
              quá 15 phút không ai trả tiền thì hệ thống tự đóng lại.
            </div>
          </c:when>
          <c:when test="${empty qrDataUri}">
            <div class="alert alert-error" role="alert">
              <strong>Chưa vẽ được mã QR.</strong> Máy chủ không mã hoá được địa chỉ trả tiền.
              Khách có thể mở đường dẫn bên dưới trên điện thoại để trả tiền.
            </div>
            <p class="mono small"><c:out value="${payUrl}"/></p>
          </c:when>
          <c:otherwise>
            <div class="qr-box">
              <img src="${qrDataUri}" alt="Mã QR thanh toán đơn #${order.orderId}"
                   width="320" height="320">
            </div>
            <p class="muted small center">Khách mở ứng dụng ngân hàng, quét mã này rồi xác nhận
              chuyển khoản. Tiền về là <c:out value="${gatewayName}"/> báo sang đây và dòng
              &ldquo;Trạng thái tiền&rdquo; bên cạnh tự đổi — không phải tải lại trang.</p>
          </c:otherwise>
        </c:choose>

        <div class="total-line grand mt">
          <span>Số tiền phải thu</span><span>${ff:money(payment.amount)}</span>
        </div>

      </div>

      <div class="card pad0 table-wrap">
        <div class="card-head"><h2>Món trong đơn</h2></div>
        <table>
          <thead><tr><th scope="col">Món</th><th scope="col" class="center">SL</th>
                     <th scope="col" class="num">Đơn giá</th>
                     <th scope="col" class="num">Thành tiền</th></tr></thead>
          <tbody>
            <c:forEach var="item" items="${order.items}">
              <tr>
                <td><c:out value="${item.productNameSnapshot}"/></td>
                <td class="center">${item.quantity}</td>
                <td class="num">${ff:money(item.unitPrice)}</td>
                <td class="num">${ff:money(item.lineTotal)}</td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </div>
    </div>

    <div>
      <div class="card">
        <h2>Khách trả xong thì bấm Xong</h2>
        <p class="muted small">Bếp chỉ nhìn thấy đơn này sau khi bạn bấm Xong. Chưa thấy tiền
          về thì đừng bấm — món đã làm ra không lấy lại được.</p>

        <p class="total-line">
          <span class="muted">Trạng thái tiền</span>
          <span id="pos-payment-state">
            <c:choose>
              <c:when test="${payment.paid}"><strong>Cổng đã báo nhận tiền</strong></c:when>
              <c:otherwise>Chưa thấy cổng báo về</c:otherwise>
            </c:choose>
          </span>
        </p>

        <form method="post" action="${ctx}/staff/pos/qr" class="stack"
              data-confirm="Xác nhận khách đã trả tiền qua mã QR? Đơn sẽ được tạo và xuống bếp.">
          <input type="hidden" name="_csrf" value="${csrfToken}">
          <input type="hidden" name="action" value="done">
          <input type="hidden" name="orderId" value="${order.orderId}">
          <button type="submit" class="btn btn-green btn-block touch">Xong — khách đã trả tiền</button>
        </form>

        <h3 class="mt">Khách đổi ý hoặc trả không được?</h3>
        <p class="small muted">
          Cứ quay về màn hình bán hàng và mời khách tiếp theo. Đơn này chưa xuống bếp nên không
          mất món nào; quá 15 phút không ai trả tiền thì hệ thống tự đóng lại.
        </p>
        <a class="btn btn-block touch mt" href="${ctx}/staff/pos">Về màn hình bán hàng</a>
      </div>
    </div>
  </div>

  <c:if test="${not payment.paid}">
    <div id="pos-payment-watch" hidden
         data-endpoint="${ctx}/staff/pos/qr/status?orderId=${order.orderId}"></div>
  </c:if>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
