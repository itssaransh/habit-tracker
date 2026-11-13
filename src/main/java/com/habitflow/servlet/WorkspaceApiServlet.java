package com.habitflow.servlet;

import com.google.gson.*;
import com.habitflow.dao.JdbcWorkspaceDao;
import com.habitflow.model.UserAccount;
import com.habitflow.service.HabitService;
import com.habitflow.web.SecurityFilter;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.Map;

@WebServlet("/api/workspace")
public final class WorkspaceApiServlet extends HttpServlet {
    private final JdbcWorkspaceDao workspace=new JdbcWorkspaceDao();
    private final Gson gson=new Gson();
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res)throws IOException {
        UserAccount user=user(req); res.setContentType("application/json;charset=UTF-8"); res.setHeader("Cache-Control","no-store");
        res.getWriter().write(gson.toJson(workspace.load(user.id())));
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res)throws IOException {
        res.setContentType("application/json;charset=UTF-8");res.setCharacterEncoding(StandardCharsets.UTF_8.name());res.setHeader("Cache-Control","no-store");
        try {
            JsonObject input=JsonParser.parseString(readBody(req)).getAsJsonObject();
            UserAccount user=user(req); long owner=user.id(); String action=text(input,"action");
            switch(action) {
                case "note" -> workspace.saveNote(owner,number(input,"habitId"),date(input,"date"),bounded(input,"note",500));
                case "metadata" -> workspace.saveMetadata(owner,number(input,"habitId"),text(input,"time"),integer(input,"count"),integer(input,"total"),date(input,"date"));
                case "checkin" -> {
                    long habit=number(input,"habitId"); LocalDate day=date(input,"date");
                    if(day.isAfter(LocalDate.now())) throw new IllegalArgumentException("A future date cannot be checked in");
                    workspace.saveHistoricalCheckin(owner,habit,day,input.get("completed").getAsBoolean());
                    if(input.has("note") && !input.get("note").isJsonNull()) workspace.saveNote(owner,habit,day,bounded(input,"note",500));
                }
                case "focus" -> {
                    JsonElement habitJson=input.get("habitId"); Long habit=habitJson==null||habitJson.isJsonNull()||habitJson.getAsString().isBlank()?null:habitJson.getAsLong();
                    Instant started; try { started=Instant.parse(text(input,"startedAt")); } catch(DateTimeParseException ex){throw new IllegalArgumentException("Focus session date is invalid");}
                    if(started.isAfter(Instant.now().plusSeconds(60))) throw new IllegalArgumentException("Focus session cannot start in the future");
                    workspace.saveFocus(owner,habit,Timestamp.from(started),integer(input,"minutes"));
                }
                case "theme" -> workspace.saveTheme(owner,text(input,"theme"));
                default -> throw new IllegalArgumentException("Unknown workspace update");
            }
            res.setStatus(HttpServletResponse.SC_OK);res.getWriter().write("{\"saved\":true}");
        } catch(IllegalArgumentException|JsonParseException|IllegalStateException e) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);res.getWriter().write(gson.toJson(Map.of("error",safe(e.getMessage()))));
        } catch(RuntimeException e) {
            getServletContext().log("Workspace save failed",e);res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);res.getWriter().write("{\"error\":\"Could not save this change. Please try again.\"}");
        }
    }
    private UserAccount user(HttpServletRequest req){return (UserAccount)req.getSession(false).getAttribute(SecurityFilter.ACCOUNT);}
    private String text(JsonObject o,String k){JsonElement e=o.get(k);if(e==null||e.isJsonNull()||!e.isJsonPrimitive())throw new IllegalArgumentException("Missing "+k);return e.getAsString();}
    private long number(JsonObject o,String k){try{return Long.parseLong(text(o,k));}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid "+k);}}
    private int integer(JsonObject o,String k){try{return Integer.parseInt(text(o,k));}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid "+k);}}
    private LocalDate date(JsonObject o,String k){try{return LocalDate.parse(text(o,k));}catch(DateTimeParseException e){throw new IllegalArgumentException("Invalid date");}}
    private String bounded(JsonObject o,String k,int max){String v=text(o,k).trim();if(v.length()>max)throw new IllegalArgumentException("Text must be "+max+" characters or fewer");return v;}
    private String safe(String value){return value==null?"Invalid request":value;}
    private String readBody(HttpServletRequest request)throws IOException {
        StringBuilder body=new StringBuilder(); char[] buffer=new char[1024]; int count; var reader=request.getReader();
        while((count=reader.read(buffer))!=-1) { if(body.length()+count>8192)throw new IllegalArgumentException("Request is too large");body.append(buffer,0,count); }
        return body.toString();
    }
}
