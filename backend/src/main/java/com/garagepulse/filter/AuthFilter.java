package com.garagepulse.filter;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Blocks access to every protected endpoint unless the user has an active
 * session (i.e. is logged in). Keeps the "is this person logged in?" check
 * in ONE place instead of repeating it in every servlet.
 */
@WebFilter({"/api/vehicles", "/api/services", "/api/insurance",
            "/api/puc", "/api/notifications", "/api/cost", "/api/profile"})
public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // Let CORS preflight requests through untouched.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(req, res);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"success\":false,\"message\":\"Please log in first.\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    @Override public void init(FilterConfig filterConfig) { }
    @Override public void destroy() { }
}
