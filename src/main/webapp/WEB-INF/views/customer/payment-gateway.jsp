<c:set var="pageTitle" value="Thanh toán" />
<c:set var="mainClass" value="container narrow" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="card">
    <div class="alert alert-warn">
      <strong>Cổng thanh toán giả lập.</strong>
      Bản chạy thử không kết nối ngân hàng thật. Bạn chọn kết quả để diễn lại các tình huống
      như khi dùng cổng thật.
    </div>

    <h1>Xác nhận thanh toán</h1>
    <div class="mt mb">
      <div class="total-line"><span class="muted">Mã đơn</span><span>#${orderId}</span></div>
      <div class="total-line"><span class="muted">Mã giao dịch</span><span class="mono small"><c:out value="${txnId}"/></span></div>
      <div class="total-line grand"><span>Số tiền</span><span><c:out value="${amount}"/> đ</span></div>
    </div>

    <div class="stack">
      <a class="btn btn-green btn-block touch"
         href="${ctx}/payment/callback?paymentId=${paymentId}&txnId=<c:out value="${txnId}"/>&orderId=${orderId}&success=true&sig=${successSig}">
        Thanh toán thành công
      </a>
      <a class="btn btn-danger btn-block"
         href="${ctx}/payment/callback?paymentId=${paymentId}&txnId=<c:out value="${txnId}"/>&orderId=${orderId}&success=false&sig=${failureSig}">
        Thanh toán thất bại
      </a>
      <a class="btn btn-block" href="${ctx}/order/track?orderId=${orderId}">
        Để sau — quay lại đơn hàng
      </a>
    </div>

    <p class="small muted mt">
      Thử bấm "thành công" hai lần: lần thứ hai hệ thống nhận ra giao dịch trùng và bỏ qua,
      không ghi nhận tiền thêm lần nữa.
    </p>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
