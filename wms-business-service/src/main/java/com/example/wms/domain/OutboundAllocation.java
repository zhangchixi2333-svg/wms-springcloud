/**
 * 本文件定义出库分配实体，用于记录出库单从哪些入库箱级看板锁定和扣减了多少数量。
 */
package com.example.wms.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbound_allocation")
public class OutboundAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long outboundOrderId;

    @Column(nullable = false)
    private Long outboundOrderItemId;

    @Column(nullable = false)
    private Long kanbanId;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal allocatedQty;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal outboundQty;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime outboundTime;

    public Long getId() {
        return id;
    }

    public Long getOutboundOrderId() {
        return outboundOrderId;
    }

    public void setOutboundOrderId(Long outboundOrderId) {
        this.outboundOrderId = outboundOrderId;
    }

    public Long getOutboundOrderItemId() {
        return outboundOrderItemId;
    }

    public void setOutboundOrderItemId(Long outboundOrderItemId) {
        this.outboundOrderItemId = outboundOrderItemId;
    }

    public Long getKanbanId() {
        return kanbanId;
    }

    public void setKanbanId(Long kanbanId) {
        this.kanbanId = kanbanId;
    }

    public BigDecimal getAllocatedQty() {
        return allocatedQty;
    }

    public void setAllocatedQty(BigDecimal allocatedQty) {
        this.allocatedQty = allocatedQty;
    }

    public BigDecimal getOutboundQty() {
        return outboundQty;
    }

    public void setOutboundQty(BigDecimal outboundQty) {
        this.outboundQty = outboundQty;
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

    public LocalDateTime getOutboundTime() {
        return outboundTime;
    }

    public void setOutboundTime(LocalDateTime outboundTime) {
        this.outboundTime = outboundTime;
    }
}
