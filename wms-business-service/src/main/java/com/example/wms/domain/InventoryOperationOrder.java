/**
 * 本文件定义库存操作单明细实体，用统一操作号串联入库、出库、移库、转包和封存流程。
 */
package com.example.wms.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_operation_order")
public class InventoryOperationOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String operationNo;

    @Column(nullable = false, length = 32)
    private String operationType;

    @Column(length = 64)
    private String businessNo;

    @Column(length = 64)
    private String sourceKanbanNo;

    @Column(length = 64)
    private String targetKanbanNo;

    @Column(length = 128)
    private String sourceBarcode;

    @Column(length = 128)
    private String targetBarcode;

    @Column(nullable = false)
    private Long partId;

    private Long sourceLocationId;

    private Long targetLocationId;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal qty;

    @Column(length = 32)
    private String sourceStatus;

    @Column(length = 32)
    private String targetStatus;

    @Column(length = 255)
    private String remark;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getOperationNo() {
        return operationNo;
    }

    public void setOperationNo(String operationNo) {
        this.operationNo = operationNo;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getBusinessNo() {
        return businessNo;
    }

    public void setBusinessNo(String businessNo) {
        this.businessNo = businessNo;
    }

    public String getSourceKanbanNo() {
        return sourceKanbanNo;
    }

    public void setSourceKanbanNo(String sourceKanbanNo) {
        this.sourceKanbanNo = sourceKanbanNo;
    }

    public String getTargetKanbanNo() {
        return targetKanbanNo;
    }

    public void setTargetKanbanNo(String targetKanbanNo) {
        this.targetKanbanNo = targetKanbanNo;
    }

    public String getSourceBarcode() {
        return sourceBarcode;
    }

    public void setSourceBarcode(String sourceBarcode) {
        this.sourceBarcode = sourceBarcode;
    }

    public String getTargetBarcode() {
        return targetBarcode;
    }

    public void setTargetBarcode(String targetBarcode) {
        this.targetBarcode = targetBarcode;
    }

    public Long getPartId() {
        return partId;
    }

    public void setPartId(Long partId) {
        this.partId = partId;
    }

    public Long getSourceLocationId() {
        return sourceLocationId;
    }

    public void setSourceLocationId(Long sourceLocationId) {
        this.sourceLocationId = sourceLocationId;
    }

    public Long getTargetLocationId() {
        return targetLocationId;
    }

    public void setTargetLocationId(Long targetLocationId) {
        this.targetLocationId = targetLocationId;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public String getSourceStatus() {
        return sourceStatus;
    }

    public void setSourceStatus(String sourceStatus) {
        this.sourceStatus = sourceStatus;
    }

    public String getTargetStatus() {
        return targetStatus;
    }

    public void setTargetStatus(String targetStatus) {
        this.targetStatus = targetStatus;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
