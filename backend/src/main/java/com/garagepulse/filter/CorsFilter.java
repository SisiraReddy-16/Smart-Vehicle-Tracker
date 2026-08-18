package com.garagepulse.filter;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Lets the static frontend (opened from a different origin/port than
 * Tomcat) call these APIs with session cookies attached.
 * If you later serve the frontend from the same Tomcat app, this filter
 * becomes unnecessary but is harmless to leave in.
 */
@WebFilter("/api/*")
public class CorsFilter implements Filter {

    // Update this if your frontend runs on a different host/port.
    private static final String ALLOWED_ORIGIN = "*";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String origin = request.getHeader("Origin");
        response.setHeader("Access-Control-Allow-Origin", origin != null ? origin : ALLOWED_ORIGIN);
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }
        chain.doFilter(req, res);
    }

    @Override public void init(FilterConfig filterConfig) { }
    @Override public void destroy() { }
}
