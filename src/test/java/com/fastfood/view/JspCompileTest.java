package com.fastfood.view;

import org.apache.jasper.JspC;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
            jspc.setArgs(new String[]{
                    "-d", outDir.toString(),
                    "-webapp", WEBAPP.toAbsolutePath().toString()
            });
            jspc.setCompile(false);
            jspc.setValidateTld(false);
            jspc.setFailOnError(false);
            jspc.setListErrors(true);
            jspc.execute();
        } catch (Exception e) {
            fail("Bo dich JSP bao loi: " + e.getMessage(), e);
        }

        // Mỗi tệp .tag cũng sinh ra một lớp Java, nên đếm cả hai loại mới khớp.
        int can_co = findJsps().size() + findTags().size();
        long sinh_ra;
        try (Stream<Path> walk = Files.walk(outDir)) {
            sinh_ra = walk.filter(p -> p.toString().endsWith(".java")).count();
        }

        assertEquals(can_co, sinh_ra,
                "Co trang khong dich duoc. So trang va the: " + can_co + ", so tep sinh ra: " + sinh_ra
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

    @Test
    @DisplayName("Mảnh .jspf và thẻ .tag nào cũng tự khai báo pageEncoding=\"UTF-8\"")
    void jspfsDeclareUtf8() throws Exception {
        List<String> thieu = new ArrayList<>();
        List<Path> canKiemTra = new ArrayList<>(findJspfs());
        canKiemTra.addAll(findTags());
        for (Path jspf : canKiemTra) {
            String noi_dung = Files.readString(jspf, StandardCharsets.UTF_8);
            if (!noi_dung.contains("pageEncoding=\"UTF-8\"")) {
                thieu.add(WEBAPP.toAbsolutePath().relativize(jspf).toString());
            }
        }

        assertTrue(thieu.isEmpty(),
                "Thieu <%@ page pageEncoding=\"UTF-8\" %> o dong dau. Nhom thuoc tinh trong web.xml "
                        + "chi ap dung cho trang duoc yeu cau truc tiep, khong ap cho tep ghep vao luc "
                        + "dich, nen Jasper doc mang theo ISO-8859-1 va chu tieng Viet trong do ra man "
                        + "hinh thanh ky tu loi: " + thieu);
    }

    private List<Path> findJspfs() throws Exception {
        try (Stream<Path> walk = Files.walk(WEBAPP.toAbsolutePath())) {
            List<Path> list = new ArrayList<>();
            walk.filter(p -> p.toString().endsWith(".jspf")).forEach(list::add);
            return list;
        }
    }

    private List<Path> findTags() throws Exception {
        try (Stream<Path> walk = Files.walk(WEBAPP.toAbsolutePath())) {
            List<Path> list = new ArrayList<>();
            walk.filter(p -> p.toString().endsWith(".tag")).forEach(list::add);
            return list;
        }
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
