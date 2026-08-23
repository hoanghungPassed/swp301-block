<c:set var="pageTitle" value="Kết quả thanh toán" />
<c:set var="mainClass" value="container narrow" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="card">
    <c:choose>
      <c:when test="${ketQua eq 'DA_TRA'}">
        <h1>Đã nhận được tiền</h1>
        <p>Đơn <strong>#${orderId}</strong> tại quầy đã thanh toán xong.</p>
        <div class="alert alert-success" role="status">
          Mời bạn quay lại quầy. Nhân viên thu ngân xác nhận trên máy rồi đơn mới được chuyển
          xuống bếp, nên đừng rời đi trước khi nghe gọi.
        </div>
      </c:when>
      <c:when test="${ketQua eq 'DANG_CHO'}">
        <h1>Chưa xác nhận được</h1>
        <p>Đơn <strong>#${orderId}</strong> chưa thấy cổng thanh toán báo tiền về.</p>
        <%-- Không nói "chưa trừ đồng nào": ngân hàng có thể đang xử lý và đã trừ thật. Nói sai
             chỗ này là đẩy khách bỏ đi trong khi tiền đã ra khỏi tài khoản họ. --%>
        <div class="alert alert-warn" role="alert">
          Nếu bạn vừa xác nhận chuyển khoản, tiền có thể còn đang trên đường về — mời bạn quay
          lại quầy và <strong>báo nhân viên thu ngân</strong> để cùng chờ. Đừng chuyển lần thứ
          hai.
        </div>
      </c:when>
      <c:when test="${ketQua eq 'DOI_SOAT'}">
        <h1>Cần nhân viên đối chiếu</h1>
        <p>Đơn <strong>#${orderId}</strong> đã nhận được tiền nhưng chưa ghép được vào đơn.</p>
        <%-- Tiền thật đã rời tài khoản khách: đơn hết hiệu lực trước lúc tiền về, hoặc số tiền
             không khớp. Hệ thống không hoàn tự động được, nên việc phải làm là đưa khách tới
             đúng người xử lý. --%>
        <div class="alert alert-error" role="alert">
          <strong>Mời bạn quay lại quầy ngay và đọc mã đơn #${orderId}</strong> cho nhân viên
          thu ngân. Khoản tiền của bạn đã được ghi lại đầy đủ để đối chiếu và xử lý.
        </div>
      </c:when>
      <c:otherwise>
        <h1>Chưa thanh toán được</h1>
        <p>Đơn <strong>#${orderId}</strong> chưa nhận được tiền — <c:out value="${failReason}"/>.</p>
        <div class="alert alert-warn" role="alert">
          Bạn quay lại quầy để quét lại mã, hoặc trả bằng tiền mặt. Chưa có khoản nào bị trừ
          cho đơn này.
        </div>
      </c:otherwise>
    </c:choose>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
