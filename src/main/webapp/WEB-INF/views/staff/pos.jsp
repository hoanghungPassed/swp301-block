<c:set var="pageTitle" value="Bán tại quầy" /><c:set var="nav" value="pos" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <%-- Thao tác nào cũng quay lại đúng trang thực đơn thu ngân đang mở. --%>
  <c:set var="posQuery" value="${ff:pageQuery(pageContext.request, '')}" />
  <c:set var="posBack"  value="/staff/pos${empty posQuery ? '' : '?'.concat(posQuery)}" />
  <c:set var="posReturn"><input type="hidden" name="returnTo" value="<c:out value="${posBack}"/>"></c:set>
  <div class="page-head">
    <h1>Bán tại quầy</h1>
    <p>Khách đứng đợi tại chỗ. Thu tiền xong đơn xuống bếp ngay, không cần mã nhận hàng.</p>
  </div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <div class="grid grid-side">
    <div>
      <div class="card">
        <form method="get" action="${ctx}/staff/pos" class="form-row" data-live-search="#live-results">
          <div class="field">
            <label for="keyword">Tìm món</label>
            <input type="search" id="keyword" name="keyword" value="<c:out value="${keyword}"/>" placeholder="Tên món...">
          </div>
          <div class="field">
            <label for="categoryId">Nhóm món</label>
            <select id="categoryId" name="categoryId">
              <option value="">Tất cả</option>
              <c:forEach var="cat" items="${categories}">
                <option value="${cat.categoryId}" ${selectedCategory eq cat.categoryId ? 'selected' : ''}><c:out value="${cat.name}"/></option>
              </c:forEach>
            </select>
          </div>
          <button type="submit" class="btn btn-primary">Lọc</button>
        </form>
      </div>

      <div id="live-results" data-live-region>
      <div class="menu-grid">
        <c:forEach var="p" items="${pageData.items}">
          <form method="post" action="${ctx}/staff/pos" class="product">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="action" value="add">
            <input type="hidden" name="productId" value="${p.productId}">
            ${posReturn}
            <div class="body">
              <span class="tag tag-muted"><c:out value="${p.categoryName}"/></span>
              <div class="name"><c:out value="${p.name}"/></div>
              <div class="price">${ff:money(p.price)}</div>
            </div>
            <div class="foot">
              <button type="submit" class="btn btn-primary btn-block touch">Thêm</button>
            </div>
          </form>
        </c:forEach>
        <c:if test="${pageData.emptyPage}">
          <div class="card empty">Không có món nào khớp bộ lọc.</div>
        </c:if>
      </div>
      <ui:pager page="${pageData}" label="món" />
      </div>
    </div>

    <div class="card">
      <div class="row-between mb">
        <h2>Phiếu tính tiền</h2>
        <c:if test="${not empty posLines}">
          <form method="post" action="${ctx}/staff/pos" class="inline-form"
                data-confirm="Xoá hết món trong giỏ đang tính tiền?">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="action" value="clear">
            ${posReturn}
            <button type="submit" class="btn btn-sm btn-danger">Xoá hết</button>
          </form>
        </c:if>
      </div>

      <c:choose>
        <c:when test="${empty posLines}">
          <div class="empty"><div class="icon" aria-hidden="true">🧾</div>Chưa chọn món nào.</div>
        </c:when>
        <c:otherwise>
          <c:forEach var="line" items="${posLines}">
            <div class="total-line middle">
              <div>
                <div class="${line.orderable ? '' : 'muted'}"><c:out value="${line.productName}"/></div>
                <c:choose>
                  <c:when test="${line.orderable}">
                    <div class="small muted">${ff:money(line.unitPrice)} × ${line.quantity}</div>
                  </c:when>
                  <c:otherwise>
                    <div class="small"><span class="tag tag-red">Không còn phục vụ</span>
                      <span class="muted">— đặt số lượng về 0 để bỏ ra</span></div>
                  </c:otherwise>
                </c:choose>
              </div>
              <div class="row-tight">
                <form method="post" action="${ctx}/staff/pos" class="row-tight">
                  <input type="hidden" name="_csrf" value="${csrfToken}">
                  <input type="hidden" name="action" value="setQty">
                  ${posReturn}
                  <input type="hidden" name="productId" value="${line.productId}">
                  <input type="number" name="quantity" value="${line.quantity}" min="0" max="50"
                         class="qty-input" data-autosubmit
                         aria-label="Số lượng của <c:out value="${line.productName}"/>">
                </form>
                <span class="line-amount">${ff:money(line.lineTotal)}</span>
              </div>
            </div>
          </c:forEach>

          <div class="total-line grand">
            <span>Tổng cộng</span>
            <span>${ff:money(posTotal)}</span>
          </div>

          <c:choose>
            <c:when test="${posUnavailable}">
              <div class="alert alert-error mt" role="alert">
                <strong>Chưa thu tiền được.</strong> Phiếu đang có món không còn phục vụ. Đặt số
                lượng của món đó về 0 rồi thu tiền lại.
              </div>
            </c:when>
            <c:otherwise>
              <h3 class="mt">Thu tiền</h3>
              <div class="stack">
                <form method="post" action="${ctx}/staff/pos"
                      data-confirm="Thu ${ff:money(posTotal)} tiền mặt và tạo đơn? Đơn sẽ xuống bếp ngay.">
                  <input type="hidden" name="_csrf" value="${csrfToken}">
                  <input type="hidden" name="action" value="pay">
                  <button type="submit" class="btn btn-green btn-block touch">Khách trả tiền mặt</button>
                </form>
                <form method="post" action="${ctx}/staff/pos"
                      data-confirm="Tạo mã QR cho khách quét? Đơn chỉ xuống bếp sau khi bạn bấm Xong ở trang mã QR.">
                  <input type="hidden" name="_csrf" value="${csrfToken}">
                  <input type="hidden" name="action" value="qr">
                  <button type="submit" class="btn btn-blue btn-block touch">Khách quét mã QR</button>
                  <p class="muted small">Mã QR hiện ra trên màn hình để khách quét bằng điện thoại.
                    Khách trả xong, bấm <strong>Xong</strong> ở trang đó thì đơn mới xuống bếp.</p>
                </form>
              </div>
            </c:otherwise>
          </c:choose>
        </c:otherwise>
      </c:choose>
    </div>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
