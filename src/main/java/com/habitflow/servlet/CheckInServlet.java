package com.habitflow.servlet;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).

import com.habitflow.service.HabitService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

// Servlets: this class receives check-in requests from the browser.
@WebServlet("/checkins")
public class CheckInServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        HabitService service = (HabitService) getServletContext().getAttribute("habitService");
        try {
            service.toggleToday(Long.parseLong(request.getParameter("habitId")), request.getParameter("note"));
            response.sendRedirect(request.getContextPath() + "/");
        } catch (NumberFormatException exception) {
            redirectWithError(request, response, "Invalid habit id");
        } catch (IllegalArgumentException exception) {
            redirectWithError(request, response, exception.getMessage());
        }
    }

    private void redirectWithError(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        String error = java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8);
        response.sendRedirect(request.getContextPath() + "/?error=" + error);
    }
}
