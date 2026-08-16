<c:set var="pageTitle" value="Tài khoản" />
<c:set var="mainClass" value="container medium" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head"><h1>Tài khoản</h1></div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <c:if test="${me.mustChangePassword}">
    <div class="alert alert-warn">
      Mật khẩu hiện tại do quản trị viên đặt hộ nên không còn là bí mật của riêng bạn.
      Hãy đặt mật khẩu mới ở ô bên dưới — trong lúc chờ, các màn hình khác tạm thời chưa mở.
    </div>
  </c:if>

  <div class="grid grid-2">
    <div class="card">
      <h2>Thông tin cá nhân</h2>
      <form method="post" action="${ctx}/profile">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <div class="field">
          <label for="profileEmail">Email <span class="hint">(không đổi được)</span></label>
          <input type="email" id="profileEmail" value="<c:out value="${profile.email}"/>" disabled>
        </div>
        <div class="field">
          <label for="profileRole">Vai trò</label>
          <input type="text" id="profileRole" value="${ff:roleName(profile.roleName)}" disabled>
        </div>
        <div class="field">
          <label for="fullName">Họ và tên</label>
          <input type="text" id="fullName" name="fullName" value="<c:out value="${profile.fullName}"/>" required>
        </div>
        <div class="field">
          <label for="phone">Số điện thoại</label>
          <input type="text" id="phone" name="phone" value="<c:out value="${profile.phone}"/>">
        </div>
        <button type="submit" class="btn btn-primary">Lưu thay đổi</button>
      </form>
    </div>

    <div class="card">
      <h2>Đổi mật khẩu</h2>
      <form method="post" action="${ctx}/profile">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <input type="hidden" name="action" value="changePassword">
        <div class="field">
          <label for="currentPassword">Mật khẩu hiện tại</label>
          <input type="password" id="currentPassword" name="currentPassword" required>
        </div>
        <div class="field">
          <label for="newPassword">Mật khẩu mới <span class="hint">(tối thiểu 8 ký tự, có cả chữ và số)</span></label>
          <input type="password" id="newPassword" name="newPassword" required>
        </div>
        <div class="field">
          <label for="confirmPassword">Nhập lại mật khẩu mới</label>
          <input type="password" id="confirmPassword" name="confirmPassword" required>
        </div>
        <button type="submit" class="btn btn-primary">Đổi mật khẩu</button>
      </form>
    </div>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
