package com.habitflow.servlet;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).

import com.habitflow.config.Database;
import com.habitflow.model.Habit;
import com.habitflow.model.HabitStats;
import com.habitflow.service.HabitService;
import com.habitflow.model.UserAccount;
import com.habitflow.web.SecurityFilter;
import com.habitflow.dao.JdbcWorkspaceDao;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.List;

// Servlets: this class loads dashboard data and forwards it to the JSP.
@WebServlet(urlPatterns = {"", "/dashboard"})
public class DashboardServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HabitService service = (HabitService) getServletContext().getAttribute("habitService");
        LocalDate today = LocalDate.now();
        UserAccount account = (UserAccount) request.getSession(false).getAttribute(SecurityFilter.ACCOUNT);
        List<Habit> habits = service.getHabits(account.id());
        request.setAttribute("account", account);
        request.setAttribute("theme", new JdbcWorkspaceDao().theme(account.id()));
        HabitStats stats = service.getStatsAsync(habits).join();
        request.setAttribute("habits", habits);
        request.setAttribute("stats", stats);
        request.setAttribute("databaseType", Database.isPostgres() ? "PostgreSQL" : "H2");
        request.setAttribute("today", today.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)));
        request.setAttribute("todayDay", today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                .toUpperCase(Locale.ROOT));
        request.getRequestDispatcher("/WEB-INF/index.jsp").forward(request, response);
    }
}
