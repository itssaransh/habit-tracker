package com.habitflow.servlet;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.habitflow.model.UserAccount;
import com.habitflow.service.AuthService;
import com.habitflow.web.SecurityFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@WebServlet(urlPatterns = {"/auth/google", "/auth/google/callback", "/auth/google/link"})
public final class GoogleAuthServlet extends HttpServlet {
    private static final String STATE = "google_oauth_state";
    private static final String NONCE = "google_oauth_nonce";
    private static final String MODE = "google_oauth_mode";
    private static final String LINK_USER = "google_oauth_link_user_id";
    public static final String RESET_VERIFIED_USER = "password_reset_verified_user_id";
    public static final String RESET_VERIFIED_AT = "password_reset_verified_at";
    private static final int REMEMBER_ME_SECONDS = 30 * 24 * 60 * 60;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = path(req);
        if ("/auth/google/callback".equals(path)) { callback(req, resp); return; }
        if (!"/auth/google".equals(path)) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
        String rawMode=req.getParameter("mode");
        String uiMode="register".equalsIgnoreCase(rawMode)?"register":("reset".equalsIgnoreCase(rawMode)?"forgot":"login");
        String oauthMode="register".equalsIgnoreCase(rawMode)?"register":("reset".equalsIgnoreCase(rawMode)?"reset":"login");
        if (!configured()) { showAuthError(req, resp, uiMode, "Google Sign-In is not configured yet. Please use the email reset link."); return; }
        try { begin(req, resp, oauthMode, null); }
        catch(IllegalArgumentException | IllegalStateException invalidConfiguration) {
            showAuthError(req,resp,uiMode,"Google Sign-In is not configured correctly. Please use the email reset link.");
        }
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!"/auth/google/link".equals(path(req))) { resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED); return; }
        UserAccount current = (UserAccount) req.getSession(false).getAttribute(SecurityFilter.ACCOUNT);
        if (current == null) { resp.sendRedirect(req.getContextPath() + "/login"); return; }
        if (!configured()) { resp.sendRedirect(req.getContextPath() + "/?error=Google+Sign-In+is+not+configured"); return; }
        try {
            authService(req).verifyPasswordForLink(current, req.getParameter("password"));
            begin(req, resp, "link", current.id());
        } catch (IllegalArgumentException e) {
            resp.sendRedirect(req.getContextPath() + "/?error=Password+could+not+be+verified");
        }
    }

    private void begin(HttpServletRequest req, HttpServletResponse resp, String mode, Long linkUserId) throws IOException {
        HttpSession session = req.getSession(true);
        String state = randomToken();
        String nonce = randomToken();
        session.setAttribute(STATE, state);
        session.setAttribute(NONCE, nonce);
        session.setAttribute(MODE, mode);
        if ("reset".equals(mode)) {
            session.removeAttribute(RESET_VERIFIED_USER);
            session.removeAttribute(RESET_VERIFIED_AT);
        }
        if (linkUserId == null) session.removeAttribute(LINK_USER);
        else session.setAttribute(LINK_USER, linkUserId);

        String redirectUri = redirectUri(req);
        String query = form(Map.of(
                "client_id", setting("GOOGLE_CLIENT_ID"),
                "redirect_uri", redirectUri,
                "response_type", "code",
                "scope", "openid email profile",
                "state", state,
                "nonce", nonce,
                "prompt", "select_account"));
        resp.sendRedirect("https://accounts.google.com/o/oauth2/v2/auth?" + query);
    }

    private void callback(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        Object storedState = session == null ? null : session.getAttribute(STATE);
        Object storedNonce = session == null ? null : session.getAttribute(NONCE);
        Object storedMode = session == null ? null : session.getAttribute(MODE);
        Object linkUserId = session == null ? null : session.getAttribute(LINK_USER);
        if (session != null) {
            session.removeAttribute(STATE); session.removeAttribute(NONCE);
            session.removeAttribute(MODE); session.removeAttribute(LINK_USER);
        }
        String mode = "register".equals(storedMode) ? "register" : ("reset".equals(storedMode)?"forgot":"login");
        if (!constantTimeEquals(storedState, req.getParameter("state"))) {
            showAuthError(req, resp, mode, "Google sign-in could not be verified. Please try again."); return;
        }
        if (req.getParameter("error") != null) {
            if ("link".equals(storedMode)) { resp.sendRedirect(req.getContextPath() + "/?error=Google+account+connection+cancelled"); return; }
            showAuthError(req, resp, mode, "Google sign-in was cancelled. You can use Email and Password instead."); return;
        }
        if (storedNonce == null || req.getParameter("code") == null || req.getParameter("code").isBlank() || !configured()) {
            showAuthError(req, resp, mode, "Google sign-in could not be completed. Please try again."); return;
        }
        try {
            GoogleIdentity identity = exchangeAndVerify(req.getParameter("code"), redirectUri(req), (String) storedNonce);
            if("reset".equals(storedMode)) {
                UserAccount account=authService(req).verifyGoogleForPasswordReset(identity.subject(),identity.email(),identity.name());
                HttpSession activeSession=req.getSession(true);
                req.changeSessionId();
                activeSession.removeAttribute(STATE); activeSession.removeAttribute(NONCE); activeSession.removeAttribute(MODE); activeSession.removeAttribute(LINK_USER);
                activeSession.removeAttribute(SecurityFilter.ACCOUNT);
                activeSession.setAttribute(RESET_VERIFIED_USER,account.id());
                activeSession.setAttribute(RESET_VERIFIED_AT,System.currentTimeMillis());
                activeSession.setAttribute(SecurityFilter.CSRF,SecurityFilter.newToken());
                resp.sendRedirect(req.getContextPath()+"/reset-password");
                return;
            }
            if ("link".equals(storedMode)) {
                UserAccount current = (UserAccount) session.getAttribute(SecurityFilter.ACCOUNT);
                if (current == null || !(linkUserId instanceof Long) || current.id() != (Long) linkUserId) {
                    resp.sendRedirect(req.getContextPath() + "/login?error=Please+sign+in+again+before+connecting+Google"); return;
                }
                authService(req).linkVerifiedGoogle(current, identity.subject(), identity.email());
                rotateSession(req, resp, current);
                resp.sendRedirect(req.getContextPath() + "/?message=Google+account+connected");
                return;
            }
            UserAccount account = authService(req).loginWithVerifiedGoogle(identity.subject(), identity.email(), identity.name());
            rotateSession(req, resp, account);
            resp.sendRedirect(req.getContextPath() + "/");
        } catch (Exception e) {
            getServletContext().log("Google sign-in was rejected or unavailable", e);
            if ("link".equals(storedMode)) resp.sendRedirect(req.getContextPath() + "/?error=Google+account+could+not+be+connected");
            else showAuthError(req, resp, mode, safeAuthMessage(e));
        }
    }

    private GoogleIdentity exchangeAndVerify(String code, String redirectUri, String expectedNonce) throws Exception {
        String body = form(Map.of("code", code, "client_id", setting("GOOGLE_CLIENT_ID"),
                "client_secret", setting("GOOGLE_CLIENT_SECRET"), "redirect_uri", redirectUri,
                "grant_type", "authorization_code"));
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new SecurityException("Google token exchange failed");
        JsonObject tokenResponse = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!tokenResponse.has("id_token") || tokenResponse.get("id_token").isJsonNull()) throw new SecurityException("Google ID token was missing");

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(setting("GOOGLE_CLIENT_ID"))).build();
        GoogleIdToken idToken = verifier.verify(tokenResponse.get("id_token").getAsString());
        if (idToken == null) throw new SecurityException("Google ID token verification failed");
        GoogleIdToken.Payload payload = idToken.getPayload();
        Object nonce = payload.get("nonce");
        if (!constantTimeEquals(expectedNonce, nonce == null ? null : nonce.toString())) throw new SecurityException("Google nonce verification failed");
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) throw new SecurityException("Google email is not verified");
        String email = payload.getEmail();
        String subject = payload.getSubject();
        if (email == null || email.isBlank() || subject == null || subject.isBlank()) throw new SecurityException("Google identity is incomplete");
        Object name = payload.get("name");
        return new GoogleIdentity(subject, email, name == null ? null : name.toString());
    }

    private void rotateSession(HttpServletRequest req, HttpServletResponse resp, UserAccount account) {
        HttpSession session = req.getSession(false);
        if (session == null) session = req.getSession(true);
        req.changeSessionId();
        session.removeAttribute(STATE); session.removeAttribute(NONCE); session.removeAttribute(MODE); session.removeAttribute(LINK_USER);
        session.setAttribute(SecurityFilter.ACCOUNT, account);
        session.setAttribute(SecurityFilter.CSRF, SecurityFilter.newToken());
        session.setMaxInactiveInterval(REMEMBER_ME_SECONDS);
        String path = req.getContextPath().isEmpty() ? "/" : req.getContextPath();
        String cookie = "JSESSIONID=" + session.getId() + "; Path=" + path + "; Max-Age=" + REMEMBER_ME_SECONDS + "; HttpOnly; SameSite=Lax";
        if (req.isSecure() || "production".equalsIgnoreCase(System.getenv("APP_ENV"))) cookie += "; Secure";
        resp.setHeader("Set-Cookie", cookie);
    }

    private String redirectUri(HttpServletRequest req) {
        String explicit = setting("GOOGLE_REDIRECT_URI");
        if (explicit != null) {
            URI uri=parseTrustedUri(explicit,"production".equalsIgnoreCase(setting("APP_ENV")));
            String expectedPath=req.getContextPath()+"/auth/google/callback";
            if(!expectedPath.equals(uri.getRawPath())) throw new IllegalStateException("GOOGLE_REDIRECT_URI must use the Google callback path");
            return uri.toASCIIString();
        }
        if ("production".equalsIgnoreCase(setting("APP_ENV"))) throw new IllegalStateException("GOOGLE_REDIRECT_URI must be configured in production");
        String host = req.getServerName();
        if (host == null || !(host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1"))) throw new IllegalStateException("Local OAuth callback must use a loopback host");
        String scheme=req.isSecure()?"https":"http";
        int port = req.getServerPort();
        String authority = (port == 80 && "http".equals(scheme)) || (port == 443 && "https".equals(scheme)) ? host : host + ":" + port;
        return scheme + "://" + authority + req.getContextPath() + "/auth/google/callback";
    }

    private URI parseTrustedUri(String value,boolean requireHttps) {
        URI uri;
        try { uri=URI.create(value.trim()); } catch(IllegalArgumentException invalid) { throw new IllegalStateException("OAuth URI is invalid"); }
        String scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase();
        if(uri.isOpaque() || !(scheme.equals("https") || scheme.equals("http")) || (requireHttps && !scheme.equals("https"))
                || uri.getHost()==null || uri.getRawAuthority()==null || uri.getRawUserInfo()!=null
                || uri.getRawAuthority().contains("@") || !uri.getRawAuthority().matches("(?i)(\\[[0-9a-f:.]+\\]|[a-z0-9.-]+)(:[0-9]{1,5})?") || uri.getRawQuery()!=null || uri.getRawFragment()!=null
                || uri.getPort()>65535 || uri.getPort()<-1) throw new IllegalStateException("OAuth URI must have a valid host and authority without credentials, query, or fragment");
        return uri;
    }

    private boolean configured() {
        return setting("GOOGLE_CLIENT_ID") != null && setting("GOOGLE_CLIENT_SECRET") != null
                && (!"production".equalsIgnoreCase(setting("APP_ENV")) || setting("GOOGLE_REDIRECT_URI") != null);
    }
    private String setting(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = System.getProperty(name);
        return value == null || value.isBlank() ? null : value.trim();
    }
    private AuthService authService(HttpServletRequest req) { return (AuthService) getServletContext().getAttribute("authService"); }
    private String path(HttpServletRequest req) { return req.getRequestURI().substring(req.getContextPath().length()); }
    private String randomToken() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String form(Map<String, String> values) {
        StringJoiner result = new StringJoiner("&");
        values.forEach((key, value) -> result.add(encode(key) + "=" + encode(value)));
        return result.toString();
    }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private boolean constantTimeEquals(Object expected, String actual) {
        if (!(expected instanceof String value) || actual == null) return false;
        return MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
    private String safeAuthMessage(Exception error) {
        String message = error.getMessage();
        if (message != null && (message.startsWith("This email already has") || message.startsWith("No HabitFlow account was found")
                || message.startsWith("This HabitFlow account is already linked") || message.startsWith("This Google account is linked"))) return message;
        return "Google sign-in could not be completed. Please try again or use Email and Password.";
    }
    private void showAuthError(HttpServletRequest req, HttpServletResponse resp, String mode, String error) throws ServletException, IOException {
        req.setAttribute("mode", mode); req.setAttribute("error", error);
        HttpSession session = req.getSession(true);
        req.setAttribute(SecurityFilter.CSRF, session.getAttribute(SecurityFilter.CSRF));
        req.getRequestDispatcher("/WEB-INF/auth.jsp").forward(req, resp);
    }
    private record GoogleIdentity(String subject, String email, String name) { }
}
