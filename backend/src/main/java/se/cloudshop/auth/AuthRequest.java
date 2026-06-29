package se.cloudshop.auth;

public record AuthRequest(
    String email,
    String password
) {
}

