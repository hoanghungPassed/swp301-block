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
        <input type="text" id="fullName" name="fullName" value="<c:out value="${fullName}"/>" required autofocus
               autocomplete="name" data-validate="text" data-label="họ tên"
               aria-describedby="fullNameMsg">
        <p class="field-msg" id="fullNameMsg" role="alert" hidden></p>
      </div>
      <div class="field">
        <label for="email">Email</label>
        <input type="email" id="email" name="email" value="<c:out value="${email}"/>" required
               autocomplete="email" data-validate="email" data-label="email"
               aria-describedby="emailMsg">
        <p class="field-msg" id="emailMsg" role="alert" hidden></p>
      </div>
      <div class="field">
        <label for="phone">Số điện thoại <span class="hint">(không bắt buộc)</span></label>
        <input type="text" id="phone" name="phone" value="<c:out value="${phone}"/>"
               inputmode="numeric" autocomplete="tel" data-validate="phone" data-label="số điện thoại"
               aria-describedby="phoneMsg">
        <p class="field-msg" id="phoneMsg" role="alert" hidden></p>
      </div>
      <div class="field">
        <label for="password">Mật khẩu <span class="hint">(tối thiểu 8 ký tự, có cả chữ và số)</span></label>
        <input type="password" id="password" name="password" required
               autocomplete="new-password" data-validate="password" data-label="mật khẩu"
               aria-describedby="passwordMsg">
        <ul class="pw-checks" data-pw-checks="password" aria-hidden="true">
          <li data-check="len">Từ 8 ký tự</li>
          <li data-check="letter">Có chữ</li>
          <li data-check="digit">Có số</li>
        </ul>
        <p class="field-msg" id="passwordMsg" role="alert" hidden></p>
      </div>
      <div class="field">
        <label for="confirmPassword">Nhập lại mật khẩu</label>
        <input type="password" id="confirmPassword" name="confirmPassword" required
               autocomplete="new-password" data-validate="match:password"
               data-label="lại mật khẩu" data-mismatch="Mật khẩu nhập lại không khớp."
               aria-describedby="confirmPasswordMsg">
        <p class="field-msg" id="confirmPasswordMsg" role="alert" hidden></p>
      </div>
      <button type="submit" class="btn btn-primary btn-block">Đăng ký</button>
    </form>
    <p class="small muted mt">Đã có tài khoản? <a href="${ctx}/login">Đăng nhập</a></p>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
