package com.habitflow.web;

import com.habitflow.model.UserAccount;
import com.habitflow.servlet.LogoutServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class SecurityFilterTest {
    @Test void unauthenticatedWorkspaceRequestRedirectsToLogin() throws Exception {
        Map<String,Object> attributes=new HashMap<>(); HttpSession session=session(attributes);
        HttpServletRequest request=request(session,"GET","/",Map.of(),Map.of());
        Reply reply=new Reply(); AtomicBoolean reached=new AtomicBoolean();
        new SecurityFilter().doFilter(request,reply.response,(req,res)->reached.set(true));
        assertEquals("/login",reply.redirect);
        assertFalse(reached.get());
        assertNotNull(attributes.get(SecurityFilter.CSRF));
    }

    @Test void authenticatedUnsafeRequestRequiresMatchingCsrfToken() throws Exception {
        Map<String,Object> attributes=new HashMap<>(); attributes.put(SecurityFilter.ACCOUNT,new UserAccount(7,"a@example.test","A")); attributes.put(SecurityFilter.CSRF,"expected-token");
        HttpSession session=session(attributes); Reply accepted=new Reply(); AtomicBoolean reached=new AtomicBoolean();
        new SecurityFilter().doFilter(request(session,"POST","/api/workspace",Map.of("X-CSRF-Token","expected-token"),Map.of()),accepted.response,(req,res)->reached.set(true));
        assertTrue(reached.get()); assertNull(accepted.errorCode);

        Reply rejected=new Reply(); AtomicBoolean shouldNotReach=new AtomicBoolean();
        new SecurityFilter().doFilter(request(session,"POST","/api/workspace",Map.of("X-CSRF-Token","wrong-token"),Map.of()),rejected.response,(req,res)->shouldNotReach.set(true));
        assertEquals(HttpServletResponse.SC_FORBIDDEN,rejected.errorCode);
        assertFalse(shouldNotReach.get());
    }

    @Test void logoutInvalidatesTheServerSession() throws Exception {
        Map<String,Object> attributes=new HashMap<>(); attributes.put(SecurityFilter.ACCOUNT,new UserAccount(7,"a@example.test","A")); attributes.put(SecurityFilter.CSRF,"token");
        Reply reply=new Reply(); new LogoutServlet().service(request(session(attributes),"POST","/logout",Map.of(),Map.of()),reply.response);
        assertTrue(attributes.isEmpty());
        assertEquals("/login",reply.redirect);
    }

    private HttpServletRequest request(HttpSession session,String method,String uri,Map<String,String> headers,Map<String,String> params) {
        return proxy(HttpServletRequest.class,(instance,methodCall,args)->switch(methodCall.getName()) {
            case "getSession" -> session;
            case "getRequestURI" -> uri;
            case "getContextPath" -> "";
            case "getMethod" -> method;
            case "getHeader" -> headers.get(args[0]);
            case "getParameter" -> params.get(args[0]);
            case "getRequestDispatcher" -> null;
            default -> defaultValue(methodCall.getReturnType());
        });
    }
    private HttpSession session(Map<String,Object> attributes) {
        return proxy(HttpSession.class,(instance,methodCall,args)->switch(methodCall.getName()) {
            case "getAttribute" -> attributes.get(args[0]);
            case "setAttribute" -> {attributes.put((String)args[0],args[1]);yield null;}
            case "removeAttribute" -> {attributes.remove(args[0]);yield null;}
            case "invalidate" -> {attributes.clear();yield null;}
            default -> defaultValue(methodCall.getReturnType());
        });
    }
    private static final class Reply {
        private String redirect; private Integer errorCode;
        private final HttpServletResponse response=proxy(HttpServletResponse.class,(instance,methodCall,args)->switch(methodCall.getName()) {
            case "sendRedirect" -> {redirect=(String)args[0];yield null;}
            case "sendError" -> {errorCode=(Integer)args[0];yield null;}
            default -> defaultValue(methodCall.getReturnType());
        });
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type,java.lang.reflect.InvocationHandler handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
    }
    private static Object defaultValue(Class<?> type) {
        if(!type.isPrimitive()||type==void.class)return null;
        if(type==boolean.class)return false;if(type==char.class)return '\0';
        if(type==byte.class)return (byte)0;if(type==short.class)return (short)0;if(type==int.class)return 0;
        if(type==long.class)return 0L;if(type==float.class)return 0f;if(type==double.class)return 0d;return null;
    }
}
