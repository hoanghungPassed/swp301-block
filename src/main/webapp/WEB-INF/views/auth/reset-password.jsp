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
      <%-- Mã đi trong thân biểu mẫu, không đi trong địa chỉ: địa chỉ nằm lại trong lịch sử
           trình duyệt và nhật ký máy chủ trung gian, thân của POST thì không. --%>
      <input type="hidden" name="token" value="<c:out value="${token}"/>">
      <div class="field">
        <label for="newPassword">Mật khẩu mới <span class="hint">(tối thiểu 8 ký tự, có cả chữ và số)</span></label>
        <input type="password" id="newPassword" name="newPassword" required autofocus>
      </div>
      <div class="field">
        <label for="confirmPassword">Nhập lại mật khẩu mới</label>
        <input type="password" id="confirmPassword" name="confirmPassword" required>
      </div>
      <button type="submit" class="btn btn-primary btn-block">Đặt mật khẩu</button>
    </form>
    <p class="small muted mt">
      Sau khi đặt xong, bạn sẽ đăng nhập lại bằng mật khẩu mới.
    </p>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
