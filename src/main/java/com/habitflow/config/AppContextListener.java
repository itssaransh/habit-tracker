package com.habitflow.config;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).

import com.habitflow.dao.JdbcHabitDao;
import com.habitflow.service.HabitService;
import com.habitflow.dao.JdbcUserDao;
import com.habitflow.service.AuthService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

// Servlet lifecycle: creates the shared service at startup and closes it when the app stops.
@WebListener
public class AppContextListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent event) {
        Database.initialize();
        event.getServletContext().setAttribute("habitService", new HabitService(new JdbcHabitDao()));
        event.getServletContext().setAttribute("authService", new AuthService(new JdbcUserDao()));
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        HabitService service = (HabitService) event.getServletContext().getAttribute("habitService");
        if (service != null) {
            service.close();
        }
    }
}
