/**
 * 本文件定义 OutboundOrderItem 持久化实体。
 */
package com.example.wms.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "outbound_order_item")
public class OutboundOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long outboundOrderId;

    @Column(nullable = false)
    private Long partId;

    private Long kanbanId;

    @Column(length = 64)
    private String kanbanNo;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal plannedQty;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal scannedQty;

    @Column(nullable = false)
    private Integer boxCount;

    @Column(length = 64)
    private String equipmentCode;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal unitPerBox;

    @Column(length = 64)
    private String locationCode;

    @Column(length = 128)
    private String warehouseName;

    @Column(length = 128)
    private String zoneName;

    public Long getId() {
        return id;
    }

    public Long getOutboundOrderId() {
        return outboundOrderId;
    }

    public void setOutboundOrderId(Long outboundOrderId) {
        this.outboundOrderId = outboundOrderId;
    }

    public Long getPartId() {
        return partId;
    }

    public void setPartId(Long partId) {
        this.partId = partId;
    }

    public Long getKanbanId() {
        return kanbanId;
    }

    public void setKanbanId(Long kanbanId) {
        this.kanbanId = kanbanId;
    }

    public String getKanbanNo() {
        return kanbanNo;
    }

    public void setKanbanNo(String kanbanNo) {
        this.kanbanNo = kanbanNo;
    }

    public BigDecimal getPlannedQty() {
        return plannedQty;
    }

    public void setPlannedQty(BigDecimal plannedQty) {
        this.plannedQty = plannedQty;
    }

    public BigDecimal getScannedQty() {
        return scannedQty;
    }

    public void setScannedQty(BigDecimal scannedQty) {
        this.scannedQty = scannedQty;
    }

    public Integer getBoxCount() {
        return boxCount;
    }

    public void setBoxCount(Integer boxCount) {
        this.boxCount = boxCount;
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

    public String getLocationCode() {
        return locationCode;
    }

    public void setLocationCode(String locationCode) {
        this.locationCode = locationCode;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }
}
