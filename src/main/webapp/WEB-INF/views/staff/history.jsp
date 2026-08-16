<c:set var="pageTitle" value="Ca làm việc & lịch sử" /><c:set var="nav" value="history" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head">
    <h1>Ca làm việc &amp; lịch sử</h1>
    <p>Mở ca đầu giờ, đối soát tiền cuối giờ, và tra lại những đơn đã bán.</p>
  </div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <%-- Ca làm việc đứng đầu trang: nó là việc có thời hạn — mở đầu ca, đóng cuối ca — còn tra
       cứu đơn thì làm lúc nào cũng được. Đặt dưới danh sách đơn thì cuối ca phải cuộn qua cả
       trang mới thấy nút đóng ca. --%>
  <div class="card">
    <c:choose>
      <c:when test="${empty currentShift}">
        <h2>Chưa mở ca</h2>
        <div class="alert alert-warn">
          Đơn bán khi chưa mở ca vẫn ghi nhận bình thường, nhưng <strong>không vào được bảng
          đối soát tiền cuối ca</strong>. Mở ca trước khi bắt đầu bán.
        </div>
        <form method="post" action="${ctx}/staff/history">
          <input type="hidden" name="_csrf" value="${csrfToken}">
          <input type="hidden" name="action" value="shiftOpen">
          <div class="field">
            <label for="openingCash">Tiền lẻ đầu ca (đ)</label>
            <input type="number" id="openingCash" name="openingCash" min="0" step="1000" value="0" required>
          </div>
          <div class="field">
            <label for="openNote">Ghi chú</label>
            <input type="text" id="openNote" name="note" maxlength="500">
          </div>
          <button type="submit" class="btn btn-primary btn-block">Mở ca</button>
        </form>
      </c:when>

      <c:otherwise>
        <h2>Ca đang mở từ ${ff:dateTime(currentShift.openedAt)}</h2>
        <table class="mb">
          <tbody>
            <tr><th scope="row">Tiền đầu ca</th><td>${ff:money(currentShift.openingCash)}</td></tr>
            <tr><th scope="row">Đơn tại quầy đã gắn</th><td>${currentShift.orderCount}</td></tr>
            <tr>
              <th scope="row">Két lẽ ra đang có</th>
              <td><strong>${ff:money(expectedCashNow)}</strong></td>
            </tr>
          </tbody>
        </table>

        <form method="post" action="${ctx}/staff/history">
          <input type="hidden" name="_csrf" value="${csrfToken}">
          <input type="hidden" name="action" value="shiftClose">
          <input type="hidden" name="shiftId" value="${currentShift.shiftId}">
          <div class="field">
            <label for="countedCash">Số tiền đếm được cuối ca (đ)</label>
            <input type="number" id="countedCash" name="countedCash" min="0" step="1000" required>
            <p class="small muted mt">
              Hệ thống sẽ tự tính chênh lệch so với ${ff:money(expectedCashNow)} và lưu lại con
              số đó. Đóng rồi không sửa được nữa.
            </p>
          </div>
          <button type="submit" class="btn btn-primary btn-block">Đóng ca và đối soát</button>
        </form>

        <form method="post" action="${ctx}/staff/history" class="mt">
          <input type="hidden" name="_csrf" value="${csrfToken}">
          <input type="hidden" name="action" value="shiftNote">
          <input type="hidden" name="shiftId" value="${currentShift.shiftId}">
          <div class="field">
            <label for="shiftNote">Ghi chú ca</label>
            <input type="text" id="shiftNote" name="note" maxlength="500"
                   value="${fn:escapeXml(currentShift.note)}">
          </div>
          <button type="submit" class="btn btn-block">Lưu ghi chú</button>
        </form>

        <%-- Chỉ thu hồi được ca chưa có đơn nào; có đơn rồi thì tiền đã nằm trong két và
             phải đóng ca cho đúng thủ tục. --%>
        <c:if test="${currentShift.orderCount eq 0}">
          <form method="post" action="${ctx}/staff/history" class="mt"
                data-confirm="Thu hồi ca mở nhầm này?">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="action" value="shiftCancel">
            <input type="hidden" name="shiftId" value="${currentShift.shiftId}">
            <button type="submit" class="btn btn-danger btn-block">Thu hồi ca mở nhầm</button>
          </form>
        </c:if>
      </c:otherwise>
    </c:choose>
  </div>

  <div class="card pad0 table-wrap">
    <div class="card-head"><h2>Ca của tôi</h2></div>
    <table>
      <thead>
        <tr>
          <th scope="col">Mở lúc</th><th scope="col">Đóng lúc</th><th scope="col">Đơn</th>
          <th scope="col">Hệ thống tính</th><th scope="col">Đếm được</th><th scope="col">Chênh lệch</th>
          <th scope="col">Trạng thái</th>
        </tr>
      </thead>
      <tbody>
        <c:forEach var="s" items="${myShifts}">
          <tr>
            <td class="small muted">${ff:dateTime(s.openedAt)}</td>
            <td class="small muted">${empty s.closedAt ? '—' : ff:dateTime(s.closedAt)}</td>
            <td>${s.orderCount}</td>
            <td>${empty s.expectedCash ? '—' : ff:money(s.expectedCash)}</td>
            <td>${empty s.countedCash ? '—' : ff:money(s.countedCash)}</td>
            <td>
              <c:choose>
                <c:when test="${empty s.variance}">—</c:when>
                <c:when test="${s.shortOfCash}"><span class="tag tag-red">thiếu ${ff:money(s.varianceAbs)}</span></c:when>
                <c:when test="${s.varied}"><span class="tag tag-amber">thừa ${ff:money(s.varianceAbs)}</span></c:when>
                <c:otherwise><span class="tag tag-green">khớp</span></c:otherwise>
              </c:choose>
            </td>
            <td>
              <c:choose>
                <c:when test="${s.open}"><span class="tag tag-info">Đang mở</span></c:when>
                <c:when test="${s.closed}"><span class="tag tag-muted">Đã đóng</span></c:when>
                <c:otherwise><span class="tag tag-muted">Đã thu hồi</span></c:otherwise>
              </c:choose>
            </td>
          </tr>
        </c:forEach>
        <c:if test="${empty myShifts}">
          <tr><td colspan="7" class="center muted cell-empty">Bạn chưa có ca làm việc nào.</td></tr>
        </c:if>
      </tbody>
    </table>
  </div>

  <div class="card">
    <form method="get" action="${ctx}/staff/history" class="form-row">
      <div class="field">
        <label for="source">Kênh</label>
        <select id="source" name="source">
          <option value="">Tất cả</option>
          <option value="ONLINE_PREORDER" ${source eq 'ONLINE_PREORDER' ? 'selected' : ''}>Đặt trước</option>
          <option value="POS" ${source eq 'POS' ? 'selected' : ''}>Tại quầy</option>
        </select>
      </div>
      <div class="field">
        <label for="status">Trạng thái</label>
        <select id="status" name="status">
          <option value="">Tất cả</option>
          <c:forEach var="s" items="${['PENDING_PAYMENT','CONFIRMED','PREPARING','READY','COMPLETED','CANCELLED','EXPIRED']}">
            <option value="${s}" ${status eq s ? 'selected' : ''}>${ff:orderStatus(s)}</option>
          </c:forEach>
        </select>
      </div>
      <div class="field">
        <label for="from">Từ</label>
        <input type="datetime-local" id="from" name="from" value="<c:out value="${param.from}"/>">
      </div>
      <div class="field">
        <label for="to">Đến</label>
        <input type="datetime-local" id="to" name="to" value="<c:out value="${param.to}"/>">
      </div>
      <button type="submit" class="btn btn-primary">Lọc</button>
      <a class="btn" href="${ctx}/staff/history">Bỏ lọc</a>
    </form>
  </div>

  <div class="card pad0 table-wrap">
    <div class="card-head"><h2>Đơn hàng (${pageData.totalItems})</h2></div>
    <table>
      <thead><tr><th scope="col">Mã</th><th scope="col">Kênh</th><th scope="col">Đặt lúc</th><th scope="col">Hoàn tất</th>
                 <th scope="col" class="num">Tổng tiền</th><th scope="col">Trạng thái</th><th scope="col"><span class="visually-hidden">Thao tác</span></th></tr></thead>
      <tbody>
        <c:forEach var="o" items="${pageData.items}">
          <tr>
            <td><strong>#${o.orderId}</strong></td>
            <td class="small">${ff:orderSource(o.orderSource)}</td>
            <td class="small muted">${ff:dateTime(o.createdAt)}</td>
            <td class="small muted">${empty o.completedAt ? '—' : ff:dateTime(o.completedAt)}</td>
            <td class="num">${ff:money(o.totalAmount)}</td>
            <td><span class="${ff:orderStatusClass(o.orderStatus)}">${ff:orderStatus(o.orderStatus)}</span></td>
            <td class="center"><a class="btn btn-sm" href="${ctx}/staff/order/detail?orderId=${o.orderId}">Xem</a></td>
          </tr>
        </c:forEach>
        <c:if test="${pageData.emptyPage}">
          <tr><td colspan="7" class="center muted cell-empty">Không có đơn nào khớp bộ lọc.</td></tr>
        </c:if>
      </tbody>
    </table>
    <%@ include file="/WEB-INF/views/layout/pager.jspf" %>
  </div>

  <div class="card pad0 table-wrap">
    <div class="card-head"><h2>Nhật ký thao tác gần đây</h2></div>
    <table>
      <thead><tr><th scope="col">Thời điểm</th><th scope="col">Đơn</th><th scope="col">Thao tác</th><th scope="col">Người thực hiện</th></tr></thead>
      <tbody>
        <c:forEach var="log" items="${auditLogs}">
          <tr>
            <td class="small muted">${ff:dateTime(log.createdAt)}</td>
            <td><a href="${ctx}/staff/order/detail?orderId=<c:out value="${log.entityId}"/>">#<c:out value="${log.entityId}"/></a></td>
            <td>${ff:auditAction(log.action)}</td>
            <td class="small"><c:out value="${log.actorDisplay}"/></td>
          </tr>
        </c:forEach>
      </tbody>
    </table>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
