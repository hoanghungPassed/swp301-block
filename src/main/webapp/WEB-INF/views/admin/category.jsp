<c:set var="pageTitle" value="Nhóm món" /><c:set var="nav" value="categories" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <%-- Ẩn nhóm hay lưu nhóm xong thì quay lại đúng trang đang xem. --%>
  <c:set var="catQuery" value="${ff:pageQuery(pageContext.request, 'edit')}" />
  <c:set var="catBack"  value="/admin/categories${empty catQuery ? '' : '?'.concat(catQuery)}" />
  <c:set var="catMore"  value="${fn:contains(catBack, '?') ? '&amp;' : '?'}" />
  <c:set var="catReturn"><input type="hidden" name="returnTo" value="<c:out value="${catBack}"/>"></c:set>
  <div class="page-head">
    <h1>Nhóm món</h1>
    <p>Tắt một nhóm sẽ ẩn toàn bộ món trong nhóm khỏi thực đơn — cách nhanh nhất để ngừng bán cả dòng sản phẩm.</p>
  </div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <div class="grid grid-side">
    <div class="card pad0 table-wrap">
      <table>
        <thead><tr><th scope="col" class="center">Thứ tự</th><th scope="col">Tên nhóm</th>
                   <th scope="col" class="center">Số món</th><th scope="col">Trạng thái</th><th scope="col"><span class="visually-hidden">Thao tác</span></th></tr></thead>
        <tbody>
          <c:forEach var="cat" items="${pageData.items}">
            <tr>
              <td class="center">${cat.displayOrder}</td>
              <td><strong><c:out value="${cat.name}"/></strong></td>
              <td class="center">${cat.productCount}</td>
              <td>
                <span class="tag ${cat.active ? 'tag-green' : 'tag-muted'}">
                  ${cat.active ? 'Đang hiện' : 'Đã ẩn'}
                </span>
              </td>
              <td class="center">
                <a class="btn btn-sm" href="${ctx}<c:out value="${catBack}"/>${catMore}edit=${cat.categoryId}">Sửa</a>
                <form method="post" action="${ctx}/admin/categories" class="inline-form"
                      data-confirm="${cat.active ? 'Ẩn nhóm này khỏi thực đơn?' : 'Hiện lại nhóm này trên thực đơn?'}<c:if test="${cat.active and cat.productCount > 0}"> ${cat.productCount} món trong nhóm sẽ không còn hiện trên thực đơn.</c:if>">
                  <input type="hidden" name="_csrf" value="${csrfToken}">
                  <input type="hidden" name="action" value="${cat.active ? 'retire' : 'restore'}">
                  ${catReturn}
                  <input type="hidden" name="categoryId" value="${cat.categoryId}">
                  <button type="submit" class="btn btn-sm ${cat.active ? 'btn-danger' : ''}">
                    ${cat.active ? 'Ẩn nhóm' : 'Hiện lại'}
                  </button>
                </form>
              </td>
            </tr>
          </c:forEach>
          <c:if test="${empty pageData.items}">
            <tr><td colspan="5" class="center muted cell-empty">Chưa có nhóm món nào. Thêm nhóm đầu tiên ở khung bên cạnh.</td></tr>
          </c:if>
        </tbody>
      </table>
      <ui:pager page="${pageData}" label="nhóm món" />
    </div>

    <div class="card">
      <h2>${empty editing ? 'Thêm nhóm món' : 'Sửa nhóm món'}</h2>
      <form method="post" action="${ctx}/admin/categories"
            data-confirm="${empty editing ? 'Thêm nhóm mới vào thực đơn?' : 'Lưu thay đổi cho nhóm này?'}">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <input type="hidden" name="categoryId" value="${editing.categoryId}">
        ${catReturn}
        <div class="field">
          <label for="name">Tên nhóm</label>
          <input type="text" id="name" name="name" value="<c:out value="${editing.name}"/>" required>
        </div>
        <div class="field">
          <label for="displayOrder">Thứ tự hiển thị</label>
          <input type="number" id="displayOrder" name="displayOrder" value="${empty editing ? 0 : editing.displayOrder}">
        </div>
        <button type="submit" class="btn btn-primary btn-block">
          ${empty editing ? 'Thêm nhóm' : 'Lưu thay đổi'}
        </button>
        <c:if test="${not empty editing}">
          <a class="btn btn-block mt" href="${ctx}<c:out value="${catBack}"/>">Huỷ sửa</a>
        </c:if>
      </form>
    </div>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
