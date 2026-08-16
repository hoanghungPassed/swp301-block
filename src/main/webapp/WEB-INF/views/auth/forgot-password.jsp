<c:set var="pageTitle" value="Quên mật khẩu" />
<c:set var="mainClass" value="container narrow" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head">
    <h1>Quên mật khẩu</h1>
    <p>Nhập email đã đăng ký. Chúng tôi gửi tới hộp thư của bạn một liên kết để đặt mật khẩu mới.</p>
  </div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <div class="card">
    <form method="post" action="${ctx}/forgot-password">
      <input type="hidden" name="_csrf" value="${csrfToken}">
      <div class="field">
        <label for="email">Email</label>
        <input type="email" id="email" name="email" required autofocus>
      </div>
      <button type="submit" class="btn btn-primary btn-block">Gửi liên kết đặt lại</button>
    </form>
    <p class="small muted mt">
      Nhớ ra mật khẩu rồi? <a href="${ctx}/login">Đăng nhập</a>
    </p>
  </div>

  <div class="card">
    <h3>Không nhận được thư?</h3>
    <p class="small muted">
      Liên kết chỉ có hiệu lực trong ít phút và mỗi lần xin mới sẽ làm liên kết cũ hết tác dụng.
      Nếu tài khoản của bạn là tài khoản nhân viên và bạn không còn mở được hộp thư,
      hãy nhờ quản trị viên đặt lại mật khẩu trực tiếp.
    </p>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
