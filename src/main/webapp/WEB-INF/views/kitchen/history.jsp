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
                <button type="button" class="btn btn-sm" data-note-edit
                        data-note-id="${n.kitchenNoteId}"
                        data-note-date="${n.shiftDate}"
                        data-note-content="${fn:escapeXml(n.content)}">Sửa</button>
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

  <div class="card" id="note-form-card">
    <h2 id="note-form-title">${empty editingNote ? 'Thêm ghi chú bếp' : 'Sửa ghi chú bếp'}</h2>
    <form method="post" action="${ctx}/kitchen/history" id="note-form"
          data-confirm="${empty editingNote ? 'Thêm ghi chú bếp này?' : 'Lưu thay đổi cho ghi chú này?'}">
      <input type="hidden" name="_csrf" value="${csrfToken}">
      <input type="hidden" name="action" id="note-action" value="${empty editingNote ? 'noteAdd' : 'noteUpdate'}">
      <input type="hidden" name="noteId" id="note-id" value="${editingNote.kitchenNoteId}">
      <input type="hidden" name="shiftDate" id="note-date" value="${empty editingNote ? today : editingNote.shiftDate}">
      <c:if test="${mineOnly}"><input type="hidden" name="mine" value="1"></c:if>

      <p class="small muted" id="note-date-text">
        <c:choose>
          <c:when test="${empty editingNote}">Ngày ghi chú: ${today}. Hệ thống không cho tạo ghi chú lùi ngày.</c:when>
          <c:otherwise>Ngày ghi chú: ${editingNote.shiftDate}</c:otherwise>
        </c:choose>
      </p>

      <div class="field">
        <label for="content">Nội dung ghi chú</label>
        <textarea id="content" name="content" maxlength="1000" required><c:out value="${editingNote.content}"/></textarea>
      </div>

      <div class="row-between">
        <button type="submit" class="btn btn-primary" id="note-submit-btn">
          ${empty editingNote ? 'Thêm ghi chú' : 'Lưu thay đổi'}
        </button>
        <button type="button" class="btn btn-secondary" id="note-cancel-btn" ${empty editingNote ? 'hidden' : ''}>Huỷ sửa</button>
      </div>
    </form>
  </div>

  <script>
    document.addEventListener('DOMContentLoaded', function () {
      var card = document.getElementById('note-form-card');
      var form = document.getElementById('note-form');
      var title = document.getElementById('note-form-title');
      var actionInput = document.getElementById('note-action');
      var noteIdInput = document.getElementById('note-id');
      var dateInput = document.getElementById('note-date');
      var dateText = document.getElementById('note-date-text');
      var contentInput = document.getElementById('content');
      var submitBtn = document.getElementById('note-submit-btn');
      var cancelBtn = document.getElementById('note-cancel-btn');

      function resetForm() {
        if (!form) return;
        title.textContent = 'Thêm ghi chú bếp';
        actionInput.value = 'noteAdd';
        noteIdInput.value = '';
        dateInput.value = '${today}';
        dateText.textContent = 'Ngày ghi chú: ${today}. Hệ thống không cho tạo ghi chú lùi ngày.';
        contentInput.value = '';
        submitBtn.textContent = 'Thêm ghi chú';
        if (cancelBtn) cancelBtn.hidden = true;
      }

      document.addEventListener('click', function (e) {
        var editBtn = e.target.closest('[data-note-edit]');
        if (editBtn) {
          e.preventDefault();
          var id = editBtn.dataset.noteId;
          var date = editBtn.dataset.noteDate;
          var content = editBtn.dataset.noteContent;

          title.textContent = 'Sửa ghi chú bếp';
          actionInput.value = 'noteUpdate';
          noteIdInput.value = id;
          dateInput.value = date;
          dateText.textContent = 'Ngày ghi chú: ' + date;
          contentInput.value = content;
          submitBtn.textContent = 'Lưu thay đổi';
          if (cancelBtn) cancelBtn.hidden = false;

          card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
          contentInput.focus();
          return;
        }

        if (e.target && e.target.id === 'note-cancel-btn') {
          resetForm();
        }
      });
    });
  </script>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
