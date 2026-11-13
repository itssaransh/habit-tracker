package com.habitflow.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.habitflow.dao.UserDao;
import com.habitflow.model.UserAccount;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AuthService {
    private static final Pattern EMAIL=Pattern.compile("^[^\\s@]{1,64}@[^\\s@.]+(?:\\.[^\\s@.]+)+$");
    private final UserDao users;
    public AuthService(UserDao users) { this.users=users; }
    public UserAccount register(String email,String password,String name) {
        String cleanEmail=normalizeEmail(email), cleanName=cleanName(name);
        validatePassword(password);
        String hash=BCrypt.withDefaults().hashToString(12,password.toCharArray());
        return users.create(cleanEmail,hash,cleanName);
    }
    public UserAccount login(String email,String password) {
        String cleanEmail=normalizeEmail(email);
        var match=users.findByEmail(cleanEmail);
        if(match.isPresent() && password!=null && BCrypt.verifyer().verify(password.toCharArray(),match.get().passwordHash()).verified) return match.get().account();
        throw new IllegalArgumentException("Email or password is incorrect");
    }
    private String normalizeEmail(String email) {
        String value=email==null?"":email.trim().toLowerCase(Locale.ROOT);
        if(value.length()>254 || !EMAIL.matcher(value).matches()) throw new IllegalArgumentException("Enter a valid email address");
        return value;
    }
    private String cleanName(String name) {
        String value=name==null?"":name.trim();
        if(value.length()<2 || value.length()>80 || value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Name must be between 2 and 80 characters");
        return value;
    }
    private void validatePassword(String password) {
        if(password==null || password.length()<10 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72 || password.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Password must be 10 or more characters and no more than 72 UTF-8 bytes");
    }
}
