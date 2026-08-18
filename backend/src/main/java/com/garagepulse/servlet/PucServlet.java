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
import java.util.ArrayList;
import java.util.List;

/**
 * GET    /api/puc?vehicleId=X  -> full PUC test history for that vehicle
 * POST   /api/puc              -> add a new PUC record
 *        {vehicleId, testDate, validUntil, certNumber, result, cost}
 * DELETE /api/puc?id=X         -> remove a single PUC record
 */
@WebServlet("/api/puc")
public class PucServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        int userId = (int) req.getSession().getAttribute("userId");
        int vehicleId;
        try {
            vehicleId = Integer.parseInt(req.getParameter("vehicleId"));
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.errorMessage("A valid vehicleId is required."));
            return;
        }

        try {
            if (!VehicleOwnership.belongsToUser(vehicleId, userId)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                resp.getWriter().write(JsonUtil.errorMessage("That vehicle doesn't belong to you."));
                return;
            }
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            resp.getWriter().write(JsonUtil.errorMessage(e.getMessage()));
            return;
        }

        String sql = "SELECT id, test_date, valid_until, certificate_number, result, cost " +
                     "FROM puc_records WHERE vehicle_id = ? ORDER BY test_date DESC";
        List<String> rows = new ArrayList<>();

        try (Connection conn = DBConnection.get();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, vehicleId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(JsonUtil.object(
                        JsonUtil.field("id", rs.getInt("id")) + "," +
                        JsonUtil.field("testDate", String.valueOf(rs.getDate("test_date"))) + "," +
                        JsonUtil.field("validUntil", String.valueOf(rs.getDate("valid_until"))) + "," +
                        JsonUtil.field("certNumber", rs.getString("certificate_number")) + "," +
                        JsonUtil.field("result", rs.getString("result")) + "," +
                        JsonUtil.field("cost", rs.getBigDecimal("cost"))
                    ));
                }
            }
            resp.getWriter().write(JsonUtil.array(rows));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.errorMessage("Could not load PUC history: " + e.getMessage()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        int userId = (int) req.getSession().getAttribute("userId");
        int vehicleId;
        try {
            vehicleId = Integer.parseInt(req.getParameter("vehicleId"));
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.errorMessage("A valid vehicleId is required."));
            return;
        }

        try {
            if (!VehicleOwnership.belongsToUser(vehicleId, userId)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                resp.getWriter().write(JsonUtil.errorMessage("That vehicle doesn't belong to you."));
                return;
            }
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            resp.getWriter().write(JsonUtil.errorMessage(e.getMessage()));
            return;
        }

        String testDate = req.getParameter("testDate");
        String validUntil = req.getParameter("validUntil");
        String certNumber = req.getParameter("certNumber");
        String result = req.getParameter("result"); // 'Pass' or 'Fail'
        String costStr = req.getParameter("cost");   // optional, defaults to 500 in the DB

        if (isBlank(testDate) || isBlank(validUntil) || isBlank(result)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.errorMessage("Test date, valid-until date and result are required."));
            return;
        }

        String sql = "INSERT INTO puc_records (vehicle_id, test_date, valid_until, certificate_number, result, cost) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.get();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, vehicleId);
            stmt.setDate(2, Date.valueOf(testDate));
            stmt.setDate(3, Date.valueOf(validUntil));
            stmt.setString(4, isBlank(certNumber) ? null : certNumber.trim());
            stmt.setString(5, result.trim());
            stmt.setBigDecimal(6, isBlank(costStr) ? new BigDecimal("500.00") : new BigDecimal(costStr));
            stmt.executeUpdate();
            resp.getWriter().write(JsonUtil.successMessage("PUC record added."));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.errorMessage("Could not add PUC record: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.errorMessage("Invalid date or cost value."));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        int userId = (int) req.getSession().getAttribute("userId");

        int recordId;
        try {
            recordId = Integer.parseInt(req.getParameter("id"));
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.errorMessage("A valid record id is required."));
            return;
        }

        try {
            int vehicleId = VehicleOwnership.vehicleIdForRecord("puc_records", recordId);
            if (vehicleId == -1 || !VehicleOwnership.belongsToUser(vehicleId, userId)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                resp.getWriter().write(JsonUtil.errorMessage("That PUC record doesn't belong to you."));
                return;
            }

            try (Connection conn = DBConnection.get();
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM puc_records WHERE id = ?")) {
                stmt.setInt(1, recordId);
                stmt.executeUpdate();
            }
            resp.getWriter().write(JsonUtil.successMessage("PUC record removed."));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.errorMessage("Could not remove PUC record: " + e.getMessage()));
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
