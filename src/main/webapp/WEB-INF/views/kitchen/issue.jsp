<c:set var="pageTitle" value="Sự cố bếp" /><c:set var="nav" value="issue" />
<%@ include file="/WEB-INF/views/layout/page-start.jspf" %>
  <div class="page-head">
    <h1>Sự cố bếp</h1>
    <p>Ghi nhận sự cố không làm món quay về hàng chờ — món vẫn thuộc về người đang làm.</p>
  </div>

  <%@ include file="/WEB-INF/views/layout/flash.jspf" %>

  <div class="grid grid-side">
    <div>
      <div class="card pad0 table-wrap">
        <div class="card-head"><h2>Đang mở (${fn:length(openIssues)})</h2></div>
        <table>
          <thead><tr><th scope="col">Món</th><th scope="col">Đơn</th><th scope="col">Loại</th><th scope="col">Mô tả</th><th scope="col">Người báo</th><th scope="col"><span class="visually-hidden">Thao tác</span></th></tr></thead>
          <tbody>
            <c:forEach var="i" items="${openIssues}">
              <tr>
                <%-- Tên món dẫn thẳng tới chi tiết món: đọc một dòng sự cố xong, việc tiếp theo
                     gần như luôn là mở món đó ra xem đang ở trạng thái nào. --%>
                <td><a href="${ctx}/kitchen/item?id=${i.orderItemId}"><c:out value="${i.productName}"/></a></td>
                <td>#${i.orderId}</td>
                <td><span class="tag tag-red">${ff:issueType(i.issueType)}</span></td>
                <td class="small"><c:out value="${i.description}"/></td>
                <td class="small muted"><c:out value="${i.createdByName}"/><br>${ff:time(i.createdAt)}</td>
                <td class="center">
                  <form method="post" action="${ctx}/kitchen/issue" class="inline-form">
                    <input type="hidden" name="_csrf" value="${csrfToken}">
                    <input type="hidden" name="action" value="resolve">
                    <input type="hidden" name="issueId" value="${i.issueId}">
                    <button type="submit" class="btn btn-sm btn-green">Đã xử lý</button>
                  </form>
                  <%-- Sửa và thu hồi chỉ hiện với người đã báo. Ẩn nút ở đây là để bảng gọn;
                       quyền thật do KitchenService.requireOwnOpenIssue quyết định. --%>
                  <c:if test="${i.createdBy eq me.userId}">
                    <a class="btn btn-sm" href="${ctx}/kitchen/issue?edit=${i.issueId}">Sửa</a>
                    <form method="post" action="${ctx}/kitchen/issue" class="inline-form"
                          data-confirm="Thu hồi sự cố báo nhầm này? Bản ghi vẫn được giữ trong nhật ký.">
                      <input type="hidden" name="_csrf" value="${csrfToken}">
                      <input type="hidden" name="action" value="cancel">
                      <input type="hidden" name="issueId" value="${i.issueId}">
                      <button type="submit" class="btn btn-sm btn-danger">Thu hồi</button>
                    </form>
                  </c:if>
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
        <div class="card-head"><h2>Đã khép lại gần đây</h2></div>
        <table>
          <thead><tr><th scope="col">Thời điểm</th><th scope="col">Món</th><th scope="col">Loại</th><th scope="col">Trạng thái</th></tr></thead>
          <tbody>
            <c:forEach var="i" items="${closedIssues}">
              <tr>
                <td class="small muted">${ff:dateTime(i.createdAt)}</td>
                <td><a href="${ctx}/kitchen/item?id=${i.orderItemId}"><c:out value="${i.productName}"/></a></td>
                <td>${ff:issueType(i.issueType)}</td>
                <%-- Tách hai đường ra chứ không gộp thành "đã đóng": sự cố có thật đã xử lý
                     xong và sự cố báo nhầm là hai chuyện khác hẳn khi đọc lại về sau. --%>
                <td><span class="tag ${ff:issueStatusTag(i.status)}">${ff:issueStatus(i.status)}</span></td>
              </tr>
            </c:forEach>
            <c:if test="${empty closedIssues}">
              <tr><td colspan="4" class="center muted cell-empty">Chưa có sự cố nào khép lại gần đây.</td></tr>
            </c:if>
          </tbody>
        </table>
      </div>
    </div>

    <c:choose>
    <%-- Chỉ cho sửa sự cố còn mở và do chính mình báo. Vào thẳng ?edit= của sự cố người khác
         thì hiện lời nhắc chứ không hiện biểu mẫu — người dùng biết vì sao không sửa được,
         thay vì gõ xong mới nhận lỗi từ tầng nghiệp vụ. --%>
    <c:when test="${not empty editing and editing.open and editing.createdBy eq me.userId}">
    <div class="card">
      <h2>Sửa sự cố #${editing.issueId}</h2>
      <p class="small muted">
        <c:out value="${editing.productName}"/> · đơn #${editing.orderId} ·
        ${ff:issueType(editing.issueType)}
      </p>
      <form method="post" action="${ctx}/kitchen/issue">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <input type="hidden" name="action" value="update">
        <input type="hidden" name="issueId" value="${editing.issueId}">
        <div class="field">
          <label for="editDescription">Mô tả</label>
          <textarea id="editDescription" name="description"><c:out value="${editing.description}"/></textarea>
          <p class="small muted mt">
            Chỉ sửa được mô tả. Báo sai loại sự cố thì thu hồi rồi báo lại — đổi loại kéo theo
            việc bật hay tắt món trên thực đơn.
          </p>
        </div>
        <button type="submit" class="btn btn-primary btn-block">Lưu mô tả</button>
      </form>
      <a class="btn btn-block mt" href="${ctx}/kitchen/issue">Huỷ sửa</a>
    </div>
    </c:when>

    <c:otherwise>
    <%-- Bọc chung một khối: .grid-side chia đúng hai cột, để hai thẻ card rời nhau thì thẻ
         thứ hai rơi xuống hàng mới và cột trái bị kéo hẹp lại. --%>
    <div>
    <c:if test="${not empty editing}">
      <div class="card">
        <p class="small muted">
          Sự cố #${editing.issueId} không sửa được: chỉ người đã báo mới sửa được, và chỉ khi
          sự cố còn đang mở.
        </p>
      </div>
    </c:if>
    <div class="card">
      <h2>Báo sự cố mới</h2>
      <%-- Bếp rỗng thì không có món nào để báo. Hiện lời nhắc thay vì một ô chọn trống:
           ô chọn rỗng kèm required chỉ dẫn tới một biểu mẫu bấm gửi mãi không đi. --%>
      <c:choose>
      <c:when test="${empty kitchenItems}">
        <p class="small muted">
          Hiện không có món nào trong bếp. Sự cố luôn gắn với một món cụ thể, nên chỉ báo được
          khi có món đang chờ làm, đang làm dở, hoặc đã xong mà chưa ra quầy.
        </p>
      </c:when>
      <c:otherwise>
      <form method="post" action="${ctx}/kitchen/issue">
        <input type="hidden" name="_csrf" value="${csrfToken}">
        <div class="field">
          <label for="orderItemId">Món gặp sự cố</label>
          <%-- Ô chọn chứ không phải ô số như trước: mã món là con số nội bộ, không có trên
               phiếu hay trên món, nên bắt đầu bếp gõ tay là bắt họ nhớ một thứ không nhìn
               thấy được. Danh sách gồm cả món của người khác — sự cố là chuyện của cả bếp. --%>
          <select id="orderItemId" name="orderItemId" required>
            <c:forEach var="v" items="${kitchenItems}">
              <option value="${v.item.orderItemId}"
                      ${v.item.orderItemId eq orderItemId ? 'selected' : ''}>
                Đơn #${v.item.orderId} · <c:out value="${v.item.productNameSnapshot}"/>
                ×${v.item.quantity} — ${ff:itemStatus(v.item.itemStatus)}<c:if
                  test="${not empty v.item.assignedToName}"> · <c:out value="${v.item.assignedToName}"/></c:if>
              </option>
            </c:forEach>
          </select>
          <p class="small muted mt">Món đang chờ làm, đang làm dở, hoặc đã xong mà chưa ra quầy.</p>
        </div>
        <div class="field">
          <label for="issueType">Loại sự cố</label>
          <select id="issueType" name="issueType" required>
            <option value="OUT_OF_STOCK">Hết nguyên liệu</option>
            <option value="QUALITY">Chất lượng không đạt</option>
            <option value="REMAKE">Phải làm lại</option>
            <option value="OTHER">Khác</option>
          </select>
          <p class="small muted mt">
            Chọn <strong>Hết nguyên liệu</strong> sẽ tắt luôn món đó trên thực đơn để khách
            không đặt tiếp. Quản trị viên bật lại khi có hàng.
          </p>
        </div>
        <div class="field">
          <label for="description">Mô tả</label>
          <textarea id="description" name="description" placeholder="Mô tả ngắn gọn..."></textarea>
        </div>
        <button type="submit" class="btn btn-primary btn-block">Ghi nhận sự cố</button>
      </form>
      </c:otherwise>
      </c:choose>
    </div>
    </div>
    </c:otherwise>
    </c:choose>
  </div>
<%@ include file="/WEB-INF/views/layout/page-end.jspf" %>
