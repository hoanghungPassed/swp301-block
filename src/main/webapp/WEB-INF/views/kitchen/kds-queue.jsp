<c:set var="pageTitle" value="Bếp" /><c:set var="nav" value="queue" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head">
    <h1>Bếp</h1>
    <p>
      Việc của bạn ở trên, hàng chờ chung ở dưới.
      Món làm xong phải bàn giao ra quầy thì thu ngân mới giao cho khách được.
    </p>
  </div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <%-- Bốn con số của cả ca, đứng trước mọi danh sách. Đầu bếp đứng cách màn hình vài bước
       thường chỉ cần biết "còn việc không" chứ chưa cần đọc từng thẻ; app.js cập nhật cả bốn
       theo cùng nhịp với hàng chờ nên chúng không bao giờ nói khác danh sách bên dưới. --%>
  <div class="grid grid-4 kds-kpis mb">
    <div class="kpi">
      <div class="label">Tôi đang làm</div>
      <div class="value" id="kds-kpi-mytasks">${fn:length(myTasks)}</div>
    </div>
    <div class="kpi ${empty awaitingHandover ? '' : 'warn'}">
      <div class="label">Chờ bàn giao</div>
      <div class="value" id="kds-kpi-handover">${fn:length(awaitingHandover)}</div>
      <div class="sub">món còn nằm trong bếp</div>
    </div>
    <div class="kpi">
      <div class="label">Hàng chờ</div>
      <div class="value" id="kds-kpi-queue">${fn:length(queue)}</div>
      <div class="sub">chưa có người nhận</div>
    </div>
    <div class="kpi ${openIssueCount gt 0 ? 'bad' : ''}">
      <div class="label">Sự cố đang mở</div>
      <div class="value" id="kds-kpi-issues">${openIssueCount}</div>
      <div class="sub"><a href="${ctx}/kitchen/issue">Xem sự cố</a></div>
    </div>
  </div>

  <%-- Hiện khi số máy chủ trả về khác số thẻ đang vẽ ở hai khối dưới đây. Hai khối đó có nút
       gửi biểu mẫu nên app.js cố ý không vẽ lại chúng giữa chừng — vẽ lại là cướp mất cú bấm
       đang dở. Thay vì im lặng để màn hình cũ, nói thẳng là có thay đổi và mời tải lại. --%>
  <div class="alert alert-info row-between" id="kds-stale" role="status" hidden>
    <span>Việc của bạn vừa thay đổi ở nơi khác — hai khối bên dưới đang là bản cũ.</span>
    <button type="button" class="btn btn-sm" id="kds-reload">Tải lại</button>
  </div>

  <%-- Việc đang làm dở đứng đầu: đó là thứ đầu bếp phải quay lại, không phải việc mới.
       Khối luôn có mặt kể cả khi rỗng: con số ở tiêu đề là chỗ app.js ghi vào, mà một khối
       biến mất thì con số cũng biến mất theo và màn hình đứng im không ai biết. --%>
  <h2>Đang làm (<span id="kds-mytasks-count">${fn:length(myTasks)}</span>)</h2>
  <c:choose>
    <c:when test="${empty myTasks}">
      <div class="card empty mb">Bạn chưa nhận món nào. Chọn một món ở hàng chờ bên dưới.</div>
    </c:when>
    <c:otherwise>
    <div class="kds-grid mb">
      <c:forEach var="v" items="${myTasks}">
        <div class="kds-card ${v.late ? 'late' : (v.urgent ? 'urgent' : (v.online ? 'online' : 'pos'))}">
          <div class="row-between">
            <span class="tag tag-amber">Đang làm</span>
            <span class="qty">×${v.item.quantity}</span>
          </div>
          <div class="title mt"><c:out value="${v.item.productNameSnapshot}"/></div>
          <div class="meta">
            <span>Đơn #${v.item.orderId}</span>
            <span class="${v.late ? 'tag tag-red' : (v.urgent ? 'tag tag-amber' : '')}">${v.pickupLabel}</span>
          </div>
          <%-- Sự cố của chính món đang nấu phải hiện ngay trên thẻ này. Trước đây nhãn đó chỉ
               có ở hàng chờ, nên báo xong sự cố là nó khuất khỏi tầm mắt đúng lúc món vào tay
               người phải xử lý nó. --%>
          <c:if test="${v.openIssueCount gt 0}">
            <div class="mt"><span class="tag tag-red">${v.openIssueCount} sự cố đang mở</span></div>
          </c:if>
          <div class="small muted mt">Bắt đầu lúc ${ff:time(v.item.startedAt)}</div>
          <div class="actions">
            <form method="post" action="${ctx}/kitchen/queue" class="grow">
              <input type="hidden" name="_csrf" value="${csrfToken}">
              <input type="hidden" name="action" value="ready">
              <input type="hidden" name="orderItemId" value="${v.item.orderItemId}">
              <button type="submit" class="btn btn-green btn-block touch">Đã làm xong</button>
            </form>
            <a class="btn touch" href="${ctx}/kitchen/issue?orderItemId=${v.item.orderItemId}">Báo sự cố</a>
            <a class="btn touch" href="${ctx}/kitchen/item?id=${v.item.orderItemId}">Chi tiết</a>
          </div>
        </div>
      </c:forEach>
    </div>
    </c:otherwise>
  </c:choose>

  <%-- Món đã xong mà chưa ra quầy. Đây là khối quan trọng nhất của màn hình này: trước khi
       có nó, món nấu xong không còn nằm trong danh sách nào và chỉ lộ ra khi khách hỏi. --%>
  <h2>Chờ bàn giao ra quầy (<span id="kds-handover-count">${fn:length(awaitingHandover)}</span>)</h2>
  <c:choose>
    <c:when test="${empty awaitingHandover}">
      <div class="card empty mb">Không còn món nào nằm lại trong bếp.</div>
    </c:when>
    <c:otherwise>
    <div class="kds-grid mb">
      <c:forEach var="v" items="${awaitingHandover}">
        <div class="kds-card ${v.late ? 'late' : 'urgent'}">
          <div class="row-between">
            <span class="tag tag-green">Đã xong</span>
            <span class="qty">×${v.item.quantity}</span>
          </div>
          <div class="title mt"><c:out value="${v.item.productNameSnapshot}"/></div>
          <div class="meta">
            <span>Đơn #${v.item.orderId}</span>
            <span>${v.pickupLabel}</span>
          </div>
          <%-- Đơn bị huỷ sau khi món đã nấu xong: món vẫn phải đưa ra khỏi bếp, nhưng đầu bếp
               cần biết ngay là nó sẽ bị bỏ đi chứ không tới tay khách. --%>
          <c:if test="${v.item.orderClosed}">
            <div class="mt"><span class="tag tag-red">${ff:orderStatus(v.item.orderStatus)} — mang bỏ, không giao khách</span></div>
          </c:if>
          <%-- Món quầy trả lại quay về đúng khối này. Không có nhãn thì nó trông y hệt một món
               vừa nấu xong, và đầu bếp bàn giao lại ngay chính món vừa bị chê. --%>
          <c:if test="${v.openIssueCount gt 0}">
            <div class="mt"><span class="tag tag-red">${v.openIssueCount} sự cố đang mở — xem lý do trước khi bàn giao lại</span></div>
          </c:if>
          <div class="small muted mt">Xong lúc ${ff:time(v.item.readyAt)}</div>
          <div class="actions">
            <form method="post" action="${ctx}/kitchen/queue" class="grow">
              <input type="hidden" name="_csrf" value="${csrfToken}">
              <input type="hidden" name="action" value="handover">
              <input type="hidden" name="orderItemId" value="${v.item.orderItemId}">
              <button type="submit" class="btn btn-primary btn-block touch">Bàn giao ra quầy</button>
            </form>
            <a class="btn touch" href="${ctx}/kitchen/item?id=${v.item.orderItemId}">Chi tiết</a>
          </div>
        </div>
      </c:forEach>
    </div>
    </c:otherwise>
  </c:choose>

  <%-- Kế hoạch chuẩn bị sẵn. Đứng TRƯỚC hàng chờ vì đây là việc của đầu ca: quyết định làm
       sẵn bao nhiêu rồi mới bắt đầu chạy theo đơn. Khác mọi khối còn lại trên trang ở chỗ nó
       không gắn với đơn hàng nào — xem PrepService. --%>
  <div class="card pad0 table-wrap mb">
    <div class="card-head row-between">
      <h2>Chuẩn bị sẵn — ${ff:date(prepDate.atStartOfDay())}</h2>
      <form method="get" action="${ctx}/kitchen/queue" class="inline-form">
        <label class="visually-hidden" for="prepDate">Ngày</label>
        <input type="date" id="prepDate" name="prepDate" value="${prepDate}">
        <button type="submit" class="btn btn-sm">Xem ngày khác</button>
      </form>
    </div>
    <table>
      <thead>
        <tr>
          <th scope="col">Món</th><th scope="col">Dự kiến</th><th scope="col">Đã làm</th>
          <th scope="col">Còn thiếu</th><th scope="col">Ghi chú</th><th scope="col">Người lập</th>
          <th scope="col"><span class="visually-hidden">Thao tác</span></th>
        </tr>
      </thead>
      <tbody>
        <c:forEach var="t" items="${prepTasks}">
          <tr>
            <td><c:out value="${t.productName}"/></td>
            <td>${t.plannedQty}</td>
            <td>${t.doneQty}</td>
            <td>
              <c:choose>
                <c:when test="${t.remainingQty gt 0}"><span class="tag tag-amber">còn ${t.remainingQty}</span></c:when>
                <c:when test="${t.remainingQty lt 0}"><span class="tag tag-muted">dư ${-t.remainingQty}</span></c:when>
                <c:otherwise><span class="tag tag-green">đủ</span></c:otherwise>
              </c:choose>
            </td>
            <td class="small"><c:out value="${t.note}"/></td>
            <td class="small muted"><c:out value="${t.createdByName}"/></td>
            <td class="center">
              <c:choose>
                <c:when test="${t.planned}">
                  <a class="btn btn-sm" href="${ctx}/kitchen/queue?prepDate=${prepDate}&amp;editPrep=${t.prepTaskId}">Sửa</a>
                  <form method="post" action="${ctx}/kitchen/queue" class="inline-form">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="prepDone">
                    <input type="hidden" name="prepTaskId" value="${t.prepTaskId}">
                    <input type="hidden" name="prepDate" value="${prepDate}">
                    <button type="submit" class="btn btn-sm btn-green">Chốt</button>
                  </form>
                  <%-- Chỉ người lập mới thu hồi được; ẩn nút ở đây cho bảng gọn, quyền thật
                       do PrepService.cancel quyết định. --%>
                  <c:if test="${t.createdBy eq me.userId}">
                    <form method="post" action="${ctx}/kitchen/queue" class="inline-form"
                          data-confirm="Thu hồi dòng kế hoạch này? Bản ghi vẫn được giữ trong nhật ký.">
                      <input type="hidden" name="_csrf" value="${csrfToken}">
                      <input type="hidden" name="action" value="prepCancel">
                      <input type="hidden" name="prepTaskId" value="${t.prepTaskId}">
                      <input type="hidden" name="prepDate" value="${prepDate}">
                      <button type="submit" class="btn btn-sm btn-danger">Thu hồi</button>
                    </form>
                  </c:if>
                </c:when>
                <c:otherwise><span class="tag tag-green">Đã chốt</span></c:otherwise>
              </c:choose>
            </td>
          </tr>
        </c:forEach>
        <c:if test="${empty prepTasks}">
          <tr><td colspan="7" class="center muted cell-empty">Chưa có gì trong kế hoạch chuẩn bị.</td></tr>
        </c:if>
      </tbody>
    </table>

  </div>

  <%-- Một biểu mẫu cho cả thêm và sửa: hai việc chỉ khác nhau ở chỗ sửa thì món đã cố định
       và có thêm ô "đã làm". Tách thành hai biểu mẫu sẽ nhân đôi phần nhập số lượng và ghi chú. --%>
  <div class="card mb">
    <h2>${empty editingPrep ? 'Thêm vào kế hoạch' : 'Sửa dòng kế hoạch'}</h2>
    <form method="post" action="${ctx}/kitchen/queue">
      <input type="hidden" name="_csrf" value="${csrfToken}">
      <input type="hidden" name="action" value="${empty editingPrep ? 'prepCreate' : 'prepUpdate'}">
      <input type="hidden" name="prepDate" value="${prepDate}">

      <c:choose>
        <c:when test="${empty editingPrep}">
          <div class="field">
            <label for="productId">Món cần chuẩn bị sẵn</label>
            <select id="productId" name="productId" required>
              <c:forEach var="p" items="${prepProducts}">
                <option value="${p.productId}"><c:out value="${p.name}"/></option>
              </c:forEach>
            </select>
            <p class="small muted mt">Chỉ liệt kê món còn bán. Mỗi món một dòng cho mỗi ngày.</p>
          </div>
        </c:when>
        <c:otherwise>
          <input type="hidden" name="prepTaskId" value="${editingPrep.prepTaskId}">
          <p class="small muted"><c:out value="${editingPrep.productName}"/></p>
          <div class="field">
            <label for="doneQty">Đã làm được</label>
            <input type="number" id="doneQty" name="doneQty" min="0" max="999"
                   value="${editingPrep.doneQty}" required>
          </div>
        </c:otherwise>
      </c:choose>

      <div class="field">
        <label for="plannedQty">Số phần dự kiến</label>
        <input type="number" id="plannedQty" name="plannedQty" min="1" max="999"
               value="${empty editingPrep ? 10 : editingPrep.plannedQty}" required>
      </div>

      <div class="field">
        <label for="note">Ghi chú</label>
        <input type="text" id="note" name="note" maxlength="300"
               value="${empty editingPrep ? '' : fn:escapeXml(editingPrep.note)}"
               placeholder="ví dụ: nướng sẵn trước 11h">
      </div>

      <button type="submit" class="btn btn-primary btn-block">
        ${empty editingPrep ? 'Thêm vào kế hoạch' : 'Lưu thay đổi'}
      </button>
    </form>
    <c:if test="${not empty editingPrep}">
      <a class="btn btn-block mt" href="${ctx}/kitchen/queue?prepDate=${prepDate}">Huỷ sửa</a>
    </c:if>
  </div>

  <h2>Hàng chờ</h2>
  <p class="small muted mb">
    Chỉ hiện món của đơn đã tới lượt làm. Đơn đặt trước chưa tới giờ chưa xuất hiện ở đây.
  </p>

  <%-- Hiện khi máy đã hụt vài lượt hỏi liên tiếp. Màn hình bếp đứng im mà không ai biết
       thì nguy hiểm hơn nhiều so với một dòng cảnh báo thừa. --%>
  <div class="alert alert-warn" id="kds-offline" role="status" hidden>
    Mất kết nối tới máy chủ — danh sách bên dưới có thể đã cũ. Hệ thống vẫn đang thử lại.
  </div>

  <noscript>
    <div class="alert alert-warn">
      Trình duyệt đang tắt JavaScript nên danh sách không tự cập nhật.
      Bấm tải lại trang để xem món mới xuống bếp.
    </div>
  </noscript>

  <%-- Cả hai khối luôn có mặt; app.js bật tắt bằng thuộc tính hidden khi hàng chờ đổi
       từ rỗng sang có món và ngược lại. --%>
  <div class="card empty" id="kds-empty" ${empty queue ? '' : 'hidden'}>
    <div class="icon" aria-hidden="true">👨‍🍳</div>
    Không còn món nào chờ làm.
  </div>

  <div class="kds-grid" id="kds-grid" ${empty queue ? 'hidden' : ''}>
    <c:forEach var="v" items="${queue}">
      <%-- data-sig phải khớp đúng thứ tự và cách nối của hàm kdsSignature trong app.js,
           nếu không thì lượt hỏi đầu tiên sẽ dựng lại toàn bộ thẻ một cách vô ích. --%>
      <div class="kds-card ${v.late ? 'late' : (v.urgent ? 'urgent' : (v.online ? 'online' : 'pos'))}"
           data-item-id="${v.item.orderItemId}"
           data-sig="${v.item.quantity}|${v.online}|${v.urgent}|${v.late}|${fn:escapeXml(v.pickupLabel)}|${v.openIssueCount}">
        <div class="row-between">
          <span class="tag ${v.online ? 'tag-info' : 'tag-muted'}">
            ${v.online ? 'Đặt trước' : 'Tại quầy'}
          </span>
          <span class="qty">×${v.item.quantity}</span>
        </div>
        <div class="title mt"><c:out value="${v.item.productNameSnapshot}"/></div>
        <div class="meta">
          <span>Đơn #${v.item.orderId}</span>
          <span class="${v.late ? 'tag tag-red' : (v.urgent ? 'tag tag-amber' : '')}">
            ${v.pickupLabel}
          </span>
        </div>
        <div class="mt" ${v.openIssueCount > 0 ? '' : 'hidden'}>
          <span class="tag tag-red">${v.openIssueCount} sự cố đang mở</span>
        </div>
        <div class="actions">
          <form method="post" action="${ctx}/kitchen/queue" class="grow">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="action" value="claim">
            <input type="hidden" name="orderItemId" value="${v.item.orderItemId}">
            <button type="submit" class="btn btn-primary btn-block touch">Nhận món này</button>
          </form>
          <a class="btn touch" href="${ctx}/kitchen/item?id=${v.item.orderItemId}">Chi tiết</a>
        </div>
      </div>
    </c:forEach>
  </div>

  <%--
    Khuôn để app.js dựng thẻ mới. Giữ phần chữ và bố cục ở đây, cùng một chỗ với thẻ do
    máy chủ vẽ bên trên, để hai bên không lệch nhau. app.js chỉ điền dữ liệu vào các ô
    đánh dấu data-field.
  --%>
  <template id="kds-card-template">
    <div class="kds-card">
      <div class="row-between">
        <span class="tag" data-field="source"></span>
        <span class="qty" data-field="qty"></span>
      </div>
      <div class="title mt" data-field="productName"></div>
      <div class="meta">
        <span data-field="orderLabel"></span>
        <span data-field="pickupLabel"></span>
      </div>
      <div class="mt" data-slot="issue" hidden>
        <span class="tag tag-red" data-field="issue"></span>
      </div>
      <div class="actions">
        <form method="post" action="${ctx}/kitchen/queue" class="grow">
          <input type="hidden" name="_csrf" value="${csrfToken}">
          <input type="hidden" name="action" value="claim">
          <input type="hidden" name="orderItemId" data-field="itemId">
          <button type="submit" class="btn btn-primary btn-block touch">Nhận món này</button>
        </form>
        <a class="btn touch" data-field="detailHref">Chi tiết</a>
      </div>
    </div>
  </template>

  <%-- Dấu hiệu để app.js biết trang này cần tự cập nhật, kèm địa chỉ để hỏi.
       Hai số "đã vẽ" là ảnh chụp lúc máy chủ dựng trang; app.js so chúng với số mới nhận về
       để biết hai khối phía trên đã cũ. Đặt ở đây chứ không trên từng khối, vì khối rỗng thì
       không được vẽ ra và con số sẽ mất theo — đúng lúc cần so nhất. --%>
  <div id="kds-watch" hidden
       data-endpoint="${ctx}/api/kds/queue"
       data-detail-base="${ctx}/kitchen/item?id="
       data-rendered-mytasks="${fn:length(myTasks)}"
       data-rendered-handover="${fn:length(awaitingHandover)}"></div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
