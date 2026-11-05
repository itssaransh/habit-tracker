package com.habitflow.service;

// Team: Shaswat Gupta (OOP models), Saransh Mishra (schema, JDBC and DAO).
// Ayush Sharma (service validation, exceptions, collections and testing), Shivansh Singh (Servlets and web UI).

// Exception handling: this exception reports invalid habit input.
public final class HabitValidationException extends IllegalArgumentException {
    public HabitValidationException(String message) { super(message); }
}
