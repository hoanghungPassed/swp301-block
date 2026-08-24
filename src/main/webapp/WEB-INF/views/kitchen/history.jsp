<c:set var="pageTitle" value="Đã hoàn thành" /><c:set var="nav" value="history" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head">
    <h1>Món đã hoàn thành</h1>
    <p>
      Kèm thời gian làm và giờ khách hẹn để đối chiếu.
    </p>
  </div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <div class="tabs">
    <a href="${ctx}/kitchen/history" class="${mineOnly ? '' : 'active'}"
       ${mineOnly ? '' : 'aria-current="page"'}>Cả bếp</a>
    <a href="${ctx}/kitchen/history?mine=1" class="${mineOnly ? 'active' : ''}"
       ${mineOnly ? 'aria-current="page"' : ''}>Món tôi làm</a>
  </div>

  <div class="card pad0 table-wrap">
    <table>
      <thead>
        <tr><th scope="col">Món</th><th scope="col">Đơn</th><th scope="col" class="center">SL</th><th scope="col">Người làm</th>
            <th scope="col">Bắt đầu</th><th scope="col">Xong lúc</th><th scope="col">Giờ hẹn</th><th scope="col">Đúng hẹn</th></tr>
      </thead>
      <tbody>
        <c:forEach var="item" items="${pageData.items}">
          <tr>
            <td><a href="${ctx}/kitchen/item?id=${item.orderItemId}"><c:out
                 value="${item.productNameSnapshot}"/></a></td>
            <td>#${item.orderId}</td>
            <td class="center">${item.quantity}</td>
            <td class="small"><c:out value="${item.assignedToName}"/></td>
            <td class="small muted">${ff:time(item.startedAt)}</td>
            <td class="small muted">${ff:time(item.readyAt)}</td>
            <td class="small">
              <c:choose>
                <c:when test="${empty item.pickupTime}"><span class="muted">Tại quầy</span></c:when>
                <c:otherwise>${ff:time(item.pickupTime)}</c:otherwise>
              </c:choose>
            </td>
            <td>
              <c:choose>
                <c:when test="${empty item.pickupTime}"><span class="tag tag-muted">—</span></c:when>
                <c:when test="${item.readyAt le item.pickupTime}"><span class="tag tag-green">Đúng hẹn</span></c:when>
                <c:otherwise><span class="tag tag-red">Trễ</span></c:otherwise>
              </c:choose>
            </td>
          </tr>
        </c:forEach>
        <c:if test="${pageData.emptyPage}">
          <tr><td colspan="8" class="center muted cell-empty">
            ${mineOnly ? 'Bạn chưa hoàn thành món nào.' : 'Chưa có món nào hoàn thành.'}
          </td></tr>
        </c:if>
      </tbody>
    </table>
    <ui:pager page="${pageData}" label="món" />
  </div>

  <div class="card pad0 table-wrap">
    <div class="card-head"><h2>Ghi chú bếp (7 ngày gần nhất)</h2></div>
    <table>
      <thead>
        <tr>
          <th scope="col">Ngày</th><th scope="col">Nội dung</th><th scope="col">Người viết</th>
          <th scope="col"><span class="visually-hidden">Thao tác</span></th>
        </tr>
      </thead>
      <tbody>
        <c:forEach var="n" items="${kitchenNotes}">
          <tr>
            <td class="small muted">
              ${n.shiftDate}
              <c:if test="${n.edited}"><br><span class="small muted">đã sửa</span></c:if>
            </td>
            <td class="small"><c:out value="${n.content}"/></td>
            <td class="small"><c:out value="${n.authorName}"/></td>
            <td class="center">
              <c:if test="${n.authorId eq me.userId}">
                <a class="btn btn-sm" href="${ctx}/kitchen/history?editNote=${n.kitchenNoteId}<c:if
                   test="${mineOnly}">&amp;mine=1</c:if>">Sửa</a>
                <form method="post" action="${ctx}/kitchen/history" class="inline-form"
                      data-confirm="Xoá hẳn ghi chú này?">
                  <input type="hidden" name="_csrf" value="${csrfToken}">
                  <input type="hidden" name="action" value="noteDelete">
                  <input type="hidden" name="noteId" value="${n.kitchenNoteId}">
                  <c:if test="${mineOnly}"><input type="hidden" name="mine" value="1"></c:if>
                  <button type="submit" class="btn btn-sm btn-danger">Xoá</button>
                </form>
              </c:if>
            </td>
          </tr>
        </c:forEach>
        <c:if test="${empty kitchenNotes}">
          <tr><td colspan="4" class="center muted cell-empty">Chưa có ghi chú bếp nào trong 7 ngày qua.</td></tr>
        </c:if>
      </tbody>
    </table>
  </div>

  <div class="card">
    <h2>${empty editingNote ? 'Thêm ghi chú bếp' : 'Sửa ghi chú bếp'}</h2>
    <form method="post" action="${ctx}/kitchen/history"
          data-confirm="${empty editingNote ? 'Thêm ghi chú bếp này?' : 'Lưu thay đổi cho ghi chú này?'}">
      <input type="hidden" name="_csrf" value="${csrfToken}">
      <input type="hidden" name="action" value="${empty editingNote ? 'noteAdd' : 'noteUpdate'}">
      <c:if test="${mineOnly}"><input type="hidden" name="mine" value="1"></c:if>
      <c:choose>
        <c:when test="${empty editingNote}">
          <input type="hidden" id="shiftDate" name="shiftDate" value="${today}">
          <p class="small muted">Ngày ghi chú: ${today}. Hệ thống không cho tạo ghi chú lùi ngày.</p>
        </c:when>
        <c:otherwise>
          <input type="hidden" name="noteId" value="${editingNote.kitchenNoteId}">
          <p class="small muted">Ngày ghi chú: ${editingNote.shiftDate}</p>
        </c:otherwise>
      </c:choose>
      <div class="field">
        <label for="content">Nội dung ghi chú</label>
        <textarea id="content" name="content" maxlength="1000" required><c:out value="${editingNote.content}"/></textarea>
      </div>
      <button type="submit" class="btn btn-primary btn-block">
        ${empty editingNote ? 'Thêm ghi chú' : 'Lưu thay đổi'}
      </button>
    </form>
    <c:if test="${not empty editingNote}">
      <a class="btn btn-block mt" href="${ctx}/kitchen/history<c:if test="${mineOnly}">?mine=1</c:if>">Huỷ sửa</a>
    </c:if>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
