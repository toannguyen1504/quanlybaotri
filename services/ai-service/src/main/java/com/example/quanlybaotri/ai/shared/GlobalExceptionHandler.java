package com.example.quanlybaotri.ai.shared;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class) ProblemDetail api(ApiException e,HttpServletRequest r){return problem(e.status(),e.code(),e.getMessage(),r);}
    @ExceptionHandler(MethodArgumentNotValidException.class) ProblemDetail validation(MethodArgumentNotValidException e,HttpServletRequest r){
        ProblemDetail p=problem(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Dữ liệu không hợp lệ",r);
        Map<String,String> errors=new LinkedHashMap<>(); e.getBindingResult().getFieldErrors().forEach(x->errors.putIfAbsent(x.getField(),x.getDefaultMessage()));
        p.setProperty("errors",errors); return p;
    }
    @ExceptionHandler(AccessDeniedException.class) ProblemDetail denied(AccessDeniedException e,HttpServletRequest r){return problem(HttpStatus.FORBIDDEN,"FORBIDDEN","Bạn không có quyền thực hiện thao tác này",r);}
    private ProblemDetail problem(HttpStatus s,String code,String message,HttpServletRequest r){ProblemDetail p=ProblemDetail.forStatusAndDetail(s,message);p.setTitle(s.getReasonPhrase());p.setProperty("code",code);p.setProperty("path",r.getRequestURI());return p;}
}
