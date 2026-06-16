/**
 * 本文件定义入库明细实体，按“总数量 + 箱数 + 每箱数量”组织入库数据。
 */
package com.example.wms.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "inbound_order_item")
public class InboundOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long inboundOrderId;

    @Column(nullable = false)
    private Long partId;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal plannedQty;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal receivedQty;

    @Column(nullable = false)
    private Integer boxCount;

    @Column(nullable = false)
    private boolean pendingRepack;

    @Column(length = 64)
    private String equipmentCode;

    @Column(name = "unit_per_box", precision = 18, scale = 3)
    private BigDecimal unitPerBox;

    @Column(length = 128)
    private String warehouseZone;

    public Long getId() {
        return id;
    }

    public Long getInboundOrderId() {
        return inboundOrderId;
    }

    public void setInboundOrderId(Long inboundOrderId) {
        this.inboundOrderId = inboundOrderId;
    }

    public Long getPartId() {
        return partId;
    }

    public void setPartId(Long partId) {
        this.partId = partId;
    }

    public BigDecimal getPlannedQty() {
        return plannedQty;
    }

    public void setPlannedQty(BigDecimal plannedQty) {
        this.plannedQty = plannedQty;
    }

    public BigDecimal getReceivedQty() {
        return receivedQty;
    }

    public void setReceivedQty(BigDecimal receivedQty) {
        this.receivedQty = receivedQty;
    }

    public Integer getBoxCount() {
        return boxCount;
    }

    public void setBoxCount(Integer boxCount) {
        this.boxCount = boxCount;
    }

    public boolean isPendingRepack() {
        return pendingRepack;
    }

    public void setPendingRepack(boolean pendingRepack) {
        this.pendingRepack = pendingRepack;
    }

    public String getEquipmentCode() {
        return equipmentCode;
    }

    public void setEquipmentCode(String equipmentCode) {
        this.equipmentCode = equipmentCode;
    }

    public BigDecimal getUnitPerBox() {
        return unitPerBox;
    }

    public void setUnitPerBox(BigDecimal unitPerBox) {
        this.unitPerBox = unitPerBox;
    }

    public String getWarehouseZone() {
        return warehouseZone;
    }

    public void setWarehouseZone(String warehouseZone) {
        this.warehouseZone = warehouseZone;
    }
}
