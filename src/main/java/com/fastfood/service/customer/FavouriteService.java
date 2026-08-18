package com.fastfood.service.customer;

import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.JdbcSupport;
import com.fastfood.dao.customer.FavouriteDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.model.entity.MenuEntities.Favourite;
import com.fastfood.model.entity.MenuEntities.Product;
import com.fastfood.service.Tx;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class FavouriteService {

    private static final int MAX_NOTE_LENGTH = 255;

    private final FavouriteDAO favouriteDAO = new FavouriteDAO();
    private final ProductDAO productDAO = new ProductDAO();

    public List<Favourite> listOf(int customerId) {
        return Tx.read(con -> favouriteDAO.findByCustomer(con, customerId));
    }

    public Set<Integer> favouriteProductIds(Integer customerId) {
        if (customerId == null) {
            return Collections.emptySet();
        }
        return Tx.read(con -> favouriteDAO.productIdsOf(con, customerId));
    }

    public Favourite findOwn(int favouriteId, int customerId) {
        return Tx.read(con -> requireOwn(con, favouriteId, customerId));
    }

    public Favourite add(int customerId, int productId, String note) {
        String text = optionalNote(note);
        LocalDateTime now = DateTimeUtil.now();
        try {
            return Tx.write(con -> {
                Product product = productDAO.findById(con, productId);
                if (product == null) {
                    throw new NotFoundException("Không tìm thấy món ăn.");
                }
                Favourite fav = new Favourite();
                fav.setCustomerId(customerId);
                fav.setProductId(productId);
                fav.setNote(text);
                fav.setCreatedAt(now);
                favouriteDAO.insert(con, fav);
                return fav;
            });
        } catch (RuntimeException e) {
            if (!JdbcSupport.isUniqueViolation(e)) {
                throw e;
            }
            throw new BusinessException("Món này đã có trong danh sách món quen của bạn.");
        }
    }

    public void updateNote(int favouriteId, int customerId, String note) {
        String text = optionalNote(note);
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            requireOwn(con, favouriteId, customerId);
            favouriteDAO.updateNote(con, favouriteId, customerId, text, now);
        });
    }

    public void remove(int favouriteId, int customerId) {
        Tx.writeVoid(con -> {
            requireOwn(con, favouriteId, customerId);
            favouriteDAO.delete(con, favouriteId, customerId);
        });
    }

    public void removeByProduct(int customerId, int productId) {
        Tx.writeVoid(con -> favouriteDAO.deleteByProduct(con, customerId, productId));
    }

    private Favourite requireOwn(Connection con, int favouriteId, int customerId)
            throws SQLException {
        Favourite fav = favouriteDAO.findById(con, favouriteId);
        if (fav == null) {
            throw new NotFoundException("Không tìm thấy món quen này.");
        }
        if (fav.getCustomerId() != customerId) {
            throw new BusinessException("Đây là món quen của tài khoản khác.");
        }
        return fav;
    }

    private String optionalNote(String note) {
        String text = note == null ? "" : note.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > MAX_NOTE_LENGTH) {
            throw new ValidationException("Ghi chú tối đa " + MAX_NOTE_LENGTH + " ký tự.");
        }
        return text;
    }
}
