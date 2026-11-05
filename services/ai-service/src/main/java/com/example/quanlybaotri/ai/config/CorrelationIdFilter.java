package com.example.quanlybaotri.ai.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER="X-Correlation-Id";
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
        throws ServletException,IOException {
        String supplied=request.getHeader(HEADER);
        String id=supplied==null||supplied.isBlank()?UUID.randomUUID().toString():supplied;
        response.setHeader(HEADER,id); MDC.put("correlationId",id);
        try{chain.doFilter(request,response);}finally{MDC.remove("correlationId");}
    }
}
