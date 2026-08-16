<c:set var="pageTitle" value="${product.name}" /><c:set var="nav" value="menu" />
<c:set var="mainClass" value="container medium" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <p class="small mb"><a href="${ctx}/menu">← Quay lại thực đơn</a></p>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <div class="card grid grid-2">
    <c:choose>
      <c:when test="${not empty product.imageUrl}">
        <img class="thumb thumb-lg thumb-img" src="<c:out value="${product.imageUrl}"/>" alt=""
             loading="lazy" referrerpolicy="no-referrer" data-fallback="🍔">
      </c:when>
      <c:otherwise>
        <div class="thumb thumb-lg" aria-hidden="true">🍔</div>
      </c:otherwise>
    </c:choose>
    <div class="stack">
      <span class="tag tag-muted"><c:out value="${product.categoryName}"/></span>
      <h1><c:out value="${product.name}"/></h1>
      <p class="muted"><c:out value="${product.description}"/></p>
      <div class="price price-lg">
        ${ff:money(product.price)}
      </div>

      <c:if test="${not reviewSummary.emptySummary}">
        <div class="rating-line">
          <span class="stars" aria-hidden="true">${reviewSummary.stars}</span>
          <strong>${reviewSummary.averageRounded}</strong>
          <span class="small muted">· ${reviewSummary.count} lượt đánh giá</span>
        </div>
      </c:if>

      <c:choose>
        <c:when test="${not product.orderable}">
          <div class="alert alert-warn flush">Món này hiện không còn phục vụ.</div>
        </c:when>
        <c:when test="${empty me}">
          <a class="btn btn-primary" href="${ctx}/login">Đăng nhập để đặt món</a>
        </c:when>
        <c:when test="${me.roleName ne 'CUSTOMER'}">
          <p class="small muted">Tài khoản nhân viên không dùng giỏ hàng của khách.</p>
        </c:when>
        <c:otherwise>
          <form method="post" action="${ctx}/cart" class="stack">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="action" value="add">
            <input type="hidden" name="productId" value="${product.productId}">
            <input type="hidden" name="returnTo" value="/cart">
            <div class="field">
              <label for="quantity">Số lượng</label>
              <input type="number" id="quantity" name="quantity" value="1" min="1" max="50">
            </div>
            <button type="submit" class="btn btn-primary">Thêm vào giỏ hàng</button>
          </form>
        </c:otherwise>
      </c:choose>

      <%-- Món quen nằm NGOÀI khối chọn mua ở trên, để nó còn đánh dấu được cả khi món đang
           ngừng phục vụ: một món khách quen mà tạm hết hàng lại càng đáng nhớ, đó chính là
           món họ sẽ hỏi khi quay lại. Biểu mẫu gửi sang /menu vì mọi thao tác món quen nằm ở
           MenuServlet; returnTo đưa khách trở lại đúng trang này. --%>
      <c:if test="${not empty me and me.roleName eq 'CUSTOMER'}">
        <c:choose>
          <c:when test="${isFavourite}">
            <form method="post" action="${ctx}/menu">
              <input type="hidden" name="_csrf" value="${csrfToken}">
              <input type="hidden" name="action" value="favRemoveByProduct">
              <input type="hidden" name="productId" value="${product.productId}">
              <input type="hidden" name="returnTo" value="/product/detail?id=${product.productId}">
              <button type="submit" class="btn btn-fav on">★ Đã lưu món quen</button>
            </form>
            <p class="small muted">
              Món này nằm trong <a href="${ctx}/menu">món quen</a> của bạn — thêm ghi chú riêng
              cho món ở đó.
            </p>
          </c:when>
          <c:otherwise>
            <form method="post" action="${ctx}/menu">
              <input type="hidden" name="_csrf" value="${csrfToken}">
              <input type="hidden" name="action" value="favAdd">
              <input type="hidden" name="productId" value="${product.productId}">
              <input type="hidden" name="returnTo" value="/product/detail?id=${product.productId}">
              <button type="submit" class="btn btn-fav">☆ Lưu vào món quen</button>
            </form>
          </c:otherwise>
        </c:choose>
      </c:if>
    </div>
  </div>

  <div class="card" id="danh-gia">
    <div class="row-between mb">
      <h2>Đánh giá của khách</h2>
      <c:if test="${not reviewSummary.emptySummary}">
        <span class="small muted">
          <span class="stars" aria-hidden="true">${reviewSummary.stars}</span>
          ${reviewSummary.averageRounded}/5 · ${reviewSummary.count} lượt
        </span>
      </c:if>
    </div>

    <%-- Ô viết đánh giá chỉ hiện cho khách đã nhận món. Ai chưa đủ điều kiện thì được nói rõ
         vì sao, thay vì thấy một cái nút bấm vào rồi mới bị từ chối. --%>
    <c:choose>
      <c:when test="${not empty myReview}">
        <div class="alert flush mb">
          <div class="row-between middle">
            <div>
              <span class="stars" aria-hidden="true">${myReview.stars}</span>
              <strong>Đánh giá của bạn</strong>
              <c:if test="${myReview.edited}"><span class="small muted"> · đã sửa</span></c:if>
              <c:if test="${not empty myReview.comment}">
                <div class="small"><c:out value="${myReview.comment}"/></div>
              </c:if>
            </div>
            <div class="actions">
              <a class="btn btn-sm" href="${ctx}/product/detail?id=${product.productId}&editReview=true">Sửa</a>
              <form method="post" action="${ctx}/product/detail" class="inline-form"
                    onsubmit="return confirm('Xoá đánh giá của bạn?');">
                <input type="hidden" name="_csrf" value="${csrfToken}">
                <input type="hidden" name="action" value="reviewDelete">
                <input type="hidden" name="reviewId" value="${myReview.reviewId}">
                <input type="hidden" name="productId" value="${product.productId}">
                <button type="submit" class="btn btn-sm btn-danger">Xoá</button>
              </form>
            </div>
          </div>
        </div>

        <c:if test="${editingReview}">
          <form method="post" action="${ctx}/product/detail" class="stack mb">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="action" value="reviewUpdate">
            <input type="hidden" name="reviewId" value="${myReview.reviewId}">
            <input type="hidden" name="productId" value="${product.productId}">
            <div class="field">
              <label for="editRating">Số sao</label>
              <select id="editRating" name="rating" required>
                <c:forEach var="n" begin="1" end="5">
                  <option value="${n}" ${myReview.rating eq n ? 'selected' : ''}>${n} sao</option>
                </c:forEach>
              </select>
            </div>
            <div class="field">
              <label for="editComment">Nhận xét</label>
              <textarea id="editComment" name="comment" rows="3" maxlength="1000"><c:out value="${myReview.comment}"/></textarea>
            </div>
            <div class="row-tight">
              <button type="submit" class="btn btn-primary">Lưu</button>
              <a class="btn" href="${ctx}/product/detail?id=${product.productId}">Thôi</a>
            </div>
          </form>
        </c:if>
      </c:when>

      <c:when test="${canReview}">
        <form method="post" action="${ctx}/product/detail" class="stack mb">
          <input type="hidden" name="_csrf" value="${csrfToken}">
          <input type="hidden" name="action" value="reviewAdd">
          <input type="hidden" name="productId" value="${product.productId}">
          <div class="field">
            <label for="rating">Bạn thấy món này thế nào?</label>
            <select id="rating" name="rating" required>
              <option value="5">5 sao — rất ngon</option>
              <option value="4">4 sao — ngon</option>
              <option value="3">3 sao — bình thường</option>
              <option value="2">2 sao — chưa ổn</option>
              <option value="1">1 sao — dở</option>
            </select>
          </div>
          <div class="field">
            <label for="comment">Nhận xét (không bắt buộc)</label>
            <textarea id="comment" name="comment" rows="3" maxlength="1000"
                      placeholder="Món nóng hay nguội, đúng giờ hẹn không..."></textarea>
          </div>
          <button type="submit" class="btn btn-primary">Gửi đánh giá</button>
        </form>
      </c:when>

      <c:when test="${empty me}">
        <p class="small muted mb">
          <a href="${ctx}/login">Đăng nhập</a> để đánh giá — chỉ khách đã nhận món mới viết được.
        </p>
      </c:when>

      <c:when test="${me.roleName eq 'CUSTOMER'}">
        <p class="small muted mb">Bạn chưa nhận món này nên chưa đánh giá được.
          Đánh giá chỉ mở cho đơn đã giao, để điểm của món nói đúng về món.</p>
      </c:when>
    </c:choose>

    <c:choose>
      <c:when test="${empty reviews}">
        <div class="empty"><div class="icon" aria-hidden="true">💬</div>
          Chưa có đánh giá nào cho món này.
        </div>
      </c:when>
      <c:otherwise>
        <c:forEach var="r" items="${reviews}">
          <div class="review">
            <div class="row-between">
              <div>
                <span class="stars" aria-hidden="true">${r.stars}</span>
                <span class="visually-hidden">${r.rating} trên 5 sao</span>
                <strong><c:out value="${r.customerName}"/></strong>
              </div>
              <span class="small muted">
                ${ff:dateTime(r.createdAt)}<c:if test="${r.edited}"> · đã sửa</c:if>
              </span>
            </div>
            <c:if test="${not empty r.comment}">
              <p class="small"><c:out value="${r.comment}"/></p>
            </c:if>
          </div>
        </c:forEach>
      </c:otherwise>
    </c:choose>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
