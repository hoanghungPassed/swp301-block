<%@ page isErrorPage="true" %>
<c:set var="pageTitle" value="Lỗi 500" />
<c:set var="mainClass" value="container narrow" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="card card-hero">
    <div class="hero-icon" aria-hidden="true">⚠️</div>
    <h1>Có lỗi xảy ra</h1>
    <p class="muted mt">${empty errorMessage ? 'Hệ thống gặp sự cố khi xử lý yêu cầu. Vui lòng thử lại sau ít phút.' : errorMessage}</p>
    <div class="actions center mt">
      <a class="btn btn-primary" href="${ctx}/">Về trang chủ</a>
    </div>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
