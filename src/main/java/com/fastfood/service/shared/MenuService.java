package com.fastfood.service.shared;

import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.dao.shared.CategoryDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.model.dto.Dtos.Page;
import com.fastfood.model.entity.MenuEntities.Category;
import com.fastfood.model.entity.MenuEntities.Product;
import com.fastfood.service.Tx;

import java.util.List;

public class MenuService {

    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();

    /** Lấy toàn bộ món đang phục vụ theo danh mục và từ khóa với cách sắp xếp mặc định. */
    public List<Product> browse(Integer categoryId, String keyword) {
        return browse(categoryId, keyword, null);
    }

    /** Lấy toàn bộ món đang phục vụ theo bộ lọc và cách sắp xếp đã chọn. */
    public List<Product> browse(Integer categoryId, String keyword, String sort) {
        return Tx.read(con -> productDAO.findMenu(con, categoryId, keyword, sort));
    }

    /** Thực đơn cắt theo trang — đếm trước rồi mới lấy đúng phần đang xem. */
    public Page<Product> browsePage(Integer categoryId, String keyword, String sort,
                                    int pageNo, int pageSize) {
        int page = Page.safePage(pageNo);
        int size = Page.safeSize(pageSize);
        return Tx.read(con -> {
            long total = productDAO.countMenu(con, categoryId, keyword);
            List<Product> items = productDAO.findMenu(con, categoryId, keyword, sort,
                    Page.offset(page, size), size);
            return new Page<>(items, page, size, total);
        });
    }

    /** Chỉ cho phép các mã sort trong whitelist, giá trị lạ được đổi thành DEFAULT. */
    public String sortOrDefault(String sort) {
        return ProductDAO.menuSortOrDefault(sort);
    }

    /** Lấy các danh mục ACTIVE để dựng bộ lọc menu. */
    public List<Category> activeCategories() {
        return Tx.read(categoryDAO::findActive);
    }

    /** Tải chi tiết món theo id và báo không tìm thấy nếu product không tồn tại. */
    public Product detail(int productId) {
        Product p = Tx.read(con -> productDAO.findById(con, productId));
        if (p == null) {
            throw new NotFoundException("Không tìm thấy món ăn.");
        }
        return p;
    }
}
