<%-- Điều hướng theo Role sau khi vào ứng dụng --%>
<c:choose>
    <c:when test="${empty sessionScope.currentUser}">
        <c:redirect url="/menu"/>
    </c:when>
    <c:when test="${sessionScope.currentUser.roleName eq 'CASHIER'}">
        <c:redirect url="/staff/orders"/>
    </c:when>
    <c:when test="${sessionScope.currentUser.roleName eq 'KITCHEN'}">
        <c:redirect url="/kitchen/queue"/>
    </c:when>
    <c:when test="${sessionScope.currentUser.roleName eq 'ADMIN'}">
        <c:redirect url="/admin/dashboard"/>
    </c:when>
    <c:otherwise>
        <c:redirect url="/menu"/>
    </c:otherwise>
</c:choose>
