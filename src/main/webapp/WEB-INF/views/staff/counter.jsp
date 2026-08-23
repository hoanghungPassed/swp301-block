<c:set var="pageTitle" value="Quầy giao nhận" /><c:set var="nav" value="counter" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <%-- Nhận món / từ chối xong quay lại đúng trang của từng bảng. --%>
  <c:set var="cntQuery" value="${ff:pageQuery(pageContext.request, '')}" />
  <c:set var="cntBack"  value="/staff/counter${empty cntQuery ? '' : '?'.concat(cntQuery)}" />
  <c:set var="cntReturn"><input type="hidden" name="returnTo" value="<c:out value="${cntBack}"/>"></c:set>
  <div class="page-head">
    <h1>Quầy giao nhận</h1>
    <p>
      Món bếp vừa đưa ra, đơn đã đủ món để gọi khách, và sự cố bếp đang vướng — ba thứ trả lời
      cùng một câu hỏi: món của đơn này đang ở đâu.
    </p>
  </div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <div class="card pad0 table-wrap" id="bep-ban-giao">
    <div class="card-head"><h2>Bếp vừa bàn giao (${handoverPage.totalItems} đơn)</h2></div>
    <table>
      <thead><tr><th scope="col">Đơn</th><th scope="col">Món</th>
                 <th scope="col">Bếp bàn giao</th><th scope="col">Kênh</th>
                 <th scope="col"><span class="visually-hidden">Thao tác</span></th></tr></thead>
      <tbody>
        <c:forEach var="o" items="${handoverPage.items}">
          <tr>
            <td>
              <a href="${ctx}/staff/order/detail?orderId=${o.orderId}">#${o.orderId}</a>
              <div class="small muted">${o.itemCount} món · ${o.totalQuantity} phần</div>
              <c:if test="${o.orderClosed}">
                <div><span class="tag tag-red">${ff:orderStatus(o.orderStatus)} — không giao</span></div>
              </c:if>
            </td>
            <td>
              <%-- Nhận thì nhận cả đơn, nhưng từ chối vẫn theo từng món vì lý do trả về bếp
                   luôn gắn với đúng một món hỏng. --%>
              <ul class="counter-items">
                <c:forEach var="v" items="${o.items}">
                  <li>
                    <span class="qty-inline">×${v.item.quantity}</span>
                    <c:out value="${v.item.productNameSnapshot}"/>
                    <form method="post" action="${ctx}/staff/counter" class="inline-form"
                          data-confirm="Từ chối món này và báo lại cho bếp?">
                      <input type="hidden" name="_csrf" value="${csrfToken}">
                      <input type="hidden" name="action" value="reject">
                      ${cntReturn}
                      <input type="hidden" name="orderItemId" value="${v.item.orderItemId}">
                      <input type="text" name="reason" maxlength="500" required
                             placeholder="Lý do từ chối" class="input-sm">
                      <button type="submit" class="btn btn-sm btn-danger touch">Từ chối</button>
                    </form>
                  </li>
                </c:forEach>
              </ul>
            </td>
            <td class="small muted">
              <c:forEach var="v" items="${o.items}" end="0">
                ${ff:time(v.item.handedOverAt)}<br><c:out value="${v.item.handedOverByName}"/>
              </c:forEach>
            </td>
            <td class="small">
              <span class="tag ${o.online ? 'tag-info' : 'tag-muted'}">
                ${ff:orderSource(o.orderSource)}
              </span>
            </td>
            <td class="center">
              <form method="post" action="${ctx}/staff/counter" class="inline-form"
                    data-confirm="Xác nhận đã nhận cả đơn này từ bếp lên quầy?">
                <input type="hidden" name="_csrf" value="${csrfToken}">
                <input type="hidden" name="action" value="receiveOrder">
                ${cntReturn}
                <input type="hidden" name="orderId" value="${o.orderId}">
                <button type="submit" class="btn btn-sm btn-green touch">Đã nhận cả đơn</button>
              </form>
            </td>
          </tr>
        </c:forEach>
        <c:if test="${handoverPage.emptyPage}">
          <tr><td colspan="5" class="center muted cell-empty">Không có đơn nào đang chờ trên quầy.</td></tr>
        </c:if>
      </tbody>
    </table>
    <ui:pager page="${handoverPage}" pageParam="handoverPage" anchor="bep-ban-giao" label="đơn" />
  </div>

  <div class="card pad0 table-wrap" id="mon-tu-choi">
    <div class="card-head"><h2>Món quầy đã từ chối (${rejectPage.totalItems})</h2></div>
    <table>
      <thead>
        <tr>
          <th scope="col">Món</th><th scope="col">Đơn</th><th scope="col">Lý do</th>
          <th scope="col">Người lập</th><th scope="col"><span class="visually-hidden">Thao tác</span></th>
        </tr>
      </thead>
      <tbody>
        <c:forEach var="r" items="${rejectPage.items}">
          <tr>
            <td><c:out value="${r.productName}"/></td>
            <td>#${r.orderId}</td>
            <td class="small"><c:out value="${r.description}"/></td>
            <td class="small muted"><c:out value="${r.createdByName}"/><br>${ff:time(r.createdAt)}</td>
            <td class="center">
              <c:if test="${r.createdBy eq me.userId}">
                <form method="post" action="${ctx}/staff/counter" class="inline-form"
                      data-confirm="Lưu lại lý do từ chối?">
                  <input type="hidden" name="_csrf" value="${csrfToken}">
                  <input type="hidden" name="action" value="rejectUpdate">
                  ${cntReturn}
                  <input type="hidden" name="issueId" value="${r.issueId}">
                  <input type="text" name="reason" maxlength="500" required
                         value="${fn:escapeXml(r.description)}" class="input-sm">
                  <button type="submit" class="btn btn-sm">Sửa lý do</button>
                </form>
                <form method="post" action="${ctx}/staff/counter" class="inline-form"
                      data-confirm="Thu hồi phiếu từ chối này? Bản ghi vẫn được giữ trong nhật ký.">
                  <input type="hidden" name="_csrf" value="${csrfToken}">
                  <input type="hidden" name="action" value="rejectCancel">
                  ${cntReturn}
                  <input type="hidden" name="issueId" value="${r.issueId}">
                  <button type="submit" class="btn btn-sm btn-danger">Thu hồi</button>
                </form>
              </c:if>
            </td>
          </tr>
        </c:forEach>
        <c:if test="${rejectPage.emptyPage}">
          <tr><td colspan="5" class="center muted cell-empty">Chưa có món nào bị từ chối.</td></tr>
        </c:if>
      </tbody>
    </table>
    <ui:pager page="${rejectPage}" pageParam="rejectPage" anchor="mon-tu-choi" label="món" />
  </div>

  <div class="card pad0 table-wrap" id="cho-khach">
    <div class="card-head"><h2>Chờ khách tới lấy (${readyPage.totalItems})</h2></div>
    <table>
      <thead><tr><th scope="col">Mã đơn</th><th scope="col">Khách</th><th scope="col">Giờ hẹn</th>
                 <th scope="col">Món tại quầy</th>
                 <th scope="col"><span class="visually-hidden">Thao tác</span></th></tr></thead>
      <tbody>
        <c:forEach var="o" items="${readyPage.items}">
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
        <c:if test="${readyPage.emptyPage}">
          <tr><td colspan="5" class="center muted cell-empty">Không có đơn nào chờ khách tới lấy.</td></tr>
        </c:if>
      </tbody>
    </table>
    <ui:pager page="${readyPage}" pageParam="readyPage" anchor="cho-khach" label="đơn" />
  </div>

  <div class="card pad0 table-wrap" id="su-co-mo">
    <div class="card-head"><h2>Sự cố bếp đang mở (${issuePage.totalItems})</h2></div>
    <table>
      <thead><tr><th scope="col">Đơn</th><th scope="col">Món</th><th scope="col">Loại</th>
                 <th scope="col">Mô tả</th><th scope="col">Người báo</th>
                 <th scope="col"><span class="visually-hidden">Thao tác</span></th></tr></thead>
      <tbody>
        <c:forEach var="i" items="${issuePage.items}">
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
        <c:if test="${issuePage.emptyPage}">
          <tr><td colspan="6" class="center muted cell-empty">Không có sự cố nào đang mở.</td></tr>
        </c:if>
      </tbody>
    </table>
    <ui:pager page="${issuePage}" pageParam="issuePage" anchor="su-co-mo" label="sự cố" />
  </div>

  <div class="card pad0 table-wrap" id="su-co-khep">
    <div class="card-head"><h2>Sự cố đã khép lại gần đây (${closedPage.totalItems})</h2></div>
    <table>
      <thead><tr><th scope="col">Thời điểm</th><th scope="col">Đơn</th><th scope="col">Món</th>
                 <th scope="col">Loại</th><th scope="col">Trạng thái</th></tr></thead>
      <tbody>
        <c:forEach var="i" items="${closedPage.items}">
          <tr>
            <td class="small muted">${ff:dateTime(i.createdAt)}</td>
            <td><a href="${ctx}/staff/order/detail?orderId=${i.orderId}">#${i.orderId}</a></td>
            <td><c:out value="${i.productName}"/></td>
            <td>${ff:issueType(i.issueType)}</td>
            <td><span class="tag ${ff:issueStatusTag(i.status)}">${ff:issueStatus(i.status)}</span></td>
          </tr>
        </c:forEach>
        <c:if test="${closedPage.emptyPage}">
          <tr><td colspan="5" class="center muted cell-empty">Chưa có sự cố nào được khép lại.</td></tr>
        </c:if>
      </tbody>
    </table>
    <ui:pager page="${closedPage}" pageParam="closedPage" anchor="su-co-khep" label="sự cố" />
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
