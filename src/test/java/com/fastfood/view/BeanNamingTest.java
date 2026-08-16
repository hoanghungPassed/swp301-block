package com.fastfood.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm tên phương thức đọc của {@code model.entity} và {@code model.dto} theo đúng thứ mà trang
 * JSP có thể gọi được.
 * <p>
 * <b>Vì sao cần một bài test cho chuyện đặt tên.</b> Hai loại lỗi dưới đây đều <i>biên dịch
 * trót lọt</i> và chỉ lộ ra trên trình duyệt, nên chúng sống rất lâu:
 * <ul>
 *   <li>{@code hasVariance()} — EL chỉ nhận ra thuộc tính qua {@code getX} hoặc {@code isX}, nên
 *       {@code ${s.hasVariance}} lặng lẽ thành rỗng. Trong {@code c:when} thì rỗng nghĩa là sai,
 *       và cả một nhánh giao diện không bao giờ hiện ra mà không ai báo lỗi gì.</li>
 *   <li>{@code isShort()} — {@code ${s.short}} không phân tích cú pháp được vì {@code short} là
 *       từ khoá. Cái này thì đổ hẳn cả trang lúc chạy.</li>
 * </ul>
 * Cả hai đều đã thật sự xảy ra trong dự án này, ở cùng một lớp và cùng một trang.
 * <p>
 * {@link JspCompileTest} chỉ bắt được loại thứ hai. Loại thứ nhất phải chặn từ phía đặt tên.
 */
@DisplayName("Tên phương thức đọc dùng được trong JSP")
class BeanNamingTest {

    private static final Path SRC = Path.of("src", "main", "java");

    /**
     * Định danh không dùng làm tên thuộc tính EL được. Gồm từ khoá Java mà bộ phân tích EL của
     * Jasper cũng giữ chỗ, và các toán tử dạng chữ của chính EL.
     */
    private static final Set<String> CAM = Set.of(
            "short", "int", "long", "float", "double", "boolean", "char", "byte", "void",
            "class", "new", "instanceof", "true", "false", "null",
            "and", "or", "not", "eq", "ne", "lt", "gt", "le", "ge", "div", "mod", "empty");

    private List<Class<?>> lopHienThi() throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        for (String pkg : List.of("com.fastfood.model.entity", "com.fastfood.model.dto")) {
            Path dir = SRC.resolve(pkg.replace('.', '/'));
            try (Stream<Path> files = Files.list(dir)) {
                for (Path p : files.toList()) {
                    String ten = p.getFileName().toString();
                    if (!ten.endsWith(".java") || ten.equals("package-info.java")) {
                        continue;
                    }
                    classes.add(Class.forName(pkg + "." + ten.substring(0, ten.length() - 5)));
                }
            }
        }
        assertTrue(classes.size() >= 30, "Chi tim thay " + classes.size() + " lop — sai duong dan?");
        return classes;
    }

    private boolean laPhuongThucDoc(Method m) {
        return Modifier.isPublic(m.getModifiers())
                && !Modifier.isStatic(m.getModifiers())
                && m.getParameterCount() == 0
                && m.getDeclaringClass() != Object.class;
    }

    @Nested
    @DisplayName("Quy ước JavaBean")
    class BeanConvention {

        @Test
        @DisplayName("Không phương thức boolean nào đặt tên bắt đầu bằng has — EL không thấy nó")
        void noHasPrefixedBooleans() throws Exception {
            List<String> vi_pham = new ArrayList<>();
            for (Class<?> c : lopHienThi()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (!laPhuongThucDoc(m)) {
                        continue;
                    }
                    boolean tra_ve_boolean = m.getReturnType() == boolean.class
                            || m.getReturnType() == Boolean.class;
                    if (tra_ve_boolean && m.getName().startsWith("has")) {
                        vi_pham.add(c.getSimpleName() + "." + m.getName() + "()");
                    }
                }
            }

            assertTrue(vi_pham.isEmpty(),
                    "Doi ten sang isX de EL nhin thay: " + vi_pham);
        }

        @Test
        @DisplayName("Không tên thuộc tính nào trùng từ khoá — biểu thức EL sẽ không phân tích được")
        void noReservedPropertyNames() throws Exception {
            List<String> vi_pham = new ArrayList<>();
            for (Class<?> c : lopHienThi()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (!laPhuongThucDoc(m)) {
                        continue;
                    }
                    String ten = tenThuocTinh(m);
                    if (ten != null && CAM.contains(ten)) {
                        vi_pham.add(c.getSimpleName() + "." + m.getName() + "() → ${x." + ten + "}");
                    }
                }
            }

            assertTrue(vi_pham.isEmpty(),
                    "Ten thuoc tinh trung tu khoa, JSP se do loi luc chay: " + vi_pham);
        }

        /** Tên thuộc tính EL suy ra từ tên phương thức, hoặc null nếu không phải phương thức đọc. */
        private String tenThuocTinh(Method m) {
            String ten = m.getName();
            String con_lai;
            if (ten.startsWith("get") && ten.length() > 3) {
                con_lai = ten.substring(3);
            } else if (ten.startsWith("is") && ten.length() > 2
                    && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
                con_lai = ten.substring(2);
            } else {
                return null;
            }
            return Character.toLowerCase(con_lai.charAt(0)) + con_lai.substring(1);
        }
    }
}
