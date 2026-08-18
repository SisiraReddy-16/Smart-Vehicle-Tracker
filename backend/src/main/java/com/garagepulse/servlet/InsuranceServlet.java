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
 * GET    /api/insurance?vehicleId=X  -> full policy history for that vehicle
 * POST   /api/insurance              -> add a new policy
 *        {vehicleId, provider, policyNumber, startDate, endDate, premium}
 * DELETE /api/insurance?id=X         -> remove a single policy record
 */
@WebServlet("/api/insurance")
public class InsuranceServlet extends HttpServlet {

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

        String sql = "SELECT id, provider_name, policy_number, start_date, end_date, premium_amount " +
                     "FROM insurance_policies WHERE vehicle_id = ? ORDER BY end_date DESC";
        List<String> rows = new ArrayList<>();

        try (Connection conn = DBConnection.get();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, vehicleId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Date endDate = rs.getDate("end_date");
                    boolean active = endDate.toLocalDate().isAfter(java.time.LocalDate.now());
                    rows.add(JsonUtil.object(
                        JsonUtil.field("id", rs.getInt("id")) + "," +
                        JsonUtil.field("provider", rs.getString("provider_name")) + "," +
                        JsonUtil.field("policyNumber", rs.getString("policy_number")) + "," +
                        JsonUtil.field("startDate", String.valueOf(rs.getDate("start_date"))) + "," +
                        JsonUtil.field("endDate", String.valueOf(endDate)) + "," +
                        JsonUtil.field("premium", rs.getBigDecimal("premium_amount")) + "," +
                        JsonUtil.field("status", active ? "Active" : "Expired")
                    ));
                }
            }
            resp.getWriter().write(JsonUtil.array(rows));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.errorMessage("Could not load insurance history: " + e.getMessage()));
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

        String provider = req.getParameter("provider");
        String policyNumber = req.getParameter("policyNumber");
        String startDate = req.getParameter("startDate");
        String endDate = req.getParameter("endDate");
        String premiumStr = req.getParameter("premium");

        if (isBlank(provider) || isBlank(policyNumber) || isBlank(startDate) || isBlank(endDate) || isBlank(premiumStr)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.errorMessage("All insurance fields are required."));
            return;
        }

        String sql = "INSERT INTO insurance_policies (vehicle_id, provider_name, policy_number, start_date, end_date, premium_amount) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.get();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, vehicleId);
            stmt.setString(2, provider.trim());
            stmt.setString(3, policyNumber.trim());
            stmt.setDate(4, Date.valueOf(startDate));
            stmt.setDate(5, Date.valueOf(endDate));
            stmt.setBigDecimal(6, new BigDecimal(premiumStr));
            stmt.executeUpdate();
            resp.getWriter().write(JsonUtil.successMessage("Insurance policy added."));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.errorMessage("Could not add policy: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.errorMessage("Invalid date or premium value."));
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
            int vehicleId = VehicleOwnership.vehicleIdForRecord("insurance_policies", recordId);
            if (vehicleId == -1 || !VehicleOwnership.belongsToUser(vehicleId, userId)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                resp.getWriter().write(JsonUtil.errorMessage("That policy doesn't belong to you."));
                return;
            }

            try (Connection conn = DBConnection.get();
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM insurance_policies WHERE id = ?")) {
                stmt.setInt(1, recordId);
                stmt.executeUpdate();
            }
            resp.getWriter().write(JsonUtil.successMessage("Policy removed."));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.errorMessage("Could not remove policy: " + e.getMessage()));
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
