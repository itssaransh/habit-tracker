package com.habitflow.dao;

import com.habitflow.model.UserAccount;
import java.util.Optional;
import java.time.Instant;

public interface UserDao {
    UserAccount create(String email, String passwordHash, String displayName);
    UserAccount createGoogleAccount(String email, String passwordHash, String displayName, String googleSubject);
    Optional<UserWithPassword> findByEmail(String email);
    Optional<UserAccount> findByGoogleSubject(String googleSubject);
    Optional<UserAccount> findById(long userId);
    void savePasswordResetToken(long userId, String tokenHash, Instant expiresAt);
    void deletePasswordResetToken(String tokenHash);
    Optional<UserAccount> findValidResetTokenUser(String tokenHash, Instant now);
    Optional<UserAccount> consumeResetTokenAndUpdatePassword(String tokenHash, String newPasswordHash, Instant now);
    Optional<UserAccount> updatePasswordHash(long userId, String newPasswordHash);
    boolean linkGoogleSubject(long userId, String email, String googleSubject);
    UserAccount updateProfile(long userId, String displayName, String theme);
    record UserWithPassword(UserAccount account, String passwordHash) { }
}
