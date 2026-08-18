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
 * POST /api/signup
 * Body (form or JSON handled the same way via request.getParameter):
 *   username, mobile, password
 *
 * New user starts with zero vehicles - they add their own afterwards.
 */
@WebServlet("/api/signup")
public class SignupServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");

        String username = req.getParameter("username");
        String mobile = req.getParameter("mobile");
        String password = req.getParameter("password");

        if (isBlank(username) || isBlank(mobile) || isBlank(password)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonUtil.errorMessage("Username, mobile number and password are all required."));
            return;
        }

        String salt = PasswordUtil.generateSalt();
        String hashedPassword = PasswordUtil.hash(password, salt);

        String sql = "INSERT INTO users (username, mobile_number, password_hash, salt) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.get();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, username.trim());
            stmt.setString(2, mobile.trim());
            stmt.setString(3, hashedPassword);
            stmt.setString(4, salt);
            stmt.executeUpdate();

            int newUserId;
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                keys.next();
                newUserId = keys.getInt(1);
            }

            // Log the new user straight in, same as the flow when they sign in later.
            HttpSession session = req.getSession(true);
            session.setAttribute("userId", newUserId);
            session.setAttribute("username", username.trim());

            resp.getWriter().write(JsonUtil.successMessage("Account created."));

        } catch (SQLIntegrityConstraintViolationException dup) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            resp.getWriter().write(JsonUtil.errorMessage("That username or mobile number is already registered."));
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(JsonUtil.errorMessage("Signup failed: " + e.getMessage()));
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
