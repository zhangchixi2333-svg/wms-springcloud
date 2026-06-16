/**
 * 本文件定义 Kanban 持久化实体。
 */
package com.example.wms.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "kanban")
public class Kanban {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String kanbanNo;

    @Column(nullable = false, unique = true, length = 128)
    private String barcode;

    @Column(unique = true, length = 255)
    private String qrContent;

    @Column(nullable = false)
    private Long inboundOrderId;

    @Column(nullable = false)
    private Long inboundOrderItemId;

    @Column(nullable = false)
    private Long partId;

    @Column(nullable = false)
    private Long supplierId;

    @Column(length = 64)
    private String outboundOrderNo;

    @Column(length = 64)
    private String batchNo;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal qty;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private boolean frozen;

    private Long locationId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime inboundTime;

    private LocalDateTime outboundTime;

    public Long getId() {
        return id;
    }

    public String getKanbanNo() {
        return kanbanNo;
    }

    public void setKanbanNo(String kanbanNo) {
        this.kanbanNo = kanbanNo;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getQrContent() {
        return qrContent;
    }

    public void setQrContent(String qrContent) {
        this.qrContent = qrContent;
    }

    public Long getInboundOrderId() {
        return inboundOrderId;
    }

    public void setInboundOrderId(Long inboundOrderId) {
        this.inboundOrderId = inboundOrderId;
    }

    public Long getInboundOrderItemId() {
        return inboundOrderItemId;
    }

    public void setInboundOrderItemId(Long inboundOrderItemId) {
        this.inboundOrderItemId = inboundOrderItemId;
    }

    public Long getPartId() {
        return partId;
    }

    public void setPartId(Long partId) {
        this.partId = partId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getOutboundOrderNo() {
        return outboundOrderNo;
    }

    public void setOutboundOrderNo(String outboundOrderNo) {
        this.outboundOrderNo = outboundOrderNo;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getInboundTime() {
        return inboundTime;
    }

    public void setInboundTime(LocalDateTime inboundTime) {
        this.inboundTime = inboundTime;
    }

    public LocalDateTime getOutboundTime() {
        return outboundTime;
    }

    public void setOutboundTime(LocalDateTime outboundTime) {
        this.outboundTime = outboundTime;
    }
}
