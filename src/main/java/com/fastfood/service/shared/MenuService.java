package com.fastfood.service.shared;

import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.dao.shared.CategoryDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.model.entity.MenuEntities.Category;
import com.fastfood.model.entity.MenuEntities.Product;
import com.fastfood.service.Tx;

import java.util.List;

public class MenuService {

    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();

    public List<Product> browse(Integer categoryId, String keyword) {
        return browse(categoryId, keyword, null);
    }

    public List<Product> browse(Integer categoryId, String keyword, String sort) {
        return Tx.read(con -> productDAO.findMenu(con, categoryId, keyword, sort));
    }

    public String sortOrDefault(String sort) {
        return ProductDAO.menuSortOrDefault(sort);
    }

    public List<Category> activeCategories() {
        return Tx.read(categoryDAO::findActive);
    }

    public Product detail(int productId) {
        Product p = Tx.read(con -> productDAO.findById(con, productId));
        if (p == null) {
            throw new NotFoundException("Không tìm thấy món ăn.");
        }
        return p;
    }
}
