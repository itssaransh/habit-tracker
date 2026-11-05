package com.habitflow.servlet;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).

import com.habitflow.service.HabitService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

// Servlets: this class handles habit creation and deletion requests.
@WebServlet("/habits")
public class HabitServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        HabitService service = (HabitService) getServletContext().getAttribute("habitService");
        try {
            service.create(request.getParameter("name"), request.getParameter("description"),
                    request.getParameter("category"), request.getParameter("frequency"),
                    request.getParameter("color"));
            response.sendRedirect(request.getContextPath() + "/?message=Habit+created+successfully");
        } catch (IllegalArgumentException exception) {
            response.sendRedirect(request.getContextPath() + "/?error=" +
                    java.net.URLEncoder.encode(exception.getMessage(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HabitService service = (HabitService) getServletContext().getAttribute("habitService");
        String id = request.getParameter("id");
        try {
            service.delete(Long.parseLong(id));
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (NumberFormatException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid habit id");
        }
    }
}
