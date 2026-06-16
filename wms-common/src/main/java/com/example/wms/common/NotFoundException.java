/**
 * 本文件实现 NotFoundException 公共支撑模块。
 */
package com.example.wms.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
