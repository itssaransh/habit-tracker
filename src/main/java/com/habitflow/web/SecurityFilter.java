package com.habitflow.web;

import com.habitflow.model.UserAccount;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

// The session stores only a server-authenticated account. Every unsafe request carries a CSRF token.
@WebFilter("/*")
public final class SecurityFilter implements Filter {
    public static final String ACCOUNT = "authenticatedAccount";
    public static final String CSRF = "csrfToken";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override public void init(FilterConfig config) {
        var cookie = config.getServletContext().getSessionCookieConfig();
        cookie.setHttpOnly(true);
        cookie.setSecure("production".equalsIgnoreCase(System.getenv("APP_ENV")));
        cookie.setAttribute("SameSite", "Lax");
        config.getServletContext().setSessionTimeout(30);
    }
    @Override public void doFilter(ServletRequest rawRequest, ServletResponse rawResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request=(HttpServletRequest)rawRequest;
        HttpServletResponse response=(HttpServletResponse)rawResponse;
        request.setCharacterEncoding("UTF-8");
        response.setHeader("X-Content-Type-Options","nosniff");
        response.setHeader("Referrer-Policy","strict-origin-when-cross-origin");
        response.setHeader("Cache-Control","no-store");
        String path=request.getRequestURI().substring(request.getContextPath().length());
        HttpSession session=request.getSession(true);
        if(session.getAttribute(CSRF)==null) session.setAttribute(CSRF,newToken());
        request.setAttribute(CSRF,session.getAttribute(CSRF));

        boolean publicPath=path.equals("/login") || path.equals("/register") || path.equals("/health") || path.startsWith("/assets/") || path.equals("/favicon.ico");
        if(!publicPath && session.getAttribute(ACCOUNT)==null) {
            if(path.startsWith("/api/")) response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            else response.sendRedirect(request.getContextPath()+"/login");
            return;
        }
        String method=request.getMethod();
        if(method.equals("POST") || method.equals("PUT") || method.equals("PATCH") || method.equals("DELETE")) {
            String expected=(String)session.getAttribute(CSRF);
            String actual=request.getHeader("X-CSRF-Token");
            if(actual==null) actual=request.getParameter("csrfToken");
            if(actual==null || expected==null || !MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),actual.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,"Invalid request token"); return;
            }
        }
        chain.doFilter(request,response);
    }
    public static String newToken() { byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
}
