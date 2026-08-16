import org.apache.catalina.startup.Tomcat;

import java.io.File;

/**
 * Chạy ứng dụng bằng Tomcat nhúng để kiểm thử thật: trang được dựng bởi đúng Jasper và
 * đúng chuỗi bộ lọc như khi chạy trên máy chủ, không phải bản mô phỏng.
 */
public class Boot {

    public static void main(String[] args) throws Exception {
        String projectDir = args.length > 0 ? args[0] : ".";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8081;

        File docBase = new File(projectDir, "target/fastfood").getAbsoluteFile();
        if (!docBase.isDirectory()) {
            throw new IllegalStateException("Chua dong goi ung dung: " + docBase);
        }

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.setBaseDir(new File(System.getProperty("java.io.tmpdir"), "ff-embed").getAbsolutePath());
        tomcat.getConnector();
        tomcat.addWebapp("", docBase.getAbsolutePath());

        tomcat.start();
        System.out.println("SAN SANG http://localhost:" + port + "/");
        tomcat.getServer().await();
    }
}
