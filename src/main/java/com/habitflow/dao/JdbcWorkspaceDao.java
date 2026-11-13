package com.habitflow.dao;

import com.habitflow.config.Database;
import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

// All workspace reads and writes include the authenticated owner id.
public final class JdbcWorkspaceDao {
    public String theme(long userId) {
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT theme FROM user_preferences WHERE user_id=?")){p.setLong(1,userId);try(ResultSet r=p.executeQuery()){if(r.next())return r.getString(1);return "light";}}
        catch(SQLException e){throw new IllegalStateException("Unable to read theme",e);}
    }
    public Map<String,Object> load(long userId) {
        Map<String,Object> root=new LinkedHashMap<>(); Map<String,Boolean> done=new LinkedHashMap<>();
        Map<String,Map<String,Object>> metadata=new LinkedHashMap<>(); Map<String,Object> focus=new LinkedHashMap<>();
        Map<String,Map<String,String>> notes=new HashMap<>(); Map<String,Map<String,Object>> targets=new HashMap<>();
        String theme="light";
        try(Connection c=Database.getConnection()) {
            try(PreparedStatement p=c.prepareStatement("SELECT theme FROM user_preferences WHERE user_id=?")) { p.setLong(1,userId); try(ResultSet r=p.executeQuery()){ if(r.next()) theme=r.getString(1); } }
            try(PreparedStatement p=c.prepareStatement("SELECT habit_id,checkin_date FROM habit_checkins WHERE user_id=?")) { p.setLong(1,userId); try(ResultSet r=p.executeQuery()){ while(r.next()) done.put(r.getLong(1)+"|"+r.getDate(2).toLocalDate(),true); } }
            try(PreparedStatement p=c.prepareStatement("SELECT habit_id,note_date,note FROM habit_notes WHERE user_id=?")) { p.setLong(1,userId); try(ResultSet r=p.executeQuery()){ while(r.next()) notes.computeIfAbsent(String.valueOf(r.getLong(1)), k->new LinkedHashMap<>()).put(r.getDate(2).toLocalDate().toString(),r.getString(3)); } }
            try(PreparedStatement p=c.prepareStatement("SELECT habit_id,time_of_day FROM habit_preferences WHERE user_id=?")) { p.setLong(1,userId); try(ResultSet r=p.executeQuery()){ while(r.next()) metadata.computeIfAbsent(String.valueOf(r.getLong(1)), k->new LinkedHashMap<>()).put("time",r.getString(2)); } }
            try(PreparedStatement p=c.prepareStatement("SELECT habit_id,target_date,target_count,target_total FROM habit_daily_targets WHERE user_id=?")) { p.setLong(1,userId); try(ResultSet r=p.executeQuery()){ while(r.next()) targets.computeIfAbsent(String.valueOf(r.getLong(1)), k->new LinkedHashMap<>()).put(r.getDate(2).toLocalDate().toString(),Map.of("date",r.getDate(2).toLocalDate().toString(),"count",r.getInt(3),"total",r.getInt(4))); } }
            try(PreparedStatement p=c.prepareStatement("SELECT habit_id,started_at,duration_minutes FROM focus_sessions WHERE user_id=? ORDER BY started_at")) { p.setLong(1,userId); try(ResultSet r=p.executeQuery()){ int total=0; Map<String,Integer> byHabit=new HashMap<>(),byDay=new HashMap<>(); while(r.next()){ int minutes=r.getInt(3); total+=minutes; String day=r.getTimestamp(2).toLocalDateTime().toLocalDate().toString(); byDay.merge(day,minutes,Integer::sum); long habit=r.getLong(1); if(!r.wasNull()) byHabit.merge(String.valueOf(habit),minutes,Integer::sum); } focus.put("totalMinutes",total); focus.put("byHabit",byHabit); focus.put("byDay",byDay); } }
        } catch(SQLException e) { throw new IllegalStateException("Unable to synchronize workspace",e); }
        metadata.forEach((id,value)->{ value.put("notes",notes.getOrDefault(id,Map.of())); value.put("targets",targets.getOrDefault(id,Map.of())); });
        notes.forEach((id,value)->metadata.computeIfAbsent(id,k->new LinkedHashMap<>()).put("notes",value));
        targets.forEach((id,value)->metadata.computeIfAbsent(id,k->new LinkedHashMap<>()).put("targets",value));
        root.put("done",done); root.put("metadata",metadata); root.put("focus",focus); root.put("theme",theme); return root;
    }
    public void saveNote(long userId,long habitId,LocalDate date,String note) {
        String sql=Database.isPostgres()?"INSERT INTO habit_notes(user_id,habit_id,note_date,note) VALUES (?,?,?,?) ON CONFLICT(user_id,habit_id,note_date) DO UPDATE SET note=EXCLUDED.note,updated_at=CURRENT_TIMESTAMP":"MERGE INTO habit_notes(user_id,habit_id,note_date,note) KEY(user_id,habit_id,note_date) VALUES (?,?,?,?)";
        try(Connection c=Database.getConnection(); PreparedStatement p=c.prepareStatement(sql)){ p.setLong(1,userId);p.setLong(2,habitId);p.setDate(3,Date.valueOf(date));p.setString(4,note);p.executeUpdate(); }
        catch(SQLException e){ throw persistenceFailure("Could not save note",e); }
    }
    public void saveMetadata(long userId,long habitId,String time,int targetCount,int targetTotal,LocalDate date) {
        if(!Set.of("Anytime","Morning","Afternoon","Evening").contains(time)) throw new IllegalArgumentException("Choose a valid time of day");
        if(targetTotal<1||targetTotal>50||targetCount<0||targetCount>targetTotal) throw new IllegalArgumentException("Choose a valid daily target");
        try(Connection c=Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                String pref=Database.isPostgres()?"INSERT INTO habit_preferences(user_id,habit_id,time_of_day) VALUES (?,?,?) ON CONFLICT(user_id,habit_id) DO UPDATE SET time_of_day=EXCLUDED.time_of_day":"MERGE INTO habit_preferences(user_id,habit_id,time_of_day) KEY(user_id,habit_id) VALUES (?,?,?)";
                try(PreparedStatement p=c.prepareStatement(pref)){p.setLong(1,userId);p.setLong(2,habitId);p.setString(3,time);p.executeUpdate();}
                String target=Database.isPostgres()?"INSERT INTO habit_daily_targets(user_id,habit_id,target_date,target_count,target_total) VALUES (?,?,?,?,?) ON CONFLICT(user_id,habit_id,target_date) DO UPDATE SET target_count=EXCLUDED.target_count,target_total=EXCLUDED.target_total":"MERGE INTO habit_daily_targets(user_id,habit_id,target_date,target_count,target_total) KEY(user_id,habit_id,target_date) VALUES (?,?,?,?,?)";
                try(PreparedStatement p=c.prepareStatement(target)){p.setLong(1,userId);p.setLong(2,habitId);p.setDate(3,Date.valueOf(date));p.setInt(4,targetCount);p.setInt(5,targetTotal);p.executeUpdate();}
                c.commit();
            } catch(SQLException e) { c.rollback(); throw e; }
        } catch(SQLException e){ throw persistenceFailure("Could not save habit settings",e); }
    }
    public void saveFocus(long userId,Long habitId,Timestamp startedAt,int minutes) {
        if(minutes<1||minutes>1440) throw new IllegalArgumentException("Focus session duration is invalid");
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO focus_sessions(user_id,habit_id,started_at,duration_minutes) VALUES (?,?,?,?)")) { p.setLong(1,userId); if(habitId==null)p.setNull(2,Types.BIGINT);else p.setLong(2,habitId);p.setTimestamp(3,startedAt);p.setInt(4,minutes);p.executeUpdate(); }
        catch(SQLException e){ throw persistenceFailure("Could not save focus session",e); }
    }
    public void saveTheme(long userId,String theme) {
        if(!Set.of("dark","light").contains(theme)) throw new IllegalArgumentException("Choose a valid theme");
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("UPDATE user_preferences SET theme=?,theme_selected=TRUE,updated_at=CURRENT_TIMESTAMP WHERE user_id=?")){p.setString(1,theme);p.setLong(2,userId);if(p.executeUpdate()!=1)throw new SQLException("Preferences not found");}
        catch(SQLException e){throw new IllegalStateException("Unable to save theme preference",e);}
    }
    public void saveHistoricalCheckin(long userId,long habitId,LocalDate date,boolean completed) {
        try(Connection c=Database.getConnection()) {
            try(PreparedStatement owner=c.prepareStatement("SELECT frequency,created_at FROM habits WHERE id=? AND user_id=?")){owner.setLong(1,habitId);owner.setLong(2,userId);try(ResultSet r=owner.executeQuery()){if(!r.next())throw new IllegalArgumentException("Habit was not found");if(completed){var schedule=com.habitflow.model.HabitScheduleFactory.fromFrequency(r.getString(1));var created=r.getDate(2).toLocalDate();if(!schedule.isScheduledOn(date,created))throw new IllegalArgumentException("This habit is not scheduled on that date");}}}
            if(completed){String sql=Database.isPostgres()?"INSERT INTO habit_checkins(habit_id,checkin_date,user_id) VALUES (?,?,?) ON CONFLICT(habit_id,checkin_date) DO NOTHING":"MERGE INTO habit_checkins(habit_id,checkin_date,user_id) KEY(habit_id,checkin_date) VALUES (?,?,?)";try(PreparedStatement p=c.prepareStatement(sql)){p.setLong(1,habitId);p.setDate(2,Date.valueOf(date));p.setLong(3,userId);p.executeUpdate();}}
            else try(PreparedStatement p=c.prepareStatement("DELETE FROM habit_checkins WHERE user_id=? AND habit_id=? AND checkin_date=?")){p.setLong(1,userId);p.setLong(2,habitId);p.setDate(3,Date.valueOf(date));p.executeUpdate();}
        }catch(SQLException e){throw new IllegalStateException("Unable to save check-in",e);}
    }
    private RuntimeException persistenceFailure(String message,SQLException error) {
        String state=error.getSQLState();
        if(state!=null && state.startsWith("23")) return new IllegalArgumentException("Habit was not found or the request is invalid",error);
        return new IllegalStateException(message,error);
    }
}
