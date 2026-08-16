<c:set var="pageTitle" value="Chi tiết món" /><c:set var="nav" value="queue" />
<c:set var="mainClass" value="container medium" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <p class="small mb"><a href="${ctx}/kitchen/queue">← Hàng chờ</a></p>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <div class="card">
    <div class="row-between mb">
      <div>
        <h1><c:out value="${item.productNameSnapshot}"/></h1>
        <p class="small muted">Đơn #${item.orderId} · ${ff:orderSource(item.orderSource)}</p>
      </div>
      <span class="${ff:itemStatusClass(item.itemStatus)}">${ff:itemStatus(item.itemStatus)}</span>
    </div>

    <div class="grid grid-2">
      <div>
        <div class="total-line"><span class="muted">Số lượng</span><span><strong>${item.quantity}</strong></span></div>
        <div class="total-line"><span class="muted">Người làm</span>
          <span><c:out value="${empty item.assignedToName ? 'Chưa có ai nhận' : item.assignedToName}"/></span></div>
      </div>
      <div>
        <c:if test="${not empty item.pickupTime}">
          <div class="total-line"><span class="muted">Giờ khách hẹn</span>
            <span><strong>${ff:dateTime(item.pickupTime)}</strong></span></div>
          <div class="total-line"><span class="muted">Còn lại</span>
            <span>${ff:humanize(item.pickupTime)}</span></div>
        </c:if>
        <c:if test="${not empty item.startedAt}">
          <div class="total-line"><span class="muted">Bắt đầu</span><span>${ff:time(item.startedAt)}</span></div>
        </c:if>
        <c:if test="${not empty item.readyAt}">
          <div class="total-line"><span class="muted">Hoàn thành</span><span>${ff:time(item.readyAt)}</span></div>
        </c:if>
        <c:if test="${item.handedOver}">
          <div class="total-line"><span class="muted">Bàn giao ra quầy</span>
            <span>${ff:time(item.handedOverAt)} · <c:out value="${item.handedOverByName}"/></span></div>
        </c:if>
        <c:if test="${item.received}">
          <div class="total-line"><span class="muted">Quầy nhận</span>
            <span>${ff:time(item.receivedAt)} · <c:out value="${item.receivedByName}"/></span></div>
        </c:if>
      </div>
    </div>

    <%-- Mọi thao tác đều gửi về /kitchen/queue và mang theo returnTo để quay lại đúng trang
         này. Trước đây hai thao tác gửi về hai địa chỉ khác nhau, một trong số đó nay đã gộp. --%>
    <div class="actions mt">
      <c:if test="${item.itemStatus eq 'WAITING'}">
        <form method="post" action="${ctx}/kitchen/queue">
          <input type="hidden" name="_csrf" value="${csrfToken}">
          <input type="hidden" name="action" value="claim">
          <input type="hidden" name="orderItemId" value="${item.orderItemId}">
          <input type="hidden" name="returnTo" value="/kitchen/item?id=${item.orderItemId}">
          <button type="submit" class="btn btn-primary">Nhận món này</button>
        </form>
      </c:if>
      <c:if test="${item.itemStatus eq 'PREPARING'}">
        <form method="post" action="${ctx}/kitchen/queue">
          <input type="hidden" name="_csrf" value="${csrfToken}">
          <input type="hidden" name="action" value="ready">
          <input type="hidden" name="orderItemId" value="${item.orderItemId}">
          <input type="hidden" name="returnTo" value="/kitchen/item?id=${item.orderItemId}">
          <button type="submit" class="btn btn-green">Đã làm xong</button>
        </form>
      </c:if>
      <c:if test="${item.awaitingHandover}">
        <form method="post" action="${ctx}/kitchen/queue">
          <input type="hidden" name="_csrf" value="${csrfToken}">
          <input type="hidden" name="action" value="handover">
          <input type="hidden" name="orderItemId" value="${item.orderItemId}">
          <input type="hidden" name="returnTo" value="/kitchen/item?id=${item.orderItemId}">
          <button type="submit" class="btn btn-primary">Bàn giao ra quầy</button>
        </form>
      </c:if>
      <a class="btn" href="${ctx}/kitchen/issue?orderItemId=${item.orderItemId}">Báo sự cố</a>
    </div>
  </div>

  <c:if test="${not empty issues}">
    <div class="card pad0 table-wrap">
      <div class="card-head"><h2>Sự cố của món này</h2></div>
      <table>
        <thead><tr><th scope="col">Thời điểm</th><th scope="col">Loại</th><th scope="col">Mô tả</th><th scope="col">Người báo</th><th scope="col">Trạng thái</th></tr></thead>
        <tbody>
          <c:forEach var="i" items="${issues}">
            <tr>
              <td class="small muted">${ff:dateTime(i.createdAt)}</td>
              <td>${ff:issueType(i.issueType)}</td>
              <td class="small"><c:out value="${i.description}"/></td>
              <td class="small"><c:out value="${i.createdByName}"/></td>
              <td><span class="tag ${ff:issueStatusTag(i.status)}">${ff:issueStatus(i.status)}</span></td>
            </tr>
          </c:forEach>
        </tbody>
      </table>
    </div>
  </c:if>

  <%-- Ghi chú chế biến. Cố ý đứng RIÊNG khỏi bảng sự cố ngay trên: sự cố là chuyện phải xử lý
       và hiện thành cảnh báo đỏ ở màn thu ngân, ghi chú thì không. Gộp hai thứ vào một bảng
       sẽ khiến con số "sự cố chưa xử lý" phồng lên vì những dòng không phải sự cố. --%>
  <div class="card pad0 table-wrap">
    <div class="card-head"><h2>Ghi chú chế biến (${fn:length(notes)})</h2></div>
    <table>
      <thead>
        <tr>
          <th scope="col">Thời điểm</th><th scope="col">Nội dung</th><th scope="col">Người viết</th>
          <th scope="col"><span class="visually-hidden">Thao tác</span></th>
        </tr>
      </thead>
      <tbody>
        <c:forEach var="n" items="${notes}">
          <tr>
            <td class="small muted">
              ${ff:dateTime(n.createdAt)}
              <c:if test="${n.edited}"><br><span class="small muted">đã sửa</span></c:if>
            </td>
            <td class="small"><c:out value="${n.content}"/></td>
            <td class="small"><c:out value="${n.authorName}"/></td>
            <td class="center">
              <%-- Ẩn nút với người khác cho bảng gọn; quyền thật do
                   KitchenNoteService.requireOwnItemNote quyết định. --%>
              <c:if test="${n.authorId eq me.userId}">
                <a class="btn btn-sm" href="${ctx}/kitchen/item?id=${item.orderItemId}&amp;editNote=${n.noteId}">Sửa</a>
                <form method="post" action="${ctx}/kitchen/item" class="inline-form"
                      data-confirm="Xoá hẳn ghi chú này? Ghi chú không lưu lại dấu vết như sự cố.">
                  <input type="hidden" name="_csrf" value="${csrfToken}">
                  <input type="hidden" name="action" value="noteDelete">
                  <input type="hidden" name="noteId" value="${n.noteId}">
                  <input type="hidden" name="orderItemId" value="${item.orderItemId}">
                  <button type="submit" class="btn btn-sm btn-danger">Xoá</button>
                </form>
              </c:if>
            </td>
          </tr>
        </c:forEach>
        <c:if test="${empty notes}">
          <tr><td colspan="4" class="center muted cell-empty">Chưa có ghi chú nào cho món này.</td></tr>
        </c:if>
      </tbody>
    </table>
  </div>

  <div class="card">
    <h2>${empty editingNote ? 'Thêm ghi chú' : 'Sửa ghi chú'}</h2>
    <form method="post" action="${ctx}/kitchen/item">
      <input type="hidden" name="_csrf" value="${csrfToken}">
      <input type="hidden" name="action" value="${empty editingNote ? 'noteAdd' : 'noteUpdate'}">
      <input type="hidden" name="orderItemId" value="${item.orderItemId}">
      <c:if test="${not empty editingNote}">
        <input type="hidden" name="noteId" value="${editingNote.noteId}">
      </c:if>
      <div class="field">
        <label for="content">Nội dung</label>
        <textarea id="content" name="content" maxlength="500" required><c:out value="${editingNote.content}"/></textarea>
        <p class="small muted mt">
          Ghi chú chỉ là thông tin để lại cho ca sau. Chuyện cần người xử lý thì
          <a href="${ctx}/kitchen/issue?orderItemId=${item.orderItemId}">báo sự cố</a> thay vì ghi ở đây.
        </p>
      </div>
      <button type="submit" class="btn btn-primary btn-block">
        ${empty editingNote ? 'Thêm ghi chú' : 'Lưu thay đổi'}
      </button>
    </form>
    <c:if test="${not empty editingNote}">
      <a class="btn btn-block mt" href="${ctx}/kitchen/item?id=${item.orderItemId}">Huỷ sửa</a>
    </c:if>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
