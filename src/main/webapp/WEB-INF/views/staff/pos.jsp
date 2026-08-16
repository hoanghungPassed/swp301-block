<c:set var="pageTitle" value="Bán tại quầy" /><c:set var="nav" value="pos" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head">
    <h1>Bán tại quầy</h1>
    <p>Khách đứng đợi tại chỗ. Thu tiền xong đơn xuống bếp ngay, không cần mã nhận hàng.</p>
  </div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <%-- Ca làm việc nằm ngay trên màn bán hàng chứ không chỉ ở trang Lịch sử. Đơn bán khi chưa
       mở ca vẫn ghi nhận bình thường nhưng không vào được bảng đối soát tiền cuối ca; một cảnh
       báo đặt ở trang mà thu ngân chỉ mở lúc cuối ca thì đã muộn mất một ca. --%>
  <c:choose>
    <c:when test="${empty currentShift}">
      <div class="alert alert-warn">
        <strong>Chưa mở ca.</strong> Đơn bán ra vẫn ghi nhận bình thường nhưng
        <strong>không vào được bảng đối soát tiền cuối ca</strong>.
        <a href="${ctx}/staff/history">Mở ca →</a>
      </div>
    </c:when>
    <c:otherwise>
      <p class="small muted">
        Ca mở lúc ${ff:time(currentShift.openedAt)} · đã gắn ${currentShift.orderCount} đơn ·
        tiền đầu ca ${ff:money(currentShift.openingCash)}.
        <a href="${ctx}/staff/history">Đóng ca và đối soát →</a>
      </p>
    </c:otherwise>
  </c:choose>

  <div class="grid grid-side">
    <div>
      <div class="card">
        <form method="get" action="${ctx}/staff/pos" class="form-row">
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

      <div class="menu-grid">
        <c:forEach var="p" items="${products}">
          <form method="post" action="${ctx}/staff/pos" class="product">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="action" value="add">
            <input type="hidden" name="productId" value="${p.productId}">
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
      </div>
    </div>

    <div class="card">
      <div class="row-between mb">
        <h2>Phiếu tính tiền</h2>
        <c:if test="${not empty posLines}">
          <form method="post" action="${ctx}/staff/pos" class="inline-form">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="action" value="clear">
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
                    <%-- Dòng ở lại phiếu thay vì lặng lẽ biến mất: ô số lượng bên cạnh chính là
                         chỗ đặt về 0 để bỏ nó ra. --%>
                    <div class="small"><span class="tag tag-red">Không còn phục vụ</span>
                      <span class="muted">— đặt số lượng về 0 để bỏ ra</span></div>
                  </c:otherwise>
                </c:choose>
              </div>
              <div class="row-tight">
                <form method="post" action="${ctx}/staff/pos" class="row-tight">
                  <input type="hidden" name="_csrf" value="${csrfToken}">
                  <input type="hidden" name="action" value="setQty">
                  <input type="hidden" name="productId" value="${line.productId}">
                  <%-- max phải bằng đúng BusinessRule.MAX_QUANTITY_PER_LINE. Để 99 như bản
                       trước thì ô nhập nhận một con số mà máy chủ chắc chắn từ chối. --%>
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
            <%-- Còn một món không bán được thì cả phiếu bị máy chủ từ chối lúc lập đơn. Giấu nút
                 thu tiền đi và nói rõ vì sao, thay vì để thu ngân bấm rồi nhận lỗi khi khách đã
                 rút ví ra. Treo phiếu và xoá giỏ vẫn dùng được — đó là hai lối thoát ở đây. --%>
            <c:when test="${posUnavailable}">
              <div class="alert alert-error mt" role="alert">
                <strong>Chưa thu tiền được.</strong> Phiếu đang có món không còn phục vụ. Đặt số
                lượng của món đó về 0 rồi thu tiền lại.
              </div>
            </c:when>
            <c:otherwise>
              <h3 class="mt">Thu tiền</h3>
              <div class="stack">
                <%-- Ô "khách đưa" không có thuộc tính name nên không gửi lên máy chủ: nó chỉ là
                     cái máy tính tay để khỏi nhẩm tiền thối. Số tiền thối không phải dữ liệu
                     nghiệp vụ — hệ thống ghi nhận khoản thu đúng bằng tổng đơn. --%>
                <form method="post" action="${ctx}/staff/pos"
                      data-change-form data-total="${posTotal}">
                  <input type="hidden" name="_csrf" value="${csrfToken}">
                  <input type="hidden" name="action" value="pay">
                  <input type="hidden" name="method" value="CASH">
                  <label for="posTendered">Khách đưa (đ)</label>
                  <input type="number" id="posTendered" min="0" step="1000" inputmode="numeric"
                         autocomplete="off" placeholder="Bỏ trống cũng thu tiền được"
                         data-change-input>
                  <p class="total-line grand" data-change-output hidden aria-live="polite"></p>
                  <button type="submit" class="btn btn-green btn-block touch">Khách trả tiền mặt</button>
                </form>
                <form method="post" action="${ctx}/staff/pos">
                  <input type="hidden" name="_csrf" value="${csrfToken}">
                  <input type="hidden" name="action" value="pay">
                  <input type="hidden" name="method" value="ONLINE_GATEWAY">
                  <label for="posReference">Mã giao dịch trên biên lai</label>
                  <input type="text" id="posReference" name="reference" required maxlength="100"
                         autocomplete="off" placeholder="Gõ lại mã in trên biên lai máy thanh toán">
                  <p class="muted small">Nhập sau khi máy báo giao dịch thành công. Mã này dùng để đối soát
                    với sao kê, và mỗi mã chỉ dùng được cho một đơn.</p>
                  <button type="submit" class="btn btn-blue btn-block touch">Khách quẹt thẻ hoặc quét mã QR</button>
                </form>
              </div>
            </c:otherwise>
          </c:choose>

          <h3 class="mt">Chưa tính tiền được ngay?</h3>
          <form method="post" action="${ctx}/staff/pos" class="stack">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="action" value="hold">
            <label for="holdLabel">Treo phiếu lại với tên</label>
            <input type="text" id="holdLabel" name="label" required maxlength="100"
                   autocomplete="off" placeholder="Bàn 3, anh áo xanh...">
            <input type="text" name="note" maxlength="500" class="input-sm"
                   placeholder="Ghi chú (không bắt buộc)">
            <p class="muted small">Giỏ được cất lại để phục vụ khách tiếp theo. Phiếu treo chưa
              phải là đơn hàng: chưa có mã đơn, chưa thu tiền, bếp chưa thấy gì.</p>
            <button type="submit" class="btn btn-block">Treo phiếu</button>
          </form>
        </c:otherwise>
      </c:choose>
    </div>
  </div>

  <div class="card mt">
    <div class="row-between mb">
      <h2>Phiếu đang treo <c:if test="${not empty holds}">(${fn:length(holds)})</c:if></h2>
    </div>

    <c:choose>
      <c:when test="${empty holds}">
        <div class="empty"><div class="icon" aria-hidden="true">📌</div>
          Chưa treo phiếu nào. Khi khách bảo chờ, treo phiếu lại rồi phục vụ người tiếp theo.
        </div>
      </c:when>
      <c:otherwise>
        <table class="table">
          <thead>
            <tr>
              <th>Phiếu</th>
              <th>Món</th>
              <th class="num">Tổng tiền</th>
              <th>Treo lúc</th>
              <th class="actions">Thao tác</th>
            </tr>
          </thead>
          <tbody>
            <c:forEach var="h" items="${holds}">
              <tr>
                <td>
                  <strong><c:out value="${h.label}"/></strong>
                  <c:if test="${not empty h.note}">
                    <div class="small muted"><c:out value="${h.note}"/></div>
                  </c:if>
                  <c:if test="${h.anyUnavailable}">
                    <span class="tag tag-red">Có món không còn bán</span>
                  </c:if>
                </td>
                <td>
                  <c:forEach var="i" items="${h.items}" varStatus="st">
                    <div class="row-between middle">
                      <span class="${i.orderable ? '' : 'muted'}">
                        <c:out value="${i.productName}"/>
                        <c:if test="${not i.orderable}"> — không còn bán</c:if>
                      </span>
                      <form method="post" action="${ctx}/staff/pos" class="row-tight">
                        <input type="hidden" name="_csrf" value="${csrfToken}">
                        <input type="hidden" name="action" value="holdSetQty">
                        <input type="hidden" name="holdId" value="${h.holdId}">
                        <input type="hidden" name="productId" value="${i.productId}">
                        <input type="number" name="quantity" value="${i.quantity}" min="0" max="50"
                               class="qty-input" data-autosubmit
                               title="Đặt về 0 để bỏ món khỏi phiếu"
                               aria-label="Số lượng của <c:out value="${i.productName}"/> trong phiếu <c:out value="${h.label}"/>">
                      </form>
                    </div>
                  </c:forEach>
                </td>
                <td class="num">${ff:money(h.total)}</td>
                <td>
                  ${ff:dateTime(h.createdAt)}
                  <c:if test="${not empty h.updatedAt}">
                    <div class="small muted">đã sửa</div>
                  </c:if>
                </td>
                <td class="actions">
                  <form method="post" action="${ctx}/staff/pos" class="inline-form">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="resume">
                    <input type="hidden" name="holdId" value="${h.holdId}">
                    <button type="submit" class="btn btn-sm btn-primary">Lấy ra tính tiền</button>
                  </form>
                  <a class="btn btn-sm" href="${ctx}/staff/pos?editHold=${h.holdId}">Sửa tên</a>
                  <%-- data-confirm chứ không phải onsubmit="confirm(...)": hộp thoại gốc của
                       trình duyệt có ô "chặn không cho trang này hỏi nữa", tick nhầm một lần là
                       từ đó bỏ phiếu không hỏi lại câu nào. Xem bindConfirm trong app.js. --%>
                  <form method="post" action="${ctx}/staff/pos" class="inline-form"
                        data-confirm="Bỏ hẳn phiếu &quot;${fn:escapeXml(h.label)}&quot;?">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="holdDiscard">
                    <input type="hidden" name="holdId" value="${h.holdId}">
                    <button type="submit" class="btn btn-sm btn-danger">Bỏ phiếu</button>
                  </form>
                </td>
              </tr>
              <c:if test="${not empty editingHold and editingHold.holdId eq h.holdId}">
                <tr class="note-row">
                  <td colspan="5">
                    <form method="post" action="${ctx}/staff/pos" class="form-row">
                      <input type="hidden" name="_csrf" value="${csrfToken}">
                      <input type="hidden" name="action" value="holdRename">
                      <input type="hidden" name="holdId" value="${h.holdId}">
                      <div class="field">
                        <label for="editLabel">Tên phiếu</label>
                        <input type="text" id="editLabel" name="label" required maxlength="100"
                               value="<c:out value="${editingHold.label}"/>">
                      </div>
                      <div class="field">
                        <label for="editNote">Ghi chú</label>
                        <input type="text" id="editNote" name="note" maxlength="500"
                               value="<c:out value="${editingHold.note}"/>">
                      </div>
                      <button type="submit" class="btn btn-primary">Lưu</button>
                      <a class="btn" href="${ctx}/staff/pos">Thôi</a>
                    </form>
                  </td>
                </tr>
              </c:if>
            </c:forEach>
          </tbody>
        </table>
      </c:otherwise>
    </c:choose>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
