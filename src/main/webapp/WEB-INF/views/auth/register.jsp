<c:set var="pageTitle" value="Đăng ký" />
<c:set var="mainClass" value="container narrow" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head">
    <h1>Tạo tài khoản</h1>
    <p>Tài khoản khách hàng để đặt trước món và hẹn giờ đến lấy.</p>
  </div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <div class="card">
    <form method="post" action="${ctx}/register">
      <input type="hidden" name="_csrf" value="${csrfToken}">
      <div class="field">
        <label for="fullName">Họ và tên</label>
        <input type="text" id="fullName" name="fullName" value="<c:out value="${fullName}"/>" required autofocus>
      </div>
      <div class="field">
        <label for="email">Email</label>
        <input type="email" id="email" name="email" value="<c:out value="${email}"/>" required>
      </div>
      <div class="field">
        <label for="phone">Số điện thoại <span class="hint">(không bắt buộc)</span></label>
        <input type="text" id="phone" name="phone" value="<c:out value="${phone}"/>" placeholder="0901234567">
      </div>
      <div class="field">
        <label for="password">Mật khẩu <span class="hint">(tối thiểu 8 ký tự, có cả chữ và số)</span></label>
        <input type="password" id="password" name="password" required>
      </div>
      <div class="field">
        <label for="confirmPassword">Nhập lại mật khẩu</label>
        <input type="password" id="confirmPassword" name="confirmPassword" required>
      </div>
      <button type="submit" class="btn btn-primary btn-block">Đăng ký</button>
    </form>
    <p class="small muted mt">Đã có tài khoản? <a href="${ctx}/login">Đăng nhập</a></p>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
