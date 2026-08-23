<c:set var="pageTitle" value="Tài khoản" /><c:set var="nav" value="users" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head">
    <h1>Tài khoản</h1>
    <p>Nhân viên nghỉ việc thì khoá tài khoản, không xoá — để lịch sử đơn người đó xử lý vẫn tra được.</p>
  </div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <c:set var="qs" value="${empty filterQuery ? '' : filterQuery.concat('&')}" />

  <div class="grid grid-side">
    <div>
      <div class="card">
        <form method="get" action="${ctx}/admin/users" class="form-row" data-live-search="#live-results">
          <div class="field">
            <label for="keyword">Tìm theo tên hoặc email</label>
            <input type="search" id="keyword" name="keyword" value="<c:out value="${keyword}"/>">
          </div>
          <div class="field">
            <label for="roleFilter">Vai trò</label>
            <select id="roleFilter" name="role">
              <option value="">Tất cả</option>
              <c:forEach var="r" items="${roles}">
                <option value="${r.name}" ${role eq r.name ? 'selected' : ''}>${ff:roleName(r.name)}</option>
              </c:forEach>
            </select>
          </div>
          <div class="field">
            <label for="statusFilter">Trạng thái</label>
            <select id="statusFilter" name="status">
              <option value="">Tất cả</option>
              <option value="ACTIVE" ${status eq 'ACTIVE' ? 'selected' : ''}>Hoạt động</option>
              <option value="LOCKED" ${status eq 'LOCKED' ? 'selected' : ''}>Đã khoá</option>
            </select>
          </div>
          <button type="submit" class="btn btn-primary">Lọc</button>
          <a class="btn" href="${ctx}/admin/users">Bỏ lọc</a>
        </form>
      </div>

      <div class="card pad0 table-wrap" id="live-results" data-live-region>
        <table>
          <thead><tr><th scope="col">Họ tên</th><th scope="col">Email</th><th scope="col">Vai trò</th><th scope="col">Trạng thái</th><th scope="col"><span class="visually-hidden">Thao tác</span></th></tr></thead>
          <tbody>
            <c:forEach var="u" items="${pageData.items}">
              <tr>
                <td><strong><c:out value="${u.fullName}"/></strong>
                  <div class="small muted"><c:out value="${u.phone}"/></div></td>
                <td class="small mono"><c:out value="${u.email}"/></td>
                <td>${ff:roleName(u.roleName)}</td>
                <td>
                  <span class="tag ${u.active ? 'tag-green' : 'tag-red'}">
                    ${u.active ? 'Hoạt động' : 'Đã khoá'}
                  </span>
                  <c:if test="${u.mustChangePassword}">
                    <div class="small muted">chờ đổi mật khẩu</div>
                  </c:if>
                </td>
                <td class="center">
                  <div class="actions center">
                    <a class="btn btn-sm" href="?${fn:escapeXml(qs)}edit=${u.userId}">Sửa</a>
                    <form method="post" action="${ctx}/admin/users" class="inline-form"
                          data-confirm="${u.active ? 'Khoá tài khoản này? Người dùng sẽ không đăng nhập được nữa.' : 'Mở khoá tài khoản này?'}">
                      <input type="hidden" name="_csrf" value="${csrfToken}">
                      <input type="hidden" name="action" value="${u.active ? 'lock' : 'unlock'}">
                      <input type="hidden" name="userId" value="${u.userId}">
                      <input type="hidden" name="back" value="<c:out value="${filterQuery}"/>">
                      <button type="submit" class="btn btn-sm ${u.active ? 'btn-danger' : ''}">
                        ${u.active ? 'Khoá' : 'Mở khoá'}
                      </button>
                    </form>
                    <form method="post" action="${ctx}/admin/users" class="inline-form"
                          data-confirm="Đặt lại mật khẩu của tài khoản này? Bạn sẽ nhận được mật khẩu tạm để đọc cho họ.">
                      <input type="hidden" name="_csrf" value="${csrfToken}">
                      <input type="hidden" name="action" value="resetPassword">
                      <input type="hidden" name="userId" value="${u.userId}">
                      <input type="hidden" name="back" value="<c:out value="${filterQuery}"/>">
                      <button type="submit" class="btn btn-sm">Đặt lại mật khẩu</button>
                    </form>
                  </div>
                </td>
              </tr>
            </c:forEach>
            <c:if test="${pageData.emptyPage}">
              <tr><td colspan="5" class="center muted cell-empty">Không có tài khoản nào khớp bộ lọc.</td></tr>
            </c:if>
          </tbody>
        </table>
        <ui:pager page="${pageData}" label="tài khoản" />
      </div>
      <p class="small muted">Mật khẩu tạm sinh ngẫu nhiên và chỉ hiện một lần ngay sau khi đặt
        lại — chép lại trước khi rời trang. Người dùng buộc phải tự đổi ở lần đăng nhập kế tiếp.</p>
    </div>

    <c:choose>
      <c:when test="${not empty editing}">
        <div class="card">
          <h2>Sửa thông tin tài khoản</h2>
          <p class="small muted mb">
            Vai trò chỉ chọn được lúc tạo tài khoản, mật khẩu đổi bằng nút đặt lại.
          </p>
          <form method="post" action="${ctx}/admin/users"
                data-confirm="Lưu thay đổi thông tin của tài khoản này?">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="action" value="updateInfo">
            <input type="hidden" name="userId" value="${editing.userId}">
            <input type="hidden" name="back" value="<c:out value="${filterQuery}"/>">
            <div class="field">
              <label for="editFullName">Họ và tên</label>
              <input type="text" id="editFullName" name="fullName"
                     value="<c:out value="${editing.fullName}"/>" required>
            </div>
            <div class="field">
              <label for="editPhone">Số điện thoại</label>
              <input type="text" id="editPhone" name="phone"
                     value="<c:out value="${editing.phone}"/>">
            </div>
            <div class="field">
              <label for="editEmail">Email</label>
              <input type="email" id="editEmail" value="<c:out value="${editing.email}"/>" disabled>
              <p class="small muted">Email là tên đăng nhập nên không đổi được ở màn hình này.</p>
            </div>
            <button type="submit" class="btn btn-primary btn-block">Lưu thay đổi</button>
            <a class="btn btn-block mt" href="?${fn:escapeXml(filterQuery)}">Huỷ sửa</a>
          </form>
        </div>
      </c:when>
      <c:otherwise>
    <div class="card">
      <h2>Tạo tài khoản nhân viên</h2>
      <p class="small muted mb">Khách hàng tự đăng ký. Tài khoản nhân viên do quản trị viên tạo.</p>
      <form method="post" action="${ctx}/admin/users"
            data-confirm="Tạo tài khoản nhân viên này? Vai trò không đổi được sau khi tạo.">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <input type="hidden" name="action" value="create">
        <input type="hidden" name="back" value="<c:out value="${filterQuery}"/>">
        <div class="field">
          <label for="fullName">Họ và tên</label>
          <input type="text" id="fullName" name="fullName" required>
        </div>
        <div class="field">
          <label for="email">Email</label>
          <input type="email" id="email" name="email" required>
        </div>
        <div class="field">
          <label for="phone">Số điện thoại</label>
          <input type="text" id="phone" name="phone">
        </div>
        <div class="field">
          <label for="roleId">Vai trò</label>
          <select id="roleId" name="roleId" required>
            <c:forEach var="r" items="${staffRoles}">
              <option value="${r.roleId}">${ff:roleName(r.name)}</option>
            </c:forEach>
          </select>
          <p class="small muted">Chỉ tạo được thu ngân và bếp — vai trò này cố định sau khi tạo.</p>
        </div>
        <div class="field">
          <label for="password">Mật khẩu ban đầu
            <span class="hint">(tối thiểu 8 ký tự, có cả chữ và số)</span></label>
          <input type="password" id="password" name="password" required>
        </div>
        <button type="submit" class="btn btn-primary btn-block">Tạo tài khoản</button>
      </form>
    </div>
      </c:otherwise>
    </c:choose>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
