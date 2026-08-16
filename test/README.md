# Kiểm thử giao diện

Hai bộ test chạy trên ứng dụng thật — Tomcat nhúng nối tới SQL Server thật, không phải
bản mô phỏng. Không cần cài Tomcat: máy chủ được nạp từ thư viện trong kho Maven.

| Tệp | Kiểm tra gì | Số phép thử |
|---|---|---|
| `http-tests.sh` | HTML máy chủ gửi ra: bảng mã, phân quyền, mã trạng thái, dấu hiệu khả năng tiếp cận | 95 |
| `ui-tests.js` | Những thứ chỉ có sau khi JavaScript chạy: vẽ lại thẻ bếp, thu gọn menu, ảnh hỏng, hộp xác nhận, chống bấm trùng | 46 |

Ngoài ra `mvn test` chạy 138 bài ở tầng Java (logic thuần + nối thẳng xuống cơ sở dữ liệu) —
xem [../docs/STRUCTURE.md](../docs/STRUCTURE.md) mục 7.

## Chuẩn bị

```bash
# 1. SQL Server phải đang chạy và đã nạp database/FastFoodPreorder.sql
# 2. Tải máy chủ nhúng về kho Maven (chỉ cần một lần)
mvn org.apache.maven.plugins:maven-dependency-plugin:3.6.1:get \
    -Dartifact=org.apache.tomcat.embed:tomcat-embed-jasper:9.0.89

# 3. Dựng đường dẫn lớp cho máy chủ nhúng (chỉ cần một lần)
mvn -f test/runner-pom.xml dependency:build-classpath -Dmdep.outputFile=test/tomcat-cp.txt

# 4. Biên dịch bộ khởi động
javac -cp "$(cat test/tomcat-cp.txt)" -d test/classes test/Boot.java
```

## Chạy

```bash
# Đóng gói rồi bật máy chủ ở cổng 8081
mvn package -DskipTests
java -cp "test/classes:$(cat test/tomcat-cp.txt)" Boot . 8081 &

# Bộ test qua HTTP
./test/http-tests.sh

# Bộ test trong trình duyệt thật (cần Node và Google Chrome)
cd test && npm install playwright && node ui-tests.js
```

## Lưu ý

Xoá thư mục làm việc của Jasper khi sửa tệp `.jspf`: Tomcat chỉ so ngày sửa của tệp `.jsp`
nên mảnh ghép lúc dịch đổi mà trang không được dịch lại.

```bash
rm -rf /tmp/ff-embed
```

Thư mục này nằm ngoài `pom.xml` của dự án nên không ảnh hưởng tới bản đóng gói WAR.
