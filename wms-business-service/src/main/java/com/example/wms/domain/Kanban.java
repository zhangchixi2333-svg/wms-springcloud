package com.example.wms.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 看板实体类
 * 对应数据库表kanban，存储仓库中每个箱子/料箱的物料标签信息
 * 看板是仓库管理中物料追溯和移动的核心单元
 */
@Entity
@Table(name = "kanban")
public class Kanban {

    /**
     * 主键ID，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 看板编号，唯一非空，最大长度64
     */
    @Column(nullable = false, unique = true, length = 64)
    private String kanbanNo;

    /**
     * 条码内容，唯一非空，最大长度128，用于扫码识别
     */
    @Column(nullable = false, unique = true, length = 128)
    private String barcode;

    @Column(unique = true, length = 255)
    private String qrContent;

    /**
     * 关联的入库单ID，指向InboundOrder实体，非空
     */
    @Column(nullable = false)
    private Long inboundOrderId;

    /**
     * 关联的入库单行项目ID，指向InboundOrderItem实体，非空
     */
    @Column(nullable = false)
    private Long inboundOrderItemId;

    /**
     * 关联的物料ID，指向Part实体，非空
     */
    @Column(nullable = false)
    private Long partId;

    /**
     * 关联的供应商ID，指向Supplier实体，非空
     */
    @Column(nullable = false)
    private Long supplierId;

    /**
     * 关联的出库单编号，指向OutboundOrder实体，最大长度64
     */
    @Column(length = 64)
    private String outboundOrderNo;

    /**
     * 批次号，最大长度64，物料的生产批次
     */
    @Column(length = 64)
    private String batchNo;

    /**
     * 看板承载数量，精度18位，小数位3位，非空
     */
    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal qty;

    /**
     * 看板状态，非空，最大长度32，如AVAILABLE(可用)/INBOUND(入库中)/OUTBOUND(出库中)
     */
    @Column(nullable = false, length = 32)
    private String status;

    /**
     * 是否冻结，非空，true表示看板被冻结，无法进行移动操作
     */
    @Column(nullable = false)
    private boolean frozen;

    /**
     * 当前绑定的库位ID，指向Location实体
     */
    private Long locationId;

    /**
     * 创建时间，非空，看板生成的时间
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * 入库完成时间
     */
    private LocalDateTime inboundTime;

    /**
     * 出库完成时间
     */
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
