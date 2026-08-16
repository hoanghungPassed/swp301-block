package com.fastfood.integration.notification;

import com.fastfood.config.AppConfig;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gửi thư thật qua một máy chủ SMTP. Bật bằng {@code notification.channel=SMTP}.
 * <p>
 * <b>Vì sao chức năng quên mật khẩu cần lớp này mới thật sự là chức năng.</b> Bản giả lập chỉ
 * ghi nội dung ra log máy chủ. Với tin "món đã sẵn sàng" thì như vậy còn tạm được lúc trình
 * bày — khách đang đứng ngay quầy. Nhưng liên kết đặt lại mật khẩu và liên kết xác thực địa
 * chỉ thì cả ý nghĩa của chúng nằm ở chỗ <i>chỉ người mở được hộp thư mới đọc được</i>. Nằm
 * trong log máy chủ thì người duy nhất đọc được lại là người có quyền vào máy chủ, tức là đúng
 * người không cần tới nó.
 * <p>
 * <b>Ba tham số hay làm hỏng việc, đặt sẵn ở đây:</b>
 * <ul>
 *   <li><b>STARTTLS.</b> Cổng 587 bắt đầu bằng kết nối thường rồi mới nâng cấp lên mã hoá. Thiếu
 *       dòng bật STARTTLS thì mật khẩu hộp thư đi qua mạng dưới dạng đọc được, và phần lớn máy
 *       chủ sẽ từ chối nhận thư luôn.</li>
 *   <li><b>Thời gian chờ.</b> Mặc định của thư viện là chờ vô hạn. Một máy chủ SMTP không trả
 *       lời sẽ giữ nguyên luồng đang phục vụ người dùng — người bấm "Đăng ký" ngồi nhìn trang
 *       trắng cho tới khi trình duyệt bỏ cuộc. Ba mốc chờ dưới đây cắt việc đó xuống mười giây.</li>
 *   <li><b>Mật khẩu ứng dụng.</b> Gmail không cho đăng nhập SMTP bằng mật khẩu thường từ lâu;
 *       phải tạo App Password riêng. Điền nhầm mật khẩu thường thì máy chủ trả về lỗi xác thực,
 *       và lỗi đó hiện trong log với đúng câu chữ của Google.</li>
 * </ul>
 * <p>
 * <b>Không bao giờ ném ngoại lệ ra ngoài.</b> Trả về {@code false} và ghi log, đúng như giao
 * kèo của {@link NotificationSender}. Hộp thư của người khác hỏng không phải là lý do để việc
 * đăng ký hay việc đặt hàng của họ hỏng theo — và riêng ở luồng quên mật khẩu, một lỗi ném ra
 * tới tận màn hình còn là cách gián tiếp nói cho người đang dò biết địa chỉ nào có thật.
 */
public class SmtpNotificationSender implements NotificationSender {

    private static final Logger LOG = Logger.getLogger(SmtpNotificationSender.class.getName());

    /** Mười giây cho mỗi mốc chờ: đủ cho một máy chủ chậm, không đủ để người dùng bỏ đi. */
    private static final String TIMEOUT_MS = "10000";

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String fromAddress;
    private final String fromName;

    public SmtpNotificationSender() {
        this.host = AppConfig.get("notification.mail.host", "smtp.gmail.com").trim();
        this.port = AppConfig.getInt("notification.mail.port", 587);
        this.username = AppConfig.get("notification.mail.username", "").trim();
        this.password = AppConfig.get("notification.mail.password", "").trim();
        // Phần lớn máy chủ SMTP từ chối gửi thư mang địa chỉ người gửi khác với tài khoản đang
        // đăng nhập, nên mặc định lấy luôn tài khoản đó thay vì bắt điền hai lần cùng một thứ.
        this.fromAddress = AppConfig.get("notification.mail.from", username).trim();
        this.fromName = AppConfig.get("notification.mail.fromName", "Fast Food Pre-order").trim();
    }

    @Override
    public String getChannel() {
        return "SMTP";
    }

    /**
     * Kênh này có đủ tham số để gửi được không.
     * <p>
     * Dùng ở {@link NotificationSenders} để bắt lỗi cấu hình ngay lúc khởi động thay vì ở lần
     * đăng ký đầu tiên của người dùng thật.
     */
    public boolean isConfigured() {
        return !host.isEmpty() && !username.isEmpty() && !password.isEmpty();
    }

    @Override
    public boolean send(String recipient, String subject, String content) {
        if (recipient == null || recipient.isBlank()) {
            LOG.warning("SMTP: khong co dia chi nguoi nhan, bo qua thu: " + subject);
            return false;
        }
        if (!isConfigured()) {
            LOG.severe("SMTP: thieu notification.mail.host/username/password, khong gui duoc thu");
            return false;
        }
        try {
            Transport.send(build(recipient, subject, content));
            LOG.info(() -> "SMTP: da gui thu den " + recipient + " | " + subject);
            return true;
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException e) {
            // Ghi cả địa chỉ nhận: khi khách báo "không nhận được thư", dòng log này là chỗ
            // duy nhất trả lời được là thư đã đi mà hỏng, hay chưa từng được gửi.
            LOG.log(Level.SEVERE, "SMTP: gui thu den " + recipient + " that bai", e);
            return false;
        }
    }

    private MimeMessage build(String recipient, String subject, String content)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = new MimeMessage(session());
        message.setFrom(new InternetAddress(fromAddress, fromName, "UTF-8"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
        // UTF-8 ở cả tiêu đề lẫn nội dung: thiếu nó thì "Xác thực địa chỉ email" tới hộp thư
        // thành một dãy ký tự hỏng, mà thư xác thực gửi đi rồi thì không sửa lại được.
        message.setSubject(subject, "UTF-8");
        message.setText(content, "UTF-8");
        return message;
    }

    private Session session() {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        props.put("mail.smtp.timeout", TIMEOUT_MS);
        props.put("mail.smtp.writetimeout", TIMEOUT_MS);
        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }
}
