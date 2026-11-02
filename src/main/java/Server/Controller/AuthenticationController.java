package Server.Controller;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import Server.Model.DatabaseCommunicationContext;

public class AuthenticationController {
    private static final ConcurrentHashMap<String, Session> activeSessions = new ConcurrentHashMap<>();

    private static final String MOCK_USER = "admin";
    private static final String MOCK_PASSWORD_HASH = hashPassword("password");

    /** Shortest password accepted when a new account is created. */
    public static final int MIN_PASSWORD_LENGTH = 8;

    /** The account a session token belongs to. */
    public record Session(String userId, String username, boolean isAdmin) {
    }

    public enum RegisterStatus {
        SUCCESS,
        INVALID,
        DUPLICATE,
        UNAVAILABLE
    }

    /** Outcome of a create-account attempt. */
    public record RegisterResult(RegisterStatus status, String sessionToken, String message) {
    }

    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check credentials against the users table. Falls back to the built-in
     * demo account (admin/password) when no database is available, so the web
     * pages still work for local demos without MySQL running.
     *
     * @return a session token on success, null when the credentials are wrong.
     */
    public static String authenticateUser(DatabaseCommunicationContext db, String usernameOrEmail, String password) {
        if (usernameOrEmail == null || password == null) {
            return null;
        }

        String identifier = usernameOrEmail.trim();
        if (db != null) {
            Map<String, Object> user = db.findUserByUsernameOrEmail(identifier);
            if (user != null) {
                String storedHash = String.valueOf(user.get("password_hash"));
                if (!hashPassword(password).equalsIgnoreCase(storedHash)) {
                    return null;
                }
                return createSession(String.valueOf(user.get("user_id")), text(user.get("username_or_email")),
                        Boolean.TRUE.equals(user.get("is_admin")));
            }
        }

        if (MOCK_USER.equals(identifier) && MOCK_PASSWORD_HASH.equals(hashPassword(password))) {
            return createSession(MOCK_USER, MOCK_USER, true);
        }
        return null;
    }

    /**
     * Demo-only credential check kept for the built-in account. Web and app
     * logins call the overload that takes a database.
     */
    public static String authenticateUser(String username, String password) {
        return authenticateUser(null, username, password);
    }

    /**
     * Credential check used by the mobile app, which sends an already-hashed
     * password (SHA-256 hex) instead of the plaintext.
     *
     * @return a session token on success, null when the credentials are wrong.
     */
    public static String authenticateUserHash(DatabaseCommunicationContext db, String usernameOrEmail,
                                              String passwordHash) {
        if (usernameOrEmail == null || passwordHash == null) {
            return null;
        }

        String identifier = usernameOrEmail.trim();
        String normalizedHash = passwordHash.trim().toLowerCase();

        if (db != null) {
            Map<String, Object> user = db.findUserByUsernameOrEmail(identifier);
            if (user != null) {
                String storedHash = String.valueOf(user.get("password_hash"));
                if (!normalizedHash.equalsIgnoreCase(storedHash)) {
                    return null;
                }
                return createSession(String.valueOf(user.get("user_id")), text(user.get("username_or_email")),
                        Boolean.TRUE.equals(user.get("is_admin")));
            }
        }

        if (MOCK_USER.equals(identifier) && MOCK_PASSWORD_HASH.equals(normalizedHash)) {
            return createSession(MOCK_USER, MOCK_USER, true);
        }
        return null;
    }

    /**
     * Validate the sign-up form and store a new user in the database.
     * A session is created on success so the new user is signed in right away.
     */
    public static RegisterResult register(DatabaseCommunicationContext db, String username, String email,
                                          String password, String confirmPassword) {
        String cleanUsername = username == null ? "" : username.trim();
        String cleanEmail = email == null ? "" : email.trim();

        if (cleanUsername.isEmpty() || cleanEmail.isEmpty()) {
            return new RegisterResult(RegisterStatus.INVALID, null, "Username and email are required.");
        }
        if (!cleanEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return new RegisterResult(RegisterStatus.INVALID, null, "Enter a valid email address.");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return new RegisterResult(RegisterStatus.INVALID, null,
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (!password.equals(confirmPassword)) {
            return new RegisterResult(RegisterStatus.INVALID, null, "Passwords do not match.");
        }
        if (db == null) {
            return new RegisterResult(RegisterStatus.UNAVAILABLE, null,
                    "Account creation is unavailable because the database is not configured.");
        }

        String userId = UUID.randomUUID().toString();
        DatabaseCommunicationContext.CreateUserResult created = db.createUser(
                userId, cleanUsername, cleanEmail, hashPassword(password), false);

        return switch (created) {
            case DUPLICATE -> new RegisterResult(RegisterStatus.DUPLICATE, null,
                    "That username or email is already registered.");
            case ERROR -> new RegisterResult(RegisterStatus.UNAVAILABLE, null,
                    "The account could not be saved. Please try again.");
            case CREATED -> new RegisterResult(RegisterStatus.SUCCESS,
                    createSession(userId, cleanUsername, false), "Account created.");
        };
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String createSession(String userId, String username, boolean isAdmin) {
        String sessionId = UUID.randomUUID().toString();
        activeSessions.put(sessionId, new Session(userId, username, isAdmin));
        return sessionId;
    }

    /** Details of the signed-in account, or null for an unknown token. */
    public static Session getSession(String sessionId) {
        return sessionId == null ? null : activeSessions.get(sessionId);
    }

    public static boolean isValidSession(String sessionId) {
        return sessionId != null && activeSessions.containsKey(sessionId);
    }

    /** True only for sessions that belong to an admin account. */
    public static boolean isAdminSession(String sessionId) {
        Session session = getSession(sessionId);
        return session != null && session.isAdmin();
    }

    public static boolean logout(String sessionId) {
        if (sessionId != null) {
            activeSessions.remove(sessionId);
            return true;
        }
        return false;
    }
}
