<c:set var="pageTitle" value="Tổng quan" /><c:set var="nav" value="dashboard" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head"><h1>Tổng quan</h1></div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <div class="card">
    <form method="get" action="${ctx}/admin/dashboard" class="form-row">
      <div class="field">
        <label for="from">Từ</label>
        <input type="datetime-local" id="from" name="from" value="<c:out value="${from}"/>">
      </div>
      <div class="field">
        <label for="to">Đến</label>
        <input type="datetime-local" id="to" name="to" value="<c:out value="${to}"/>">
      </div>
      <button type="submit" class="btn btn-primary">Xem</button>
    </form>
    <%-- Ba khoảng hay xem nhất, đi thẳng bằng liên kết. Gõ tay hai mốc thời gian đầy đủ chỉ
         để xem doanh thu hôm nay là bốn thao tác cho một câu hỏi hỏi mỗi ngày. --%>
    <div class="row-tight mt">
      <span class="small muted">Xem nhanh:</span>
      <a class="btn btn-sm ${range eq 'today' ? 'btn-primary' : ''}" href="${ctx}/admin/dashboard?range=today">Hôm nay</a>
      <a class="btn btn-sm ${range eq '7d' ? 'btn-primary' : ''}" href="${ctx}/admin/dashboard?range=7d">7 ngày</a>
      <a class="btn btn-sm ${range eq '30d' ? 'btn-primary' : ''}" href="${ctx}/admin/dashboard?range=30d">30 ngày</a>
    </div>
    <c:if test="${not empty rangeSwapped}">
      <p class="small muted mt">Hai mốc thời gian bị gõ ngược nên đã tự đảo lại — số liệu bên
        dưới tính cho khoảng từ ${from} đến ${to}.</p>
    </c:if>
  </div>

  <div class="grid grid-4 mb">
    <div class="kpi">
      <div class="label">Doanh thu thuần</div>
      <div class="value">${ff:money(kpi.netRevenue)}</div>
      <div class="sub">Thu ${ff:money(kpi.grossRevenue)} · hoàn ${ff:money(kpi.refundedAmount)}</div>
    </div>
    <div class="kpi">
      <div class="label">Số đơn</div>
      <div class="value">${kpi.totalOrderCount}</div>
      <div class="sub">${kpi.onlineOrderCount} đặt trước · ${kpi.posOrderCount} tại quầy</div>
    </div>
    <div class="kpi ${kpi.onTimeReadyRate >= 90 ? 'good' : (kpi.onTimeReadyRate >= 70 ? 'warn' : 'bad')}">
      <div class="label">Món xong đúng hẹn</div>
      <div class="value"><fmt:formatNumber value="${kpi.onTimeReadyRate}" maxFractionDigits="1"/>%</div>
      <div class="sub">${kpi.onTimeReadyCount}/${kpi.totalReadyMeasured} đơn đặt trước</div>
    </div>
    <div class="kpi ${kpi.overduePickupCount > 0 ? 'warn' : ''}">
      <div class="label">Khách đến muộn</div>
      <div class="value">${kpi.overduePickupCount}</div>
      <div class="sub">${kpi.readyOrderCount} đơn đang chờ khách lấy</div>
    </div>
  </div>

  <div class="grid grid-4 mb">
    <div class="kpi">
      <div class="label">Đã giao</div>
      <div class="value">${kpi.completedOrderCount}</div>
    </div>
    <div class="kpi">
      <div class="label">Đã huỷ</div>
      <div class="value">${kpi.cancelledOrderCount}</div>
    </div>
    <div class="kpi">
      <div class="label">Hết hạn thanh toán</div>
      <div class="value">${kpi.expiredOrderCount}</div>
    </div>
    <div class="kpi">
      <div class="label">Thời gian làm trung bình</div>
      <div class="value">
        <c:choose>
          <c:when test="${empty kpi.avgPrepLeadMinutes}">—</c:when>
          <c:otherwise><fmt:formatNumber value="${kpi.avgPrepLeadMinutes}" maxFractionDigits="0"/> phút</c:otherwise>
        </c:choose>
      </div>
      <div class="sub">Từ lúc bếp nhận tới lúc xong</div>
    </div>
  </div>

  <%-- Chỉ tiêu doanh thu. Hai ô dưới đây cố ý KHÔNG đổi theo bộ lọc ngày ở đầu trang: mức đạt
       chỉ tiêu tháng mà nhảy theo khoảng người dùng đang xem thì con số ấy không so được với gì. --%>
  <div class="card">
    <div class="row-between mb">
      <h2>Chỉ tiêu doanh thu</h2>
      <span class="small muted">Tính theo doanh thu thuần, cùng cách tính với ô đầu trang</span>
    </div>

    <div class="grid grid-2 mb">
      <c:set var="cur" value="${monthTarget}" /><c:set var="curLabel" value="Tháng này" />
      <%@ include file="/WEB-INF/views/admin/target-kpi.jspf" %>
      <c:set var="cur" value="${dayTarget}" /><c:set var="curLabel" value="Hôm nay" />
      <%@ include file="/WEB-INF/views/admin/target-kpi.jspf" %>
    </div>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th scope="col">Kỳ</th>
            <th scope="col" class="num">Chỉ tiêu</th>
            <th scope="col" class="num">Đã đạt</th>
            <th scope="col">Ghi chú</th>
            <th scope="col">Người đặt</th>
            <th scope="col" class="actions">Thao tác</th>
          </tr>
        </thead>
        <tbody>
          <c:forEach var="t" items="${targets}">
            <tr>
              <td>
                <strong>${t.monthly ? 'Tháng' : 'Ngày'} ${t.periodLabel}</strong>
                <c:if test="${t.edited}"><div class="small muted">đã sửa</div></c:if>
              </td>
              <td class="num">${ff:money(t.targetAmount)}</td>
              <td class="num">
                <span class="${t.reached ? 'tag tag-green' : 'tag tag-muted'}">${t.achievedPercent}%</span>
                <div class="small muted">${ff:money(t.achieved)}</div>
              </td>
              <td class="small"><c:out value="${t.note}"/></td>
              <td class="small muted"><c:out value="${t.createdByName}"/></td>
              <td class="actions">
                <a class="btn btn-sm" href="${ctx}/admin/dashboard?editTarget=${t.targetId}">Sửa</a>
                <form method="post" action="${ctx}/admin/dashboard" class="inline-form"
                      onsubmit="return confirm('Xoá chỉ tiêu này? Con số cũ vẫn còn trong nhật ký thao tác.');">
                  <input type="hidden" name="_csrf" value="${csrfToken}">
                  <input type="hidden" name="action" value="targetDelete">
                  <input type="hidden" name="targetId" value="${t.targetId}">
                  <button type="submit" class="btn btn-sm btn-danger">Xoá</button>
                </form>
              </td>
            </tr>
            <c:if test="${not empty editingTarget and editingTarget.targetId eq t.targetId}">
              <tr class="note-row">
                <td colspan="6">
                  <form method="post" action="${ctx}/admin/dashboard" class="form-row">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="targetUpdate">
                    <input type="hidden" name="targetId" value="${t.targetId}">
                    <div class="field">
                      <label for="editAmount">Chỉ tiêu (đồng)</label>
                      <input type="number" id="editAmount" name="targetAmount" min="1" step="1000"
                             required value="${editingTarget.targetAmount}">
                    </div>
                    <div class="field">
                      <label for="editTargetNote">Ghi chú</label>
                      <input type="text" id="editTargetNote" name="note" maxlength="500"
                             value="<c:out value="${editingTarget.note}"/>">
                    </div>
                    <button type="submit" class="btn btn-primary">Lưu</button>
                    <a class="btn" href="${ctx}/admin/dashboard">Thôi</a>
                  </form>
                </td>
              </tr>
            </c:if>
          </c:forEach>
          <c:if test="${empty targets}">
            <tr><td colspan="6" class="center muted cell-empty">Chưa đặt chỉ tiêu nào.</td></tr>
          </c:if>
        </tbody>
      </table>
    </div>

    <form method="post" action="${ctx}/admin/dashboard" class="form-row mt">
      <input type="hidden" name="_csrf" value="${csrfToken}">
      <input type="hidden" name="action" value="targetCreate">
      <div class="field">
        <label for="periodType">Kỳ</label>
        <select id="periodType" name="periodType">
          <option value="MONTH">Theo tháng</option>
          <option value="DAY">Theo ngày</option>
        </select>
      </div>
      <div class="field">
        <label for="periodStart">Bắt đầu từ</label>
        <input type="date" id="periodStart" name="periodStart" required value="${today}">
      </div>
      <div class="field">
        <label for="targetAmount">Chỉ tiêu (đồng)</label>
        <input type="number" id="targetAmount" name="targetAmount" min="1" step="1000" required>
      </div>
      <div class="field">
        <label for="targetNote">Ghi chú</label>
        <input type="text" id="targetNote" name="note" maxlength="500">
      </div>
      <button type="submit" class="btn btn-primary">Đặt chỉ tiêu</button>
    </form>
    <p class="muted small">Chọn kỳ theo tháng thì ngày nào trong tháng cũng được — hệ thống tự quy
      về ngày mùng 1, để hai chỉ tiêu của cùng một tháng không cùng tồn tại.</p>
  </div>

  <div class="grid grid-2">
    <div class="card pad0 table-wrap">
      <div class="card-head"><h2>Món bán chạy</h2></div>
      <table>
        <thead><tr><th scope="col">Món</th><th scope="col">Nhóm</th><th scope="col" class="num">Đã bán</th><th scope="col" class="num">Doanh thu</th></tr></thead>
        <tbody>
          <c:forEach var="row" items="${bestSellers}">
            <tr>
              <td><c:out value="${row.label}"/></td>
              <td class="small muted"><c:out value="${row.subLabel}"/></td>
              <td class="num"><strong>${row.quantity}</strong></td>
              <td class="num">${ff:money(row.amount)}</td>
            </tr>
          </c:forEach>
          <c:if test="${empty bestSellers}">
            <tr><td colspan="4" class="center muted cell-empty">Chưa có đơn hoàn tất trong kỳ.</td></tr>
          </c:if>
        </tbody>
      </table>
    </div>

    <div class="card pad0 table-wrap">
      <div class="card-head"><h2>Thanh toán</h2></div>
      <table>
        <thead><tr><th scope="col">Phương thức</th><th scope="col">Trạng thái</th><th scope="col" class="num">Số lượt</th><th scope="col" class="num">Số tiền</th></tr></thead>
        <tbody>
          <c:forEach var="row" items="${paymentSummary}">
            <tr>
              <td><c:out value="${row.label}"/></td>
              <td><span class="${ff:paymentStatusClass(row.subLabel)}">${ff:paymentStatus(row.subLabel)}</span></td>
              <td class="num">${row.quantity}</td>
              <td class="num">${ff:money(row.amount)}</td>
            </tr>
          </c:forEach>
          <c:if test="${empty paymentSummary}">
            <tr><td colspan="4" class="center muted cell-empty">Chưa có giao dịch trong kỳ.</td></tr>
          </c:if>
        </tbody>
      </table>
    </div>
  </div>

  <c:if test="${not empty revenueByDay}">
    <div class="card">
      <h2>Doanh thu theo ngày</h2>
      <c:set var="maxRevenue" value="0" />
      <c:forEach var="row" items="${revenueByDay}">
        <c:if test="${row.amount > maxRevenue}"><c:set var="maxRevenue" value="${row.amount}" /></c:if>
      </c:forEach>
      <%-- role="img" kèm nhãn cho từng cột: không có nó thì trình đọc màn hình chỉ gặp một
           chuỗi ô trống, còn thuộc tính title thì chỉ chuột mới thấy. --%>
      <div class="chart" role="img" aria-label="Biểu đồ doanh thu theo ngày, ${fn:length(revenueByDay)} ngày">
        <c:forEach var="row" items="${revenueByDay}">
          <div class="chart-col"
               title="<c:out value="${row.label}"/>: ${ff:money(row.amount)}"
               aria-label="<c:out value="${row.label}"/>: ${ff:money(row.amount)}">
            <%-- Chỉ chiều cao còn nằm trong style: đó là số liệu, không phải kiểu hiển thị. --%>
            <div class="chart-bar"
                 style="height:${maxRevenue > 0 ? (row.amount * 130 / maxRevenue) : 0}px;"></div>
            <div class="small muted chart-label">
              ${fn:substring(row.label, 5, 10)}
            </div>
          </div>
        </c:forEach>
      </div>
    </div>
  </c:if>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
