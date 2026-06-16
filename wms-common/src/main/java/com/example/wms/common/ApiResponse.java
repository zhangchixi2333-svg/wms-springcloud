/**
 * 本文件实现 ApiResponse 公共支撑模块。
 */
package com.example.wms.common;

public record ApiResponse<T>(
    boolean success,
    T data,
    String message
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "OK");
    }

    public static ApiResponse<Void> okMessage(String message) {
        return new ApiResponse<>(true, null, message);
    }
}
