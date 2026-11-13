package com.habitflow.model;

public record UserAccount(long id, String email, String displayName) {
    // JavaBean getters make record fields available to JSP Expression Language.
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
}
