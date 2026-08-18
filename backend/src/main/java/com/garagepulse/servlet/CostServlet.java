package com.garagepulse.servlet;

import com.garagepulse.util.DBConnection;
import com.garagepulse.util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;

/**
 * GET /api/cost
 * Returns two things in one payload:
 *  - breakdown: how much has actually been spent so far (Cost Analytics page)
 *  - upcomingEstimate: estimated cost of dues in the next 30 days, using the
 *    last known cost for each category as the estimate (Dashboard "Total Cost" tile)
 */
@WebServlet("/api/cost")
public class CostServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        int userId = (int) req.getSession().getAttribute("userId");

        try (Connection conn = DBConnection.get()) {
            BigDecimal serviceTotal = sumFor(conn, "services", "cost", userId);
            BigDecimal insuranceTotal = sumFor(conn, "insurance_policies", "premium_amount", userId);
            BigDecimal pucTotal = sumFor(conn, "puc_records", "cost", userId);
            BigDecimal grandTotal = serviceTotal.add(insuranceTotal).add(pucTotal);
            BigDecimal upcomingEstimate = estimateUpcomingDues(conn, userId);

            String json = JsonUtil.object(
                JsonUtil.field("serviceTotal", serviceTotal) + "," +
                JsonUtil.field("insuranceTotal", insuranceTotal) + "," +
                JsonUtil.field("pucTotal", pucTotal) + "," +
                JsonUtil.field("grandTotal", grandTotal) + "," +
                JsonUtil.field("upcomingEstimate", upcomingEstimate)
            );
            resp.getWriter().write(json);

        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.errorMessage("Could not load cost summary: " + e.getMessage()));
        }
    }

    private BigDecimal sumFor(Connection conn, String table, String column, int userId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(t." + column + "), 0) AS total FROM " + table + " t " +
                     "JOIN vehicles v ON t.vehicle_id = v.id WHERE v.user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getBigDecimal("total");
            }
        }
    }

    /** Sums the last-known cost for every due-soon/overdue item (next 30 days), same window as notifications. */
    private BigDecimal estimateUpcomingDues(Connection conn, int userId) throws SQLException {
        String sql =
            "SELECT s.cost AS amount, s.next_due_date AS due_date FROM vehicles v " +
            "JOIN services s ON s.vehicle_id = v.id " +
            "WHERE v.user_id = ? AND s.next_due_date IS NOT NULL " +
            "  AND s.id = (SELECT id FROM services WHERE vehicle_id = v.id ORDER BY service_date DESC LIMIT 1) " +
            "UNION ALL " +
            "SELECT i.premium_amount AS amount, i.end_date AS due_date FROM vehicles v " +
            "JOIN insurance_policies i ON i.vehicle_id = v.id " +
            "WHERE v.user_id = ? " +
            "  AND i.id = (SELECT id FROM insurance_policies WHERE vehicle_id = v.id ORDER BY end_date DESC LIMIT 1) " +
            "UNION ALL " +
            "SELECT p.cost AS amount, p.valid_until AS due_date FROM vehicles v " +
            "JOIN puc_records p ON p.vehicle_id = v.id " +
            "WHERE v.user_id = ? " +
            "  AND p.id = (SELECT id FROM puc_records WHERE vehicle_id = v.id ORDER BY test_date DESC LIMIT 1)";

        BigDecimal total = BigDecimal.ZERO;
        LocalDate cutoff = LocalDate.now().plusDays(30);

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            stmt.setInt(3, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Date dueDate = rs.getDate("due_date");
                    if (dueDate != null && !dueDate.toLocalDate().isAfter(cutoff)) {
                        total = total.add(rs.getBigDecimal("amount"));
                    }
                }
            }
        }
        return total;
    }
}
