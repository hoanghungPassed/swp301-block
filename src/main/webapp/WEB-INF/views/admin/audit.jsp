<c:set var="pageTitle" value="Nhật ký" /><c:set var="nav" value="audit" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head">
    <h1>Nhật ký thao tác</h1>
    <p>Mọi việc liên quan tới tiền và trạng thái đơn đều để lại dấu vết ở đây.</p>
  </div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <div class="card">
    <form method="get" action="${ctx}/admin/audit" class="form-row">
      <div class="field">
        <label for="entityType">Đối tượng</label>
        <select id="entityType" name="entityType">
          <option value="">Tất cả</option>
          <c:forEach var="t" items="${['ORDER','ORDER_ITEM','PAYMENT','PRODUCT','CATEGORY','USER','KITCHEN_ISSUE']}">
            <option value="${t}" ${entityType eq t ? 'selected' : ''}>${t}</option>
          </c:forEach>
        </select>
      </div>
      <div class="field">
        <label for="action">Thao tác</label>
        <select id="action" name="action">
          <option value="">Tất cả</option>
          <c:forEach var="a" items="${actions}">
            <option value="${a}" ${param.action eq a ? 'selected' : ''}>${ff:auditAction(a)}</option>
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
      <a class="btn" href="${ctx}/admin/audit">Bỏ lọc</a>
    </form>
  </div>

  <div class="card pad0 table-wrap">
    <div class="card-head"><h2>Bản ghi</h2></div>
    <table>
      <thead>
        <tr><th scope="col">Thời điểm</th><th scope="col">Đối tượng</th><th scope="col">Mã</th><th scope="col">Thao tác</th>
            <th scope="col">Người thực hiện</th><th scope="col">Thay đổi</th></tr>
      </thead>
      <tbody>
        <c:forEach var="log" items="${pageData.items}">
          <tr>
            <td class="small muted">${ff:dateTime(log.createdAt)}</td>
            <td class="small"><c:out value="${log.entityType}"/></td>
            <td class="small">
              <c:choose>
                <c:when test="${log.entityType eq 'ORDER'}">
                  <a href="${ctx}/staff/order/detail?orderId=<c:out value="${log.entityId}"/>">#<c:out value="${log.entityId}"/></a>
                </c:when>
                <c:otherwise><c:out value="${log.entityId}"/></c:otherwise>
              </c:choose>
            </td>
            <td>${ff:auditAction(log.action)}</td>
            <td class="small"><c:out value="${log.actorDisplay}"/></td>
            <td class="small muted">
              <c:if test="${not empty log.oldValue}"><c:out value="${log.oldValue}"/> → </c:if><c:out value="${log.newValue}"/>
            </td>
          </tr>
        </c:forEach>
        <c:if test="${pageData.emptyPage}">
          <tr><td colspan="6" class="center muted cell-empty">Không có bản ghi nào khớp bộ lọc.</td></tr>
        </c:if>
      </tbody>
    </table>
    <%@ include file="/WEB-INF/views/layout/pager.jspf" %>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
