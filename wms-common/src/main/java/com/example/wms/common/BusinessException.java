package com.example.wms.common;

/**
 * 业务逻辑异常类
 * 用于封装业务流程中发生的可预期错误，继承自RuntimeException避免强制捕获
 */
public class BusinessException extends RuntimeException {

    /**
     * 构造业务异常实例
     * @param message 异常描述消息，说明具体的业务错误原因
     */
    public BusinessException(String message) {
        super(message);
    }
}