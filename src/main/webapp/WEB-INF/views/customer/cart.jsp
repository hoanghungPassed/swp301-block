<c:set var="pageTitle" value="Giỏ hàng" /><c:set var="nav" value="cart" />
<c:set var="mainClass" value="container medium" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head"><h1>Giỏ hàng</h1></div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <c:if test="${cart.hasUnavailable}">
    <div class="alert alert-warn">
      Có món trong giỏ vừa hết hàng hoặc ngừng bán. Bỏ các món đó ra để tiếp tục đặt hàng.
      <form method="post" action="${ctx}/cart" class="inline-form">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <input type="hidden" name="action" value="removeUnavailable">
        <button type="submit" class="btn btn-sm">Bỏ các món đó</button>
      </form>
    </div>
  </c:if>

  <c:choose>
    <c:when test="${empty cart.items}">
      <div class="card empty">
        <div class="icon" aria-hidden="true">🛒</div>
        Giỏ hàng đang trống.
        <div class="mt"><a class="btn btn-primary" href="${ctx}/menu">Xem thực đơn</a></div>
      </div>
    </c:when>
    <c:otherwise>
      <div class="card pad0 table-wrap">
        <table class="table-cards">
          <thead>
            <tr>
              <th scope="col">Món</th><th scope="col" class="num">Đơn giá</th>
              <th scope="col" class="center">Số lượng</th><th scope="col" class="num">Thành tiền</th><th scope="col"><span class="visually-hidden">Thao tác</span></th>
            </tr>
          </thead>
          <tbody>
            <c:forEach var="item" items="${cart.items}">
              <tr>
                <td data-label="Món">
                  <c:out value="${item.productName}"/>
                  <c:if test="${not item.orderable}">
                    <div><span class="tag tag-red">Không còn phục vụ</span></div>
                  </c:if>
                </td>
                <td class="num" data-label="Đơn giá">${ff:money(item.unitPrice)}</td>
                <td class="center" data-label="Số lượng">
                  <form method="post" action="${ctx}/cart" class="qty-form">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="update">
                    <input type="hidden" name="cartItemId" value="${item.cartItemId}">
                    <input type="number" name="quantity" value="${item.quantity}" min="0" max="50" class="qty-input">
                    <button type="submit" class="btn btn-sm">Lưu</button>
                  </form>
                </td>
                <td class="num" data-label="Thành tiền">${ff:money(item.lineTotal)}</td>
                <td class="center" data-label="">
                  <form method="post" action="${ctx}/cart" class="inline-form">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="remove">
                    <input type="hidden" name="cartItemId" value="${item.cartItemId}">
                    <button type="submit" class="btn btn-sm btn-danger">Bỏ</button>
                  </form>
                </td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </div>

      <div class="card">
        <div class="total-line grand">
          <span>Tổng cộng</span>
          <span>${ff:money(cart.totalAmount)}</span>
        </div>
        <div class="actions mt">
          <a class="btn" href="${ctx}/menu">Chọn thêm món</a>
        </div>
      </div>

      <%-- Chọn giờ nằm ngay dưới giỏ chứ không ở trang riêng: khách sửa số lượng rồi đặt
           luôn, không phải đi tới đi lui giữa hai trang. --%>
      <c:choose>
        <%--
          Chưa xác thực email thì không dựng biểu mẫu chọn giờ ra. Dựng ra rồi để máy chủ từ
          chối lúc bấm nút là bắt khách chọn giờ, đọc điều khoản, rồi mới biết mình không đặt
          được — trong khi lý do đã biết từ trước cả lúc mở trang.

          Chốt chặn thật nằm ở CustomerOrderService.createOnlineOrder; nhánh này chỉ nói trước.
          Dải nhắc kèm nút gửi lại thư nằm ở khung dùng chung, ngay trên đầu trang này.
        --%>
        <c:when test="${not me.emailVerified}">
          <div class="card">
            <h2>Chọn giờ đến lấy</h2>
            <p class="small muted">
              Bạn cần xác thực địa chỉ <strong><c:out value="${me.email}"/></strong> trước khi đặt
              đơn online. Mở thư chúng tôi đã gửi và bấm liên kết trong đó — giỏ hàng vẫn được
              giữ nguyên.
            </p>
            <button class="btn btn-primary btn-block mt" disabled>Đặt đơn và thanh toán</button>
          </div>
        </c:when>
        <c:when test="${cart.checkoutable}">
          <form method="post" action="${ctx}/cart" class="card">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <h2>Chọn giờ đến lấy</h2>
            <p class="small muted mb">Món sẽ được làm sát giờ bạn hẹn để còn nóng khi bạn tới.</p>

            <input type="hidden" name="action" value="placeOrder">
            <input type="hidden" name="idempotencyKey" value="${idempotencyKey}">

            <div class="field">
              <label for="pickupTime">Giờ đến lấy</label>
              <input type="datetime-local" id="pickupTime" name="pickupTime"
                     value="${suggestedPickupTime}" min="${minPickupTime}" required>
              <p class="small muted mt">
                Sớm nhất là ${minLeadMinutes} phút kể từ bây giờ, đủ thời gian thanh toán và chế biến.
              </p>
            </div>

            <div class="alert alert-info">
              <strong>Nhận hàng tại cửa hàng.</strong>
              Hệ thống chưa có giao hàng tận nơi. Sau khi thanh toán bạn sẽ nhận được mã nhận hàng,
              đưa mã này tại quầy để lấy món.
            </div>

            <div class="field">
              <%-- Không phải <label>: đây là tiêu đề của một khối thông tin, không gắn với ô nhập nào. --%>
              <span class="field-label">Phương thức thanh toán</span>
              <div class="card card-inset">
                <div class="row-between">
                  <div>
                    <strong>Thanh toán online</strong>
                    <div class="small muted">Đơn đặt trước bắt buộc thanh toán trước khi cửa hàng nhận đơn.</div>
                  </div>
                  <span class="tag tag-info">Bắt buộc</span>
                </div>
              </div>
              <p class="small muted mt">
                Muốn trả tiền mặt? Bạn có thể mua trực tiếp tại quầy.
              </p>
            </div>

            <button type="submit" class="btn btn-primary btn-block">
              Đặt đơn và thanh toán ${ff:money(cart.totalAmount)}
            </button>
            <p class="small muted mt">
              Đơn chưa thanh toán trong ${paymentExpiryMinutes} phút sẽ tự hết hiệu lực.
            </p>
          </form>
        </c:when>
        <c:otherwise>
          <div class="card">
            <h2>Chọn giờ đến lấy</h2>
            <p class="small muted">
              Bỏ các món không còn phục vụ ra khỏi giỏ để đặt trước.
            </p>
            <button class="btn btn-primary btn-block mt" disabled>Đặt đơn và thanh toán</button>
          </div>
        </c:otherwise>
      </c:choose>
    </c:otherwise>
  </c:choose>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
