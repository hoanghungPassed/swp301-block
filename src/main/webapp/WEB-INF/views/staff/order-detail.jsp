<c:set var="pageTitle" value="Đơn #${order.orderId}" /><c:set var="nav" value="orders" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <%-- Hoá đơn không còn là trang riêng: bấm in ngay tại đây, khối hoá đơn nằm cuối trang và
       chỉ hiện ra trên giấy. Trước đây phải mở thêm một trang nữa chỉ để bấm đúng một nút. --%>
  <div class="row-between mb no-print">
    <p class="small"><a href="${ctx}/staff/orders">← Danh sách đơn hàng</a></p>
    <button type="button" class="btn touch" data-print>In hoá đơn</button>
  </div>

  <div class="no-print">
    <%@ include file="/WEB-INF/views/layout/flash.jspf" %>
  </div>

  <c:if test="${not empty openIssues}">
    <div class="alert alert-warn no-print">
      <strong>Bếp báo sự cố với đơn này.</strong> Đơn sẽ không tự chuyển sang sẵn sàng cho tới
      khi bếp xử lý xong. Liên hệ khách để đổi món hoặc chờ thêm; nếu khách không đồng ý thì
      huỷ đơn ở khung bên phải.
      <ul class="mt">
        <c:forEach var="i" items="${openIssues}">
          <li>
            <c:out value="${i.productName}"/> — ${ff:issueType(i.issueType)}
            <c:if test="${not empty i.description}">: <c:out value="${i.description}"/></c:if>
            <span class="small muted">(<c:out value="${i.createdByName}"/>, ${ff:time(i.createdAt)})</span>
          </li>
        </c:forEach>
      </ul>
    </div>
  </c:if>

  <div class="grid grid-side no-print">
    <div>
      <div class="card">
        <div class="row-between mb">
          <div>
            <h1>Đơn #${order.orderId}</h1>
            <p class="small muted">
              ${ff:orderSource(order.orderSource)} · đặt lúc ${ff:dateTime(order.createdAt)}
            </p>
          </div>
          <span class="${ff:orderStatusClass(order.orderStatus)}">${ff:orderStatus(order.orderStatus)}</span>
        </div>

        <div class="grid grid-2">
          <div>
            <div class="total-line"><span class="muted">Khách hàng</span>
              <span><c:out value="${empty order.customerName ? 'Khách tại quầy' : order.customerName}"/></span></div>
            <c:if test="${order.online}">
              <div class="total-line"><span class="muted">Giờ hẹn</span>
                <span><strong>${ff:dateTime(order.pickupTime)}</strong></span></div>
              <div class="total-line"><span class="muted">Kế hoạch vào bếp</span>
                <span>${ff:dateTime(order.kitchenReleaseAt)}</span></div>
            </c:if>
            <div class="total-line"><span class="muted">Bếp nhận lúc</span>
              <span>${empty order.releasedToKdsAt ? 'Chưa nhận' : ff:dateTime(order.releasedToKdsAt)}</span></div>
          </div>
          <div>
            <c:if test="${not empty order.readyAt}">
              <div class="total-line"><span class="muted">Sẵn sàng lúc</span><span>${ff:dateTime(order.readyAt)}</span>
              </div>
            </c:if>
            <c:if test="${not empty order.pickedUpAt}">
              <div class="total-line"><span class="muted">Giao lúc</span><span>${ff:dateTime(order.pickedUpAt)}</span></div>
              <div class="total-line"><span class="muted">Người giao</span><span><c:out value="${order.handoffByName}"/></span></div>
            </c:if>
            <div class="total-line grand"><span>Tổng tiền</span><span>${ff:money(order.totalAmount)}</span></div>
          </div>
        </div>
      </div>

      <div class="card pad0 table-wrap">
        <div class="card-head"><h2>Món trong đơn</h2></div>
        <table>
          <thead><tr><th scope="col">Món</th><th scope="col" class="center">SL</th><th scope="col" class="num">Đơn giá</th>
                     <th scope="col" class="num">Thành tiền</th><th scope="col">Bếp</th><th scope="col">Tại quầy</th>
                     <th scope="col">Người làm</th></tr></thead>
          <tbody>
            <c:forEach var="item" items="${order.items}">
              <tr>
                <td><c:out value="${item.productNameSnapshot}"/>
                  <c:if test="${item.openIssueCount > 0}">
                    <span class="tag tag-red">${item.openIssueCount} sự cố</span>
                  </c:if>
                </td>
                <td class="center">${item.quantity}</td>
                <td class="num">${ff:money(item.unitPrice)}</td>
                <td class="num">${ff:money(item.lineTotal)}</td>
                <td><span class="${ff:itemStatusClass(item.itemStatus)}">${ff:itemStatus(item.itemStatus)}</span></td>
                <%-- Ba mức của chặng bàn giao. Cột này trả lời câu thu ngân hay phải hỏi bếp:
                     món đã ra tới quầy chưa, hay vẫn còn trong bếp. --%>
                <td class="small">
                  <c:choose>
                    <c:when test="${item.received}">
                      <span class="tag tag-green">Đã nhận</span>
                      <div class="muted">${ff:time(item.receivedAt)}</div>
                    </c:when>
                    <c:when test="${item.handedOver}">
                      <span class="tag tag-amber">Chờ quầy nhận</span>
                      <div class="muted">${ff:time(item.handedOverAt)}</div>
                    </c:when>
                    <c:otherwise><span class="muted">Còn trong bếp</span></c:otherwise>
                  </c:choose>
                </td>
                <td class="small muted"><c:out value="${empty item.assignedToName ? '—' : item.assignedToName}"/></td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </div>

      <div class="card pad0 table-wrap">
        <div class="card-head"><h2>Thanh toán</h2></div>
        <table>
          <thead><tr><th scope="col" class="center">Lần</th><th scope="col">Phương thức</th><th scope="col" class="num">Số tiền</th>
                     <th scope="col">Trạng thái</th><th scope="col">Thời điểm</th></tr></thead>
          <tbody>
            <c:forEach var="p" items="${payments}">
              <tr>
                <td class="center">${p.attemptNo}</td>
                <td>${ff:paymentMethod(p.method)}</td>
                <td class="num">${ff:money(p.amount)}</td>
                <td><span class="${ff:paymentStatusClass(p.paymentStatus)}">${ff:paymentStatus(p.paymentStatus)}</span></td>
                <td class="small muted">
                  ${empty p.paidAt ? ff:dateTime(p.createdAt) : ff:dateTime(p.paidAt)}
                </td>
              </tr>
            </c:forEach>
            <c:if test="${empty payments}">
              <tr><td colspan="5" class="muted center">Chưa có giao dịch nào.</td></tr>
            </c:if>
          </tbody>
        </table>
      </div>

      <div class="card pad0 table-wrap">
        <div class="card-head"><h2>Nhật ký thao tác</h2></div>
        <table>
          <thead><tr><th scope="col">Thời điểm</th><th scope="col">Thao tác</th><th scope="col">Người thực hiện</th><th scope="col">Thay đổi</th></tr></thead>
          <tbody>
            <c:forEach var="log" items="${auditLogs}">
              <tr>
                <td class="small muted">${ff:dateTime(log.createdAt)}</td>
                <td>${ff:auditAction(log.action)}</td>
                <td class="small"><c:out value="${log.actorDisplay}"/></td>
                <td class="small muted">
                  <c:if test="${not empty log.oldValue}"><c:out value="${log.oldValue}"/> → </c:if><c:out value="${log.newValue}"/>
                </td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </div>
    </div>

    <div>
      <c:if test="${order.orderStatus eq 'READY'}">
        <div class="card">
          <h2>Giao món cho khách</h2>
          <c:choose>
            <c:when test="${order.online}">
              <p class="small muted mb">
                Nhập mã khách đưa. Mã sai thì hệ thống từ chối giao — tránh đưa nhầm đơn.
              </p>
              <form method="post" action="${ctx}/staff/order/detail">
                <input type="hidden" name="_csrf" value="${csrfToken}">
                <input type="hidden" name="orderId" value="${order.orderId}">
                <input type="hidden" name="action" value="handoff">
                <div class="field">
                  <label for="pickupCode">Mã nhận hàng</label>
                  <input type="text" id="pickupCode" name="pickupCode" class="mono code-input"
                         placeholder="VD: 260813A1C7" required autofocus>
                </div>
                <button type="submit" class="btn btn-green btn-block touch">Xác minh và giao món</button>
              </form>
            </c:when>
            <c:otherwise>
              <p class="small muted mb">Đơn tại quầy, khách đang đứng đợi — giao trực tiếp.</p>
              <form method="post" action="${ctx}/staff/order/detail">
                <input type="hidden" name="_csrf" value="${csrfToken}">
                <input type="hidden" name="orderId" value="${order.orderId}">
                <input type="hidden" name="action" value="handoff">
                <button type="submit" class="btn btn-green btn-block touch">Đã giao món</button>
              </form>
            </c:otherwise>
          </c:choose>
        </div>
      </c:if>

      <c:if test="${order.staffCancellable}">
        <div class="card">
          <h2>Huỷ đơn &amp; hoàn tiền</h2>
          <p class="small muted mb">
            <c:choose>
              <c:when test="${order.orderStatus eq 'READY'}">
                Món đã làm xong nhưng khách không tới lấy. Huỷ để đóng đơn và trả lại tiền.
              </c:when>
              <c:when test="${order.orderStatus eq 'PREPARING'}">
                Bếp đang làm dở. Chỉ huỷ khi đã thống nhất với khách — nguyên liệu đã dùng rồi.
              </c:when>
              <c:otherwise>
                Huỷ đơn và hoàn lại toàn bộ tiền nếu khách đã thanh toán. Không có hoàn một phần.
              </c:otherwise>
            </c:choose>
          </p>
          <form method="post" action="${ctx}/staff/order/detail"
                data-confirm="Huỷ đơn #${order.orderId} và hoàn tiền cho khách?">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="orderId" value="${order.orderId}">
            <input type="hidden" name="action" value="cancel">
            <div class="field">
              <label for="reason">Lý do huỷ</label>
              <input type="text" id="reason" name="reason" maxlength="200" required
                     placeholder="VD: khách không tới lấy, bếp hết nguyên liệu">
            </div>
            <button type="submit" class="btn btn-danger btn-block">
              Huỷ đơn<c:if test="${not empty order.latestPayment
                                   and order.latestPayment.paymentStatus eq 'PAID'}"> &amp; hoàn ${ff:money(order.totalAmount)}</c:if>
            </button>
          </form>
        </div>
      </c:if>

      <c:if test="${order.refundPending}">
        <div class="card">
          <h2>Hoàn tiền sót</h2>
          <p class="small muted mb">
            Đơn đã đóng nhưng khoản thanh toán vẫn chưa được hoàn. Bình thường huỷ đơn đã tự
            hoàn tiền kèm theo, nên nếu thấy ô này thì có một lần hoàn trước đó không thành công.
          </p>
          <form method="post" action="${ctx}/staff/order/detail"
                data-confirm="Hoàn tiền cho đơn #${order.orderId}?">
            <input type="hidden" name="_csrf" value="${csrfToken}">
            <input type="hidden" name="orderId" value="${order.orderId}">
            <input type="hidden" name="action" value="refund">
            <div class="field">
              <label for="refundReason">Lý do hoàn tiền</label>
              <input type="text" id="refundReason" name="refundReason" required maxlength="200"
                     placeholder="VD: lần hoàn lúc huỷ đơn bị lỗi kết nối">
            </div>
            <button type="submit" class="btn btn-danger btn-block">
              Hoàn ${ff:money(order.totalAmount)}
            </button>
          </form>
        </div>
      </c:if>

      <c:if test="${not empty order.pickupCode}">
        <div class="card">
          <h3>Mã nhận hàng của đơn</h3>
          <div class="pickup-code"><c:out value="${order.pickupCode}"/></div>
        </div>
      </c:if>
    </div>
  </div>

  <%-- Hoá đơn: ẩn trên màn hình, chỉ hiện khi in. Khổ giấy và bảng màu do khối @media print
       trong main.css lo — cùng một khối đang dùng cho mọi trang có nút in. --%>
  <div class="card receipt print-only">
    <div class="receipt-head">
      <div class="receipt-shop">FAST FOOD</div>
      <div class="small muted">Đặt trước &amp; bán tại quầy</div>
    </div>

    <div class="receipt-meta small">
      <div class="total-line"><span>Số hoá đơn</span><span>#${order.orderId}</span></div>
      <div class="total-line"><span>Thời điểm</span><span>${ff:dateTime(order.createdAt)}</span></div>
      <div class="total-line"><span>Hình thức</span><span>${ff:orderSource(order.orderSource)}</span></div>
      <c:if test="${order.online}">
        <div class="total-line"><span>Giờ hẹn lấy</span><span>${ff:dateTime(order.pickupTime)}</span></div>
      </c:if>
      <div class="total-line">
        <span>Khách hàng</span>
        <span><c:out value="${empty order.customerName ? 'Khách tại quầy' : order.customerName}"/></span>
      </div>
    </div>

    <table class="receipt-items">
      <thead>
        <tr><th scope="col">Món</th><th scope="col" class="center">SL</th><th scope="col" class="num">Thành tiền</th></tr>
      </thead>
      <tbody>
        <c:forEach var="item" items="${order.items}">
          <tr>
            <td>
              <c:out value="${item.productNameSnapshot}"/>
              <div class="small muted">${ff:money(item.unitPrice)}</div>
            </td>
            <td class="center">${item.quantity}</td>
            <td class="num">${ff:money(item.lineTotal)}</td>
          </tr>
        </c:forEach>
      </tbody>
    </table>

    <div class="total-line grand"><span>Tổng cộng</span><span>${ff:money(order.totalAmount)}</span></div>

    <div class="receipt-meta small mt">
      <c:forEach var="p" items="${payments}">
        <div class="total-line">
          <span>${ff:paymentMethod(p.method)}</span>
          <span>${ff:paymentStatus(p.paymentStatus)} · ${ff:money(p.amount)}</span>
        </div>
      </c:forEach>
      <c:if test="${empty payments}">
        <div class="total-line"><span>Thanh toán</span><span>Chưa ghi nhận</span></div>
      </c:if>
    </div>

    <c:if test="${not empty order.pickupCode}">
      <div class="mt">
        <div class="small muted center">Mã nhận hàng</div>
        <div class="pickup-code"><c:out value="${order.pickupCode}"/></div>
      </div>
    </c:if>

    <p class="small muted center mt">Cảm ơn quý khách. Hẹn gặp lại!</p>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
