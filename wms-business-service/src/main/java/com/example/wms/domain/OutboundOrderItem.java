package com.example.wms.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * 出库单行项目实体类
 * 对应数据库表outbound_order_item，存储出库单中每个商品的明细信息
 */
@Entity
@Table(name = "outbound_order_item")
public class OutboundOrderItem {

    /**
     * 主键ID，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的出库单主表ID，指向OutboundOrder实体，非空
     */
    @Column(nullable = false)
    private Long outboundOrderId;

    /**
     * 关联的零件ID，指向Part实体，非空
     */
    @Column(nullable = false)
    private Long partId;

    /**
     * 计划出库数量，精度18位，小数位3位，非空
     */
    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal plannedQty;

    /**
     * 实际已扫描出库数量，精度18位，小数位3位，非空
     */
    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal scannedQty;

    /**
     * 出库仓库名称，最大长度128
     */
    @Column(length = 128)
    private String warehouseName;

    /**
     * 出库库区名称，最大长度128
     */
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