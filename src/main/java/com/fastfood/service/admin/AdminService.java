package com.fastfood.service.admin;

import com.fastfood.common.constant.Constants.AuditAction;
import com.fastfood.common.constant.Constants.RoleName;
import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.PasswordUtil;
import com.fastfood.common.util.ValidationUtil;
import com.fastfood.dao.shared.CategoryDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.dao.shared.RoleDAO;
import com.fastfood.dao.shared.UserDAO;
import com.fastfood.model.dto.Dtos.Page;
import com.fastfood.model.entity.MenuEntities.Category;
import com.fastfood.model.entity.MenuEntities.Product;
import com.fastfood.model.entity.UserEntities.Role;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.Tx;
import com.fastfood.service.auth.PasswordResetService;
import com.fastfood.service.shared.AuditService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class AdminService {

    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final UserDAO userDAO = new UserDAO();
    private final RoleDAO roleDAO = new RoleDAO();
    private final AuditService auditService = new AuditService();
    private final PasswordResetService passwordResetService = new PasswordResetService();

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
                form.setStatus(current.getStatus());
                form.setUpdatedAt(DateTimeUtil.now());
                productDAO.update(con, form);
                auditService.log(con, actorId, "PRODUCT", form.getProductId(),
                        AuditAction.PRODUCT_CHANGED, current.getPrice().toPlainString(),
                        form.getPrice().toPlainString());
            } else {
                form.setStatus("ACTIVE");
                form.setCreatedAt(DateTimeUtil.now());
                productDAO.insert(con, form);
                auditService.log(con, actorId, "PRODUCT", form.getProductId(),
                        AuditAction.PRODUCT_CHANGED, null, "CREATED");
            }
        });
    }

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

    public void toggleProductAvailability(int actorId, int productId, boolean available) {
        Tx.writeVoid(con -> {
            productDAO.toggleAvailability(con, productId, available);
            auditService.log(con, actorId, "PRODUCT", productId, AuditAction.PRODUCT_CHANGED,
                    null, available ? "AVAILABLE" : "OUT_OF_STOCK");
        });
    }

    public List<Category> listCategories() {
        return Tx.read(categoryDAO::findAllWithCount);
    }

    public Page<Category> listCategories(int pageNo) {
        int page = Page.safePage(pageNo);
        return Tx.read(con -> {
            long total = categoryDAO.countAll(con);
            List<Category> items = categoryDAO.findAllWithCount(con,
                    Page.offset(page, Page.SIZE), Page.SIZE);
            return new Page<>(items, page, Page.SIZE, total);
        });
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
                    throw new NotFoundException("Không tìm thấy nhóm món.");
                }
                form.setStatus(current.getStatus());
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

    /** Vai trò được phép chọn khi tạo tài khoản: chỉ thu ngân và bếp. */
    public List<Role> listStaffRoles() {
        return Tx.read(con -> roleDAO.findAll(con).stream()
                .filter(r -> isCreatableStaffRole(r.getName()))
                .collect(Collectors.toList()));
    }

    public User findUser(int userId) {
        User u = Tx.read(con -> userDAO.findById(con, userId));
        if (u == null) {
            throw new NotFoundException("Không tìm thấy tài khoản.");
        }
        return u;
    }

    public void createStaff(int actorId, String fullName, String email, String phone,
                            String password, int roleId) {
        String name = ValidationUtil.requireText(fullName, "họ tên");
        String normalizedEmail = ValidationUtil.requireEmail(email);
        String normalizedPhone = ValidationUtil.optionalPhone(phone);
        ValidationUtil.requirePasswordStrength(password);

        Tx.writeVoid(con -> {
            Role role = requireRole(con, roleId);
            if (!isCreatableStaffRole(role.getName())) {
                throw new ValidationException(
                        "Chỉ tạo được tài khoản thu ngân hoặc bếp. Khách hàng tự đăng ký, "
                                + "còn quản trị viên không thêm mới ở màn hình này.");
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
            u.setEmailVerified(true);
            u.setCreatedAt(DateTimeUtil.now());
            userDAO.insert(con, u);
            auditService.log(con, actorId, "USER", u.getUserId(),
                    AuditAction.USER_CHANGED, null, "CREATED");
        });
    }

    public void updateUserInfo(int actorId, int userId, String fullName, String phone) {
        String name = ValidationUtil.requireText(fullName, "họ tên");
        String normalizedPhone = ValidationUtil.optionalPhone(phone);

        Tx.writeVoid(con -> {
            User current = userDAO.findById(con, userId);
            if (current == null) {
                throw new NotFoundException("Không tìm thấy tài khoản.");
            }
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
            auditService.log(con, actorId, "USER", userId,
                    AuditAction.USER_CHANGED, u.getRoleName(), role.getName());
        });
    }

    public void resetPassword(int actorId, int userId, String newPassword) {
        ValidationUtil.requirePasswordStrength(newPassword);
        if (actorId == userId) {
            throw new ValidationException("Đổi mật khẩu của chính mình ở trang tài khoản.");
        }
        Tx.writeVoid(con -> {
            if (userDAO.findById(con, userId) == null) {
                throw new NotFoundException("Không tìm thấy tài khoản.");
            }
            userDAO.updatePassword(con, userId, PasswordUtil.hash(newPassword), true);
            passwordResetService.invalidateOutstanding(con, userId);
            auditService.log(con, actorId, "USER", userId,
                    AuditAction.USER_CHANGED, null, "PASSWORD_RESET");
        });
    }

    private static boolean isCreatableStaffRole(String roleName) {
        return RoleName.CASHIER.name().equals(roleName) || RoleName.KITCHEN.name().equals(roleName);
    }

    private Role requireRole(Connection con, int roleId) throws SQLException {
        Role role = roleDAO.findById(con, roleId);
        if (role == null) {
            throw new ValidationException("Vai trò không hợp lệ.");
        }
        return role;
    }

    private String normalizedStatus(String status) {
        return "ACTIVE".equals(status) || "INACTIVE".equals(status) ? status : null;
    }

    private String normalizedUserStatus(String status) {
        return "ACTIVE".equals(status) || "LOCKED".equals(status) ? status : null;
    }

    private String normalizedRoleName(String roleName) {
        RoleName role = RoleName.parse(roleName);
        return role == null ? null : role.name();
    }
}
