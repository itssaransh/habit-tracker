package com.habitflow.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet("/logout")
public final class LogoutServlet extends HttpServlet {
    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response) throws IOException {
        HttpSession session=request.getSession(false); if(session!=null) session.invalidate();
        response.sendRedirect(request.getContextPath()+"/login");
    }
}
