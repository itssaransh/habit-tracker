package com.habitflow.dao;

import com.habitflow.config.Database;
import com.habitflow.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceIsolationTest {
    @BeforeEach void setup() {
        System.setProperty("habit.tracker.environment","test");
        System.setProperty("habit.tracker.jdbc.url","jdbc:h2:mem:workspace_"+UUID.randomUUID().toString().replace("-","")+";DB_CLOSE_DELAY=-1");
        System.setProperty("habit.tracker.jdbc.user","sa"); System.setProperty("habit.tracker.jdbc.password",""); Database.initialize();
    }
    @Test void lightIsDefaultAndManualThemeChoicePersistsAcrossLogin() throws Exception {
        JdbcUserDao users=new JdbcUserDao(); AuthService auth=new AuthService(users);
        var account=auth.register("theme@example.test","a-long-test-password","Theme");
        JdbcWorkspaceDao workspace=new JdbcWorkspaceDao();

        assertEquals("light",workspace.theme(account.id()));
        assertEquals("light",workspace.load(account.id()).get("theme"));
        workspace.saveTheme(account.id(),"dark");
        assertEquals("dark",workspace.load(account.id()).get("theme"));
        assertEquals(account.id(),auth.login("theme@example.test","a-long-test-password").id());
        assertEquals("dark",new JdbcWorkspaceDao().theme(account.id()));

        workspace.saveTheme(account.id(),"light");
        assertEquals("light",new JdbcWorkspaceDao().load(account.id()).get("theme"));
        assertEquals(account.id(),auth.login("theme@example.test","a-long-test-password").id());
        assertEquals("light",new JdbcWorkspaceDao().theme(account.id()));
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT theme_selected FROM user_preferences WHERE user_id=?")){
            p.setLong(1,account.id());try(ResultSet r=p.executeQuery()){assertTrue(r.next());assertTrue(r.getBoolean(1));}
        }
    }
    @Test void migrationPreservesExistingThemesRegardlessOfTimestampsAndDefaultsNewAccountsToLight() throws Exception {
        JdbcUserDao users=new JdbcUserDao(); AuthService auth=new AuthService(users);
        var oldDark=auth.register("old-dark-theme@example.test","a-long-test-password","Old dark");
        var selectedDark=auth.register("selected-dark-theme@example.test","another-test-password","Selected dark");
        var oldLight=auth.register("old-light-theme@example.test","a-third-test-password","Old light");
        Timestamp signup=Timestamp.valueOf(LocalDateTime.of(2025,1,1,12,0));
        try(Connection c=Database.getConnection()) {
            try(PreparedStatement p=c.prepareStatement("UPDATE users SET created_at=? WHERE id=?")) {
                p.setTimestamp(1,signup);p.setLong(2,oldDark.id());p.executeUpdate();
                p.setTimestamp(1,signup);p.setLong(2,selectedDark.id());p.executeUpdate();
                p.setTimestamp(1,signup);p.setLong(2,oldLight.id());p.executeUpdate();
            }
            try(PreparedStatement p=c.prepareStatement("UPDATE user_preferences SET theme=?,updated_at=? WHERE user_id=?")) {
                p.setString(1,"dark");p.setTimestamp(2,signup);p.setLong(3,oldDark.id());p.executeUpdate();
                p.setString(1,"dark");p.setTimestamp(2,Timestamp.valueOf(signup.toLocalDateTime().plusSeconds(5)));p.setLong(3,selectedDark.id());p.executeUpdate();
                p.setString(1,"light");p.setTimestamp(2,signup);p.setLong(3,oldLight.id());p.executeUpdate();
            }
            try(PreparedStatement p=c.prepareStatement("DELETE FROM schema_migrations WHERE version=?")) {
                p.setString(1,"V003__light_theme_default.sql");p.executeUpdate();
            }
        }
        Database.initialize();
        JdbcWorkspaceDao workspace=new JdbcWorkspaceDao();
        assertEquals("dark",workspace.theme(oldDark.id()));
        assertEquals("dark",workspace.theme(selectedDark.id()));
        assertEquals("light",workspace.theme(oldLight.id()));
        var newAccount=auth.register("new-theme@example.test","a-fourth-test-password","New");
        assertEquals("light",workspace.theme(newAccount.id()));
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT theme_selected FROM user_preferences WHERE user_id=?")) {
            p.setLong(1,oldDark.id());try(ResultSet r=p.executeQuery()){assertTrue(r.next());assertFalse(r.getBoolean(1));}
            p.setLong(1,newAccount.id());try(ResultSet r=p.executeQuery()){assertTrue(r.next());assertFalse(r.getBoolean(1));}
        }
    }
    @Test void privateDataIsAccountScopedAndAvailableThroughAnotherDatabaseClient() {
        JdbcUserDao users=new JdbcUserDao(); AuthService auth=new AuthService(users);
        var alice=auth.register("alice@example.test","a-long-test-password","Alice");
        var bob=auth.register("bob@example.test","another-test-password","Bob");
        assertNotEquals(alice.id(),bob.id());
        assertTrue(users.findByEmail("alice@example.test").orElseThrow().passwordHash().startsWith("$2"));
        assertEquals(alice.id(),auth.login("ALICE@example.test","a-long-test-password").id());
        assertThrows(IllegalArgumentException.class,()->auth.login("alice@example.test","wrong-password"));
        long habitId=new JdbcHabitDao().save(alice.id(),"Read","A chapter","Learning","Daily","#5B5FEF");
        JdbcWorkspaceDao writer=new JdbcWorkspaceDao(); LocalDate today=LocalDate.now();
        writer.saveHistoricalCheckin(alice.id(),habitId,today,true);
        writer.saveNote(alice.id(),habitId,today,"Read for 20 minutes");
        writer.saveMetadata(alice.id(),habitId,"Morning",1,2,today);
        writer.saveFocus(alice.id(),habitId,Timestamp.valueOf(LocalDateTime.now().minusMinutes(25)),25);

        // A second DAO instance checks persisted reads; this does not emulate a browser or device.
        Map<String,Object> onSecondClient=new JdbcWorkspaceDao().load(alice.id());
        assertTrue(((Map<?,?>)onSecondClient.get("done")).containsKey(habitId+"|"+today));
        Map<?,?> aliceMetadata=(Map<?,?>)((Map<?,?>)onSecondClient.get("metadata")).get(String.valueOf(habitId));
        assertEquals("Read for 20 minutes",((Map<?,?>)aliceMetadata.get("notes")).get(today.toString()));
        assertEquals(25,((Map<?,?>)onSecondClient.get("focus")).get("totalMinutes"));

        Map<String,Object> bobWorkspace=new JdbcWorkspaceDao().load(bob.id());
        assertTrue(((Map<?,?>)bobWorkspace.get("done")).isEmpty());
        assertTrue(((Map<?,?>)bobWorkspace.get("metadata")).isEmpty());
        assertEquals(0,((Map<?,?>)bobWorkspace.get("focus")).get("totalMinutes"));
        assertTrue(new JdbcHabitDao().findAllWithProgress(bob.id(),today).isEmpty());
        assertTrue(new JdbcHabitDao().findById(bob.id(),habitId).isEmpty());
    }
}
