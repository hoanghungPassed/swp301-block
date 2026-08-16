<c:set var="pageTitle" value="Món ăn" /><c:set var="nav" value="products" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head">
    <h1>Món ăn</h1>
    <p>Món ngừng bán thì chuyển sang trạng thái ngừng kinh doanh, không xoá — đơn cũ vẫn cần tham chiếu tới.</p>
  </div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <%-- Bộ lọc hiện tại, đã mã hoá sẵn, dùng lại ở ba chỗ: liên kết Sửa, ô 'back' của mọi biểu
       mẫu ghi, và thanh chuyển trang. Thiếu nó thì mỗi lần bấm một nút là danh sách nhảy về
       trang đầu không lọc, và người dùng phải gõ lại bộ lọc sau từng thao tác. --%>
  <c:set var="qs" value="${empty filterQuery ? '' : filterQuery.concat('&')}" />

  <div class="grid grid-side">
    <div>
      <div class="card">
        <form method="get" action="${ctx}/admin/products" class="form-row">
          <div class="field">
            <label for="keyword">Tìm món</label>
            <input type="search" id="keyword" name="keyword" value="<c:out value="${keyword}"/>">
          </div>
          <div class="field">
            <label for="filterCategory">Nhóm món</label>
            <select id="filterCategory" name="categoryId">
              <option value="">Tất cả</option>
              <c:forEach var="cat" items="${categories}">
                <option value="${cat.categoryId}" ${selectedCategory eq cat.categoryId ? 'selected' : ''}><c:out value="${cat.name}"/><c:if test="${cat.status ne 'ACTIVE'}"> (đang ẩn)</c:if></option>
              </c:forEach>
            </select>
          </div>
          <div class="field">
            <label for="filterStatus">Trên thực đơn</label>
            <select id="filterStatus" name="status">
              <option value="">Tất cả</option>
              <option value="ACTIVE" ${status eq 'ACTIVE' ? 'selected' : ''}>Đang bán</option>
              <option value="INACTIVE" ${status eq 'INACTIVE' ? 'selected' : ''}>Ngừng bán</option>
            </select>
          </div>
          <%-- Tách khỏi ô trên: "ngừng bán" là quyết định lâu dài, "tạm hết" là chuyện của
               hôm nay. Gộp làm một danh sách thì không hỏi được "món nào đang bán mà hết hàng" —
               đúng câu người quản lý cần trả lời mỗi sáng. --%>
          <div class="field">
            <label for="filterStock">Tình trạng</label>
            <select id="filterStock" name="stock">
              <option value="">Tất cả</option>
              <option value="IN" ${stock eq 'IN' ? 'selected' : ''}>Còn hàng</option>
              <option value="OUT" ${stock eq 'OUT' ? 'selected' : ''}>Tạm hết</option>
            </select>
          </div>
          <button type="submit" class="btn btn-primary">Lọc</button>
          <a class="btn" href="${ctx}/admin/products">Bỏ lọc</a>
        </form>
      </div>

      <div class="card pad0 table-wrap">
        <table>
          <thead><tr><th scope="col">Tên món</th><th scope="col">Nhóm</th><th scope="col" class="num">Giá</th>
                     <th scope="col">Tình trạng</th><th scope="col">Trên thực đơn</th><th scope="col"><span class="visually-hidden">Thao tác</span></th></tr></thead>
          <tbody>
            <c:forEach var="p" items="${pageData.items}">
              <tr>
                <td>
                  <strong><c:out value="${p.name}"/></strong>
                  <div class="small muted"><c:out value="${p.description}"/></div>
                </td>
                <td class="small"><c:out value="${p.categoryName}"/></td>
                <td class="num">${ff:money(p.price)}</td>
                <td>
                  <form method="post" action="${ctx}/admin/products" class="inline-form">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="toggle">
                    <input type="hidden" name="productId" value="${p.productId}">
                    <input type="hidden" name="available" value="${p.available ? 'false' : 'true'}">
                    <input type="hidden" name="back" value="<c:out value="${filterQuery}"/>">
                    <button type="submit" class="tag tag-btn ${p.available ? 'tag-green' : 'tag-amber'}">
                      ${p.available ? 'Còn hàng' : 'Tạm hết'}
                    </button>
                  </form>
                </td>
                <td>
                  <span class="tag ${p.status eq 'ACTIVE' ? 'tag-info' : 'tag-muted'}">
                    ${p.status eq 'ACTIVE' ? 'Đang bán' : 'Ngừng bán'}
                  </span>
                </td>
                <td class="center">
                  <a class="btn btn-sm" href="?${fn:escapeXml(qs)}edit=${p.productId}">Sửa</a>
                  <form method="post" action="${ctx}/admin/products" class="inline-form">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="${p.status eq 'ACTIVE' ? 'retire' : 'restore'}">
                    <input type="hidden" name="productId" value="${p.productId}">
                    <input type="hidden" name="back" value="<c:out value="${filterQuery}"/>">
                    <button type="submit" class="btn btn-sm ${p.status eq 'ACTIVE' ? 'btn-danger' : ''}">
                      ${p.status eq 'ACTIVE' ? 'Ngừng bán' : 'Bán lại'}
                    </button>
                  </form>
                </td>
              </tr>
            </c:forEach>
            <c:if test="${pageData.emptyPage}">
              <tr><td colspan="6" class="center muted cell-empty">Không có món nào khớp bộ lọc.</td></tr>
            </c:if>
          </tbody>
        </table>
        <%@ include file="/WEB-INF/views/layout/pager.jspf" %>
      </div>
    </div>

    <div class="card">
      <h2>${empty editing ? 'Thêm món mới' : 'Sửa món'}</h2>
      <form method="post" action="${ctx}/admin/products">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <input type="hidden" name="productId" value="${editing.productId}">
        <input type="hidden" name="back" value="<c:out value="${filterQuery}"/>">
        <div class="field">
          <label for="name">Tên món</label>
          <input type="text" id="name" name="name" value="<c:out value="${editing.name}"/>" required>
        </div>
        <div class="field">
          <label for="categoryId">Nhóm món</label>
          <%-- Liệt kê cả nhóm đang ẩn. Bỏ chúng đi thì món thuộc một nhóm đã ẩn không có mục
               nào được chọn sẵn, trình duyệt lấy mục đầu danh sách, và bấm Lưu là món lặng lẽ
               chuyển sang nhóm khác — không ai chủ ý làm vậy và cũng không nhìn thấy. --%>
          <select id="categoryId" name="categoryId" required>
            <c:forEach var="cat" items="${categories}">
              <option value="${cat.categoryId}" ${editing.categoryId eq cat.categoryId ? 'selected' : ''}><c:out value="${cat.name}"/><c:if test="${cat.status ne 'ACTIVE'}"> (đang ẩn)</c:if></option>
            </c:forEach>
          </select>
          <c:if test="${not empty editing and editing.categoryId gt 0}">
            <c:forEach var="cat" items="${categories}">
              <c:if test="${cat.categoryId eq editing.categoryId and cat.status ne 'ACTIVE'}">
                <p class="small muted">Nhóm này đang ẩn nên món vẫn không hiện trên thực đơn,
                  dù món đang ở trạng thái bán.</p>
              </c:if>
            </c:forEach>
          </c:if>
        </div>
        <div class="field">
          <label for="price">Giá bán (đồng)</label>
          <input type="number" id="price" name="price" value="${editing.price}" min="0" step="1000" required>
        </div>
        <div class="field">
          <label for="description">Mô tả</label>
          <textarea id="description" name="description"><c:out value="${editing.description}"/></textarea>
        </div>
        <div class="field">
          <label for="imageUrl">Đường dẫn ảnh <span class="hint">(tối đa 255 ký tự)</span></label>
          <%-- maxlength khớp với độ dài cột image_url trong cơ sở dữ liệu: đường dẫn CDN rất
               dễ dài hơn 255, và nếu không chặn ở đây thì nó bị cắt cụt lúc lưu mà không báo. --%>
          <input type="url" id="imageUrl" name="imageUrl" maxlength="255"
                 placeholder="https://..." data-preview="imageUrlPreview"
                 value="<c:out value="${editing.imageUrl}"/>">
          <%-- Xem trước ngay tại chỗ để biết đường dẫn có sống không, khỏi phải lưu lại rồi
               mở thực đơn ra kiểm tra. --%>
          <img class="url-preview" id="imageUrlPreview" alt="" hidden referrerpolicy="no-referrer">
          <p class="small muted" id="imageUrlPreviewMsg" hidden>
            Không tải được ảnh từ đường dẫn này. Kiểm tra lại hoặc để trống.
          </p>
        </div>
        <div class="field check">
          <input type="checkbox" id="available" name="available" value="true"
                 ${empty editing or editing.available ? 'checked' : ''}>
          <label for="available">Còn hàng hôm nay</label>
        </div>
        <%-- Không có ô tick "Đang kinh doanh" ở đây: ngừng bán/bán lại là nút riêng trên từng
             dòng của bảng bên trái. Để chung form thì sửa mô tả một món cũng ghi đè trạng thái
             kinh doanh của nó. --%>
        <button type="submit" class="btn btn-primary btn-block">
          ${empty editing ? 'Thêm món' : 'Lưu thay đổi'}
        </button>
        <c:if test="${not empty editing}">
          <a class="btn btn-block mt" href="?${fn:escapeXml(filterQuery)}">Huỷ sửa</a>
        </c:if>
      </form>
    </div>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
