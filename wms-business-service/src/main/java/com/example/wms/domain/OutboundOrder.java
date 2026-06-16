/**
 * 本文件定义 OutboundOrder 持久化实体。
 */
package com.example.wms.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbound_order")
public class OutboundOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String outboundNo;

    private Long customerId;

    @Column(length = 512)
    private String inboundOrderNos;

    @Column(nullable = false, length = 32)
    private String status;

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
