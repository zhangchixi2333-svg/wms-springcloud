package com.example.wms.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 入库单实体类
 * 对应数据库表inbound_order，存储仓库入库订单的核心信息
 */
@Entity
@Table(name = "inbound_order")
public class InboundOrder {

    /**
     * 主键ID，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 入库单编号，唯一非空，最大长度64
     */
    @Column(nullable = false, unique = true, length = 64)
    private String inboundNo;

    /**
     * 关联的供应商ID，指向Supplier实体，非空
     */
    @Column(nullable = false)
    private Long supplierId;

    /**
     * 订单状态，非空，最大长度32，如CREATED/RECEIVED/CANCELLED
     */
    @Column(nullable = false, length = 32)
    private String status;

    /**
     * 订单创建时间，非空
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getInboundNo() {
        return inboundNo;
    }

    public void setInboundNo(String inboundNo) {
        this.inboundNo = inboundNo;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}