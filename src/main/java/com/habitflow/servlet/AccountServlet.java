package com.habitflow.servlet;

import com.habitflow.dao.JdbcUserDao;
import com.habitflow.model.UserAccount;
import com.habitflow.web.SecurityFilter;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

@WebServlet("/account")
public final class AccountServlet extends HttpServlet {
    private final JdbcUserDao users=new JdbcUserDao();
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res)throws IOException {
        UserAccount current=(UserAccount)req.getSession(false).getAttribute(SecurityFilter.ACCOUNT);
        String name=clean(req.getParameter("displayName"));
        String fullName=clean(req.getParameter("fullName"));
        String workspaceName=clean(req.getParameter("workspaceName"));
        String theme=req.getParameter("theme");
        String reminderValue=req.getParameter("dailyReminderTime");
        int weeklyGoalDays;
        LocalTime reminderTime=null;
        try {
            weeklyGoalDays=Integer.parseInt(req.getParameter("weeklyGoalDays"));
            if(reminderValue!=null && !reminderValue.isBlank()) reminderTime=LocalTime.parse(reminderValue);
        } catch(NumberFormatException|DateTimeParseException ex) {
            res.sendRedirect(req.getContextPath()+"/?error=Enter+valid+account+settings");return;
        }
        if(!valid(name) || !valid(fullName) || !valid(workspaceName) || weeklyGoalDays<1 || weeklyGoalDays>7
                || theme==null || !(theme.equals("dark")||theme.equals("light"))) {
            res.sendRedirect(req.getContextPath()+"/?error=Please+enter+valid+account+settings");return;
        }
        try {
            UserAccount updated=users.updateProfile(current.id(),name,fullName,workspaceName,reminderTime,weeklyGoalDays,theme);
            req.getSession(false).setAttribute(SecurityFilter.ACCOUNT,updated);
            res.sendRedirect(req.getContextPath()+"/?message=Account+settings+saved");
        } catch(RuntimeException e) { getServletContext().log("Account settings save failed",e);res.sendRedirect(req.getContextPath()+"/?error=Account+settings+could+not+be+saved"); }
    }
    private String clean(String value) { return value==null?"":value.trim(); }
    private boolean valid(String value) { return value.length()>=2 && value.length()<=80 && value.chars().noneMatch(Character::isISOControl); }
}
