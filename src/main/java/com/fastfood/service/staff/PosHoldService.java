package com.fastfood.service.staff;

import com.fastfood.common.constant.Constants.BusinessRule;
import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.JdbcSupport;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.dao.staff.PosHoldDAO;
import com.fastfood.model.entity.OperationEntities.PosHold;
import com.fastfood.model.entity.OperationEntities.PosHoldItem;
import com.fastfood.model.entity.MenuEntities.Product;
import com.fastfood.service.Tx;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PosHoldService {

    private static final int MAX_HOLDS_PER_CASHIER = 20;
    private static final int MAX_LABEL_LENGTH = 100;
    private static final int MAX_NOTE_LENGTH = 500;

    private final PosHoldDAO holdDAO = new PosHoldDAO();
    private final ProductDAO productDAO = new ProductDAO();

    public List<PosHold> myHolds(int cashierId) {
        return Tx.read(con -> holdDAO.findByCashier(con, cashierId));
    }

    public PosHold findOwn(int holdId, int cashierId) {
        return Tx.read(con -> requireOwn(con, holdId, cashierId));
    }

    public PosHold hold(int cashierId, String label, String note, Map<Integer, Integer> lines) {
        String name = requireLabel(label);
        String text = optionalNote(note);
        if (lines == null || lines.isEmpty()) {
            throw new ValidationException("Giỏ đang trống, không có gì để treo.");
        }
        for (Integer quantity : lines.values()) {
            requireSaneQuantity(quantity == null ? 0 : quantity);
        }
        LocalDateTime now = DateTimeUtil.now();

        try {
            return Tx.write(con -> {
                if (holdDAO.countByCashier(con, cashierId) >= MAX_HOLDS_PER_CASHIER) {
                    throw new BusinessException("Bạn đang treo " + MAX_HOLDS_PER_CASHIER
                            + " phiếu — nhiều nhất rồi. Hãy tính tiền hoặc bỏ bớt phiếu cũ.");
                }
                PosHold hold = new PosHold();
                hold.setCashierId(cashierId);
                hold.setLabel(name);
                hold.setNote(text);
                hold.setCreatedAt(now);
                holdDAO.insert(con, hold);

                for (Map.Entry<Integer, Integer> line : lines.entrySet()) {
                    requireProduct(con, line.getKey());
                    holdDAO.addItem(con, hold.getHoldId(), line.getKey(), line.getValue());
                }
                hold.setItems(holdDAO.findItems(con, hold.getHoldId()));
                return hold;
            });
        } catch (RuntimeException e) {
            if (!JdbcSupport.isUniqueViolation(e)) {
                throw e;
            }
            throw new BusinessException("Bạn đang có một phiếu treo tên \"" + name
                    + "\". Hãy đặt tên khác để không lẫn.");
        }
    }

    public void rename(int holdId, int cashierId, String label, String note) {
        String name = requireLabel(label);
        String text = optionalNote(note);
        LocalDateTime now = DateTimeUtil.now();
        try {
            Tx.writeVoid(con -> {
                requireOwn(con, holdId, cashierId);
                holdDAO.updateHeader(con, holdId, cashierId, name, text, now);
            });
        } catch (RuntimeException e) {
            if (!JdbcSupport.isUniqueViolation(e)) {
                throw e;
            }
            throw new BusinessException("Bạn đang có một phiếu treo khác tên \"" + name + "\".");
        }
    }

    public void setQuantity(int holdId, int cashierId, int productId, int quantity) {
        if (quantity > 0) {
            requireSaneQuantity(quantity);
        }
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            requireOwn(con, holdId, cashierId);
            if (quantity <= 0) {
                holdDAO.removeItem(con, holdId, productId);
            } else {
                holdDAO.updateItemQuantity(con, holdId, productId, quantity);
            }
            if (holdDAO.findItems(con, holdId).isEmpty()) {
                holdDAO.delete(con, holdId, cashierId);
            } else {
                holdDAO.touch(con, holdId, now);
            }
        });
    }

    public void addItem(int holdId, int cashierId, int productId, int quantity) {
        requireSaneQuantity(quantity);
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            requireOwn(con, holdId, cashierId);
            requireProduct(con, productId);
            holdDAO.addItem(con, holdId, productId, quantity);
            holdDAO.touch(con, holdId, now);
        });
    }

    public Map<Integer, Integer> resume(int holdId, int cashierId) {
        return Tx.write(con -> {
            PosHold hold = requireOwn(con, holdId, cashierId);
            Map<Integer, Integer> lines = new LinkedHashMap<>();
            for (PosHoldItem item : hold.getItems()) {
                lines.put(item.getProductId(), item.getQuantity());
            }
            holdDAO.delete(con, holdId, cashierId);
            return lines;
        });
    }

    public void discard(int holdId, int cashierId) {
        Tx.writeVoid(con -> {
            requireOwn(con, holdId, cashierId);
            holdDAO.delete(con, holdId, cashierId);
        });
    }

    private PosHold requireOwn(Connection con, int holdId, int cashierId) throws SQLException {
        PosHold hold = holdDAO.findById(con, holdId);
        if (hold == null) {
            throw new NotFoundException("Không tìm thấy phiếu treo. Có thể ai đó vừa tính tiền phiếu này.");
        }
        if (hold.getCashierId() != cashierId) {
            throw new BusinessException("Đây là phiếu treo của thu ngân khác.");
        }
        return hold;
    }

    private Product requireProduct(Connection con, int productId) throws SQLException {
        Product product = productDAO.findById(con, productId);
        if (product == null) {
            throw new NotFoundException("Không tìm thấy món ăn.");
        }
        return product;
    }

    private void requireSaneQuantity(int quantity) {
        if (quantity <= 0) {
            throw new ValidationException("Số lượng phải lớn hơn 0.");
        }
        if (quantity > BusinessRule.MAX_QUANTITY_PER_LINE) {
            throw new ValidationException("Mỗi món chỉ đặt được tối đa "
                    + BusinessRule.MAX_QUANTITY_PER_LINE + " phần.");
        }
    }

    private String requireLabel(String label) {
        String name = label == null ? "" : label.trim();
        if (name.isEmpty()) {
            throw new ValidationException("Đặt tên cho phiếu để lát nữa còn nhận ra, "
                    + "ví dụ \"Bàn 3\" hoặc \"Anh áo xanh\".");
        }
        if (name.length() > MAX_LABEL_LENGTH) {
            throw new ValidationException("Tên phiếu tối đa " + MAX_LABEL_LENGTH + " ký tự.");
        }
        return name;
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
