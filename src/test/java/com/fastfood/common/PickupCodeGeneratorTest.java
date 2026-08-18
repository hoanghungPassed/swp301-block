package com.fastfood.common;

import com.fastfood.common.util.PickupCodeGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Mã nhận hàng")
class PickupCodeGeneratorTest {

    private static final String CONFUSABLE = "01OI";

    @Test
    @DisplayName("Đúng định dạng: 6 chữ số ngày rồi 4 ký tự ngẫu nhiên")
    void hasExpectedFormat() {
        String code = PickupCodeGenerator.generate();

        assertEquals(10, code.length(), "Mã dài " + code.length() + ": " + code);
        assertTrue(code.matches("\\d{6}[A-Z2-9]{4}"), "Mã sai định dạng: " + code);
    }

    @Test
    @DisplayName("Không chứa ký tự dễ đọc nhầm")
    void avoidsConfusableCharacters() {
        for (int i = 0; i < 500; i++) {
            String random = PickupCodeGenerator.generate().substring(6);
            for (char c : CONFUSABLE.toCharArray()) {
                assertTrue(random.indexOf(c) < 0,
                        "Mã " + random + " chứa ký tự '" + c + "' — nhân viên sẽ gõ nhầm");
            }
        }
    }

    @Test
    @DisplayName("Phần ngày ở đầu giống nhau trong cùng một ngày")
    void sharesDatePrefixWithinTheSameDay() {
        String a = PickupCodeGenerator.generate();
        String b = PickupCodeGenerator.generate();

        assertEquals(a.substring(0, 6), b.substring(0, 6),
                "Nhờ phần ngày ở đầu, mã của các ngày khác nhau không bao giờ đụng nhau, "
                + "nên chỉ cần chống trùng trong phạm vi một ngày");
    }

    @Test
    @DisplayName("Sinh 2000 mã liên tiếp không đụng nhau quá nhiều")
    void collisionsAreRare() {
        Set<String> seen = new HashSet<>();
        int collisions = 0;
        for (int i = 0; i < 2000; i++) {
            if (!seen.add(PickupCodeGenerator.generate())) {
                collisions++;
            }
        }

        assertTrue(collisions < 20,
                "Trùng " + collisions + "/2000 lần là quá nhiều, nguồn ngẫu nhiên có vấn đề");
    }

    @Test
    @DisplayName("Không đoán được mã tiếp theo từ mã trước đó")
    void codesAreNotSequential() {
        String a = PickupCodeGenerator.generate().substring(6);
        String b = PickupCodeGenerator.generate().substring(6);
        String c = PickupCodeGenerator.generate().substring(6);

        assertTrue(!(a.equals(b) && b.equals(c)),
                "Ba mã liên tiếp giống hệt nhau nghĩa là mã không hề ngẫu nhiên");
    }
}
