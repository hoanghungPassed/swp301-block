package com.fastfood.payment;

import com.fastfood.integration.payment.GatewayCallback;
import com.fastfood.integration.payment.MockPaymentGateway;
import com.fastfood.integration.payment.PaymentInitResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Trang cổng thanh toán giả lập")
class MockGatewayPageTest {

    private static final Path TRANG =
            Path.of("src", "main", "webapp", "WEB-INF", "views", "customer", "payment-gateway.jsp");

    private static final Pattern DUONG_DAN_GOI_VE =
            Pattern.compile("\\$\\{ctx}/payment/callback\\?(.*?)\">");

    private static final int PAYMENT_ID = 57;
    private static final int ORDER_ID = 12;
    private static final BigDecimal SO_TIEN = new BigDecimal("185000.00");

    private final MockPaymentGateway gateway = new MockPaymentGateway();

    @Test
    @DisplayName("Nút 'thành công' gửi về đủ tham số nên chữ ký khớp và đơn được ghi nhận đã trả")
    void nutThanhCongKyDung() throws Exception {
        GatewayCallback callback = bamNut(true);
        assertTrue(callback.isSuccess(), "Nut nay phai bao ket qua thanh cong");
        assertEquals(0, SO_TIEN.compareTo(callback.getAmount()),
                "Thieu tham so amount thi PaymentService coi la lech tien va khong ghi nhan da tra");
        assertTrue(gateway.verifySignature(callback),
                "Chu ky khong khop nen ket qua thanh toan bi bo qua, thu ngan van thay chua thu");
    }

    @Test
    @DisplayName("Nút 'thất bại' cũng ký đúng nên khoản thanh toán chuyển sang thất bại")
    void nutThatBaiKyDung() throws Exception {
        GatewayCallback callback = bamNut(false);
        assertTrue(gateway.verifySignature(callback));
    }

    /** Dựng lại đúng lời gọi về mà trang JSP tạo ra khi khách bấm nút. */
    private GatewayCallback bamNut(boolean thanhCong) throws Exception {
        PaymentInitResult init = gateway.initiate(PAYMENT_ID, ORDER_ID, SO_TIEN, "http://localhost:8080/fastfood");
        Map<String, String> thamSoMoDau = thamSo(init.getRedirectUrl().substring(
                init.getRedirectUrl().indexOf('?') + 1));

        Map<String, String> el = new LinkedHashMap<>();
        el.put("paymentId", String.valueOf(PAYMENT_ID));
        el.put("orderId", String.valueOf(ORDER_ID));
        el.put("txnId", thamSoMoDau.get("txnId"));
        el.put("amount", thamSoMoDau.get("amount"));
        el.put("successSig", thamSoMoDau.get("sig"));
        el.put("failureSig", gateway.signFailure(PAYMENT_ID, thamSoMoDau.get("txnId"),
                thamSoMoDau.get("amount")));

        Map<String, String> q = thamSo(chuoiTruyVan(thanhCong, el));
        GatewayCallback callback = new GatewayCallback();
        callback.setPaymentId(Integer.parseInt(q.getOrDefault("paymentId", "0")));
        callback.setExternalTransactionId(q.get("txnId"));
        callback.setSuccess("true".equalsIgnoreCase(q.get("success")));
        callback.setAmount(q.get("amount") == null ? null : new BigDecimal(q.get("amount")));
        callback.setSignature(q.get("sig"));
        return callback;
    }

    /** Đọc đường dẫn trong trang JSP rồi thay biến EL bằng giá trị thật. */
    private static String chuoiTruyVan(boolean thanhCong, Map<String, String> el) throws Exception {
        String nguon = Files.readString(TRANG);
        Matcher m = DUONG_DAN_GOI_VE.matcher(nguon);
        String tim = "success=" + thanhCong;
        while (m.find()) {
            String duongDan = m.group(1);
            if (!duongDan.contains(tim)) {
                continue;
            }
            String ket = duongDan.replaceAll("<c:out value=\"\\$\\{(\\w+)}\"/>", "\\$\\{$1}");
            for (Map.Entry<String, String> e : el.entrySet()) {
                ket = ket.replace("${" + e.getKey() + "}", e.getValue());
            }
            return ket;
        }
        throw new AssertionError("Khong thay duong dan goi ve voi " + tim + " trong " + TRANG);
    }

    private static Map<String, String> thamSo(String chuoi) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String phan : chuoi.split("&")) {
            int dau = phan.indexOf('=');
            assertNotNull(phan);
            if (dau > 0) {
                map.put(phan.substring(0, dau), phan.substring(dau + 1));
            }
        }
        return map;
    }
}
