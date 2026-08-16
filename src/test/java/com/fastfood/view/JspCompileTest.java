package com.fastfood.view;

import org.apache.jasper.JspC;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Dịch thử toàn bộ trang JSP bằng chính bộ dịch của Tomcat.
 * <p>
 * <b>Vì sao cần.</b> JSP là tầng duy nhất của dự án không được biên dịch trong {@code mvn test}:
 * mã Java sai thì Maven báo đỏ ngay, còn một thẻ quên đóng, một hàm thẻ gõ sai tên hay một tệp
 * {@code jspf} chèn nhầm đường dẫn chỉ lộ ra khi có người mở đúng trang đó trên trình duyệt.
 * Trang càng ít dùng thì lỗi càng sống lâu.
 * <p>
 * <b>Bài test này bắt được gì:</b> cú pháp JSP, thẻ không đóng, thẻ hay hàm không khai báo trong
 * {@code taglib}, tệp chèn không tồn tại, và biểu thức EL viết sai cú pháp.
 * <p>
 * <b>Không bắt được gì:</b> tên thuộc tính EL trỏ vào phương thức không tồn tại
 * ({@code ${hold.khongCoThat}}). EL tra cứu lúc chạy nên không bộ dịch nào biết trước — chốt
 * chặn cho loại lỗi đó vẫn là quy ước đặt tên theo JavaBean ở tầng entity.
 * <p>
 * Đây là bài {@code *Test} chứ không phải {@code *IT}: nó không cần cơ sở dữ liệu, chạy được
 * trên mọi máy.
 */
@DisplayName("Toàn bộ trang JSP dịch được")
class JspCompileTest {

    private static final Path WEBAPP = Path.of("src", "main", "webapp");

    @Test
    @DisplayName("Không trang nào hỏng cú pháp, thiếu thẻ hay chèn nhầm tệp")
    void allJspsTranslate() throws Exception {
        assertTrue(Files.isDirectory(WEBAPP), "Khong thay thu muc webapp: " + WEBAPP.toAbsolutePath());
        Path outDir = Files.createTempDirectory("jspc-");

        try {
            JspC jspc = new JspC();
            /* Truyền bằng tham số dòng lệnh chứ không gọi từng setter: chỉ cờ -webapp mới bảo
               JspC tự quét mọi trang dưới thư mục, còn setUriroot đơn thuần thì nó ngồi chờ một
               danh sách trang mà lớp này không có cách nào đưa vào (setPages không công khai). */
            jspc.setArgs(new String[]{
                    "-d", outDir.toString(),
                    "-webapp", WEBAPP.toAbsolutePath().toString()
            });
            // Chỉ dịch JSP sang Java, không gọi javac: phần Java đã có mvn compile lo, còn thứ
            // cần bắt ở đây nằm hết ở bước dịch.
            jspc.setCompile(false);
            /* Không kiểm lược đồ của tệp .tld: nó khai báo lược đồ theo đường dẫn mạng, và một
               bài test chỉ chạy được khi có mạng thì không phải bài test. Cấu trúc tệp .tld vẫn
               được kiểm gián tiếp — hàm thẻ gõ sai tên sẽ làm bước dịch hỏng. */
            jspc.setValidateTld(false);
            // Dừng ở lỗi đầu tiên thì mỗi lượt chạy chỉ lộ một trang; gom hết rồi báo một lần
            // để sửa được cả loạt.
            jspc.setFailOnError(false);
            jspc.setListErrors(true);
            jspc.execute();
        } catch (Exception e) {
            fail("Bo dich JSP bao loi: " + e.getMessage(), e);
        }

        // JspC với failOnError=false không ném ngoại lệ, nên bằng chứng duy nhất là số tệp .java
        // sinh ra có khớp số trang hay không.
        List<Path> jsps = findJsps();
        long sinh_ra;
        try (Stream<Path> walk = Files.walk(outDir)) {
            sinh_ra = walk.filter(p -> p.toString().endsWith(".java")).count();
        }

        assertEquals(jsps.size(), sinh_ra,
                "Co trang khong dich duoc. So trang: " + jsps.size() + ", so tep sinh ra: " + sinh_ra
                        + ". Xem thong bao cua Jasper o phia tren.");

        xoaDe(outDir);
    }

    @Test
    @DisplayName("Mọi trang JSP đều nằm trong WEB-INF, trừ trang chủ")
    void jspsLiveUnderWebInf() throws Exception {
        List<String> ngoai_web_inf = new ArrayList<>();
        for (Path jsp : findJsps()) {
            String duong_dan = WEBAPP.toAbsolutePath().relativize(jsp).toString();
            if (!duong_dan.startsWith("WEB-INF") && !duong_dan.equals("index.jsp")) {
                ngoai_web_inf.add(duong_dan);
            }
        }

        assertTrue(ngoai_web_inf.isEmpty(),
                "Trang nam ngoai WEB-INF mo thang tu trinh duyet duoc, bo qua ca chuoi bo loc: "
                        + ngoai_web_inf);
    }

    private List<Path> findJsps() throws Exception {
        try (Stream<Path> walk = Files.walk(WEBAPP.toAbsolutePath())) {
            List<Path> list = new ArrayList<>();
            walk.filter(p -> p.toString().endsWith(".jsp")).forEach(list::add);
            return list;
        }
    }

    private void xoaDe(Path dir) throws Exception {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }
}
