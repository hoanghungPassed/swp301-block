<%@ tag pageEncoding="UTF-8" body-content="empty" %>
<%@ taglib prefix="c"  uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="ff" uri="http://fastfood.com/functions" %>

<%--
  Thanh phân trang dùng chung cho mọi màn hình.

  Một trang có thể đặt nhiều thanh: mỗi thanh mang tên tham số riêng qua thuộc tính
  "pageParam", nên đổi trang bảng này không làm mất vị trí của bảng kia — link tự giữ lại
  toàn bộ tham số đang có trên URL, chỉ thay đúng số trang của mình.

  Ví dụ:
    <ui:pager page="${pageData}" label="đơn" />
    <ui:pager page="${issuePage}" pageParam="issuePage" anchor="su-co" label="sự cố" />

  Chú ý: không đặt tên thuộc tính là "param" — trong EL, ${param} luôn là map tham số
  của request nên tên đó sẽ bị nuốt mất.
--%>

<%@ attribute name="page"   required="true"  type="java.lang.Object"
              description="Đối tượng Dtos.Page cần hiển thị" %>
<%@ attribute name="pageParam" required="false"
              description="Tên tham số số trang trên URL, mặc định là page" %>
<%@ attribute name="anchor" required="false"
              description="Id của khối danh sách, để bấm đổi trang xong quay lại đúng chỗ" %>
<%@ attribute name="label"  required="false"
              description="Danh từ đếm bản ghi, ví dụ món, đơn, sự cố" %>
<%@ attribute name="omit"   required="false"
              description="Các tham số khác cần bỏ khỏi link, ngăn nhau bằng dấu phẩy" %>

<c:if test="${not empty page}">
  <c:set var="pgParam" value="${empty pageParam ? 'page' : pageParam}" />
  <c:set var="pgLabel" value="${empty label ? 'bản ghi' : label}" />
  <c:set var="pgDrop"  value="${empty omit ? 'edit,editNote,editPrep,editFav,editTarget,editReview' : omit}" />
  <c:set var="pgHash"  value="${empty anchor ? '' : '#'.concat(anchor)}" />
  <c:set var="pgKeep"  value="${ff:pageQuery(pageContext.request, pgParam.concat(',').concat(pgDrop))}" />
  <c:set var="pgBase"  value="?${fn:escapeXml(empty pgKeep ? '' : pgKeep.concat('&'))}${pgParam}=" />

  <nav class="pager" aria-label="Chuyển trang">
    <p class="pager-count">
      <c:choose>
        <c:when test="${page.totalItems == 0}">Không có ${pgLabel} nào.</c:when>
        <%-- Số trang gõ tay vượt quá cuối danh sách: nói thẳng ra và chỉ đường về, chứ
             để nguyên thì màn hình trống mà thanh đếm lại khoe có mấy chục bản ghi. --%>
        <c:when test="${page.emptyPage}">
          Trang ${page.pageNo} không có ${pgLabel} nào —
          <a href="${pgBase}${page.totalPages}${pgHash}">về trang ${page.totalPages}</a>
          trong tổng số <strong>${page.totalItems}</strong> ${pgLabel}.
        </c:when>
        <c:otherwise>
          Đang xem <strong>${page.firstIndex}–${page.lastIndex}</strong>
          trong <strong>${page.totalItems}</strong> ${pgLabel}
          <c:if test="${page.paged}">
            · trang ${page.pageNo}/${page.totalPages}
          </c:if>
        </c:otherwise>
      </c:choose>
    </p>

    <c:if test="${page.paged}">
      <c:set var="from" value="${page.pageNo - 2 < 1 ? 1 : page.pageNo - 2}" />
      <c:set var="to" value="${page.pageNo + 2 > page.totalPages
                               ? page.totalPages : page.pageNo + 2}" />

      <div class="pager-links">
        <c:choose>
          <c:when test="${page.first}">
            <span class="pager-link disabled" aria-hidden="true">← Trước</span>
          </c:when>
          <c:otherwise>
            <a class="pager-link" rel="prev" href="${pgBase}${page.prevPage}${pgHash}">← Trước</a>
          </c:otherwise>
        </c:choose>

        <c:if test="${from > 1}">
          <a class="pager-link" href="${pgBase}1${pgHash}" aria-label="Trang 1">1</a>
          <c:if test="${from > 2}"><span class="pager-gap" aria-hidden="true">…</span></c:if>
        </c:if>

        <c:forEach var="i" begin="${from}" end="${to}">
          <c:choose>
            <c:when test="${i == page.pageNo}">
              <span class="pager-link current" aria-current="page"
                    aria-label="Trang ${i}, trang hiện tại">${i}</span>
            </c:when>
            <c:otherwise>
              <a class="pager-link" href="${pgBase}${i}${pgHash}" aria-label="Trang ${i}">${i}</a>
            </c:otherwise>
          </c:choose>
        </c:forEach>

        <c:if test="${to < page.totalPages}">
          <c:if test="${to < page.totalPages - 1}">
            <span class="pager-gap" aria-hidden="true">…</span>
          </c:if>
          <a class="pager-link" href="${pgBase}${page.totalPages}${pgHash}"
             aria-label="Trang ${page.totalPages}">${page.totalPages}</a>
        </c:if>

        <c:choose>
          <c:when test="${page.last}">
            <span class="pager-link disabled" aria-hidden="true">Sau →</span>
          </c:when>
          <c:otherwise>
            <a class="pager-link" rel="next" href="${pgBase}${page.nextPage}${pgHash}">Sau →</a>
          </c:otherwise>
        </c:choose>
      </div>
    </c:if>
  </nav>
</c:if>
