package com.fastfood.service.admin;

import com.fastfood.common.constant.AuditAction;
import com.fastfood.common.constant.RoleName;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.PasswordUtil;
import com.fastfood.common.util.ValidationUtil;
import com.fastfood.dao.shared.CategoryDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.dao.shared.RoleDAO;
import com.fastfood.dao.shared.UserDAO;
import com.fastfood.model.dto.Page;
import com.fastfood.model.entity.Category;
import com.fastfood.model.entity.Product;
import com.fastfood.model.entity.Role;
import com.fastfood.model.entity.User;
import com.fastfood.service.Tx;
import com.fastfood.service.auth.PasswordResetService;
import com.fastfood.service.shared.AuditService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Quản trị danh mục món và tài khoản.
 * <p>
 * Xoá ở tầng này luôn là xoá <b>mềm</b>, và luôn có đường đi riêng tách khỏi Sửa:
 * {@link #setProductStatus}, {@link #setCategoryStatus}, {@link #setUserStatus}. Món ngừng bán
 * thì chuyển sang trạng thái ngừng kinh doanh, nhóm món thì ẩn khỏi thực đơn, tài khoản nghỉ
 * việc thì khoá lại. Xoá thật sẽ làm hỏng các đơn cũ đang tham chiếu tới.
 */
public class AdminService {

    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final UserDAO userDAO = new UserDAO();
    private final RoleDAO roleDAO = new RoleDAO();
    private final AuditService auditService = new AuditService();
    private final PasswordResetService passwordResetService = new PasswordResetService();

    // ------------------------------------------------------------ món ăn

    /**
     * Một trang của danh sách món cho màn hình quản trị.
     * <p>
     * Bốn bộ lọc đi cùng nhau vì chúng trả lời bốn câu hỏi khác nhau và người dùng thường
     * hỏi chồng lên nhau: "món gà" (từ khoá), "trong nhóm Đồ uống" (nhóm), "đã ngừng bán"
     * (trạng thái kinh doanh), "đang tạm hết" (tình trạng hàng trong ngày).
     *
     * @param status    ACTIVE / INACTIVE, hoặc rỗng để lấy cả hai
     * @param available true chỉ lấy món còn hàng, false chỉ lấy món tạm hết, null lấy cả hai
     */
    public Page<Product> listProducts(Integer categoryId, String keyword, String status,
                                      Boolean available, int pageNo) {
        String normalizedStatus = normalizedStatus(status);
        int page = Page.safePage(pageNo);
        return Tx.read(con -> {
            long total = productDAO.countForAdmin(con, categoryId, keyword, normalizedStatus, available);
            List<Product> items = productDAO.searchForAdmin(con, categoryId, keyword, normalizedStatus,
                    available, Page.offset(page, Page.SIZE), Page.SIZE);
            return new Page<>(items, page, Page.SIZE, total);
        });
    }

    public Product findProduct(int productId) {
        Product p = Tx.read(con -> productDAO.findById(con, productId));
        if (p == null) {
            throw new NotFoundException("Không tìm thấy món ăn.");
        }
        return p;
    }

    public void saveProduct(int actorId, Product form) {
        ValidationUtil.requireText(form.getName(), "tên món");
        if (form.getPrice() == null || form.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Giá bán không hợp lệ.");
        }
        if (form.getCategoryId() <= 0) {
            throw new ValidationException("Vui lòng chọn nhóm món.");
        }

        Tx.writeVoid(con -> {
            if (form.getProductId() > 0) {
                Product current = productDAO.findById(con, form.getProductId());
                if (current == null) {
                    throw new NotFoundException("Không tìm thấy món ăn.");
                }
                // Trạng thái kinh doanh không đi qua form Sửa: nó có đường riêng là
                // setProductStatus. Giữ nguyên giá trị đang có, nếu không thì mỗi lần sửa mô tả
                // lại vô tình bật lại một món đã ngừng bán.
                form.setStatus(current.getStatus());
                form.setUpdatedAt(DateTimeUtil.now());
                productDAO.update(con, form);
                auditService.log(con, actorId, "PRODUCT", form.getProductId(),
                        AuditAction.PRODUCT_CHANGED, current.getPrice().toPlainString(),
                        form.getPrice().toPlainString());
            } else {
                form.setStatus("ACTIVE");   // thêm món là để bán, không phải để cất đi
                form.setCreatedAt(DateTimeUtil.now());
                productDAO.insert(con, form);
                auditService.log(con, actorId, "PRODUCT", form.getProductId(),
                        AuditAction.PRODUCT_CHANGED, null, "CREATED");
            }
        });
    }

    /**
     * Ngừng bán một món, hoặc bán lại. Đây là thao tác <b>Xoá</b> của màn hình quản trị món.
     * <p>
     * Xoá mềm chứ không xoá hẳn: các đơn cũ vẫn đang trỏ tới món này, xoá thật sẽ làm hỏng
     * lịch sử đơn hàng và mọi báo cáo doanh thu dựng trên đó.
     */
    public void setProductStatus(int actorId, int productId, String status) {
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new ValidationException("Trạng thái món không hợp lệ.");
        }
        Tx.writeVoid(con -> {
            Product current = productDAO.findById(con, productId);
            if (current == null) {
                throw new NotFoundException("Không tìm thấy món ăn.");
            }
            productDAO.updateStatus(con, productId, status);
            auditService.log(con, actorId, "PRODUCT", productId,
                    "ACTIVE".equals(status) ? AuditAction.PRODUCT_RESTORED : AuditAction.PRODUCT_RETIRED,
                    current.getStatus(), status);
        });
    }

    /** Bật/tắt nhanh tình trạng còn hàng ngay trên danh sách. */
    public void toggleProductAvailability(int actorId, int productId, boolean available) {
        Tx.writeVoid(con -> {
            productDAO.toggleAvailability(con, productId, available);
            auditService.log(con, actorId, "PRODUCT", productId, AuditAction.PRODUCT_CHANGED,
                    null, available ? "AVAILABLE" : "OUT_OF_STOCK");
        });
    }

    // ------------------------------------------------------------ nhóm món

    /**
     * Mọi nhóm món, kèm số món trong nhóm, gồm cả nhóm đang ẩn.
     * <p>
     * Màn hình quản trị món cũng dùng danh sách này chứ không dùng riêng danh sách nhóm đang
     * hiện: một món có thể đang thuộc nhóm đã ẩn, và nếu ô chọn nhóm không có mặt nhóm đó thì
     * trình duyệt tự chọn nhóm đầu tiên — bấm Lưu là món lặng lẽ đổi sang nhóm khác.
     */
    public List<Category> listCategories() {
        return Tx.read(categoryDAO::findAllWithCount);
    }

    public Category findCategory(int categoryId) {
        Category c = Tx.read(con -> categoryDAO.findById(con, categoryId));
        if (c == null) {
            throw new NotFoundException("Không tìm thấy nhóm món.");
        }
        return c;
    }

    public void saveCategory(int actorId, Category form) {
        ValidationUtil.requireText(form.getName(), "tên nhóm món");
        Tx.writeVoid(con -> {
            if (form.getCategoryId() > 0) {
                Category current = categoryDAO.findById(con, form.getCategoryId());
                if (current == null) {
                    // Thiếu bước này thì sửa một mã không tồn tại vẫn báo "đã cập nhật",
                    // trong khi không có dòng nào thay đổi.
                    throw new NotFoundException("Không tìm thấy nhóm món.");
                }
                form.setStatus(current.getStatus());   // trạng thái đi đường riêng, xem setCategoryStatus
                categoryDAO.update(con, form);
                auditService.log(con, actorId, "CATEGORY", form.getCategoryId(),
                        AuditAction.CATEGORY_CHANGED, current.getName(), form.getName());
            } else {
                form.setStatus("ACTIVE");
                categoryDAO.insert(con, form);
                auditService.log(con, actorId, "CATEGORY", form.getCategoryId(),
                        AuditAction.CATEGORY_CHANGED, null, "CREATED");
            }
        });
    }

    /**
     * Ẩn một nhóm món khỏi thực đơn, hoặc hiện lại. Đây là thao tác <b>Xoá</b> của màn hình
     * quản trị nhóm món.
     * <p>
     * Xoá mềm vì các món trong nhóm vẫn trỏ tới nó bằng khoá ngoại, và những món đó lại nằm
     * trong các đơn cũ. Ẩn nhóm là cách ngừng bán cả một dòng sản phẩm chỉ bằng một thao tác.
     */
    public void setCategoryStatus(int actorId, int categoryId, String status) {
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new ValidationException("Trạng thái nhóm món không hợp lệ.");
        }
        Tx.writeVoid(con -> {
            Category current = categoryDAO.findById(con, categoryId);
            if (current == null) {
                throw new NotFoundException("Không tìm thấy nhóm món.");
            }
            categoryDAO.updateStatus(con, categoryId, status);
            auditService.log(con, actorId, "CATEGORY", categoryId,
                    "ACTIVE".equals(status) ? AuditAction.CATEGORY_RESTORED : AuditAction.CATEGORY_RETIRED,
                    current.getStatus(), status);
        });
    }

    // ------------------------------------------------------------ tài khoản

    /**
     * Một trang của danh sách tài khoản.
     *
     * @param status ACTIVE / LOCKED, hoặc rỗng để lấy cả hai
     */
    public Page<User> listUsers(String roleName, String keyword, String status, int pageNo) {
        String normalizedRole = normalizedRoleName(roleName);
        String normalizedStatus = normalizedUserStatus(status);
        int page = Page.safePage(pageNo);
        return Tx.read(con -> {
            long total = userDAO.countSearch(con, normalizedRole, keyword, normalizedStatus);
            List<User> items = userDAO.search(con, normalizedRole, keyword, normalizedStatus,
                    Page.offset(page, Page.SIZE), Page.SIZE);
            return new Page<>(items, page, Page.SIZE, total);
        });
    }

    public List<Role> listRoles() {
        return Tx.read(roleDAO::findAll);
    }

    public User findUser(int userId) {
        User u = Tx.read(con -> userDAO.findById(con, userId));
        if (u == null) {
            throw new NotFoundException("Không tìm thấy tài khoản.");
        }
        return u;
    }

    /**
     * Tạo tài khoản nhân viên. Khách hàng tự đăng ký, nhân viên do quản trị viên tạo.
     * <p>
     * Vai trò được kiểm ở đây chứ không chỉ ở ô chọn trên biểu mẫu. Trang tạo tài khoản đã bỏ
     * mục "Khách hàng" khỏi danh sách, nhưng ẩn một mục trong thẻ {@code select} chỉ đổi thứ
     * người dùng <i>nhìn thấy</i> — chuỗi gửi lên máy chủ thì ai cũng sửa được, và chính
     * {@code RoleName} đã ghi rằng phân quyền phải thi hành ở tầng sau, không dựa vào giao diện.
     */
    public void createStaff(int actorId, String fullName, String email, String phone,
                            String password, int roleId) {
        String name = ValidationUtil.requireText(fullName, "họ tên");
        String normalizedEmail = ValidationUtil.requireEmail(email);
        String normalizedPhone = ValidationUtil.optionalPhone(phone);
        ValidationUtil.requirePasswordStrength(password);

        Tx.writeVoid(con -> {
            Role role = requireRole(con, roleId);
            if (RoleName.CUSTOMER.name().equals(role.getName())) {
                throw new ValidationException(
                        "Tài khoản khách hàng do chính khách tự đăng ký, không tạo ở màn hình này.");
            }
            if (userDAO.emailExists(con, normalizedEmail)) {
                throw new ValidationException("Email này đã được sử dụng.");
            }
            User u = new User();
            u.setFullName(name);
            u.setEmail(normalizedEmail);
            u.setPhone(normalizedPhone);
            u.setPasswordHash(PasswordUtil.hash(password));
            u.setRoleId(roleId);
            u.setStatus("ACTIVE");
            // Nhân viên không đi qua luồng xác thực email: địa chỉ do quản trị viên nhập và
            // xác nhận bằng đường khác — thường là địa chỉ công ty đã có sẵn. Để cờ tắt thì
            // tài khoản mới sẽ mang dải nhắc "chưa xác thực email" mà chẳng có nút nào bấm
            // được, vì bốn màn hình nội bộ đều không đi qua chốt chặn ấy.
            u.setEmailVerified(true);
            u.setCreatedAt(DateTimeUtil.now());
            userDAO.insert(con, u);
            auditService.log(con, actorId, "USER", u.getUserId(),
                    AuditAction.USER_CHANGED, null, "CREATED");
        });
    }

    /**
     * Sửa họ tên và số điện thoại của một tài khoản.
     * <p>
     * Email không sửa được ở đây, dù nó nằm ngay cạnh trên cùng một dòng. Email là danh tính
     * đăng nhập: đổi nó là đổi người vào được tài khoản, chứ không phải sửa một dòng thông tin.
     * Việc đó cần xác minh địa chỉ mới có thật và thuộc về đúng người, nên thuộc một luồng riêng.
     * <p>
     * Không chặn quản trị viên sửa thông tin của chính mình như ở {@link #setUserStatus} hay
     * {@link #setUserRole}: sửa sai tên thì vào sửa lại được, không có chuyện tự khoá mình ra ngoài.
     */
    public void updateUserInfo(int actorId, int userId, String fullName, String phone) {
        String name = ValidationUtil.requireText(fullName, "họ tên");
        String normalizedPhone = ValidationUtil.optionalPhone(phone);

        Tx.writeVoid(con -> {
            User current = userDAO.findById(con, userId);
            if (current == null) {
                throw new NotFoundException("Không tìm thấy tài khoản.");
            }
            // Giữ lại tên cũ trước khi ghi đè — nhật ký cần cả hai đầu để đối chứng
            String before = current.getFullName();

            current.setFullName(name);
            current.setPhone(normalizedPhone);
            current.setUpdatedAt(DateTimeUtil.now());
            userDAO.updateProfile(con, current);

            auditService.log(con, actorId, "USER", userId,
                    AuditAction.USER_CHANGED, before, name);
        });
    }

    public void setUserStatus(int actorId, int userId, String status) {
        if (!"ACTIVE".equals(status) && !"LOCKED".equals(status)) {
            // Cùng cách làm với món và nhóm món: chặn ngay tại đây thay vì để ràng buộc của cơ
            // sở dữ liệu bắt, vì lỗi ràng buộc chỉ ra được một thông báo chung chung.
            throw new ValidationException("Trạng thái tài khoản không hợp lệ.");
        }
        if (actorId == userId) {
            throw new ValidationException("Không thể tự khoá tài khoản của chính mình.");
        }
        Tx.writeVoid(con -> {
            User u = userDAO.findById(con, userId);
            if (u == null) {
                throw new NotFoundException("Không tìm thấy tài khoản.");
            }
            userDAO.updateStatus(con, userId, status);
            auditService.log(con, actorId, "USER", userId,
                    AuditAction.USER_CHANGED, u.getStatus(), status);
        });
    }

    /**
     * Đổi vai trò của một tài khoản.
     * <p>
     * Chặn quản trị viên tự đổi vai trò của chính mình, cùng lý do với việc chặn tự khoá:
     * hạ quyền của mình là thao tác không tự quay lại được — đăng xuất xong là không còn
     * đường vào khu vực quản trị để sửa lại.
     */
    public void setUserRole(int actorId, int userId, int roleId) {
        if (actorId == userId) {
            throw new ValidationException("Không thể tự đổi vai trò của chính mình.");
        }
        Tx.writeVoid(con -> {
            User u = userDAO.findById(con, userId);
            if (u == null) {
                throw new NotFoundException("Không tìm thấy tài khoản.");
            }
            Role role = requireRole(con, roleId);
            userDAO.updateRole(con, userId, roleId);
            // Ghi tên vai trò chứ không ghi mã: dòng "CASHIER → ADMIN" đọc được ngay, còn
            // "CASHIER → ROLE_4" thì phải mở bảng Role ra tra mới biết ai vừa được nâng quyền.
            auditService.log(con, actorId, "USER", userId,
                    AuditAction.USER_CHANGED, u.getRoleName(), role.getName());
        });
    }

    /**
     * Quản trị viên đặt lại mật khẩu hộ một tài khoản.
     * <p>
     * Mật khẩu đặt ra ở đây là <b>mật khẩu tạm</b>: quản trị viên biết nó, và thường còn phải
     * đọc nó qua điện thoại hoặc nhắn cho người kia. Vì vậy tài khoản bị đánh dấu buộc phải
     * đổi mật khẩu, và {@code AuthenticationFilter} giữ người dùng ở trang tài khoản cho tới
     * khi họ tự đặt mật khẩu mới. Thiếu bước này thì mọi tài khoản từng được đặt lại đều chạy
     * tiếp bằng một mật khẩu mà ít nhất hai người biết.
     */
    public void resetPassword(int actorId, int userId, String newPassword) {
        ValidationUtil.requirePasswordStrength(newPassword);
        if (actorId == userId) {
            // Tự đặt lại cho mình rồi tự bị chặn ở trang tài khoản là vòng luẩn quẩn vô nghĩa;
            // quản trị viên đổi mật khẩu của chính mình ở trang tài khoản.
            throw new ValidationException("Đổi mật khẩu của chính mình ở trang tài khoản.");
        }
        Tx.writeVoid(con -> {
            // Kiểm tra tài khoản có thật trước khi ghi: thiếu bước này thì đặt lại mật khẩu
            // cho một mã không tồn tại vẫn báo "đã đặt lại", trong khi không có gì thay đổi.
            if (userDAO.findById(con, userId) == null) {
                throw new NotFoundException("Không tìm thấy tài khoản.");
            }
            userDAO.updatePassword(con, userId, PasswordUtil.hash(newPassword), true);
            // Mật khẩu vừa đổi thì mọi liên kết "quên mật khẩu" còn treo của tài khoản này phải
            // hết giá trị theo — kể cả liên kết do chính kẻ đang chiếm tài khoản xin ra, vốn là
            // lý do thường gặp khiến quản trị viên phải đặt lại mật khẩu hộ.
            passwordResetService.invalidateOutstanding(con, userId);
            auditService.log(con, actorId, "USER", userId,
                    AuditAction.USER_CHANGED, null, "PASSWORD_RESET");
        });
    }

    /**
     * Vai trò theo mã, và ném lỗi đọc được nếu mã đó không có thật.
     * <p>
     * Gọi <b>trong cùng giao dịch</b> với lệnh ghi chứ không kiểm trước rồi mới mở giao dịch:
     * kiểm ngoài thì giữa lúc kiểm và lúc ghi vẫn còn một khe hở, và khoá ngoại lại là thứ
     * cuối cùng lên tiếng — đúng tình huống mà lớp kiểm này sinh ra để tránh.
     */
    private Role requireRole(Connection con, int roleId) throws SQLException {
        Role role = roleDAO.findById(con, roleId);
        if (role == null) {
            throw new ValidationException("Vai trò không hợp lệ.");
        }
        // Không kiểm thêm rằng tên vai trò có nằm trong enum RoleName hay không: bảng Role đã
        // có CK_Role_name khoá đúng bốn tên đó, nên một tên lạ không vào được bảng ngay từ đầu.
        // Ràng buộc ấy được canh bởi SchemaConstraintIT, và AdminRoleAssignmentIT canh vế còn
        // lại — dữ liệu mẫu trong bảng khớp đúng enum.
        return role;
    }

    // ------------------------------------------------------------ bộ lọc

    /*
     * Ba hàm dưới đây lọc giá trị đến từ địa chỉ trên thanh trình duyệt. Người dùng sửa tay
     * được, nên một giá trị lạ phải hiểu thành "không lọc" chứ không được đi thẳng vào câu
     * lệnh SQL rồi trả về bảng rỗng — bảng rỗng trông y hệt như "cửa hàng chưa có món nào".
     */

    private String normalizedStatus(String status) {
        return "ACTIVE".equals(status) || "INACTIVE".equals(status) ? status : null;
    }

    private String normalizedUserStatus(String status) {
        return "ACTIVE".equals(status) || "LOCKED".equals(status) ? status : null;
    }

    /**
     * Vai trò dùng để lọc danh sách tài khoản.
     * <p>
     * Đây là bộ lọc thứ ba trên cùng màn hình mà hai bộ kia đã lọc từ lâu; thiếu nó thì
     * {@code /admin/users?role=BANH_MI} trả về một bảng trống trông y hệt "chưa có tài khoản
     * nào" — cùng đúng cái bẫy mà ghi chú bên trên nói tới.
     * <p>
     * Trả về tên chuẩn hoá của {@link RoleName} chứ không trả lại nguyên chuỗi người dùng gõ:
     * nhờ vậy {@code ?role=cashier} lọc đúng thay vì phụ thuộc vào cách so chuỗi của cơ sở dữ
     * liệu, và chuỗi đi vào câu lệnh SQL luôn là một trong bốn giá trị đã biết.
     */
    private String normalizedRoleName(String roleName) {
        RoleName role = RoleName.parse(roleName);
        return role == null ? null : role.name();
    }
}
