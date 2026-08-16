package com.fastfood.common.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * Sinh ảnh QR cho mã nhận hàng.
 * <p>
 * Trả về chuỗi data URI nhúng thẳng vào thẻ img, không phải lưu file ảnh lên đĩa —
 * mã chỉ dùng trong vài chục phút nên không đáng để quản lý vòng đời file.
 */
public final class QrCodeUtil {

    private QrCodeUtil() {
    }

    /** Trả về chuỗi dạng {@code data:image/png;base64,...}, hoặc null nếu sinh ảnh thất bại. */
    public static String toDataUri(String content, int size) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }
}
