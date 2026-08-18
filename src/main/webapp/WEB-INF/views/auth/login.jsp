<c:set var="pageTitle" value="Đăng nhập" />
<c:set var="mainClass" value="container narrow" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head">
    <h1>Đăng nhập</h1>
    <p>Đặt trước và hẹn giờ đến lấy cần đăng nhập. Mua trực tiếp tại quầy thì không cần.</p>
  </div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <div class="card">
    <form method="post" action="${ctx}/login">
      <input type="hidden" name="_csrf" value="${csrfToken}">
      <div class="field">
        <label for="email">Email</label>
        <input type="email" id="email" name="email" value="<c:out value="${email}"/>" required autofocus
               autocomplete="email" data-validate="email" data-label="email"
               aria-describedby="emailMsg">
        <p class="field-msg" id="emailMsg" role="alert" hidden></p>
      </div>
      <div class="field">
        <label for="password">Mật khẩu</label>
        <input type="password" id="password" name="password" required
               autocomplete="current-password" data-validate="text" data-label="mật khẩu"
               aria-describedby="passwordMsg">
        <p class="field-msg" id="passwordMsg" role="alert" hidden></p>
      </div>
      <button type="submit" class="btn btn-primary btn-block">Đăng nhập</button>
    </form>
    <p class="small muted mt">
      <a href="${ctx}/forgot-password">Quên mật khẩu?</a>
      · Chưa có tài khoản? <a href="${ctx}/register">Đăng ký</a>
    </p>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
