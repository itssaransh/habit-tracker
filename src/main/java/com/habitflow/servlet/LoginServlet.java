package com.habitflow.servlet;

import com.habitflow.model.UserAccount;
import com.habitflow.service.AuthService;
import com.habitflow.web.SecurityFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet("/login")
public final class LoginServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res) throws ServletException,IOException {
        if(req.getSession().getAttribute(SecurityFilter.ACCOUNT)!=null) { res.sendRedirect(req.getContextPath()+"/"); return; }
        req.setAttribute("mode","login"); req.getRequestDispatcher("/WEB-INF/auth.jsp").forward(req,res);
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res) throws ServletException,IOException {
        try {
            AuthService auth=(AuthService)getServletContext().getAttribute("authService");
            UserAccount account=auth.login(req.getParameter("email"),req.getParameter("password"));
            HttpSession session=req.getSession(); req.changeSessionId();
            boolean remember="on".equals(req.getParameter("remember"));
            session.setMaxInactiveInterval(remember ? 60*60*24*30 : 60*30);
            setSessionCookie(req,res,session.getId(),remember?60*60*24*30:-1);
            session.setAttribute(SecurityFilter.ACCOUNT,account); session.setAttribute(SecurityFilter.CSRF,SecurityFilter.newToken());
            res.sendRedirect(req.getContextPath()+"/");
        } catch(IllegalArgumentException e) { req.setAttribute("mode","login"); req.setAttribute("error",e.getMessage()); req.getRequestDispatcher("/WEB-INF/auth.jsp").forward(req,res); }
    }
    private void setSessionCookie(HttpServletRequest request,HttpServletResponse response,String sessionId,int maxAge) {
        String path=request.getContextPath().isEmpty()?"/":request.getContextPath();
        String cookie="JSESSIONID="+sessionId+"; Path="+path+"; HttpOnly; SameSite=Lax";
        if(request.isSecure() || "production".equalsIgnoreCase(System.getenv("APP_ENV"))) cookie+="; Secure";
        if(maxAge>=0) cookie+="; Max-Age="+maxAge;
        response.setHeader("Set-Cookie",cookie);
    }
}
