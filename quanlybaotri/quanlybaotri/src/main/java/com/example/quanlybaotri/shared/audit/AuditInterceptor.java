package com.example.quanlybaotri.shared.audit;

import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.identity.persistence.UserRepository;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuditInterceptor implements HandlerInterceptor {
    private final AuditLogRepository logs;
    private final UserRepository users;

    public AuditInterceptor(AuditLogRepository l, UserRepository u) {
        logs = l;
        users = u;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        if (ex != null || res.getStatus() >= 400
                || !java.util.Set.of("POST", "PUT", "PATCH", "DELETE").contains(req.getMethod())
                || !req.getRequestURI().startsWith("/api/v1/"))
            return;
        try {
            UserAccount actor = null;
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName()))
                actor = users.findWithRolesByUsername(auth.getName()).orElse(null);
            String forwarded = req.getHeader("X-Forwarded-For");
            String ip = forwarded == null ? req.getRemoteAddr() : forwarded.split(",")[0].trim();
            logs.save(new AuditLog(actor, req.getMethod() + " " + req.getRequestURI(), category(req.getRequestURI()),
                    "{\"status\":" + res.getStatus() + "}", ip));
        } catch (RuntimeException ignored) {
        }
    }

    private String category(String path) {
        String[] p = path.split("/");
        return p.length > 3 ? p[3].toUpperCase() : "API";
    }
}
