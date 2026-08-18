package com.garagepulse.servlet;

import com.garagepulse.util.DBConnection;
import com.garagepulse.util.JsonUtil;
import com.garagepulse.util.PasswordUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.*;

/**
 * POST /api/login
 * Body: username, password
 *
 * Every login re-hashes the submitted password with the user's stored salt
 * and compares it to the stored hash -- the password itself is never stored
 * or compared in plain text.
 */
@WebServlet("/api/login")
public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");

        String username = req.getParameter("username");
        String password = req.getParameter("password");

        if (username == null || password == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.errorMessage("Username and password are required."));
            return;
        }

        String sql = "SELECT id, password_hash, salt FROM users WHERE username = ?";

        try (Connection conn = DBConnection.get();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username.trim());

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    resp.getWriter().write(JsonUtil.errorMessage("Incorrect username or password."));
                    return;
                }

                int userId = rs.getInt("id");
                String storedHash = rs.getString("password_hash");
                String storedSalt = rs.getString("salt");

                boolean matches = PasswordUtil.verify(password, storedSalt, storedHash);
                if (!matches) {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    resp.getWriter().write(JsonUtil.errorMessage("Incorrect username or password."));
                    return;
                }

                HttpSession session = req.getSession(true);
                session.setAttribute("userId", userId);
                session.setAttribute("username", username.trim());

                resp.getWriter().write(JsonUtil.successMessage("Logged in."));
            }

        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.errorMessage("Login failed: " + e.getMessage()));
        }
    }
}
