<c:set var="pageTitle" value="Thực đơn" /><c:set var="nav" value="menu" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <%--
    Địa chỉ giữ nguyên bộ lọc hiện tại, chỉ thay một mảnh. Dựng sẵn ở đây rồi dùng lại cho
    cả dãy nhãn nhóm món bên dưới: bấm "Món chính" mà mất luôn từ khoá vừa gõ là kiểu mất
    mát khiến người ta gõ lại lần thứ hai rồi thôi không lọc nữa.
  --%>
  <c:url var="urlAllCategories" value="/menu">
    <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}"/></c:if>
    <c:if test="${sort ne 'DEFAULT'}"><c:param name="sort" value="${sort}"/></c:if>
  </c:url>

  <c:choose>
    <c:when test="${empty me}">
      <%--
        Khách lạ mở trang lần đầu chỉ thấy một lưới món thì không biết đây là chỗ đặt trước
        hay chỉ là tờ thực đơn xem cho biết. Khối này trả lời đúng ba câu: làm gì được ở
        đây, làm theo mấy bước, và bước đầu tiên nằm ở nút nào.

        Con số phút và khung giờ lấy từ cấu hình qua MenuServlet, không gõ tay vào đây.
      --%>
      <section class="hero">
        <div class="hero-main">
          <p class="hero-eyebrow">Đặt trước · Đến lấy tại quầy</p>
          <h1>Đồ ăn xong đúng lúc bạn tới</h1>
          <p class="hero-lead">
            Chọn món và hẹn giờ ghé qua. Bếp canh đúng giờ đó mới làm, nên bạn không phải
            xếp hàng gọi món và cũng không nhận đồ đã nguội vì làm sẵn từ sớm.
          </p>
          <div class="actions hero-cta">
            <a class="btn btn-primary touch" href="${ctx}/register">Tạo tài khoản</a>
            <a class="btn touch" href="${ctx}/login">Tôi đã có tài khoản</a>
          </div>
          <p class="small muted">Xem thực đơn và giá bên dưới không cần đăng nhập.</p>
        </div>

        <%-- role="list" vì list-style:none làm Safari bỏ luôn ngữ nghĩa danh sách, và khi đó
             trình đọc màn hình không còn nói "3 mục" — mất đúng cái ý "chỉ có ba bước". --%>
        <ol class="hero-steps" role="list">
          <li>
            <span class="n" aria-hidden="true">1</span>
            <div>
              <strong>Chọn món</strong>
              <span class="small muted">Giá và điểm đánh giá của khách hiện ngay trên từng món.</span>
            </div>
          </li>
          <li>
            <span class="n" aria-hidden="true">2</span>
            <div>
              <strong>Hẹn giờ lấy</strong>
              <span class="small muted">
                Cửa hàng nhận đơn cho khung ${openHour}h–${closeHour}h, hẹn trước ít nhất
                ${minLeadMinutes} phút để bếp kịp làm.
              </span>
            </div>
          </li>
          <li>
            <span class="n" aria-hidden="true">3</span>
            <div>
              <strong>Đọc mã, nhận đồ</strong>
              <span class="small muted">Tới quầy đọc mã nhận hàng là xong, không phải chờ lượt.</span>
            </div>
          </li>
        </ol>
      </section>
    </c:when>

    <c:otherwise>
      <div class="page-head row-between">
        <div>
          <h1>Thực đơn</h1>
          <p>Đặt trước, chọn giờ đến lấy — món làm xong đúng lúc bạn tới.</p>
        </div>
        <c:if test="${not empty cartCount and cartCount > 0}">
          <a class="btn btn-primary" href="${ctx}/cart">Giỏ hàng (${cartCount})</a>
        </c:if>
      </div>
    </c:otherwise>
  </c:choose>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <c:if test="${not empty favourites}">
    <div class="card pad0 table-wrap">
      <div class="card-head">
        <h2>Món quen của tôi (${fn:length(favourites)})</h2>
        <span class="small muted">Ghi chú ở đây là để bạn nhớ, chưa gửi sang bếp</span>
      </div>
      <%-- table-cards: trên điện thoại mỗi dòng gập thành một thẻ dọc. Bảng bốn cột này
           trước đây tràn ngang khỏi màn hình, mà đây lại đúng là khối khách hay mở trên
           điện thoại nhất — chỗ đặt lại món đã ăn quen. --%>
      <table class="table-cards">
        <thead>
          <tr><th scope="col">Món</th><th scope="col">Ghi chú riêng</th>
              <th scope="col" class="num">Giá</th><th scope="col" class="actions">Thao tác</th></tr>
        </thead>
        <tbody>
          <c:forEach var="f" items="${favourites}">
            <tr>
              <td data-label="Món">
                <a href="${ctx}/product/detail?id=${f.productId}"><c:out value="${f.productName}"/></a>
                <div class="small muted"><c:out value="${f.categoryName}"/></div>
                <c:if test="${not f.orderable}">
                  <span class="tag tag-amber">Hiện không phục vụ</span>
                </c:if>
              </td>
              <td class="small" data-label="Ghi chú">
                <c:choose>
                  <c:when test="${empty f.note}"><span class="muted">—</span></c:when>
                  <c:otherwise><c:out value="${f.note}"/></c:otherwise>
                </c:choose>
                <c:if test="${f.edited}"><span class="small muted"> · đã sửa</span></c:if>
              </td>
              <td class="num" data-label="Giá">${ff:money(f.price)}</td>
              <td class="actions" data-label="Thao tác">
                <c:if test="${f.orderable}">
                  <form method="post" action="${ctx}/cart" class="inline-form">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="add">
                    <input type="hidden" name="productId" value="${f.productId}">
                    <input type="hidden" name="quantity" value="1">
                    <input type="hidden" name="returnTo" value="/menu">
                    <button type="submit" class="btn btn-sm btn-primary">Thêm vào giỏ</button>
                  </form>
                </c:if>
                <a class="btn btn-sm" href="${ctx}/menu?editFav=${f.favouriteId}">Sửa ghi chú</a>
                <form method="post" action="${ctx}/menu" class="inline-form">
                  <input type="hidden" name="_csrf" value="${csrfToken}">
                  <input type="hidden" name="action" value="favRemove">
                  <input type="hidden" name="favouriteId" value="${f.favouriteId}">
                  <input type="hidden" name="returnTo" value="/menu">
                  <button type="submit" class="btn btn-sm btn-danger">Bỏ</button>
                </form>
              </td>
            </tr>
            <c:if test="${not empty editingFavourite and editingFavourite.favouriteId eq f.favouriteId}">
              <tr class="note-row">
                <td colspan="4">
                  <form method="post" action="${ctx}/menu" class="form-row">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="favNote">
                    <input type="hidden" name="favouriteId" value="${f.favouriteId}">
                    <input type="hidden" name="returnTo" value="/menu">
                    <div class="field">
                      <label for="favNote">Ghi chú riêng cho món này</label>
                      <input type="text" id="favNote" name="note" maxlength="255"
                             placeholder="ít cay, không hành, nhiều đá..."
                             value="<c:out value="${editingFavourite.note}"/>">
                    </div>
                    <button type="submit" class="btn btn-primary">Lưu</button>
                    <a class="btn" href="${ctx}/menu">Thôi</a>
                  </form>
                </td>
              </tr>
            </c:if>
          </c:forEach>
        </tbody>
      </table>
    </div>
  </c:if>

  <div class="card">
    <form method="get" action="${ctx}/menu" class="form-row">
      <%-- Nhóm món đang mở đi kèm ô ẩn: gõ từ khoá rồi bấm Tìm thì vẫn ở trong nhóm đang xem,
           chứ không bị ném ngược ra toàn bộ thực đơn. --%>
      <input type="hidden" name="categoryId" value="${selectedCategory}">
      <div class="field">
        <label for="keyword">Tìm món</label>
        <input type="search" id="keyword" name="keyword" value="<c:out value="${keyword}"/>" placeholder="Nhập tên món...">
      </div>
      <div class="field field-narrow">
        <label for="sort">Sắp xếp</label>
        <%-- data-autosubmit: có JavaScript thì đổi ô là danh sách xếp lại luôn. Không có thì
             nút Tìm bên cạnh vẫn làm đúng việc đó — nên ô này không bao giờ thành ô chết. --%>
        <select id="sort" name="sort" data-autosubmit>
          <option value="DEFAULT"    ${sort eq 'DEFAULT'    ? 'selected' : ''}>Mặc định</option>
          <option value="PRICE_ASC"  ${sort eq 'PRICE_ASC'  ? 'selected' : ''}>Giá thấp → cao</option>
          <option value="PRICE_DESC" ${sort eq 'PRICE_DESC' ? 'selected' : ''}>Giá cao → thấp</option>
          <option value="RATING"     ${sort eq 'RATING'     ? 'selected' : ''}>Đánh giá cao nhất</option>
        </select>
      </div>
      <button type="submit" class="btn btn-primary">Tìm</button>
    </form>

    <%--
      Nhóm món là liên kết chứ không phải ô chọn kèm nút bấm: lọc theo nhóm là thao tác hay
      dùng nhất ở trang này, và một cú bấm vẫn hơn ba cú (mở ô, chọn dòng, bấm Lọc). Là thẻ <a>
      nên mỗi nhóm có địa chỉ riêng — chia sẻ được, và nút Lùi của trình duyệt chạy đúng.
    --%>
    <nav class="chips mt" aria-label="Lọc theo nhóm món">
      <a class="chip ${empty selectedCategory ? 'on' : ''}" href="${urlAllCategories}"
         ${empty selectedCategory ? 'aria-current="true"' : ''}>Tất cả</a>
      <c:forEach var="cat" items="${categories}">
        <c:url var="urlCategory" value="/menu">
          <c:param name="categoryId" value="${cat.categoryId}"/>
          <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}"/></c:if>
          <c:if test="${sort ne 'DEFAULT'}"><c:param name="sort" value="${sort}"/></c:if>
        </c:url>
        <a class="chip ${selectedCategory eq cat.categoryId ? 'on' : ''}" href="${urlCategory}"
           ${selectedCategory eq cat.categoryId ? 'aria-current="true"' : ''}><c:out value="${cat.name}"/></a>
      </c:forEach>
    </nav>
  </div>

  <%--
    Dòng đếm kết quả, đặt ngay trên lưới. Lọc xong mà không có con số nào thì khách phải tự
    đếm thẻ món để biết bộ lọc vừa rồi có tác dụng gì — nhất là khi kết quả nhiều hơn một
    màn hình. Nút bỏ lọc nằm cùng dòng và chỉ hiện khi đang thật sự có bộ lọc.
  --%>
  <div class="row-between result-line">
    <p class="small muted">
      <c:choose>
        <c:when test="${empty products}">Không có món nào khớp</c:when>
        <c:otherwise><strong>${fn:length(products)}</strong> món</c:otherwise>
      </c:choose>
      <c:if test="${not empty keyword}"> cho “<c:out value="${keyword}"/>”</c:if>
    </p>
    <c:if test="${hasFilter}">
      <a class="btn btn-sm" href="${ctx}/menu">Bỏ lọc</a>
    </c:if>
  </div>

  <c:choose>
    <c:when test="${empty products}">
      <%-- Ngõ cụt phải có lối ra. Trước đây khối này chỉ báo không tìm thấy rồi để khách tự
           nghĩ cách quay lại thực đơn đầy đủ. --%>
      <div class="card empty">
        <div class="icon" aria-hidden="true">🍽️</div>
        <p>Không tìm thấy món nào phù hợp với bộ lọc đang chọn.</p>
        <c:if test="${hasFilter}">
          <a class="btn mt" href="${ctx}/menu">Xem toàn bộ thực đơn</a>
        </c:if>
      </div>
    </c:when>
    <c:otherwise>
      <div class="menu-grid">
        <c:forEach var="p" items="${products}">
          <%-- <article> chứ không phải <div>: mỗi thẻ món là một khối tự đứng được, nhờ vậy
               trình đọc màn hình cho phép nhảy giữa các món thay vì đọc tuột cả lưới. --%>
          <article class="product">
            <%--
              Ảnh lấy từ đường dẫn ngoài do quản trị viên nhập, nên phải phòng trường hợp
              đường dẫn chết hoặc máy không có mạng: app.js bắt sự kiện lỗi và thay ảnh
              bằng nền giữ chỗ, trang không bao giờ hiện ô ảnh vỡ.
              alt để rỗng vì tên món nằm ngay bên dưới — đọc lại lần nữa chỉ làm rối.
            --%>
            <c:choose>
              <c:when test="${not empty p.imageUrl}">
                <img class="thumb thumb-img" src="<c:out value="${p.imageUrl}"/>" alt=""
                     loading="lazy" referrerpolicy="no-referrer" data-fallback="🍔">
              </c:when>
              <c:otherwise>
                <div class="thumb" aria-hidden="true">🍔</div>
              </c:otherwise>
            </c:choose>
            <div class="body">
              <span class="tag tag-muted"><c:out value="${p.categoryName}"/></span>
              <h3 class="name"><a href="${ctx}/product/detail?id=${p.productId}"><c:out value="${p.name}"/></a></h3>
              <%-- Món chưa ai đánh giá thì không hiện gì cả: một hàng năm sao rỗng trông như
                   món bị chê, trong khi thật ra chỉ là chưa ai chấm. --%>
              <c:if test="${p.rated}">
                <a class="rating-line small" href="${ctx}/product/detail?id=${p.productId}#danh-gia">
                  <span class="stars" aria-hidden="true">${p.ratingStars}</span>
                  <span class="visually-hidden">${p.ratingRounded} trên 5 sao,</span>
                  <strong>${p.ratingRounded}</strong>
                  <span class="muted">(${p.ratingCount})</span>
                </a>
              </c:if>
              <div class="desc"><c:out value="${p.description}"/></div>
              <div class="price">${ff:money(p.price)}</div>
            </div>
            <div class="foot">
              <c:choose>
                <c:when test="${empty me}">
                  <%-- Khách chưa đăng nhập nhìn thấy đúng một lối đi, không phải một nút bấm
                       vào rồi mới báo là phải đăng nhập. --%>
                  <a class="btn btn-block" href="${ctx}/login">Đăng nhập để đặt</a>
                </c:when>
                <c:when test="${me.roleName ne 'CUSTOMER'}">
                  <a class="btn btn-block" href="${ctx}/product/detail?id=${p.productId}">Xem chi tiết</a>
                </c:when>
                <c:otherwise>
                  <form method="post" action="${ctx}/cart">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="add">
                    <input type="hidden" name="productId" value="${p.productId}">
                    <input type="hidden" name="quantity" value="1">
                    <input type="hidden" name="returnTo" value="/menu">
                    <button type="submit" class="btn btn-primary btn-block">Thêm vào giỏ</button>
                  </form>
                  <c:choose>
                    <c:when test="${favouriteIds.contains(p.productId)}">
                      <form method="post" action="${ctx}/menu">
                        <input type="hidden" name="_csrf" value="${csrfToken}">
                        <input type="hidden" name="action" value="favRemoveByProduct">
                        <input type="hidden" name="productId" value="${p.productId}">
                        <input type="hidden" name="returnTo" value="/menu">
                        <button type="submit" class="btn btn-block btn-fav on"
                                title="Bỏ khỏi món quen">★ Món quen</button>
                      </form>
                    </c:when>
                    <c:otherwise>
                      <form method="post" action="${ctx}/menu">
                        <input type="hidden" name="_csrf" value="${csrfToken}">
                        <input type="hidden" name="action" value="favAdd">
                        <input type="hidden" name="productId" value="${p.productId}">
                        <input type="hidden" name="returnTo" value="/menu">
                        <button type="submit" class="btn btn-block btn-fav"
                                title="Lưu vào món quen">☆ Lưu món quen</button>
                      </form>
                    </c:otherwise>
                  </c:choose>
                </c:otherwise>
              </c:choose>
            </div>
          </article>
        </c:forEach>
      </div>
    </c:otherwise>
  </c:choose>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
