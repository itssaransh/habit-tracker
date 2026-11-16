package com.habitflow.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet("/logout")
public final class LogoutServlet extends HttpServlet {
    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response) throws IOException {
        HttpSession session=request.getSession(false); if(session!=null) session.invalidate();
        String path=request.getContextPath().isEmpty()?"/":request.getContextPath();
        String cookie="JSESSIONID=; Path="+path+"; Max-Age=0; HttpOnly; SameSite=Lax";
        if(request.isSecure() || "production".equalsIgnoreCase(System.getenv("APP_ENV"))) cookie+="; Secure";
        response.setHeader("Set-Cookie",cookie);
        response.sendRedirect(request.getContextPath()+"/login");
    }
}
