package com.fastfood.service.shared;

import com.fastfood.common.exception.NotFoundException;
import com.fastfood.dao.shared.CategoryDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.model.entity.Category;
import com.fastfood.model.entity.Product;
import com.fastfood.service.Tx;

import java.util.List;

/** Thực đơn hiển thị cho khách và cho thu ngân. */
public class MenuService {

    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();

    /** Thực đơn theo thứ tự mặc định của cửa hàng: nhóm món trước, trong nhóm xếp theo tên. */
    public List<Product> browse(Integer categoryId, String keyword) {
        return browse(categoryId, keyword, null);
    }

    /**
     * Thực đơn theo thứ tự khách chọn. {@code sort} nhận thẳng từ địa chỉ trang nên có thể là
     * bất cứ thứ gì; {@link ProductDAO} tra nó qua bảng mã và bỏ qua giá trị lạ.
     */
    public List<Product> browse(Integer categoryId, String keyword, String sort) {
        return Tx.read(con -> productDAO.findMenu(con, categoryId, keyword, sort));
    }

    /** Mã sắp xếp thật sự được áp dụng, để trang tô đúng lựa chọn đang mở. */
    public String sortOrDefault(String sort) {
        return ProductDAO.menuSortOrDefault(sort);
    }

    public List<Category> activeCategories() {
        return Tx.read(categoryDAO::findActive);
    }

    /** Xem chi tiết món. Món đã ngừng bán vẫn xem được nhưng không cho thêm vào giỏ. */
    public Product detail(int productId) {
        Product p = Tx.read(con -> productDAO.findById(con, productId));
        if (p == null) {
            throw new NotFoundException("Không tìm thấy món ăn.");
        }
        return p;
    }
}
