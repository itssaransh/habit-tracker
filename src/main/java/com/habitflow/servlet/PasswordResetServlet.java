package com.habitflow.servlet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
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
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@WebServlet(urlPatterns = {"/forgot-password", "/reset-password"})
public final class PasswordResetServlet extends HttpServlet {
    private static final long WINDOW_MS=15L*60*1000;
    private static final int MAX_BUCKETS=20_000;
    private static final SlidingWindowLimiter LIMITER=new SlidingWindowLimiter();
    private static final Gson GSON=new Gson();
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp) throws ServletException,IOException {
        String path=path(req);
        if("/forgot-password".equals(path)) { show(req,resp,"forgot",null,null); return; }
        if(!"/reset-password".equals(path)) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
        if(!allowResetIp(req,resp)) return;
        // Email reset tokens arrive in the URL fragment and are submitted only in the CSRF-protected POST.
        // Fragments are not sent to this endpoint, access logs, or Referer headers.
        HttpSession session=req.getSession(false);
        boolean hadGoogleRecovery=session!=null && (session.getAttribute(GoogleAuthServlet.RESET_VERIFIED_USER)!=null
                || session.getAttribute(GoogleAuthServlet.RESET_VERIFIED_AT)!=null);
        if(hadGoogleRecovery && verifiedRecoveryUser(req)==null) {
            show(req,resp,"forgot","Google recovery verification expired. Please verify with Google again or request a new reset link.",null);
            return;
        }
        show(req,resp,"reset",null,null);
    }

    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp) throws ServletException,IOException {
        String path=path(req);
        if("/forgot-password".equals(path)) { forgot(req,resp); return; }
        if("/reset-password".equals(path)) { reset(req,resp); return; }
        resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    private void forgot(HttpServletRequest req,HttpServletResponse resp) throws ServletException,IOException {
        if(!LIMITER.allow("forgot-ip:"+clientIp(req),5,System.currentTimeMillis())) {
            rateLimited(req,resp,"forgot"); return;
        }
        final String email;
        try { email=auth(req).validateEmailForReset(req.getParameter("email")); }
        catch(IllegalArgumentException invalid) { show(req,resp,"forgot",invalid.getMessage(),null); return; }
        if(!LIMITER.allow("forgot-email:"+email,3,System.currentTimeMillis())) { rateLimited(req,resp,"forgot"); return; }
        String base=trustedBaseUrl(req);
        if(setting("RESEND_API_KEY")==null || setting("RESEND_FROM_EMAIL")==null || base==null) {
            show(req,resp,"forgot","Email delivery is not configured on this server yet. Please click 'Verify with Google to reset password' below to verify your email and set a new password.",null);
            return;
        }
        Optional<String> token=auth(req).createPasswordResetToken(email);
        if(token.isPresent()) {
            String url=base+"/reset-password#token="+URLEncoder.encode(token.get(),StandardCharsets.UTF_8);
            if(!sendResetEmail(req,email,url)) {
                try { auth(req).revokePasswordResetToken(token.get()); }
                catch(RuntimeException revokeFailure) { getServletContext().log("Password reset token revocation failed after email delivery failure"); }
                show(req,resp,"forgot","We could not send the reset email. Please try again later or verify with Google.",null);
                return;
            }
        }
        show(req,resp,"forgot",null,"If an account exists for that email, a one-time password reset link valid for 15 minutes has been sent.");
    }

    private void reset(HttpServletRequest req,HttpServletResponse resp) throws ServletException,IOException {
        if(!allowResetIp(req,resp)) return;
        String token=req.getParameter("token");
        Long verifiedUser=verifiedRecoveryUser(req);
        try {
            UserAccount updated;
            if(token!=null && !token.isBlank()) updated=auth(req).resetPasswordWithToken(token,req.getParameter("password"),req.getParameter("confirmPassword"));
            else if(verifiedUser!=null) updated=auth(req).resetPasswordForVerifiedUser(verifiedUser,req.getParameter("password"),req.getParameter("confirmPassword"));
            else throw new IllegalArgumentException("Password reset verification is invalid or has expired");
            establishAuthenticatedSession(req,resp,updated);
            resp.sendRedirect(req.getContextPath()+"/");
        } catch(IllegalArgumentException invalid) {
            if(token!=null && !token.isBlank()) req.setAttribute("resetToken",token);
            show(req,resp,"reset",invalid.getMessage(),null);
        }
    }

    private Long verifiedRecoveryUser(HttpServletRequest req) {
        HttpSession session=req.getSession(false);
        if(session==null) return null;
        Object id=session.getAttribute(GoogleAuthServlet.RESET_VERIFIED_USER);
        Object verifiedAt=session.getAttribute(GoogleAuthServlet.RESET_VERIFIED_AT);
        long now=System.currentTimeMillis();
        boolean valid=false;
        if(id instanceof Long && verifiedAt instanceof Long at && at<=now) {
            try { valid=now<Math.addExact(at,Duration.ofMinutes(15).toMillis()); }
            catch(ArithmeticException ignored) { valid=false; }
        }
        if(!valid) {
            session.removeAttribute(GoogleAuthServlet.RESET_VERIFIED_USER);
            session.removeAttribute(GoogleAuthServlet.RESET_VERIFIED_AT);
            return null;
        }
        return (Long)id;
    }

    private boolean allowResetIp(HttpServletRequest req,HttpServletResponse resp) throws ServletException,IOException {
        if(LIMITER.allow("reset-ip:"+clientIp(req),10,System.currentTimeMillis())) return true;
        rateLimited(req,resp,"reset"); return false;
    }

    private void rateLimited(HttpServletRequest req,HttpServletResponse resp,String mode) throws ServletException,IOException {
        resp.setStatus(429);
        show(req,resp,mode,"Too many password reset requests. Please wait 15 minutes before trying again.",null);
    }

    private void show(HttpServletRequest req,HttpServletResponse resp,String mode,String error,String notice) throws ServletException,IOException {
        req.setAttribute("mode",mode);
        if(error!=null) req.setAttribute("error",error);
        if(notice!=null) req.setAttribute("notice",notice);
        HttpSession session=req.getSession(true);
        req.setAttribute(SecurityFilter.CSRF,session.getAttribute(SecurityFilter.CSRF));
        req.getRequestDispatcher("/WEB-INF/auth.jsp").forward(req,resp);
    }

    private void establishAuthenticatedSession(HttpServletRequest req,HttpServletResponse resp,UserAccount account) {
        HttpSession session=req.getSession(true);
        req.changeSessionId();
        session.removeAttribute(GoogleAuthServlet.RESET_VERIFIED_USER);
        session.removeAttribute(GoogleAuthServlet.RESET_VERIFIED_AT);
        session.setAttribute(SecurityFilter.ACCOUNT,account);
        session.setAttribute(SecurityFilter.CSRF,SecurityFilter.newToken());
        session.setMaxInactiveInterval(30*24*60*60);
        String path=req.getContextPath().isEmpty()?"/":req.getContextPath();
        String cookie="JSESSIONID="+session.getId()+"; Path="+path+"; Max-Age="+(30*24*60*60)+"; HttpOnly; SameSite=Lax";
        if(req.isSecure() || "production".equalsIgnoreCase(setting("APP_ENV"))) cookie+="; Secure";
        resp.setHeader("Set-Cookie",cookie);
    }

    private boolean sendResetEmail(HttpServletRequest req,String email,String resetUrl) {
        try {
            JsonObject message=new JsonObject();
            JsonArray to=new JsonArray(); to.add(email);
            message.add("to",to); message.addProperty("from",setting("RESEND_FROM_EMAIL"));
            message.addProperty("subject","Reset your HabitFlow password");
            message.addProperty("html","<p>Use this link to set a new HabitFlow password. It expires in 15 minutes.</p><p><a href=\""+escapeHtml(resetUrl)+"\">Reset password</a></p><p>If you did not request this, ignore this email.</p>");
            HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+setting("RESEND_API_KEY"))
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(message))).build();
            HttpResponse<Void> response=HTTP.send(request,HttpResponse.BodyHandlers.discarding());
            return response.statusCode()>=200 && response.statusCode()<300;
        } catch(IOException failure) {
            getServletContext().log("Resend request failed while delivering a password reset email");
            return false;
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            getServletContext().log("Resend request was interrupted while delivering a password reset email");
            return false;
        } catch(RuntimeException invalidRequest) {
            getServletContext().log("Resend request could not be prepared for password reset email delivery");
            return false;
        }
    }

    private String trustedBaseUrl(HttpServletRequest req) {
        String appBase=setting("APP_BASE_URL");
        boolean production="production".equalsIgnoreCase(setting("APP_ENV"));
        if(appBase!=null) {
            URI uri=parseBaseUri(appBase,production,false);
            if(uri==null) return null;
            return origin(uri)+req.getContextPath();
        }
        String redirect=setting("GOOGLE_REDIRECT_URI");
        if(redirect!=null) {
            URI uri=parseBaseUri(redirect,production,true);
            if(uri==null) return null;
            if(!(req.getContextPath()+"/auth/google/callback").equals(uri.getRawPath())) return null;
            return origin(uri)+req.getContextPath();
        }
        if(production) return null;
        String host=req.getServerName();
        if(host==null || !(host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1"))) return null;
        String scheme=req.isSecure()?"https":"http";
        int port=req.getServerPort();
        return scheme+"://"+host+":"+port+req.getContextPath();
    }

    private URI parseBaseUri(String value,boolean production,boolean allowPath) {
        try {
            URI uri=URI.create(value.trim());
            String scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase();
            if(uri.isOpaque() || !(scheme.equals("https") || scheme.equals("http")) || (production && !scheme.equals("https"))
                    || uri.getHost()==null || uri.getRawAuthority()==null || uri.getRawUserInfo()!=null || uri.getRawAuthority().contains("@") || !validAuthority(uri)
                    || uri.getRawQuery()!=null || uri.getRawFragment()!=null || uri.getPort()>65535 || uri.getPort()<-1
                    || (!allowPath && uri.getRawPath()!=null && !uri.getRawPath().isEmpty() && !uri.getRawPath().equals("/"))) return null;
            return uri;
        } catch(IllegalArgumentException invalid) { return null; }
    }

    private String origin(URI uri) {
        String scheme=uri.getScheme().toLowerCase();
        int port=uri.getPort();
        boolean defaultPort=(scheme.equals("https") && port==443) || (scheme.equals("http") && port==80);
        return scheme+"://"+uri.getHost()+(port==-1 || defaultPort?"":":"+port);
    }

    private boolean validAuthority(URI uri) {
        String authority=uri.getRawAuthority();
        return authority!=null && !authority.endsWith(":")
                && authority.matches("(?i)(\\[[0-9a-f:.]+\\]|[a-z0-9.-]+)(:[0-9]{1,5})?");
    }

    // Render's Cloudflare edge replaces CF-Connecting-IP; trust it only with CF-Ray. X-Forwarded-For is not trusted.
    private String clientIp(HttpServletRequest req) {
        String forwarded=req.getHeader("CF-Connecting-IP");
        if(req.getHeader("CF-Ray")!=null && validIp(forwarded)) return forwarded.trim();
        String remote=req.getRemoteAddr();
        return validIp(remote)?remote:"unknown";
    }

    private boolean validIp(String value) {
        if(value==null || value.isBlank() || value.length()>64) return false;
        String ip=value.trim();
        try {
            if(ip.indexOf(':')>=0) return ip.matches("[0-9A-Fa-f:.]+") && InetAddress.getByName(ip).getAddress().length==16;
            String[] parts=ip.split("\\.",-1); if(parts.length!=4) return false;
            for(String part:parts) { if(part.isEmpty() || !part.matches("[0-9]{1,3}") || Integer.parseInt(part)>255) return false; }
            return true;
        } catch(Exception invalid) { return false; }
    }

    private String escapeHtml(String value) { return value.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;").replace(">","&gt;"); }
    private String setting(String name) {
        String value=System.getenv(name); if(value==null || value.isBlank()) value=System.getProperty(name);
        return value==null || value.isBlank()?null:value.trim();
    }
    private AuthService auth(HttpServletRequest req) { return (AuthService)getServletContext().getAttribute("authService"); }
    private String path(HttpServletRequest req) { return req.getRequestURI().substring(req.getContextPath().length()); }

    // Per-instance in-memory windows are bounded; a full map fails closed until expired buckets are swept.
    private static final class SlidingWindowLimiter {
        private final LinkedHashMap<String,ArrayDeque<Long>> buckets=new LinkedHashMap<>(128,0.75f,true);
        private long calls;
        synchronized boolean allow(String key,int limit,long now) {
            if((++calls & 127)==0) evictExpired(now);
            ArrayDeque<Long> times=buckets.get(key);
            if(times==null) {
                if(buckets.size()>=MAX_BUCKETS) evictExpired(now);
                if(buckets.size()>=MAX_BUCKETS) return false;
                times=new ArrayDeque<>(); buckets.put(key,times);
            }
            while(!times.isEmpty() && now-times.peekFirst()>=WINDOW_MS) times.removeFirst();
            if(times.isEmpty()) { buckets.remove(key); times=new ArrayDeque<>(); buckets.put(key,times); }
            if(times.size()>=limit) return false;
            times.addLast(now); return true;
        }
        private void evictExpired(long now) {
            Iterator<Map.Entry<String,ArrayDeque<Long>>> it=buckets.entrySet().iterator();
            while(it.hasNext()) {
                ArrayDeque<Long> timestamps=it.next().getValue();
                while(!timestamps.isEmpty() && now-timestamps.peekFirst()>=WINDOW_MS) timestamps.removeFirst();
                if(timestamps.isEmpty()) it.remove();
            }
        }
    }
}
