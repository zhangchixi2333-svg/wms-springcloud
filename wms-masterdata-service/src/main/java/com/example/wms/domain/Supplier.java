package com.example.wms.domain;

import jakarta.persistence.*;

/**
 * 供应商实体类
 * 对应数据库表supplier，存储仓库的供应商信息
 */
@Entity
@Table(name = "supplier")
public class Supplier {

    /**
     * 主键ID，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 供应商编码，唯一非空，最大长度64
     */
    @Column(nullable = false, unique = true, length = 64)
    private String supplierCode;

    /**
     * 供应商名称，非空，最大长度128
     */
    @Column(nullable = false, length = 128)
    private String supplierName;

    public Long getId() {
        return id;
    }

    public String getSupplierCode() {
        return supplierCode;
    }

    public void setSupplierCode(String supplierCode) {
        this.supplierCode = supplierCode;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }
}