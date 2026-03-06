package com.marketing.common;
import com.marketing.strategy.service.BusinessProfileNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req){
    return ResponseEntity.badRequest().body(new ApiError(req.getHeader("X-Request-Id"),"VALIDATION_ERROR","Request validation failed",Map.of("errors", ex.getBindingResult().getFieldErrors().stream().map(e->e.getField()+":"+e.getDefaultMessage()).toList())));
  }
  @ExceptionHandler(BusinessProfileNotFoundException.class)
  public ResponseEntity<ApiError> notFound(BusinessProfileNotFoundException ex, HttpServletRequest req){
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(req.getHeader("X-Request-Id"),"NOT_FOUND",ex.getMessage(),Map.of()));
  }
  @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
  public ResponseEntity<ApiError> badRequest(Exception ex, HttpServletRequest req){
    return ResponseEntity.badRequest().body(new ApiError(req.getHeader("X-Request-Id"),"BAD_REQUEST",ex.getMessage(),Map.of()));
  }
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> generic(Exception ex, HttpServletRequest req){
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(req.getHeader("X-Request-Id"),"INTERNAL_ERROR",ex.getMessage(),Map.of()));
  }
}
