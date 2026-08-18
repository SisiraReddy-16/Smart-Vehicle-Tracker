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
import java.util.ArrayList;
import java.util.List;

/**
 * GET    /api/services?vehicleId=X  -> latest 5 service records for that vehicle
 * POST   /api/services              -> add a new service record
 *        {vehicleId, serviceDate, serviceType, cost, nextDueDate, shopName}
 * DELETE /api/services?id=X         -> remove a single service record
 *
 * After every insert, anything past the 5 most recent rows for that vehicle
 * is deleted -- this is the "keep last 5" rule from the brief, enforced in
 * the database itself so no other code path can violate it.
 */
@WebServlet("/api/services")
public class ServiceServlet extends HttpServlet {

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

        String sql = "SELECT id, service_date, service_type, cost, next_due_date, shop_name " +
                     "FROM services WHERE vehicle_id = ? ORDER BY service_date DESC LIMIT 5";
        List<String> rows = new ArrayList<>();

        try (Connection conn = DBConnection.get();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, vehicleId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(JsonUtil.object(
                        JsonUtil.field("id", rs.getInt("id")) + "," +
                        JsonUtil.field("serviceDate", String.valueOf(rs.getDate("service_date"))) + "," +
                        JsonUtil.field("serviceType", rs.getString("service_type")) + "," +
                        JsonUtil.field("cost", rs.getBigDecimal("cost")) + "," +
                        JsonUtil.field("nextDueDate", String.valueOf(rs.getDate("next_due_date"))) + "," +
                        JsonUtil.field("shopName", rs.getString("shop_name"))
                    ));
                }
            }
            resp.getWriter().write(JsonUtil.array(rows));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.errorMessage("Could not load services: " + e.getMessage()));
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

        String serviceDate = req.getParameter("serviceDate");
        String serviceType = req.getParameter("serviceType");
        String costStr = req.getParameter("cost");
        String nextDueDate = req.getParameter("nextDueDate"); // optional
        String shopName = req.getParameter("shopName");       // optional

        if (isBlank(serviceDate) || isBlank(serviceType) || isBlank(costStr)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.errorMessage("Service date, type and cost are required."));
            return;
        }

        String insertSql = "INSERT INTO services (vehicle_id, service_date, service_type, cost, next_due_date, shop_name) " +
                            "VALUES (?, ?, ?, ?, ?, ?)";

        // Keeps ONLY the 5 most recent rows for this vehicle - deletes the rest.
        String purgeSql = "DELETE FROM services WHERE vehicle_id = ? AND id NOT IN (" +
                           "  SELECT id FROM (SELECT id FROM services WHERE vehicle_id = ? " +
                           "  ORDER BY service_date DESC LIMIT 5) AS keep_ids)";

        try (Connection conn = DBConnection.get()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                    insert.setInt(1, vehicleId);
                    insert.setDate(2, Date.valueOf(serviceDate));
                    insert.setString(3, serviceType.trim());
                    insert.setBigDecimal(4, new java.math.BigDecimal(costStr));
                    if (isBlank(nextDueDate)) insert.setNull(5, Types.DATE);
                    else insert.setDate(5, Date.valueOf(nextDueDate));
                    insert.setString(6, isBlank(shopName) ? null : shopName.trim());
                    insert.executeUpdate();
                }
                try (PreparedStatement purge = conn.prepareStatement(purgeSql)) {
                    purge.setInt(1, vehicleId);
                    purge.setInt(2, vehicleId);
                    purge.executeUpdate();
                }
                conn.commit();
                resp.getWriter().write(JsonUtil.successMessage("Service record added."));
            } catch (SQLException | IllegalArgumentException inner) {
                conn.rollback();
                throw inner;
            }
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.errorMessage("Could not add service: " + e.getMessage()));
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
            int vehicleId = VehicleOwnership.vehicleIdForRecord("services", recordId);
            if (vehicleId == -1 || !VehicleOwnership.belongsToUser(vehicleId, userId)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                resp.getWriter().write(JsonUtil.errorMessage("That service record doesn't belong to you."));
                return;
            }

            try (Connection conn = DBConnection.get();
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM services WHERE id = ?")) {
                stmt.setInt(1, recordId);
                stmt.executeUpdate();
            }
            resp.getWriter().write(JsonUtil.successMessage("Service record removed."));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.errorMessage("Could not remove service record: " + e.getMessage()));
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
