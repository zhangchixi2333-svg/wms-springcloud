package com.example.wms.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * 入库单行项目实体类
 * 对应数据库表inbound_order_item，存储入库单中每个商品的明细信息
 */
@Entity
@Table(name = "inbound_order_item")
public class InboundOrderItem {

    /**
     * 主键ID，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的入库单主表ID，指向InboundOrder实体，非空
     */
    @Column(nullable = false)
    private Long inboundOrderId;

    /**
     * 关联的零件ID，指向Part实体，非空
     */
    @Column(nullable = false)
    private Long partId;

    /**
     * 计划入库数量，精度18位，小数位3位，非空
     */
    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal plannedQty;

    /**
     * 实际已入库数量，精度18位，小数位3位，非空
     */
    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal receivedQty;

    /**
     * 箱子数量，非空
     */
    @Column(nullable = false)
    private Integer boxCount;

    /**
     * 是否有待转包处理，非空
     */
    @Column(nullable = false)
    private boolean pendingRepack;

    /**
     * 使用的器具编码，最大长度64，指向Equipment实体
     */
    @Column(length = 64)
    private String equipmentCode;

    /**
     * 包装容量，精度18位，小数位3位，每个箱子可存放的数量
     */
    @Column(precision = 18, scale = 3)
    private BigDecimal packageCapacity;

    /**
     * 入库库区，最大长度128，如"A仓 / 一区"
     */
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

    public BigDecimal getPackageCapacity() {
        return packageCapacity;
    }

    public void setPackageCapacity(BigDecimal packageCapacity) {
        this.packageCapacity = packageCapacity;
    }

    public String getWarehouseZone() {
        return warehouseZone;
    }

    public void setWarehouseZone(String warehouseZone) {
        this.warehouseZone = warehouseZone;
    }
}