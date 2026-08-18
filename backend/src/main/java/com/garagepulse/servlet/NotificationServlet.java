package com.garagepulse.servlet;

import com.garagepulse.util.DBConnection;
import com.garagepulse.util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * GET /api/notifications
 * Builds the Dashboard's notification feed by looking directly at:
 *   - each vehicle's most recent service's next_due_date
 *   - each vehicle's most recent insurance policy's end_date
 *   - each vehicle's most recent PUC record's valid_until
 * for every vehicle the logged-in user owns, within +/- 30 days of today.
 * Nothing is duplicated into a separate notifications table.
 */
@WebServlet("/api/notifications")
public class NotificationServlet extends HttpServlet {

    private static final String SQL =
        "SELECT v.brand, v.model, 'Service' AS category, s.next_due_date AS due_date " +
        "FROM vehicles v JOIN services s ON s.vehicle_id = v.id " +
        "WHERE v.user_id = ? AND s.next_due_date IS NOT NULL " +
        "  AND s.id = (SELECT id FROM services WHERE vehicle_id = v.id ORDER BY service_date DESC LIMIT 1) " +
        "UNION ALL " +
        "SELECT v.brand, v.model, 'Insurance' AS category, i.end_date AS due_date " +
        "FROM vehicles v JOIN insurance_policies i ON i.vehicle_id = v.id " +
        "WHERE v.user_id = ? " +
        "  AND i.id = (SELECT id FROM insurance_policies WHERE vehicle_id = v.id ORDER BY end_date DESC LIMIT 1) " +
        "UNION ALL " +
        "SELECT v.brand, v.model, 'PUC' AS category, p.valid_until AS due_date " +
        "FROM vehicles v JOIN puc_records p ON p.vehicle_id = v.id " +
        "WHERE v.user_id = ? " +
        "  AND p.id = (SELECT id FROM puc_records WHERE vehicle_id = v.id ORDER BY test_date DESC LIMIT 1) " +
        "ORDER BY due_date ASC";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        int userId = (int) req.getSession().getAttribute("userId");
        LocalDate today = LocalDate.now();
        List<String> notifications = new ArrayList<>();

        try (Connection conn = DBConnection.get();
             PreparedStatement stmt = conn.prepareStatement(SQL)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            stmt.setInt(3, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Date dueDateSql = rs.getDate("due_date");
                    LocalDate dueDate = dueDateSql.toLocalDate();
                    long daysLeft = ChronoUnit.DAYS.between(today, dueDate);

                    // Only surface things due within the next 30 days, or already overdue.
                    if (daysLeft > 30) continue;

                    String vehicleName = rs.getString("brand") + " " + rs.getString("model");
                    String category = rs.getString("category");
                    String severity = daysLeft < 0 ? "critical" : (daysLeft <= 7 ? "warning" : "info");
                    String message = buildMessage(vehicleName, category, daysLeft);

                    notifications.add(JsonUtil.object(
                        JsonUtil.field("category", category) + "," +
                        JsonUtil.field("vehicle", vehicleName) + "," +
                        JsonUtil.field("dueDate", String.valueOf(dueDate)) + "," +
                        JsonUtil.field("daysLeft", (int) daysLeft) + "," +
                        JsonUtil.field("severity", severity) + "," +
                        JsonUtil.field("message", message)
                    ));
                }
            }
            resp.getWriter().write(JsonUtil.array(notifications));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.errorMessage("Could not load notifications: " + e.getMessage()));
        }
    }

    private String buildMessage(String vehicleName, String category, long daysLeft) {
        String action = category.equals("Service") ? "service" :
                         category.equals("Insurance") ? "insurance" : "PUC";
        if (daysLeft < -1) {
            return vehicleName + " " + action + " expired " + Math.abs(daysLeft) + " days ago";
        } else if (daysLeft == -1) {
            return vehicleName + " " + action + " expired yesterday";
        } else if (daysLeft == 0) {
            return vehicleName + " " + action + " is due today";
        } else {
            return vehicleName + " " + action + " due in " + daysLeft + " days";
        }
    }
}
