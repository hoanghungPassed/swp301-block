#!/bin/bash
# Bộ kiểm thử giao diện chạy trên ứng dụng thật (Tomcat nhúng + SQL Server thật).
# Dùng: ./run-tests.sh [base-url]

BASE="${1:-http://localhost:8081}"
JAR=$(mktemp -d)
PASS=0; FAIL=0
declare -a FAILURES

ok()   { PASS=$((PASS+1)); printf "  \033[32mOK\033[0m   %s\n" "$1"; }
bad()  { FAIL=$((FAIL+1)); FAILURES+=("$1"); printf "  \033[31mFAIL\033[0m %s\n" "$1"; }

# has <mô tả> <url> <chuỗi phải có>
has() {
  local desc="$1" url="$2" needle="$3" jar="${4:-$JAR/anon.txt}"
  if curl -s -b "$jar" -c "$jar" "$BASE$url" | grep -qF -- "$needle"; then ok "$desc"; else bad "$desc"; fi
}

# hasnt <mô tả> <url> <chuỗi không được có>
hasnt() {
  local desc="$1" url="$2" needle="$3" jar="${4:-$JAR/anon.txt}"
  if curl -s -b "$jar" -c "$jar" "$BASE$url" | grep -qF -- "$needle"; then bad "$desc"; else ok "$desc"; fi
}

# code <mô tả> <url> <mã mong đợi> [cookie jar]
code() {
  local desc="$1" url="$2" want="$3" jar="${4:-$JAR/anon.txt}"
  local got
  got=$(curl -s -o /dev/null -w '%{http_code}' -b "$jar" -c "$jar" "$BASE$url")
  if [ "$got" = "$want" ]; then ok "$desc ($got)"; else bad "$desc (mong $want, nhan $got)"; fi
}

login() {
  local jar="$JAR/$1.txt" email="$2"
  curl -s -o /dev/null -c "$jar" -b "$jar" -L \
       --data-urlencode "email=$email" --data-urlencode "password=123456" \
       "$BASE/login"
  if curl -s -b "$jar" -c "$jar" "$BASE/profile" | grep -qF 'name="fullName"'; then
    ok "Đăng nhập $1"
  else
    bad "Đăng nhập $1"
  fi
}

echo "════ 1. Bảng mã tiếng Việt (lỗi mảnh .jspf bị đọc theo ISO-8859-1) ════"
has "Tiêu đề trang đọc được biến pageTitle"        /menu '<title>Thực đơn · Fast Food</title>'
hasnt "Tiêu đề không còn là Fast Food · Fast Food" /menu '<title>Fast Food · Fast Food</title>'
has "Chữ trong header.jspf đúng bảng mã"           /menu '>Thực đơn</a>'
has "Chữ trong footer.jspf đúng bảng mã"           /menu 'Đồ án SWP301'
has "Chữ trong page-end.jspf đúng bảng mã"         /menu 'Xác nhận'
hasnt "Không còn dấu hiệu mã hoá hai lần"          /menu 'Ã'

echo
echo "════ 2. Điều hướng và khung trang ════"
has "Mục điều hướng đang mở có aria-current"  /menu 'aria-current="page"'
has "Có liên kết nhảy tới nội dung"           /menu 'class="skip-link"'
has "Có nút thu gọn menu, ẩn sẵn"             /menu 'id="nav-toggle"'
has "Thẻ main có id để skip-link trỏ tới"     /menu 'id="main"'
has "Có hộp xác nhận dùng chung"              /menu 'id="confirm-dialog"'

echo
echo "════ 3. Tệp tĩnh và chống đệm ════"
code "Tệp CSS tải được"  /assets/css/main.css 200
code "Tệp JS tải được"   /assets/js/app.js 200
has  "CSS có tem phiên bản"  /menu 'main.css?v='
has  "JS nạp kèm defer"      /menu 'app.js?v='
has  "Có biểu tượng trang"   /menu 'rel="icon"'
has  "Có mô tả trang"        /menu 'name="description"'

echo
echo "════ 4. Ảnh món lấy từ link ngoài ════"
has "Thực đơn dùng thẻ img thật"        /menu '<img class="thumb thumb-img"'
has "Ảnh trỏ tới địa chỉ ngoài"         /menu 'src="https://placehold.co'
has "Ảnh có tải trễ"                    /menu 'loading="lazy"'
has "Ảnh có dữ liệu dự phòng khi hỏng"  /menu 'data-fallback'

