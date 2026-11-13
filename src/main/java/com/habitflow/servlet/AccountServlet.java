package com.habitflow.servlet;

import com.habitflow.dao.JdbcUserDao;
import com.habitflow.model.UserAccount;
import com.habitflow.web.SecurityFilter;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet("/account")
public final class AccountServlet extends HttpServlet {
    private final JdbcUserDao users=new JdbcUserDao();
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res)throws IOException {
        UserAccount current=(UserAccount)req.getSession(false).getAttribute(SecurityFilter.ACCOUNT);
        String name=req.getParameter("displayName"); String theme=req.getParameter("theme");
        if(name==null || name.trim().length()<2 || name.trim().length()>80 || name.trim().chars().anyMatch(Character::isISOControl) || theme==null || !(theme.equals("dark")||theme.equals("light"))) {
            res.sendRedirect(req.getContextPath()+"/?error=Please+enter+a+valid+name+and+theme");return;
        }
        try {
            UserAccount updated=users.updateProfile(current.id(),name.trim(),theme);
            req.getSession(false).setAttribute(SecurityFilter.ACCOUNT,updated);
            res.sendRedirect(req.getContextPath()+"/?message=Account+settings+saved");
        } catch(RuntimeException e) { getServletContext().log("Account settings save failed",e);res.sendRedirect(req.getContextPath()+"/?error=Account+settings+could+not+be+saved"); }
    }
}
