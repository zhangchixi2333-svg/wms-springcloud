/**
 * 本文件定义箱级看板实体；每条看板对应一个实际包装箱，旧层级字段仅保留兼容历史数据。
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

    private Long parentKanbanId;

    @Column(nullable = false)
    private boolean parentKanban;

    @Column(nullable = false)
    private Integer boxIndex;

    @Column(nullable = false)
    private Long partId;

    @Column(nullable = false)
    private Long supplierId;

    @Column(length = 512)
    private String outboundOrderNo;

    @Column(length = 64)
    private String batchNo;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal qty;

    @Column(precision = 18, scale = 3)
    private BigDecimal availableQty;

    @Column(precision = 18, scale = 3)
    private BigDecimal reservedQty;

    @Column(precision = 18, scale = 3)
    private BigDecimal reservedTransferQty;

    @Column(precision = 18, scale = 3)
    private BigDecimal outboundQty;

    private Long sourceKanbanId;

    @Column(length = 512)
    private String transferOrderNo;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private boolean frozen;

    @Column(length = 32)
    private String frozenPreviousStatus;

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

    public Long getParentKanbanId() {
        return parentKanbanId;
    }

    public void setParentKanbanId(Long parentKanbanId) {
        this.parentKanbanId = parentKanbanId;
    }

    public boolean isParentKanban() {
        return parentKanban;
    }

    public void setParentKanban(boolean parentKanban) {
        this.parentKanban = parentKanban;
    }

    public Integer getBoxIndex() {
        return boxIndex;
    }

    public void setBoxIndex(Integer boxIndex) {
        this.boxIndex = boxIndex;
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

    public BigDecimal getAvailableQty() {
        return availableQty;
    }

    public void setAvailableQty(BigDecimal availableQty) {
        this.availableQty = availableQty;
    }

    public BigDecimal getReservedQty() {
        return reservedQty;
    }

    public void setReservedQty(BigDecimal reservedQty) {
        this.reservedQty = reservedQty;
    }

    public BigDecimal getReservedTransferQty() {
        return reservedTransferQty;
    }

    public void setReservedTransferQty(BigDecimal reservedTransferQty) {
        this.reservedTransferQty = reservedTransferQty;
    }

    public BigDecimal getOutboundQty() {
        return outboundQty;
    }

    public void setOutboundQty(BigDecimal outboundQty) {
        this.outboundQty = outboundQty;
    }

    public Long getSourceKanbanId() {
        return sourceKanbanId;
    }

    public void setSourceKanbanId(Long sourceKanbanId) {
        this.sourceKanbanId = sourceKanbanId;
    }

    public String getTransferOrderNo() {
        return transferOrderNo;
    }

    public void setTransferOrderNo(String transferOrderNo) {
        this.transferOrderNo = transferOrderNo;
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

    public String getFrozenPreviousStatus() {
        return frozenPreviousStatus;
    }

    public void setFrozenPreviousStatus(String frozenPreviousStatus) {
        this.frozenPreviousStatus = frozenPreviousStatus;
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