echo
echo "════ 5. Khả năng tiếp cận ════"
hasnt "Không còn onclick/onsubmit nội tuyến" /menu 'onsubmit='
hasnt "Không còn confirm() của trình duyệt"  /menu 'confirm('
hasnt "Không còn style nội tuyến"            /menu 'style="'

echo
echo "════ 6. Khách hàng ════"
login cus "customer1@gmail.com"
# /menu không có bảng nào, nên phải kiểm tra scope ở trang thật sự có bảng.
has  "Cột tiêu đề bảng có scope"          /order/history 'scope="col"' "$JAR/cus.txt"
has  "Cột thao tác có nhãn cho trình đọc" /order/history 'visually-hidden' "$JAR/cus.txt"
has  "Ô bảng có nhãn để xếp thẻ dọc"      /order/history 'data-label=' "$JAR/cus.txt"
code "Giỏ hàng"        /cart 200 "$JAR/cus.txt"
code "Lịch sử đơn"     /order/history 200 "$JAR/cus.txt"
code "Tài khoản"       /profile 200 "$JAR/cus.txt"
code "Chi tiết món"    /product/detail?id=1 200 "$JAR/cus.txt"
# Bảng giỏ hàng chỉ được vẽ khi giỏ có món, nên phải thêm món thật trước khi kiểm tra —
# đồng thời cũng là phép thử cho chính nút "Thêm vào giỏ" ngoài thực đơn.
curl -s -o /dev/null -b "$JAR/cus.txt" -c "$JAR/cus.txt" -L \
     -d "action=add&productId=1&quantity=1&returnTo=/cart" "$BASE/cart"
hasnt "Thêm món xong thì giỏ không còn trống" /cart 'Giỏ hàng đang trống' "$JAR/cus.txt"
has  "Giỏ hàng chuyển thẻ trên màn nhỏ"  /cart 'table-cards' "$JAR/cus.txt"
has  "Ô số lượng đủ lớn để chạm"          /cart 'class="qty-input"' "$JAR/cus.txt"
has  "Ô giỏ hàng có nhãn xếp thẻ dọc"     /cart 'data-label="Thành tiền"' "$JAR/cus.txt"

echo
echo "════ 7. Bếp — màn hình KDS ════"
login kit "kitchen1@fastfood.vn"
code "Hàng chờ"            /kitchen/queue 200 "$JAR/kit.txt"
code "Sự cố"               /kitchen/issue 200 "$JAR/kit.txt"
code "Đã hoàn thành"       /kitchen/history 200 "$JAR/kit.txt"
# Việc của tôi và món chờ đưa ra quầy nay là hai khối trên chính trang hàng chờ
has "Có khối việc đang làm"        /kitchen/queue 'id="kds-mytasks-count"' "$JAR/kit.txt"
has "Có khối chờ bàn giao ra quầy" /kitchen/queue 'id="kds-handover-count"' "$JAR/kit.txt"
# Địa chỉ cũ đã bỏ — còn sống nghĩa là servlet chưa xoá hết, hoặc đã lặng lẽ quay lại
code "Địa chỉ cũ đã bỏ hẳn"        /kitchen/my-tasks 404 "$JAR/kit.txt"
has "Có dấu hiệu tự cập nhật"      /kitchen/queue 'id="kds-watch"' "$JAR/kit.txt"
has "Có khuôn dựng thẻ"            /kitchen/queue 'id="kds-card-template"' "$JAR/kit.txt"
has "Có lưới thẻ để cập nhật"      /kitchen/queue 'id="kds-grid"' "$JAR/kit.txt"
has "Có cảnh báo mất kết nối"      /kitchen/queue 'id="kds-offline"' "$JAR/kit.txt"
has "Có cảnh báo khi tắt JS"       /kitchen/queue '<noscript>' "$JAR/kit.txt"
has "Nút bếp đủ lớn để chạm"       /kitchen/queue 'btn-block touch' "$JAR/kit.txt"
code "API hàng chờ"        /api/kds/queue 200 "$JAR/kit.txt"
has "API trả về số sự cố đang mở"  /api/kds/queue 'openIssueCount' "$JAR/kit.txt"
has "API trả về cả danh sách"      /api/kds/queue '"queue"' "$JAR/kit.txt"

