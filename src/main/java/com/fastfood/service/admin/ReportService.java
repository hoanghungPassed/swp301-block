package com.fastfood.service.admin;

import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.config.AppConfig;
import com.fastfood.dao.admin.ReportDAO;
import com.fastfood.model.dto.Dtos.DashboardKpi;
import com.fastfood.model.dto.Dtos.ReportRow;
import com.fastfood.service.Tx;

import java.time.LocalDateTime;
import java.util.List;

public class ReportService {

    private final ReportDAO reportDAO = new ReportDAO();

    public DashboardKpi loadKpi(LocalDateTime from, LocalDateTime to) {
        LocalDateTime start = from != null ? from : DateTimeUtil.now().toLocalDate().atStartOfDay();
        LocalDateTime end = to != null ? to : DateTimeUtil.now();
        return Tx.read(con -> reportDAO.loadKpi(con, start, end, AppConfig.pickupOverdueMinutes()));
    }

    public List<ReportRow> bestSellers(LocalDateTime from, LocalDateTime to, int limit) {
        LocalDateTime start = from != null ? from : DateTimeUtil.now().minusDays(30);
        LocalDateTime end = to != null ? to : DateTimeUtil.now();
        return Tx.read(con -> reportDAO.bestSellers(con, start, end, limit));
    }

    public List<ReportRow> paymentSummary(LocalDateTime from, LocalDateTime to) {
        LocalDateTime start = from != null ? from : DateTimeUtil.now().minusDays(30);
        LocalDateTime end = to != null ? to : DateTimeUtil.now();
        return Tx.read(con -> reportDAO.paymentSummary(con, start, end));
    }

    public List<ReportRow> revenueByDay(LocalDateTime from, LocalDateTime to) {
        LocalDateTime start = from != null ? from : DateTimeUtil.now().minusDays(14);
        LocalDateTime end = to != null ? to : DateTimeUtil.now();
        return Tx.read(con -> reportDAO.revenueByDay(con, start, end));
    }
}
