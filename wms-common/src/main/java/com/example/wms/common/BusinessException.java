/**
 * 本文件实现 BusinessException 公共支撑模块。
 */
package com.example.wms.common;

public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
