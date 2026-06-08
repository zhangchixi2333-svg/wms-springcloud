package com.example.wms.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 出库单实体类
 * 对应数据库表outbound_order，存储仓库出库订单的核心信息
 */
@Entity
@Table(name = "outbound_order")
public class OutboundOrder {

    /**
     * 主键ID，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 出库单编号，唯一非空，最大长度64
     */
    @Column(nullable = false, unique = true, length = 64)
    private String outboundNo;

    /**
     * 关联的客户ID，指向Customer实体
     */
    private Long customerId;

    /**
     * 本次出库绑定的来源入库单号，多个以逗号分隔
     */
    @Column(length = 512)
    private String inboundOrderNos;

    /**
     * 订单状态，非空，最大长度32，如CREATED/COMPLETED/CANCELLED
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

    public String getOutboundNo() {
        return outboundNo;
    }

    public void setOutboundNo(String outboundNo) {
        this.outboundNo = outboundNo;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getInboundOrderNos() {
        return inboundOrderNos;
    }

    public void setInboundOrderNos(String inboundOrderNos) {
        this.inboundOrderNos = inboundOrderNos;
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
