<c:set var="pageTitle" value="Lịch sử đơn hàng" /><c:set var="nav" value="history" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head">
    <h1>Lịch sử đơn hàng</h1>
    <p>Tra lại những đơn đã bán và nhật ký thao tác trên đơn.</p>
  </div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

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
          <c:forEach var="s" items="${['PENDING_PAYMENT','CONFIRMED','PREPARING','READY','COMPLETED','EXPIRED']}">
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
    <ui:pager page="${pageData}" label="đơn" />
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
