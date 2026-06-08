package com.example.wms.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局API异常处理器
 * 使用@RestControllerAdvice注解统一捕获所有Controller抛出的异常，统一处理并返回标准格式响应
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * 处理资源未找到异常
     * @param ex  NotFoundException实例，包含错误信息
     * @return 统一格式的响应实体，HTTP状态码404
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * 处理业务逻辑异常
     * @param ex  BusinessException实例，包含业务错误信息
     * @return 统一格式的响应实体，HTTP状态码400
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * 处理请求参数校验失败异常（如@Valid注解校验不通过）
     * @param ex  MethodArgumentNotValidException实例，包含校验错误信息
     * @return 统一格式的响应实体，HTTP状态码400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        // 提取第一个校验错误信息，拼接字段名和错误消息
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return build(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 处理所有未明确捕获的其他通用异常
     * @param ex  Exception实例，通用异常
     * @return 统一格式的响应实体，HTTP状态码500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    /**
     * 构建统一格式的错误响应
     * @param status  HTTP状态码
     * @param message 错误消息
     * @return 包装后的响应实体，包含success和message字段
     */
    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("success", false);    // 响应成功标识，异常场景固定为false
        payload.put("message", message);  // 错误详情消息
        return ResponseEntity.status(status).body(payload);
    }
}