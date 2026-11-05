package com.habitflow.servlet;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).
// Shivansh Singh: this Servlet answers hosting health checks.
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/health")
public class HealthServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("OK");
    }
}
