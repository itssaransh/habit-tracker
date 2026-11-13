package com.habitflow.dao;

import com.habitflow.config.Database;
import com.habitflow.model.UserAccount;
import java.sql.*;
import java.util.Optional;

public final class JdbcUserDao implements UserDao {
    @Override public UserAccount create(String email, String passwordHash, String displayName) {
        String sql = "INSERT INTO users(email,password_hash,display_name) VALUES (?,?,?)";
        try (Connection c=Database.getConnection(); PreparedStatement p=c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            c.setAutoCommit(false);
            p.setString(1,email); p.setString(2,passwordHash); p.setString(3,displayName); p.executeUpdate();
            long id;
            try (ResultSet r=p.getGeneratedKeys()) { if (!r.next()) throw new SQLException("No generated user id"); id=r.getLong(1); }
            saveDefaults(c,id); c.commit(); return new UserAccount(id,email,displayName);
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) throw new IllegalArgumentException("An account with that email already exists");
            throw new IllegalStateException("Unable to create account",e);
        }
    }
    private void saveDefaults(Connection c,long userId) throws SQLException {
        try (PreparedStatement p=c.prepareStatement("INSERT INTO user_preferences(user_id) VALUES (?)")) { p.setLong(1,userId); p.executeUpdate(); }
    }
    @Override public Optional<UserWithPassword> findByEmail(String email) {
        String sql="SELECT id,email,display_name,password_hash FROM users WHERE email=?";
        try (Connection c=Database.getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,email); try (ResultSet r=p.executeQuery()) { return r.next()?Optional.of(new UserWithPassword(new UserAccount(r.getLong("id"),r.getString("email"),r.getString("display_name")),r.getString("password_hash"))):Optional.empty(); }
        } catch (SQLException e) { throw new IllegalStateException("Unable to read account",e); }
    }
    @Override public UserAccount updateProfile(long userId,String displayName,String theme) {
        try (Connection c=Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement p=c.prepareStatement("UPDATE users SET display_name=? WHERE id=?")) { p.setString(1,displayName); p.setLong(2,userId); if(p.executeUpdate()!=1) throw new SQLException("Account not found"); }
                try (PreparedStatement p=c.prepareStatement("UPDATE user_preferences SET theme=?, theme_selected=TRUE, updated_at=CURRENT_TIMESTAMP WHERE user_id=?")) { p.setString(1,theme); p.setLong(2,userId); if(p.executeUpdate()!=1) throw new SQLException("Account preferences not found"); }
                String email; try (PreparedStatement p=c.prepareStatement("SELECT email FROM users WHERE id=?")) { p.setLong(1,userId); try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Account not found");email=r.getString(1);} }
                c.commit(); return new UserAccount(userId,email,displayName);
            } catch(SQLException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new IllegalStateException("Unable to save profile",e); }
    }
}
