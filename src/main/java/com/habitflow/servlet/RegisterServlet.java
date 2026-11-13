package com.habitflow.servlet;

import com.habitflow.model.UserAccount;
import com.habitflow.service.AuthService;
import com.habitflow.web.SecurityFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet("/register")
public final class RegisterServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res) throws ServletException,IOException {
        if(req.getSession().getAttribute(SecurityFilter.ACCOUNT)!=null) { res.sendRedirect(req.getContextPath()+"/"); return; }
        req.setAttribute("mode","register"); req.getRequestDispatcher("/WEB-INF/auth.jsp").forward(req,res);
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res) throws ServletException,IOException {
        try {
            AuthService auth=(AuthService)getServletContext().getAttribute("authService");
            UserAccount account=auth.register(req.getParameter("email"),req.getParameter("password"),req.getParameter("name"));
            HttpSession session=req.getSession(); req.changeSessionId();
            session.setAttribute(SecurityFilter.ACCOUNT,account); session.setAttribute(SecurityFilter.CSRF,SecurityFilter.newToken());
            res.sendRedirect(req.getContextPath()+"/");
        } catch(IllegalArgumentException e) { req.setAttribute("mode","register"); req.setAttribute("error",e.getMessage()); req.getRequestDispatcher("/WEB-INF/auth.jsp").forward(req,res); }
    }
}
