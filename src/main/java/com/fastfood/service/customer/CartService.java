package com.fastfood.service.customer;

import com.fastfood.common.constant.Constants.BusinessRule;
import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.ValidationUtil;
import com.fastfood.dao.customer.CartDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.model.dto.Dtos.CartView;
import com.fastfood.model.entity.OrderEntities.CartItem;
import com.fastfood.model.entity.MenuEntities.Product;
import com.fastfood.service.Tx;

import java.util.List;

public class CartService {

    private final CartDAO cartDAO = new CartDAO();
    private final ProductDAO productDAO = new ProductDAO();

    public CartView getCart(int userId) {
        return Tx.read(con -> {
            int cartId = cartDAO.getOrCreateCartId(con, userId, DateTimeUtil.now());
            List<CartItem> items = cartDAO.findItems(con, cartId);
            CartView view = new CartView();
            view.setCartId(cartId);
            view.setItems(items);
            return view;
        });
    }

    public int countItems(int userId) {
        return Tx.read(con -> cartDAO.countItems(con, userId));
    }

    public void addProduct(int userId, int productId, int quantity) {
        ValidationUtil.requirePositive(quantity, "Số lượng");
        requireSaneQuantity(quantity);
        Tx.writeVoid(con -> {
            Product p = productDAO.findForCheckout(con, productId);
            if (p == null) {
                throw new NotFoundException("Không tìm thấy món ăn.");
            }
            if (!p.isOrderable()) {
                throw new BusinessException("Món \"" + p.getName() + "\" hiện không còn phục vụ.");
            }
            int cartId = cartDAO.getOrCreateCartId(con, userId, DateTimeUtil.now());
            requireSaneQuantity(cartDAO.quantityOf(con, cartId, productId) + quantity);
            cartDAO.addItem(con, cartId, productId, quantity);
            cartDAO.touch(con, cartId, DateTimeUtil.now());
        });
    }

    private void requireSaneQuantity(int quantity) {
        if (quantity > BusinessRule.MAX_QUANTITY_PER_LINE) {
            throw new BusinessException("Mỗi món chỉ đặt được tối đa "
                    + BusinessRule.MAX_QUANTITY_PER_LINE
                    + " phần. Cần nhiều hơn, vui lòng liên hệ cửa hàng.");
        }
    }

    public void updateQuantity(int userId, int cartItemId, int quantity) {
        requireSaneQuantity(quantity);
        Tx.writeVoid(con -> {
            int cartId = cartDAO.getOrCreateCartId(con, userId, DateTimeUtil.now());
            if (quantity <= 0) {
                cartDAO.removeItem(con, cartId, cartItemId);
            } else {
                cartDAO.updateQuantity(con, cartId, cartItemId, quantity);
            }
            cartDAO.touch(con, cartId, DateTimeUtil.now());
        });
    }

    public void removeItem(int userId, int cartItemId) {
        Tx.writeVoid(con -> {
            int cartId = cartDAO.getOrCreateCartId(con, userId, DateTimeUtil.now());
            cartDAO.removeItem(con, cartId, cartItemId);
            cartDAO.touch(con, cartId, DateTimeUtil.now());
        });
    }

    public int removeUnavailable(int userId) {
        return Tx.write(con -> {
            int cartId = cartDAO.getOrCreateCartId(con, userId, DateTimeUtil.now());
            int removed = 0;
            for (CartItem item : cartDAO.findItems(con, cartId)) {
                if (!item.isOrderable()) {
                    cartDAO.removeItem(con, cartId, item.getCartItemId());
                    removed++;
                }
            }
            cartDAO.touch(con, cartId, DateTimeUtil.now());
            return removed;
        });
    }
}
