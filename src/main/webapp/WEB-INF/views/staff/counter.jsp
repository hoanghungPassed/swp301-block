<c:set var="pageTitle" value="Quầy giao nhận" /><c:set var="nav" value="counter" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head">
    <h1>Quầy giao nhận</h1>
    <p>
      Món bếp vừa đưa ra, đơn đã đủ món để gọi khách, và sự cố bếp đang vướng — ba thứ trả lời
      cùng một câu hỏi: món của đơn này đang ở đâu.
    </p>
  </div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <%-- Món đang nằm trên quầy đứng đầu: món càng để lâu càng nguội. --%>
  <div class="card pad0 table-wrap">
    <div class="card-head"><h2>Bếp vừa bàn giao (${fn:length(awaitingCounter)})</h2></div>
    <table>
      <thead><tr><th scope="col">Đơn</th><th scope="col">Món</th><th scope="col" class="center">SL</th>
                 <th scope="col">Bếp bàn giao</th><th scope="col">Kênh</th>
                 <th scope="col"><span class="visually-hidden">Thao tác</span></th></tr></thead>
      <tbody>
        <c:forEach var="it" items="${awaitingCounter}">
          <tr>
            <td><a href="${ctx}/staff/order/detail?orderId=${it.orderId}">#${it.orderId}</a>
              <%-- Đơn có thể bị huỷ sau khi bếp đã nấu xong. Món vẫn có thật nên vẫn phải
                   hiện ra, nhưng để mang đi bỏ chứ không đưa cho khách. --%>
              <c:if test="${it.orderClosed}">
                <div><span class="tag tag-red">${ff:orderStatus(it.orderStatus)} — không giao</span></div>
              </c:if>
            </td>
            <td><c:out value="${it.productNameSnapshot}"/></td>
            <td class="center">${it.quantity}</td>
            <td class="small muted">
              ${ff:time(it.handedOverAt)}<br><c:out value="${it.handedOverByName}"/>
            </td>
            <td class="small">
              <span class="tag ${it.orderSource eq 'ONLINE_PREORDER' ? 'tag-info' : 'tag-muted'}">
                ${ff:orderSource(it.orderSource)}
              </span>
            </td>
            <td class="center">
              <form method="post" action="${ctx}/staff/counter" class="inline-form">
                <input type="hidden" name="_csrf" value="${csrfToken}">
                <input type="hidden" name="action" value="receive">
                <input type="hidden" name="orderItemId" value="${it.orderItemId}">
                <button type="submit" class="btn btn-sm btn-green touch">Đã nhận món</button>
              </form>
              <%-- Đường ra thứ hai cho cùng một món: món sai hoặc nguội thì trả về bếp thay vì
                   nhận rồi đi nói miệng. Lý do bắt buộc nhập để bếp biết cần sửa gì. --%>
              <form method="post" action="${ctx}/staff/counter" class="inline-form">
                <input type="hidden" name="_csrf" value="${csrfToken}">
                <input type="hidden" name="action" value="reject">
                <input type="hidden" name="orderItemId" value="${it.orderItemId}">
                <input type="text" name="reason" maxlength="500" required
                       placeholder="Lý do từ chối" class="input-sm">
                <button type="submit" class="btn btn-sm btn-danger touch">Từ chối</button>
              </form>
            </td>
          </tr>
        </c:forEach>
        <c:if test="${empty awaitingCounter}">
          <tr><td colspan="6" class="center muted cell-empty">Không có món nào đang chờ trên quầy.</td></tr>
        </c:if>
      </tbody>
    </table>
  </div>

  <%-- Phiếu do chính quầy lập. Tách khỏi bảng sự cố bếp bên dưới vì hai bên cần thấy phần của
       mình trước, dù cả hai nằm chung một bảng dữ liệu. --%>
  <div class="card pad0 table-wrap">
    <div class="card-head"><h2>Món quầy đã từ chối (${fn:length(counterRejects)})</h2></div>
    <table>
      <thead>
        <tr>
          <th scope="col">Món</th><th scope="col">Đơn</th><th scope="col">Lý do</th>
          <th scope="col">Người lập</th><th scope="col"><span class="visually-hidden">Thao tác</span></th>
        </tr>
      </thead>
      <tbody>
        <c:forEach var="r" items="${counterRejects}">
          <tr>
            <td><c:out value="${r.productName}"/></td>
            <td>#${r.orderId}</td>
            <td class="small"><c:out value="${r.description}"/></td>
            <td class="small muted"><c:out value="${r.createdByName}"/><br>${ff:time(r.createdAt)}</td>
            <td class="center">
              <c:if test="${r.createdBy eq me.userId}">
                <form method="post" action="${ctx}/staff/counter" class="inline-form">
                  <input type="hidden" name="_csrf" value="${csrfToken}">
                  <input type="hidden" name="action" value="rejectUpdate">
                  <input type="hidden" name="issueId" value="${r.issueId}">
                  <input type="text" name="reason" maxlength="500" required
                         value="${fn:escapeXml(r.description)}" class="input-sm">
                  <button type="submit" class="btn btn-sm">Sửa lý do</button>
                </form>
                <form method="post" action="${ctx}/staff/counter" class="inline-form"
                      data-confirm="Thu hồi phiếu từ chối này? Bản ghi vẫn được giữ trong nhật ký.">
                  <input type="hidden" name="_csrf" value="${csrfToken}">
                  <input type="hidden" name="action" value="rejectCancel">
                  <input type="hidden" name="issueId" value="${r.issueId}">
                  <button type="submit" class="btn btn-sm btn-danger">Thu hồi</button>
                </form>
              </c:if>
            </td>
          </tr>
        </c:forEach>
        <c:if test="${empty counterRejects}">
          <tr><td colspan="5" class="center muted cell-empty">Chưa có món nào bị từ chối.</td></tr>
        </c:if>
      </tbody>
    </table>
  </div>

  <%-- Đơn đã nhận đủ món mới gọi khách được. Đơn còn thiếu vẫn hiện ở đây nhưng nói rõ còn
       thiếu mấy món, thay vì biến mất — biến mất thì thu ngân tưởng đơn chưa xong và đi hỏi bếp. --%>
  <div class="card pad0 table-wrap">
    <div class="card-head"><h2>Chờ khách tới lấy (${fn:length(readyOrders)})</h2></div>
    <table>
      <thead><tr><th scope="col">Mã đơn</th><th scope="col">Khách</th><th scope="col">Giờ hẹn</th>
                 <th scope="col">Món tại quầy</th>
                 <th scope="col"><span class="visually-hidden">Thao tác</span></th></tr></thead>
      <tbody>
        <c:forEach var="o" items="${readyOrders}">
          <%-- Đếm tại chỗ từ danh sách món đã nạp sẵn cùng đơn, không hỏi thêm cơ sở dữ liệu. --%>
          <c:set var="notReceived" value="0"/>
          <c:forEach var="it" items="${o.items}">
            <c:if test="${not it.received}"><c:set var="notReceived" value="${notReceived + 1}"/></c:if>
          </c:forEach>
          <tr>
            <td><strong>#${o.orderId}</strong>
              <c:if test="${not empty o.pickupCode}">
                <div class="mono small muted">${o.pickupCode}</div>
              </c:if>
            </td>
            <td class="small">${empty o.customerName ? 'Khách tại quầy' : o.customerName}</td>
            <td class="small">
              <c:choose>
                <c:when test="${o.online}">
                  ${ff:time(o.pickupTime)}
                  <c:if test="${o.overdue}"><div><span class="tag tag-red">Đến muộn</span></div></c:if>
                </c:when>
                <c:otherwise><span class="muted">—</span></c:otherwise>
              </c:choose>
            </td>
            <td>
              <c:choose>
                <c:when test="${notReceived == 0}">
                  <span class="tag tag-green">Đủ món</span>
                </c:when>
                <c:otherwise>
                  <span class="tag tag-amber">Còn thiếu ${notReceived} món</span>
                </c:otherwise>
              </c:choose>
            </td>
            <td class="center">
              <a class="btn touch ${notReceived == 0 ? 'btn-primary' : ''}"
                 href="${ctx}/staff/order/detail?orderId=${o.orderId}">
                ${notReceived == 0 ? 'Giao cho khách' : 'Mở đơn'}
              </a>
            </td>
          </tr>
        </c:forEach>
        <c:if test="${empty readyOrders}">
          <tr><td colspan="5" class="center muted cell-empty">Không có đơn nào chờ khách tới lấy.</td></tr>
        </c:if>
      </tbody>
    </table>
  </div>

  <div class="card pad0 table-wrap">
    <div class="card-head"><h2>Sự cố bếp đang mở (${fn:length(openIssues)})</h2></div>
    <table>
      <thead><tr><th scope="col">Đơn</th><th scope="col">Món</th><th scope="col">Loại</th>
                 <th scope="col">Mô tả</th><th scope="col">Người báo</th>
                 <th scope="col"><span class="visually-hidden">Thao tác</span></th></tr></thead>
      <tbody>
        <c:forEach var="i" items="${openIssues}">
          <tr>
            <td><a href="${ctx}/staff/order/detail?orderId=${i.orderId}">#${i.orderId}</a></td>
            <td><c:out value="${i.productName}"/></td>
            <td><span class="tag tag-red">${ff:issueType(i.issueType)}</span></td>
            <td class="small"><c:out value="${i.description}"/></td>
            <td class="small muted"><c:out value="${i.createdByName}"/><br>${ff:time(i.createdAt)}</td>
            <td class="center">
              <a class="btn btn-sm" href="${ctx}/staff/order/detail?orderId=${i.orderId}">Mở đơn</a>
            </td>
          </tr>
        </c:forEach>
        <c:if test="${empty openIssues}">
          <tr><td colspan="6" class="center muted cell-empty">Không có sự cố nào đang mở.</td></tr>
        </c:if>
      </tbody>
    </table>
  </div>

  <div class="card pad0 table-wrap">
    <div class="card-head"><h2>Sự cố đã khép lại gần đây</h2></div>
    <table>
      <thead><tr><th scope="col">Thời điểm</th><th scope="col">Đơn</th><th scope="col">Món</th>
                 <th scope="col">Loại</th><th scope="col">Trạng thái</th></tr></thead>
      <tbody>
        <c:forEach var="i" items="${recentIssues}">
          <c:if test="${not i.open}">
            <tr>
              <td class="small muted">${ff:dateTime(i.createdAt)}</td>
              <td><a href="${ctx}/staff/order/detail?orderId=${i.orderId}">#${i.orderId}</a></td>
              <td><c:out value="${i.productName}"/></td>
              <td>${ff:issueType(i.issueType)}</td>
              <td><span class="tag ${ff:issueStatusTag(i.status)}">${ff:issueStatus(i.status)}</span></td>
            </tr>
          </c:if>
        </c:forEach>
      </tbody>
    </table>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
