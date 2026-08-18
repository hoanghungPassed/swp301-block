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

@DisplayName("Tên phương thức đọc dùng được trong JSP")
class BeanNamingTest {

    private static final Path SRC = Path.of("src", "main", "java");

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
                    themCaLopLong(Class.forName(pkg + "." + ten.substring(0, ten.length() - 5)), classes);
                }
            }
        }
        assertTrue(classes.size() >= 30, "Chi tim thay " + classes.size() + " lop — sai duong dan?");
        return classes;
    }

    private void themCaLopLong(Class<?> c, List<Class<?>> ra) {
        ra.add(c);
        for (Class<?> con : c.getDeclaredClasses()) {
            themCaLopLong(con, ra);
        }
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