echo
echo "════ 8. Thu ngân ════"
login sta "cashier1@fastfood.vn"
code "Bán tại quầy"     /staff/pos 200 "$JAR/sta.txt"
code "Đơn hàng"         /staff/orders 200 "$JAR/sta.txt"
code "Quầy giao nhận"   /staff/counter 200 "$JAR/sta.txt"
code "Lịch sử"          /staff/history 200 "$JAR/sta.txt"
# Ba khối cùng trả lời "món của đơn này đang ở đâu"; sự cố bếp không còn trang riêng
has "Quầy có khối bếp vừa bàn giao" /staff/counter 'Bếp vừa bàn giao' "$JAR/sta.txt"
has "Quầy có khối chờ khách tới lấy" /staff/counter 'Chờ khách tới lấy' "$JAR/sta.txt"
has "Quầy có khối sự cố bếp"        /staff/counter 'Sự cố bếp đang mở' "$JAR/sta.txt"
code "Địa chỉ cũ đã bỏ hẳn"         /staff/issues 404 "$JAR/sta.txt"
# Nút thu tiền và ô số lượng chỉ hiện khi phiếu tính tiền đã có món, nên phải thêm món
# thật vào phiếu rồi mới kiểm tra — đồng thời cũng là phép thử cho chính luồng bán hàng.
curl -s -o /dev/null -b "$JAR/sta.txt" -c "$JAR/sta.txt" -L \
     -d "action=add&productId=1" "$BASE/staff/pos"
has "Thêm món vào phiếu thì hiện nút thu tiền"  /staff/pos 'btn-green btn-block touch' "$JAR/sta.txt"
has "Nút quét mã QR đủ lớn để chạm"             /staff/pos 'btn-blue btn-block touch' "$JAR/sta.txt"
has "Ô số lượng tự gửi biểu mẫu"                 /staff/pos 'data-autosubmit' "$JAR/sta.txt"
curl -s -o /dev/null -b "$JAR/sta.txt" -c "$JAR/sta.txt" -L \
     -d "action=clear" "$BASE/staff/pos"

echo
echo "════ 9. Quản trị ════"
login adm "admin@fastfood.vn"
code "Tổng quan"  /admin/dashboard 200 "$JAR/adm.txt"
code "Món ăn"     /admin/products 200 "$JAR/adm.txt"
code "Nhóm món"   /admin/categories 200 "$JAR/adm.txt"
code "Tài khoản"  /admin/users 200 "$JAR/adm.txt"
code "Nhật ký"    /admin/audit 200 "$JAR/adm.txt"
has "Ô nhập đường dẫn ảnh kiểu url"  /admin/products 'type="url"' "$JAR/adm.txt"
has "Có giới hạn 255 ký tự"          /admin/products 'maxlength="255"' "$JAR/adm.txt"
has "Có ô xem trước ảnh"             /admin/products 'id="imageUrlPreview"' "$JAR/adm.txt"
has "Biểu đồ có nhãn cho trình đọc"  /admin/dashboard 'role="img"' "$JAR/adm.txt"
has "Xác nhận đặt lại mật khẩu"      /admin/users 'data-confirm' "$JAR/adm.txt"


echo
echo "════ 10. Phân trang ════"

# Các dòng dữ liệu thật của bảng đầu tiên trên trang, đã bỏ hết thẻ HTML.
rows() {
  curl -s -b "$2" -c "$2" "$BASE$1" \
    | tr -d '\n' \
    | sed 's/.*<tbody>//; s|</tbody>.*||' \
    | sed 's|</tr>|\n|g' \
    | sed 's/<[^>]*>//g' \
    | sed 's/[[:space:]][[:space:]]*/ /g; s/^ //; s/ $//' \
    | grep -v '^$'
}

has "Nhật ký: dòng đếm ở trang 1"  "/admin/audit" 'Đang xem <strong>1–20</strong>' "$JAR/adm.txt"
has "Nhật ký: có thanh chuyển trang" "/admin/audit" 'class="pager"' "$JAR/adm.txt"
has "Trang hiện tại được đánh dấu"  "/admin/audit" 'aria-current="page"' "$JAR/adm.txt"
has "Trang 1 không có nút Trước"    "/admin/audit" 'pager-link disabled' "$JAR/adm.txt"
has "Nhật ký: trang 2 đếm tiếp"     "/admin/audit?page=2" 'Đang xem <strong>21–' "$JAR/adm.txt"
has "Trang 2 có liên kết quay lại"  "/admin/audit?page=2" 'rel="prev"' "$JAR/adm.txt"

