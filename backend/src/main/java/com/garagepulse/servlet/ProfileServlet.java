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

/**
 * GET /api/profile
 * Returns the logged-in user's basic account info (username, mobile number,
 * vehicle count) for the simple Profile page - just a person icon, no
 * uploaded photo, matching the "keep it simple like a contacts app" ask.
 */
@WebServlet("/api/profile")
public class ProfileServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        int userId = (int) req.getSession().getAttribute("userId");

        String sql = "SELECT u.username, u.mobile_number, " +
                     "  (SELECT COUNT(*) FROM vehicles v WHERE v.user_id = u.id) AS vehicle_count " +
                     "FROM users u WHERE u.id = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    resp.getWriter().write(JsonUtil.errorMessage("Account not found."));
                    return;
                }
                String json = JsonUtil.object(
                    JsonUtil.field("username", rs.getString("username")) + "," +
                    JsonUtil.field("mobile", rs.getString("mobile_number")) + "," +
                    JsonUtil.field("vehicleCount", rs.getInt("vehicle_count"))
                );
                resp.getWriter().write(json);
            }
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.errorMessage("Could not load profile: " + e.getMessage()));
        }
    }
}
