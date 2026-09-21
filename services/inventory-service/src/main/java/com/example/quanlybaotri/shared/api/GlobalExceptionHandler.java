package com.example.quanlybaotri.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException ex, HttpServletRequest request) {
        return problem(ex.getStatus(), ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail detail = problem(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "Dữ liệu không hợp lệ",
            request
        );
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult()
            .getFieldErrors()
            .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler({
        ObjectOptimisticLockingFailureException.class,
        DataIntegrityViolationException.class,
    })
    ProblemDetail conflict(Exception ex, HttpServletRequest request) {
        return problem(
            HttpStatus.CONFLICT,
            "CONCURRENT_OR_DUPLICATE_UPDATE",
            "Dữ liệu đã thay đổi hoặc vi phạm ràng buộc duy nhất",
            request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail denied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(
            HttpStatus.FORBIDDEN,
            "FORBIDDEN",
            "Bạn không có quyền thực hiện thao tác này",
            request
        );
    }

    private ProblemDetail problem(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request
    ) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(status.getReasonPhrase());
        detail.setProperty("code", code);
        detail.setProperty("path", request.getRequestURI());
        return detail;
    }
}
