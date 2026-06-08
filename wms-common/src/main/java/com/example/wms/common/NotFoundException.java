package com.example.wms.common;

/**
 * 资源未找到异常类
 * 当请求的数据库资源（如用户、订单、商品等）不存在时抛出，统一处理404场景
 */
public class NotFoundException extends RuntimeException {

    /**
     * 构造资源未找到异常实例
     * @param message 异常描述消息，说明未找到的资源类型和标识
     */
    public NotFoundException(String message) {
        super(message);
    }
}