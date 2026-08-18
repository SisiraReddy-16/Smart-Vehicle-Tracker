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
 * GET    /api/vehicles         -> list the logged-in user's vehicles (My Garage page)
 * POST   /api/vehicles         -> add a new vehicle {plate, brand, model, type, year}
 * DELETE /api/vehicles?id=X    -> remove a vehicle (and, via ON DELETE CASCADE, its
 *                                  service/insurance/PUC history) once it's no longer needed
 * AuthFilter guarantees a valid session before any method runs.
 */
@WebServlet("/api/vehicles")
public class VehicleServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        int userId = (int) req.getSession().getAttribute("userId");

        String sql = "SELECT id, license_plate, brand, model, vehicle_type, reg_year FROM vehicles WHERE user_id = ?";
        List<String> vehicles = new ArrayList<>();

        try (Connection conn = DBConnection.get();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    vehicles.add(JsonUtil.object(
                        JsonUtil.field("id", rs.getInt("id")) + "," +
                        JsonUtil.field("licensePlate", rs.getString("license_plate")) + "," +
                        JsonUtil.field("brand", rs.getString("brand")) + "," +
                        JsonUtil.field("model", rs.getString("model")) + "," +
                        JsonUtil.field("vehicleType", rs.getString("vehicle_type")) + "," +
                        JsonUtil.field("regYear", rs.getInt("reg_year"))
                    ));
                }
            }
            resp.getWriter().write(JsonUtil.array(vehicles));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.errorMessage("Could not load vehicles: " + e.getMessage()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        int userId = (int) req.getSession().getAttribute("userId");

        String plate = req.getParameter("plate");
        String brand = req.getParameter("brand");
        String model = req.getParameter("model");
        String type = req.getParameter("type");     // 'Car' or 'Bike'
        String yearStr = req.getParameter("year");

        if (isBlank(plate) || isBlank(brand) || isBlank(model) || isBlank(type)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.errorMessage("Plate, brand, model and type are required."));
            return;
        }

        String sql = "INSERT INTO vehicles (user_id, license_plate, brand, model, vehicle_type, reg_year) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.get();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, plate.trim());
            stmt.setString(3, brand.trim());
            stmt.setString(4, model.trim());
            stmt.setString(5, type.trim());
            if (isBlank(yearStr)) {
                stmt.setNull(6, Types.INTEGER);
            } else {
                stmt.setInt(6, Integer.parseInt(yearStr));
            }
            stmt.executeUpdate();
            resp.getWriter().write(JsonUtil.successMessage("Vehicle added."));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.errorMessage("Could not add vehicle: " + e.getMessage()));
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.errorMessage("Year must be a number."));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        int userId = (int) req.getSession().getAttribute("userId");

        String idStr = req.getParameter("id");
        if (isBlank(idStr)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.errorMessage("Vehicle id is required."));
            return;
        }

        int vehicleId;
        try {
            vehicleId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.errorMessage("Vehicle id must be a number."));
            return;
        }

        String sql = "DELETE FROM vehicles WHERE id = ? AND user_id = ?";
        try (Connection conn = DBConnection.get();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, vehicleId);
            stmt.setInt(2, userId);
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                resp.getWriter().write(JsonUtil.errorMessage("That vehicle doesn't belong to you or no longer exists."));
                return;
            }
            resp.getWriter().write(JsonUtil.successMessage("Vehicle removed."));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.errorMessage("Could not remove vehicle: " + e.getMessage()));
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
