package com.example.wms.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存交易记录实体类
 * 对应数据库表inventory_transaction，记录所有库存变动的历史流水
 */
@Entity
@Table(name = "inventory_transaction")
public class InventoryTransaction {

    /**
     * 主键ID，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 交易流水号，唯一非空，最大长度64
     */
    @Column(nullable = false, unique = true, length = 64)
    private String transactionNo;

    /**
     * 关联的物料ID，指向Part实体，非空
     */
    @Column(nullable = false)
    private Long partId;

    /**
     * 关联的库位ID，指向Location实体，非空
     */
    @Column(nullable = false)
    private Long locationId;

    /**
     * 条码，非空，最大长度128，记录操作的看板条码
     */
    @Column(nullable = false, length = 128)
    private String barcode;

    /**
     * 数量变动，精度18位，小数位3位，非空
     * 正数表示入库/增加，负数表示出库/减少
     */
    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal qtyChange;

    /**
     * 业务类型，非空，最大长度32，如INBOUND/OUTBOUND/MANUAL等
     */
    @Column(nullable = false, length = 32)
    private String businessType;

    /**
     * 关联业务单号，非空，最大长度64，入库单/出库单编号
     */
    @Column(nullable = false, length = 64)
    private String businessNo;

    /**
     * 操作备注，最大长度255
     */
    @Column(length = 255)
    private String remark;

    /**
     * 创建时间，非空，记录交易发生的时间
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getTransactionNo() {
        return transactionNo;
    }

    public void setTransactionNo(String transactionNo) {
        this.transactionNo = transactionNo;
    }

    public Long getPartId() {
        return partId;
    }

    public void setPartId(Long partId) {
        this.partId = partId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public BigDecimal getQtyChange() {
        return qtyChange;
    }

    public void setQtyChange(BigDecimal qtyChange) {
        this.qtyChange = qtyChange;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getBusinessNo() {
        return businessNo;
    }

    public void setBusinessNo(String businessNo) {
        this.businessNo = businessNo;
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