package com.example.wms.common;

/**
 * 通用API响应记录类
 * 使用Java Record特性实现不可变数据结构，统一所有接口的响应格式
 * @param <T> 响应数据的泛型类型
 */
public record ApiResponse<T>(
    boolean success,  // 响应成功标识：true表示成功，false表示失败
    T data,           // 响应携带的业务数据，成功时才有值
    String message    // 响应消息，成功或失败的描述信息
) {

    /**
     * 创建成功的响应，携带业务数据
     * @param data 业务数据对象
     * @param <T>  业务数据的泛型类型
     * @return 包装好的ApiResponse实例
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "OK");
    }

    /**
     * 创建成功的响应，只携带消息不携带数据
     * @param message 成功消息
     * @return 包装好的ApiResponse实例，data为null
     */
    public static ApiResponse<Void> okMessage(String message) {
        return new ApiResponse<>(true, null, message);
    }
}