<c:set var="pageTitle" value="Bếp" /><c:set var="nav" value="queue" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <%-- Nhận món / bàn giao xong quay lại đúng trang của từng khối. Khối kế hoạch chuẩn bị
       dùng đường riêng vì máy chủ tự nối ngày vào cuối. Sửa kế hoạch mở tại chỗ bằng JS. --%>
  <c:set var="kdsQuery"  value="${ff:pageQuery(pageContext.request, '')}" />
  <c:set var="kdsBack"   value="/kitchen/queue${empty kdsQuery ? '' : '?'.concat(kdsQuery)}" />
  <c:set var="kdsReturn"><input type="hidden" name="returnTo" value="<c:out value="${kdsBack}"/>"></c:set>
  <c:set var="prepQuery" value="${ff:pageQuery(pageContext.request, 'prepDate')}" />
  <c:set var="prepBack"  value="/kitchen/queue${empty prepQuery ? '' : '?'.concat(prepQuery)}" />
  <c:set var="prepReturn"><input type="hidden" name="returnTo" value="<c:out value="${prepBack}"/>"></c:set>
  <div class="page-head">
    <h1>Bếp</h1>
    <p>
      Việc của bạn ở trên, hàng chờ chung ở dưới.
      Món làm xong phải bàn giao ra quầy thì thu ngân mới giao cho khách được.
    </p>
  </div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <div class="grid grid-4 kds-kpis mb">
    <div class="kpi">
      <div class="label">Tôi đang làm</div>
      <div class="value" id="kds-kpi-mytasks">${taskPage.totalItems}</div>
      <div class="sub">đơn đang trong tay</div>
    </div>
    <div class="kpi ${handoverPage.totalItems gt 0 ? 'warn' : ''}">
      <div class="label">Chờ bàn giao</div>
      <div class="value" id="kds-kpi-handover">${handoverPage.totalItems}</div>
      <div class="sub">đơn đã xong, còn trong bếp</div>
    </div>
    <div class="kpi">
      <div class="label">Hàng chờ</div>
      <div class="value" id="kds-kpi-queue">${queuePage.totalItems}</div>
      <div class="sub">đơn chưa có người nhận</div>
    </div>
    <div class="kpi ${openIssueCount gt 0 ? 'bad' : ''}">
      <div class="label">Sự cố đang mở</div>
      <div class="value" id="kds-kpi-issues">${openIssueCount}</div>
      <div class="sub"><a href="${ctx}/kitchen/issue">Xem sự cố</a></div>
    </div>
  </div>

  <div class="alert alert-info row-between" id="kds-stale" role="status" hidden>
    <span>Danh sách vừa thay đổi ở nơi khác — các khối bên dưới đang là bản cũ.</span>
    <button type="button" class="btn btn-sm" id="kds-reload">Tải lại</button>
  </div>

  <h2 id="dang-lam">Đang làm (<span id="kds-mytasks-count">${taskPage.totalItems}</span> đơn)</h2>
  <c:choose>
    <c:when test="${taskPage.emptyPage}">
      <div class="card empty mb">Bạn chưa nhận đơn nào. Chọn một đơn ở hàng chờ bên dưới.</div>
    </c:when>
    <c:otherwise>
    <div class="kds-grid mb">
      <c:forEach var="o" items="${taskPage.items}">
        <div class="kds-card ${o.late ? 'late' : (o.urgent ? 'urgent' : (o.online ? 'online' : 'pos'))}">
          <div class="row-between">
            <span class="tag tag-amber">Đang làm</span>
            <span class="qty">${o.totalQuantity} phần</span>
          </div>
          <div class="title mt">Đơn #${o.orderId}</div>
          <div class="meta">
            <span class="tag ${o.online ? 'tag-info' : 'tag-muted'}">
              ${o.online ? 'Đặt trước' : 'Tại quầy'}
            </span>
            <span class="${o.late ? 'tag tag-red' : (o.urgent ? 'tag tag-amber' : '')}">${o.pickupLabel}</span>
          </div>
          <ul class="kds-items">
            <c:forEach var="v" items="${o.items}">
              <li>
                <span class="qty-inline">×${v.item.quantity}</span>
                <a href="${ctx}/kitchen/item?id=${v.item.orderItemId}"><c:out value="${v.item.productNameSnapshot}"/></a>
                <span class="${ff:itemStatusClass(v.item.itemStatus)}">${ff:itemStatus(v.item.itemStatus)}</span>
              </li>
            </c:forEach>
          </ul>
          <c:if test="${o.openIssueCount gt 0}">
            <div class="mt"><span class="tag tag-red">${o.openIssueCount} sự cố đang mở</span></div>
          </c:if>
          <div class="small muted mt">
            Bắt đầu lúc ${ff:time(o.startedAt)} · đã xong ${o.readyCount}/${o.itemCount} món
          </div>
          <div class="actions">
            <c:if test="${o.preparingCount gt 0}">
              <form method="post" action="${ctx}/kitchen/queue" class="grow"
                    data-confirm="Đánh dấu cả đơn này đã làm xong?">
                <input type="hidden" name="_csrf" value="${csrfToken}">
                <input type="hidden" name="action" value="readyOrder">
                ${kdsReturn}
                <input type="hidden" name="orderId" value="${o.orderId}">
                <button type="submit" class="btn btn-green btn-block touch">Đã làm xong cả đơn</button>
              </form>
            </c:if>
            <%-- Đơn nhận lẻ từ trước còn sót món chưa ai nhận: kéo nốt về cho đủ một đơn. --%>
            <c:if test="${o.waitingCount gt 0}">
              <form method="post" action="${ctx}/kitchen/queue" class="grow"
                    data-confirm="Nhận nốt ${o.waitingCount} món còn lại của đơn này?">
                <input type="hidden" name="_csrf" value="${csrfToken}">
                <input type="hidden" name="action" value="claimOrder">
                ${kdsReturn}
                <input type="hidden" name="orderId" value="${o.orderId}">
                <button type="submit" class="btn btn-primary btn-block touch">
                  Nhận nốt ${o.waitingCount} món
                </button>
              </form>
            </c:if>
            <a class="btn touch" href="${ctx}/kitchen/issue">Báo sự cố</a>
          </div>
        </div>
      </c:forEach>
    </div>
    </c:otherwise>
  </c:choose>

  <ui:pager page="${taskPage}" pageParam="taskPage" anchor="dang-lam" label="đơn" />

  <h2 id="cho-ban-giao">Chờ bàn giao ra quầy (<span id="kds-handover-count">${handoverPage.totalItems}</span> đơn)</h2>
  <c:choose>
    <c:when test="${handoverPage.emptyPage}">
      <div class="card empty mb">Không còn đơn nào nằm lại trong bếp.</div>
    </c:when>
    <c:otherwise>
    <div class="kds-grid mb">
      <c:forEach var="o" items="${handoverPage.items}">
        <div class="kds-card ${o.late ? 'late' : 'urgent'}">
          <div class="row-between">
            <span class="tag tag-green">Đã xong</span>
            <span class="qty">${o.totalQuantity} phần</span>
          </div>
          <div class="title mt">Đơn #${o.orderId}</div>
          <div class="meta">
            <span class="tag ${o.online ? 'tag-info' : 'tag-muted'}">
              ${o.online ? 'Đặt trước' : 'Tại quầy'}
            </span>
            <span>${o.pickupLabel}</span>
          </div>
          <ul class="kds-items">
            <c:forEach var="v" items="${o.items}">
              <li>
                <span class="qty-inline">×${v.item.quantity}</span>
                <a href="${ctx}/kitchen/item?id=${v.item.orderItemId}"><c:out value="${v.item.productNameSnapshot}"/></a>
                <c:if test="${not empty v.item.handedOverAt}">
                  <span class="tag tag-muted">đã ra quầy</span>
                </c:if>
              </li>
            </c:forEach>
          </ul>
          <c:if test="${o.orderClosed}">
            <div class="mt"><span class="tag tag-red">${ff:orderStatus(o.orderStatus)} — mang bỏ, không giao khách</span></div>
          </c:if>
          <c:if test="${o.openIssueCount gt 0}">
            <div class="mt"><span class="tag tag-red">${o.openIssueCount} sự cố đang mở — phải xử lý hoặc thu hồi trước khi đưa ra quầy</span></div>
          </c:if>
          <div class="small muted mt">Xong lúc ${ff:time(o.readyAt)}</div>
          <div class="actions">
            <c:choose>
              <c:when test="${o.openIssueCount gt 0}">
                <button type="button" class="btn btn-block touch" disabled
                        title="Hãy xử lý hoặc thu hồi tất cả sự cố đang mở trước">
                  Chưa thể đưa ra quầy
                </button>
              </c:when>
              <c:otherwise>
                <form method="post" action="${ctx}/kitchen/queue" class="grow"
                      data-confirm="Bàn giao cả đơn này ra quầy?">
                  <input type="hidden" name="_csrf" value="${csrfToken}">
                  <input type="hidden" name="action" value="handoverOrder">
                  ${kdsReturn}
                  <input type="hidden" name="orderId" value="${o.orderId}">
                  <button type="submit" class="btn btn-primary btn-block touch">
                    Bàn giao cả đơn ra quầy
                  </button>
                </form>
              </c:otherwise>
            </c:choose>
          </div>
        </div>
      </c:forEach>
    </div>
    </c:otherwise>
  </c:choose>

  <ui:pager page="${handoverPage}" pageParam="handoverPage" anchor="cho-ban-giao" label="đơn" />

  <div class="card pad0 table-wrap mb" id="chuan-bi">
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
        <c:forEach var="t" items="${prepPage.items}">
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
                  <button type="button" class="btn btn-sm" data-prep-edit
                          data-prep-id="${t.prepTaskId}"
                          data-prep-product="${fn:escapeXml(t.productName)}"
                          data-prep-planned="${t.plannedQty}"
                          data-prep-done="${t.doneQty}"
                          data-prep-note="${fn:escapeXml(t.note)}"
                          aria-controls="prep-edit-panel">Sửa</button>
                  <form method="post" action="${ctx}/kitchen/queue" class="inline-form"
                        data-confirm="Chốt dòng kế hoạch chuẩn bị này?">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="prepDone">
                    ${prepReturn}
                    <input type="hidden" name="prepTaskId" value="${t.prepTaskId}">
                    <input type="hidden" name="prepDate" value="${prepDate}">
                    <button type="submit" class="btn btn-sm btn-green">Chốt</button>
                  </form>
                  <c:if test="${t.createdBy eq me.userId}">
                    <form method="post" action="${ctx}/kitchen/queue" class="inline-form"
                          data-confirm="Thu hồi dòng kế hoạch này? Bản ghi vẫn được giữ trong nhật ký.">
                      <input type="hidden" name="_csrf" value="${csrfToken}">
                      <input type="hidden" name="action" value="prepCancel">
                      ${prepReturn}
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
        <c:if test="${prepPage.emptyPage}">
          <tr><td colspan="7" class="center muted cell-empty">Chưa có gì trong kế hoạch chuẩn bị.</td></tr>
        </c:if>
      </tbody>
    </table>
    <ui:pager page="${prepPage}" pageParam="prepPage" anchor="chuan-bi" label="dòng kế hoạch" />
  </div>

  <div class="card mb" id="prep-edit-panel" hidden>
    <div class="row-between">
      <h2>Sửa phần chuẩn bị sẵn</h2>
      <button type="button" class="btn btn-sm" data-prep-edit-cancel>Đóng</button>
    </div>
    <p class="small muted" id="prep-edit-product"></p>
    <form method="post" action="${ctx}/kitchen/queue" id="prep-edit-form"
          data-confirm="Lưu thay đổi cho dòng kế hoạch này?">
      <input type="hidden" name="_csrf" value="${csrfToken}">
      <input type="hidden" name="action" value="prepUpdate">
      ${prepReturn}
      <input type="hidden" name="prepDate" value="${prepDate}">
      <input type="hidden" name="prepTaskId" id="prep-edit-id">

      <div class="field">
        <label for="prep-edit-done">Đã làm được</label>
        <input type="number" id="prep-edit-done" name="doneQty" min="0" max="999" required>
      </div>
      <div class="field">
        <label for="prep-edit-planned">Số phần dự kiến</label>
        <input type="number" id="prep-edit-planned" name="plannedQty" min="1" max="999" required>
      </div>
      <div class="field">
        <label for="prep-edit-note">Ghi chú</label>
        <input type="text" id="prep-edit-note" name="note" maxlength="300"
               placeholder="ví dụ: nướng sẵn trước 11h">
      </div>
      <button type="submit" class="btn btn-primary btn-block">Lưu thay đổi</button>
    </form>
  </div>

  <div class="card mb">
    <h2>Thêm vào kế hoạch</h2>
    <form method="post" action="${ctx}/kitchen/queue"
          data-confirm="Thêm dòng này vào kế hoạch chuẩn bị?">
      <input type="hidden" name="_csrf" value="${csrfToken}">
      <input type="hidden" name="action" value="prepCreate">
      ${prepReturn}
      <input type="hidden" name="prepDate" value="${prepDate}">

      <div class="field">
        <label for="productId">Món cần chuẩn bị sẵn</label>
        <select id="productId" name="productId" required>
          <c:forEach var="p" items="${prepProducts}">
            <option value="${p.productId}"><c:out value="${p.name}"/></option>
          </c:forEach>
        </select>
        <p class="small muted mt">Chỉ liệt kê món còn bán. Mỗi món một dòng cho mỗi ngày.</p>
      </div>

      <div class="field">
        <label for="plannedQty">Số phần dự kiến</label>
        <input type="number" id="plannedQty" name="plannedQty" min="1" max="999"
               value="10" required>
      </div>

      <div class="field">
        <label for="note">Ghi chú</label>
        <input type="text" id="note" name="note" maxlength="300"
               placeholder="ví dụ: nướng sẵn trước 11h">
      </div>

      <button type="submit" class="btn btn-primary btn-block">
        Thêm vào kế hoạch
      </button>
    </form>
  </div>

  <h2 id="hang-cho">Hàng chờ</h2>
  <p class="small muted mb">
    Mỗi thẻ là một đơn, nhận là nhận trọn đơn. Đơn đặt trước chưa tới giờ chưa xuất hiện ở đây.
  </p>

  <div class="alert alert-warn" id="kds-offline" role="status" hidden>
    Mất kết nối tới máy chủ — danh sách bên dưới có thể đã cũ. Hệ thống vẫn đang thử lại.
  </div>

  <noscript>
    <div class="alert alert-warn">
      Trình duyệt đang tắt JavaScript nên danh sách không tự cập nhật.
      Bấm tải lại trang để xem đơn mới xuống bếp.
    </div>
  </noscript>

  <div class="card empty" id="kds-empty" ${queuePage.emptyPage ? '' : 'hidden'}>
    <div class="icon" aria-hidden="true">👨‍🍳</div>
    Không còn đơn nào chờ làm.
  </div>

  <div class="kds-grid" id="kds-grid" ${queuePage.emptyPage ? 'hidden' : ''}>
    <c:forEach var="o" items="${queuePage.items}">
      <div class="kds-card ${o.late ? 'late' : (o.urgent ? 'urgent' : (o.online ? 'online' : 'pos'))}"
           data-order-id="${o.orderId}"
           data-sig="${o.totalQuantity}|${o.itemCount}|${o.online}|${o.urgent}|${o.late}|${fn:escapeXml(o.pickupLabel)}|${o.openIssueCount}">
        <div class="row-between">
          <span class="tag ${o.online ? 'tag-info' : 'tag-muted'}">
            ${o.online ? 'Đặt trước' : 'Tại quầy'}
          </span>
          <span class="qty">${o.totalQuantity} phần</span>
        </div>
        <div class="title mt">Đơn #${o.orderId}</div>
        <div class="meta">
          <span>${o.itemCount} món</span>
          <span class="${o.late ? 'tag tag-red' : (o.urgent ? 'tag tag-amber' : '')}">
            ${o.pickupLabel}
          </span>
        </div>
        <ul class="kds-items">
          <c:forEach var="v" items="${o.items}">
            <li>
              <span class="qty-inline">×${v.item.quantity}</span>
              <a href="${ctx}/kitchen/item?id=${v.item.orderItemId}"><c:out value="${v.item.productNameSnapshot}"/></a>
            </li>
          </c:forEach>
        </ul>
        <div class="mt" ${o.openIssueCount > 0 ? '' : 'hidden'}>
          <span class="tag tag-red">${o.openIssueCount} sự cố đang mở</span>
        </div>
        <div class="actions">
          <form method="post" action="${ctx}/kitchen/queue" class="grow"
                data-confirm="Nhận làm cả đơn này?">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="action" value="claimOrder">
            ${kdsReturn}
            <input type="hidden" name="orderId" value="${o.orderId}">
            <button type="submit" class="btn btn-primary btn-block touch">Nhận cả đơn</button>
          </form>
        </div>
      </div>
    </c:forEach>
  </div>

  <ui:pager page="${queuePage}" pageParam="queuePage" anchor="hang-cho" label="đơn" />

  <%-- Khuôn thẻ để JavaScript dựng lại hàng chờ mỗi lần hỏi máy chủ. --%>
  <template id="kds-card-template">
    <div class="kds-card">
      <div class="row-between">
        <span class="tag" data-field="source"></span>
        <span class="qty" data-field="qty"></span>
      </div>
      <div class="title mt" data-field="orderLabel"></div>
      <div class="meta">
        <span data-field="itemCount"></span>
        <span data-field="pickupLabel"></span>
      </div>
      <ul class="kds-items" data-slot="items"></ul>
      <div class="mt" data-slot="issue" hidden>
        <span class="tag tag-red" data-field="issue"></span>
      </div>
      <div class="actions">
        <form method="post" action="${ctx}/kitchen/queue" class="grow"
              data-confirm="Nhận làm cả đơn này?">
          <input type="hidden" name="_csrf" value="${csrfToken}">
          <input type="hidden" name="action" value="claimOrder">
          ${kdsReturn}
          <input type="hidden" name="orderId" data-field="orderId">
          <button type="submit" class="btn btn-primary btn-block touch">Nhận cả đơn</button>
        </form>
      </div>
    </div>
  </template>

  <template id="kds-item-template">
    <li>
      <span class="qty-inline" data-field="itemQty"></span>
      <a data-field="itemName"></a>
    </li>
  </template>

  <div id="kds-watch" hidden
       data-endpoint="${ctx}/api/kds/queue"
       data-detail-base="${ctx}/kitchen/item?id="
       data-rendered-mytasks="${taskPage.totalItems}"
       data-rendered-handover="${handoverPage.totalItems}"
       data-rendered-queue="${queuePage.totalItems}"
       data-queue-page="${queuePage.pageNo}"
       data-queue-size="${queuePage.pageSize}"></div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
