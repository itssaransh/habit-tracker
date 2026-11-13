package com.habitflow.dao;

import com.habitflow.model.UserAccount;
import java.util.Optional;

public interface UserDao {
    UserAccount create(String email, String passwordHash, String displayName);
    Optional<UserWithPassword> findByEmail(String email);
    UserAccount updateProfile(long userId, String displayName, String theme);
    record UserWithPassword(UserAccount account, String passwordHash) { }
}
