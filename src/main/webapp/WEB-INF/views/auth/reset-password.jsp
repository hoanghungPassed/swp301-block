<c:set var="pageTitle" value="Đặt mật khẩu mới" />
<c:set var="mainClass" value="container narrow" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head">
    <h1>Đặt mật khẩu mới</h1>
    <p>Tài khoản <span class="mono"><c:out value="${email}"/></span></p>
  </div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <div class="card">
    <form method="post" action="${ctx}/reset-password">
      <input type="hidden" name="_csrf" value="${csrfToken}">
      <input type="hidden" name="token" value="<c:out value="${token}"/>">
      <div class="field">
        <label for="newPassword">Mật khẩu mới <span class="hint">(tối thiểu 8 ký tự, có cả chữ và số)</span></label>
        <input type="password" id="newPassword" name="newPassword" required autofocus
               autocomplete="new-password" data-validate="password" data-label="mật khẩu mới"
               aria-describedby="newPasswordMsg">
        <ul class="pw-checks" data-pw-checks="newPassword" aria-hidden="true">
          <li data-check="len">Từ 8 ký tự</li>
          <li data-check="letter">Có chữ</li>
          <li data-check="digit">Có số</li>
        </ul>
        <p class="field-msg" id="newPasswordMsg" role="alert" hidden></p>
      </div>
      <div class="field">
        <label for="confirmPassword">Nhập lại mật khẩu mới</label>
        <input type="password" id="confirmPassword" name="confirmPassword" required
               autocomplete="new-password" data-validate="match:newPassword"
               data-label="lại mật khẩu mới" data-mismatch="Mật khẩu nhập lại không khớp."
               aria-describedby="confirmPasswordMsg">
        <p class="field-msg" id="confirmPasswordMsg" role="alert" hidden></p>
      </div>
      <button type="submit" class="btn btn-primary btn-block">Đặt mật khẩu</button>
    </form>
    <p class="small muted mt">
      Sau khi đặt xong, bạn sẽ đăng nhập lại bằng mật khẩu mới.
    </p>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
