package com.example.quanlybaotri.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler{
    @ExceptionHandler(ApiException.class) ProblemDetail api(ApiException e,HttpServletRequest r){return problem(e.getStatus(),e.getCode(),e.getMessage(),r);}
    @ExceptionHandler(MethodArgumentNotValidException.class) ProblemDetail validation(MethodArgumentNotValidException e,HttpServletRequest r){
        ProblemDetail d=problem(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Dữ liệu không hợp lệ",r);Map<String,String> errors=new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(x->errors.putIfAbsent(x.getField(),x.getDefaultMessage()));d.setProperty("errors",errors);return d;
    }
    private ProblemDetail problem(HttpStatus s,String c,String m,HttpServletRequest r){ProblemDetail d=ProblemDetail.forStatusAndDetail(s,m);d.setProperty("code",c);d.setProperty("path",r.getRequestURI());return d;}
}
