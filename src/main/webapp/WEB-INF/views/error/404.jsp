<%@ page isErrorPage="true" %>
<c:set var="pageTitle" value="Lỗi 404" />
<c:set var="mainClass" value="container narrow" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="card card-hero">
    <div class="hero-icon" aria-hidden="true">🔍</div>
    <h1>Không tìm thấy trang</h1>
    <p class="muted mt">${empty errorMessage ? 'Trang hoặc dữ liệu bạn tìm không tồn tại.' : errorMessage}</p>
    <div class="actions center mt">
      <a class="btn btn-primary" href="${ctx}/">Về trang chủ</a>
    </div>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
