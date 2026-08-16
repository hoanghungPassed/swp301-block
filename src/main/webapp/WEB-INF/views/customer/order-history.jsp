<c:set var="pageTitle" value="Đơn của tôi" /><c:set var="nav" value="orders" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head"><h1>Đơn của tôi</h1></div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <c:if test="${not empty activeOrders}">
    <div class="card">
      <h2>Đang theo dõi</h2>
      <div class="grid grid-3">
        <c:forEach var="o" items="${activeOrders}">
          <a class="kds-card ${o.online ? 'online' : 'pos'}" href="${ctx}/order/track?orderId=${o.orderId}">
            <div class="row-between">
              <strong>#${o.orderId}</strong>
              <span class="${ff:orderStatusClass(o.orderStatus)}">${ff:orderStatus(o.orderStatus)}</span>
            </div>
            <div class="meta mt">
              <span>${ff:money(o.totalAmount)}</span>
              <c:if test="${o.online}"><span>${ff:humanize(o.pickupTime)}</span></c:if>
            </div>
            <c:if test="${not empty o.pickupCode}">
              <div class="mono small mt">Mã: ${o.pickupCode}</div>
            </c:if>
          </a>
        </c:forEach>
      </div>
    </div>
  </c:if>

  <%-- Bộ lọc nằm ngoài thẻ bảng để nó không cuộn ngang theo bảng trên màn hình hẹp. --%>
  <div class="card">
    <form method="get" action="${ctx}/order/history" class="form-row">
      <div class="field">
        <label for="status">Trạng thái</label>
        <select id="status" name="status">
          <option value="">Tất cả</option>
          <c:forEach var="s" items="${['PENDING_PAYMENT','CONFIRMED','PREPARING','READY','COMPLETED','CANCELLED','EXPIRED']}">
            <option value="${s}" ${param.status eq s ? 'selected' : ''}>${ff:orderStatus(s)}</option>
          </c:forEach>
        </select>
      </div>
      <%-- Ô ngày chứ không phải ngày giờ: khách nhớ "hôm thứ bảy tuần trước", không nhớ phút.
           Ngày kết thúc được service mở tới cuối ngày nên chọn hôm nay vẫn thấy đơn hôm nay. --%>
      <div class="field">
        <label for="from">Đặt từ ngày</label>
        <input type="date" id="from" name="from" value="<c:out value="${param.from}"/>">
      </div>
      <div class="field">
        <label for="to">Đến ngày</label>
        <input type="date" id="to" name="to" value="<c:out value="${param.to}"/>">
      </div>
      <button type="submit" class="btn btn-primary">Lọc</button>
      <c:if test="${filtering}">
        <a class="btn" href="${ctx}/order/history">Bỏ lọc</a>
      </c:if>
    </form>
  </div>

  <div class="card pad0 table-wrap">
    <div class="card-head"><h2>Toàn bộ lịch sử</h2></div>
    <c:choose>
      <c:when test="${pageData.emptyPage and filtering}">
        <div class="empty">
          <div class="icon" aria-hidden="true">🔍</div>
          Không có đơn nào khớp bộ lọc này.
          <div class="mt"><a class="btn" href="${ctx}/order/history">Bỏ lọc</a></div>
        </div>
      </c:when>
      <c:when test="${pageData.emptyPage}">
        <div class="empty">
          <div class="icon" aria-hidden="true">📋</div>
          Bạn chưa có đơn hàng nào.
          <div class="mt"><a class="btn btn-primary" href="${ctx}/menu">Đặt món ngay</a></div>
        </div>
      </c:when>
      <c:otherwise>
        <table class="table-cards">
          <thead>
            <tr><th scope="col">Mã đơn</th><th scope="col">Đặt lúc</th><th scope="col">Giờ hẹn</th>
                <th scope="col" class="num">Tổng tiền</th><th scope="col">Trạng thái</th><th scope="col"><span class="visually-hidden">Thao tác</span></th></tr>
          </thead>
          <tbody>
            <c:forEach var="o" items="${pageData.items}">
              <tr>
                <td data-label="Mã đơn"><strong>#${o.orderId}</strong></td>
                <td class="small" data-label="Đặt lúc">${ff:dateTime(o.createdAt)}</td>
                <td class="small" data-label="Giờ hẹn">
                  <c:choose>
                    <c:when test="${o.online}">${ff:dateTime(o.pickupTime)}</c:when>
                    <c:otherwise><span class="muted">Mua tại quầy</span></c:otherwise>
                  </c:choose>
                </td>
                <td class="num" data-label="Tổng tiền">${ff:money(o.totalAmount)}</td>
                <td data-label="Trạng thái"><span class="${ff:orderStatusClass(o.orderStatus)}">${ff:orderStatus(o.orderStatus)}</span></td>
                <td class="center" data-label="">
                  <a class="btn touch" href="${ctx}/order/track?orderId=${o.orderId}">Xem</a>
                  <%-- Lưu thành mẫu ngay tại dòng đơn: đây là lúc khách đang nhìn thấy đúng
                       thứ họ muốn lưu, không phải gõ lại từ trí nhớ ở một trang khác. --%>
                  <form method="post" action="${ctx}/order/history" class="inline-form">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="templateSave">
                    <input type="hidden" name="orderId" value="${o.orderId}">
                    <input type="text" name="name" required maxlength="100" class="input-sm"
                           placeholder="Tên mẫu" aria-label="Tên mẫu cho đơn #${o.orderId}">
                    <button type="submit" class="btn btn-sm">Lưu mẫu</button>
                  </form>
                </td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </c:otherwise>
    </c:choose>
    <%@ include file="/WEB-INF/views/layout/pager.jspf" %>
  </div>

  <div class="card">
    <div class="row-between mb">
      <h2>Mẫu đặt nhanh <c:if test="${not empty templates}">(${fn:length(templates)})</c:if></h2>
      <span class="small muted">Giá luôn tính theo bảng giá hiện hành, không phải giá lúc lưu</span>
    </div>

    <c:choose>
      <c:when test="${empty templates}">
        <div class="empty"><div class="icon" aria-hidden="true">🗂️</div>
          Chưa có mẫu nào. Ở bảng lịch sử phía trên, đặt tên rồi bấm "Lưu mẫu" cho một đơn
          bạn hay đặt lại.
        </div>
      </c:when>
      <c:otherwise>
        <table class="table">
          <thead>
            <tr><th scope="col">Mẫu</th><th scope="col">Món</th>
                <th scope="col" class="num">Tạm tính</th><th scope="col" class="actions">Thao tác</th></tr>
          </thead>
          <tbody>
            <c:forEach var="t" items="${templates}">
              <tr>
                <td>
                  <strong><c:out value="${t.name}"/></strong>
                  <div class="small muted">${ff:dateTime(t.createdAt)}<c:if test="${t.edited}"> · đã sửa</c:if></div>
                  <c:if test="${t.anyUnavailable}">
                    <span class="tag tag-amber">Có món không còn phục vụ</span>
                  </c:if>
                </td>
                <td>
                  <c:forEach var="i" items="${t.items}">
                    <div class="row-between middle">
                      <span class="${i.orderable ? '' : 'muted'}">
                        <c:out value="${i.productName}"/>
                        <c:if test="${not i.orderable}"> — không còn phục vụ</c:if>
                      </span>
                      <form method="post" action="${ctx}/order/history" class="row-tight">
                        <input type="hidden" name="_csrf" value="${csrfToken}">
                        <input type="hidden" name="action" value="templateSetQty">
                        <input type="hidden" name="templateId" value="${t.templateId}">
                        <input type="hidden" name="productId" value="${i.productId}">
                        <input type="number" name="quantity" value="${i.quantity}" min="0" max="50"
                               class="qty-input" data-autosubmit
                               title="Đặt về 0 để bỏ món khỏi mẫu">
                      </form>
                    </div>
                  </c:forEach>
                </td>
                <td class="num">${ff:money(t.estimatedTotal)}</td>
                <td class="actions">
                  <form method="post" action="${ctx}/order/history" class="inline-form">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="templateApply">
                    <input type="hidden" name="templateId" value="${t.templateId}">
                    <button type="submit" class="btn btn-sm btn-primary">Nạp vào giỏ</button>
                  </form>
                  <a class="btn btn-sm" href="${ctx}/order/history?editTemplate=${t.templateId}">Đổi tên</a>
                  <form method="post" action="${ctx}/order/history" class="inline-form"
                        onsubmit="return confirm('Xoá mẫu &quot;<c:out value="${t.name}"/>&quot;?');">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="templateDelete">
                    <input type="hidden" name="templateId" value="${t.templateId}">
                    <button type="submit" class="btn btn-sm btn-danger">Xoá</button>
                  </form>
                </td>
              </tr>
              <c:if test="${not empty editingTemplate and editingTemplate.templateId eq t.templateId}">
                <tr class="note-row">
                  <td colspan="4">
                    <form method="post" action="${ctx}/order/history" class="form-row">
                      <input type="hidden" name="_csrf" value="${csrfToken}">
                      <input type="hidden" name="action" value="templateRename">
                      <input type="hidden" name="templateId" value="${t.templateId}">
                      <div class="field">
                        <label for="templateName">Tên mẫu</label>
                        <input type="text" id="templateName" name="name" required maxlength="100"
                               value="<c:out value="${editingTemplate.name}"/>">
                      </div>
                      <button type="submit" class="btn btn-primary">Lưu</button>
                      <a class="btn" href="${ctx}/order/history">Thôi</a>
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
