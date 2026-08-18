package com.fastfood.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Biểu mẫu POST mang theo mã chống giả mạo")
class CsrfTokenPresenceTest {

    private static final Path VIEWS = Path.of("src", "main", "webapp", "WEB-INF", "views");

    private static final String FIELD = "name=\"_csrf\"";

    private static final Pattern FORM_OPEN = Pattern.compile("<form\\b");

    @Test
    @DisplayName("Không biểu mẫu POST nào thiếu ô ẩn _csrf")
    void everyPostFormCarriesTheToken() throws IOException {
        List<String> missing = new ArrayList<>();
        int checked = 0;

        for (Path page : viewFiles()) {
            String text = Files.readString(page, StandardCharsets.UTF_8);
            Matcher m = FORM_OPEN.matcher(text);
            while (m.find()) {
                int tagEnd = endOfTag(text, m.start());
                String openTag = text.substring(m.start(), tagEnd);
                if (!openTag.contains("method=\"post\"")) {
                    continue;
                }
                checked++;
                if (!bodyOf(text, tagEnd).contains(FIELD)) {
                    missing.add(page.getFileName() + " dòng " + lineOf(text, m.start()));
                }
            }
        }

        assertTrue(checked >= 80,
                "Chi tim thay " + checked + " bieu mau POST — sai duong dan, hay bo test dang do nham cho?");
        assertTrue(missing.isEmpty(),
                "Bieu mau POST thieu <input type=\"hidden\" name=\"_csrf\" value=\"${csrfToken}\">: " + missing);
    }

    @Test
    @DisplayName("Ô ẩn lấy giá trị từ đúng biến ${csrfToken}")
    void tokenComesFromTheRightAttribute() throws IOException {
        List<String> wrong = new ArrayList<>();
        Pattern field = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]*)\"");

        for (Path page : viewFiles()) {
            Matcher m = field.matcher(Files.readString(page, StandardCharsets.UTF_8));
            while (m.find()) {
                if (!"${csrfToken}".equals(m.group(1))) {
                    wrong.add(page.getFileName() + " → " + m.group(1));
                }
            }
        }

        assertEquals(List.of(), wrong, "O an _csrf lay gia tri tu bien sai");
    }

    private List<Path> viewFiles() throws IOException {
        try (Stream<Path> files = Files.walk(VIEWS)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".jsp") || name.endsWith(".jspf");
                    })
                    .sorted()
                    .toList();
        }
    }

    private int endOfTag(String text, int start) {
        int i = start;
        char quote = 0;
        while (i < text.length()) {
            if (text.startsWith("<%--", i)) {
                int close = text.indexOf("--%>", i);
                i = close < 0 ? text.length() : close + 4;
                continue;
            }
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return i + 1;
            }
            i++;
        }
        return text.length();
    }

    private String bodyOf(String text, int tagEnd) {
        int close = text.indexOf("</form>", tagEnd);
        return text.substring(tagEnd, close < 0 ? text.length() : close);
    }

    private int lineOf(String text, int index) {
        return (int) text.substring(0, index).chars().filter(c -> c == '\n').count() + 1;
    }
}