# Số trang tính từ COUNT phải khớp số dòng thật lấy được, và hai trang không được
# trùng dòng nào — đây chính là chỗ hỏng nếu ORDER BY không xác định.
P1=$(rows "/admin/audit" "$JAR/adm.txt")
P2=$(rows "/admin/audit?page=2" "$JAR/adm.txt")
N1=$(printf '%s\n' "$P1" | grep -c .)
N2=$(printf '%s\n' "$P2" | grep -c .)
DUP=$(comm -12 <(printf '%s\n' "$P1" | sort) <(printf '%s\n' "$P2" | sort) | grep -c .)
if [ "$N1" = "20" ]; then ok "Trang 1 đúng 20 dòng"; else bad "Trang 1 có $N1 dòng, mong 20"; fi
# Trang 2 đầy hay chỉ còn phần dư đều đúng — tuỳ số dòng nhật ký có sẵn. Ràng buộc "phải
# nhỏ hơn 20" của bản trước là ngầm giả định nhật ký chỉ đủ hai trang, nên nó báo đỏ ngay
# khi dữ liệu mẫu dày lên, trong khi phân trang vẫn chạy đúng.
if [ "$N2" -gt 0 ] && [ "$N2" -le 20 ]; then ok "Trang 2 có $N2 dòng"; else bad "Trang 2 có $N2 dòng, mong 1–20"; fi
if [ "$DUP" = "0" ]; then ok "Hai trang không lặp dòng nào"; else bad "$DUP dòng xuất hiện ở cả hai trang"; fi

# Địa chỉ do người dùng gõ tay không được làm sập trang hay lọt số âm vào OFFSET.
has  "page=0 quay về trang 1"       "/admin/audit?page=0" 'Đang xem <strong>1–' "$JAR/adm.txt"
has  "page=-5 quay về trang 1"      "/admin/audit?page=-5" 'Đang xem <strong>1–' "$JAR/adm.txt"
code "page=999 không làm sập trang" "/admin/audit?page=999" 200 "$JAR/adm.txt"
code "page=abc không làm sập trang" "/admin/audit?page=abc" 200 "$JAR/adm.txt"

# Bấm sang trang khác phải giữ nguyên bộ lọc đang áp dụng. Dùng bộ lọc theo ngày vì nó
# vẫn để lại đủ hai trang; lọc theo loại đối tượng thì kết quả gọn trong một trang và
# thanh chuyển trang không hiện ra để mà kiểm tra.
has "Liên kết chuyển trang mang theo bộ lọc" \
    "/admin/audit?from=2020-01-01T00:00" 'href="?from=2020-01-01T00%3A00&amp;page=' "$JAR/adm.txt"
has "Dấu & trong liên kết được thoát đúng" \
    "/admin/audit?from=2020-01-01T00:00" '&amp;page=' "$JAR/adm.txt"

has "Lịch sử thu ngân có dòng đếm"  /staff/history 'Đang xem <strong>' "$JAR/sta.txt"
has "Đơn của khách có dòng đếm"     /order/history 'Đang xem <strong>' "$JAR/cus.txt"
has "Lịch sử bếp có dòng đếm"       /kitchen/history 'Đang xem <strong>' "$JAR/kit.txt"

echo
echo "════ 11. Phân quyền vẫn nguyên vẹn ════"
code "Khách không vào được trang bếp"    /kitchen/queue 403 "$JAR/cus.txt"
code "Bếp không vào được trang quản trị" /admin/users 403 "$JAR/kit.txt"

echo
echo "════════════════════════════════════════"
printf "Đạt: \033[32m%d\033[0m   Hỏng: \033[31m%d\033[0m\n" "$PASS" "$FAIL"
if [ "$FAIL" -gt 0 ]; then
  echo "Các mục hỏng:"
  for f in "${FAILURES[@]}"; do echo "  · $f"; done
fi
rm -rf "$JAR"
exit $([ "$FAIL" -eq 0 ] && echo 0 || echo 1)
