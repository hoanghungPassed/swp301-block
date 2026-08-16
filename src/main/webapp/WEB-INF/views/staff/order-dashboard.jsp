<c:set var="pageTitle" value="Đơn hàng" /><c:set var="nav" value="orders" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head"><h1>Đơn hàng</h1></div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <%-- Tra mã đặt trên cùng: khách đứng ở quầy là việc gấp nhất trong màn hình này. --%>
  <div class="card">
    <form method="get" action="${ctx}/staff/orders" class="form-row">
      <%-- Giữ tab đang mở, nếu không thì tra một mã xong lại quay về tab mặc định. --%>
      <input type="hidden" name="tab" value="${tab}">
      <div class="field">
        <label for="code">Mã nhận hàng</label>
        <input type="text" id="code" name="code" value="<c:out value="${code}"/>"
               class="mono code-input" placeholder="VD: 260813A1C7">
      </div>
      <button type="submit" class="btn btn-primary touch">Tra cứu</button>
    </form>

    <c:if test="${not empty lookupError}">
      <div class="alert alert-error mt" role="alert"><c:out value="${lookupError}"/></div>
    </c:if>
    <c:if test="${not empty lookupWarning}">
      <div class="alert alert-warn mt" role="alert"><c:out value="${lookupWarning}"/></div>
    </c:if>

    <c:if test="${not empty found}">
      <div class="mt">
        <div class="row-between mb">
          <div>
            <h2>Đơn #${found.orderId}</h2>
            <p class="small muted"><c:out value="${empty found.customerName ? 'Khách tại quầy' : found.customerName}"/></p>
          </div>
          <span class="${ff:orderStatusClass(found.orderStatus)}">${ff:orderStatus(found.orderStatus)}</span>
        </div>

        <div class="grid grid-2">
          <div>
            <div class="total-line"><span class="muted">Giờ hẹn</span><span>${ff:dateTime(found.pickupTime)}</span></div>
            <div class="total-line"><span class="muted">Sẵn sàng lúc</span>
              <span>${empty found.readyAt ? 'Chưa xong' : ff:dateTime(found.readyAt)}</span></div>
          </div>
          <div>
            <div class="total-line"><span class="muted">Thanh toán</span>
              <span class="${ff:paymentStatusClass(found.latestPayment.paymentStatus)}">
                ${ff:paymentStatus(found.latestPayment.paymentStatus)}</span></div>
            <div class="total-line grand"><span>Tổng tiền</span><span>${ff:money(found.totalAmount)}</span></div>
          </div>
        </div>

        <ul class="mt bullets">
          <c:forEach var="item" items="${found.items}">
            <li><c:out value="${item.productNameSnapshot}"/> × ${item.quantity}
              <span class="${ff:itemStatusClass(item.itemStatus)}">${ff:itemStatus(item.itemStatus)}</span>
            </li>
          </c:forEach>
        </ul>

        <div class="actions mt">
          <a class="btn btn-primary" href="${ctx}/staff/order/detail?orderId=${found.orderId}">
            Mở đơn để giao món
          </a>
        </div>
      </div>
    </c:if>
  </div>

  <%-- Món đang nằm trên quầy là việc phải làm ngay: món để lâu thì nguội, và đơn không giao
       được cho khách chừng nào quầy chưa nhận đủ món. --%>
  <c:if test="${awaitingCounterCount > 0}">
    <div class="alert alert-info">
      Bếp vừa bàn giao <strong>${awaitingCounterCount} món</strong> ra quầy mà chưa ai xác nhận
      đã nhận. Đơn chỉ giao cho khách được khi quầy đã nhận đủ món.
      <a href="${ctx}/staff/counter">Nhận món →</a>
    </div>
  </c:if>

  <c:if test="${openIssueCount > 0}">
    <div class="alert alert-warn">
      Bếp đang báo <strong>${openIssueCount} sự cố</strong> chưa xử lý. Đơn vướng sự cố sẽ
      không tự sẵn sàng được — cần liên hệ khách để đổi món hoặc huỷ đơn.
      <a href="${ctx}/staff/counter">Xem danh sách →</a>
    </div>
  </c:if>

  <div class="tabs">
    <a href="${ctx}/staff/orders?tab=POS" class="${tab eq 'POS' ? 'active' : ''}">
      Tại quầy <span class="count">${countPos}</span></a>
    <a href="${ctx}/staff/orders?tab=SCHEDULED" class="${tab eq 'SCHEDULED' ? 'active' : ''}">
      Đơn đặt trước <span class="count">${countScheduled}</span></a>
    <a href="${ctx}/staff/orders?tab=READY" class="${tab eq 'READY' ? 'active' : ''}">
      Chờ khách tới lấy <span class="count">${countReady}</span></a>
    <a href="${ctx}/staff/orders?tab=OVERDUE" class="${tab eq 'OVERDUE' ? 'active' : ''}">
      Khách đến muộn <span class="count">${countOverdue}</span></a>
  </div>

  <c:if test="${tab eq 'SCHEDULED'}">
    <div class="alert alert-info">
      Đơn đã thanh toán, đang chờ tới giờ hoặc đang được bếp làm. Đơn còn ở trạng thái đã xác nhận
      là cố ý chưa đưa xuống bếp — hệ thống tự đưa xuống trước giờ hẹn để món ra đúng lúc khách tới.
    </div>
  </c:if>
  <c:if test="${tab eq 'OVERDUE'}">
    <div class="alert alert-warn">
      Khách đã quá giờ hẹn mà chưa tới lấy. Đơn vẫn được giữ nguyên — hệ thống không tự huỷ
      cũng không tự hoàn tiền vì khách đã trả tiền trước.
    </div>
  </c:if>

  <div class="card pad0 table-wrap">
    <c:choose>
      <c:when test="${empty orders}">
        <div class="empty"><div class="icon" aria-hidden="true">✅</div>Không có đơn nào trong mục này.</div>
      </c:when>
      <c:otherwise>
        <table>
          <thead>
            <tr><th scope="col">Mã đơn</th><th scope="col">Kênh</th><th scope="col">Khách</th><th scope="col">Giờ hẹn</th>
                <th scope="col">Bếp</th><th scope="col" class="num">Tổng tiền</th><th scope="col">Trạng thái</th><th scope="col"><span class="visually-hidden">Thao tác</span></th></tr>
          </thead>
          <tbody>
            <c:forEach var="o" items="${orders}">
              <tr>
                <td><strong>#${o.orderId}</strong>
                  <c:if test="${not empty o.pickupCode}">
                    <div class="mono small muted">${o.pickupCode}</div>
                  </c:if>
                </td>
                <td><span class="tag ${o.online ? 'tag-info' : 'tag-muted'}">${ff:orderSource(o.orderSource)}</span></td>
                <td class="small">${empty o.customerName ? 'Khách tại quầy' : o.customerName}</td>
                <td class="small">
                  <c:choose>
                    <c:when test="${o.online}">
                      ${ff:time(o.pickupTime)}
                      <div class="muted">${ff:humanize(o.pickupTime)}</div>
                    </c:when>
                    <c:otherwise><span class="muted">—</span></c:otherwise>
                  </c:choose>
                </td>
                <td class="small">${ff:releaseState(o.releaseState)}</td>
                <td class="num">${ff:money(o.totalAmount)}</td>
                <td>
                  <span class="${ff:orderStatusClass(o.orderStatus)}">${ff:orderStatus(o.orderStatus)}</span>
                  <c:if test="${o.overdue}"><div><span class="tag tag-red">Đến muộn</span></div></c:if>
                </td>
                <td class="center">
                  <a class="btn touch btn-primary" href="${ctx}/staff/order/detail?orderId=${o.orderId}">Mở</a>
                </td>
              </tr>
              <%-- Ghi chú điều phối nằm ngay dưới dòng đơn thay vì một cột riêng: nội dung là
                   câu văn, nhét vào cột sẽ ép cả bảng hẹp lại. Hàng này chỉ hiện khi có ghi chú
                   hoặc khi đang sửa, nên bảng vẫn gọn với đơn không có gì đặc biệt. --%>
              <c:set var="notes" value="${notesByOrder[o.orderId]}" />
              <tr class="note-row">
                <td colspan="8">
                  <c:forEach var="n" items="${notes}">
                    <div class="small">
                      <c:choose>
                        <c:when test="${not empty editingNote and editingNote.orderNoteId eq n.orderNoteId}">
                          <form method="post" action="${ctx}/staff/orders" class="inline-form">
                            <input type="hidden" name="_csrf" value="${csrfToken}">
                            <input type="hidden" name="action" value="noteUpdate">
                            <input type="hidden" name="noteId" value="${n.orderNoteId}">
                            <input type="hidden" name="tab" value="${tab}">
                            <input type="text" name="content" maxlength="500" required
                                   value="${fn:escapeXml(n.content)}" class="input-sm">
                            <button type="submit" class="btn btn-sm btn-primary">Lưu</button>
                            <a class="btn btn-sm" href="${ctx}/staff/orders?tab=${tab}">Huỷ</a>
                          </form>
                        </c:when>
                        <c:otherwise>
                          <c:out value="${n.content}"/>
                          <span class="muted">— <c:out value="${n.authorName}"/>
                            <c:if test="${n.edited}"> (đã sửa)</c:if></span>
                          <c:if test="${n.authorId eq me.userId}">
                            <a class="btn btn-sm" href="${ctx}/staff/orders?tab=${tab}&amp;editNote=${n.orderNoteId}">Sửa</a>
                            <form method="post" action="${ctx}/staff/orders" class="inline-form">
                              <input type="hidden" name="_csrf" value="${csrfToken}">
                              <input type="hidden" name="action" value="noteDelete">
                              <input type="hidden" name="noteId" value="${n.orderNoteId}">
                              <input type="hidden" name="tab" value="${tab}">
                              <button type="submit" class="btn btn-sm btn-danger">Xoá</button>
                            </form>
                          </c:if>
                        </c:otherwise>
                      </c:choose>
                    </div>
                  </c:forEach>
                  <form method="post" action="${ctx}/staff/orders" class="inline-form">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="noteAdd">
                    <input type="hidden" name="orderId" value="${o.orderId}">
                    <input type="hidden" name="tab" value="${tab}">
                    <input type="text" name="content" maxlength="500" required
                           placeholder="Ghi chú cho đơn này" class="input-sm">
                    <button type="submit" class="btn btn-sm">Thêm ghi chú</button>
                  </form>
                </td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </c:otherwise>
    </c:choose>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
