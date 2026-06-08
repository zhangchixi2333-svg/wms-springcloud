package com.example.wms.domain;

import jakarta.persistence.*;

/**
 * 客户实体类
 * 对应数据库表customer，存储仓库的客户信息
 */
@Entity
@Table(name = "customer")
public class Customer {

    /**
     * 主键ID，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 客户编码，唯一非空，最大长度64
     */
    @Column(nullable = false, unique = true, length = 64)
    private String customerCode;

    /**
     * 客户名称，非空，最大长度128
     */
    @Column(nullable = false, length = 128)
    private String customerName;

    public Long getId() {
        return id;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }
}