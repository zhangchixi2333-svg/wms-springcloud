package com.example.wms.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存实体类
 * 对应数据库表inventory，存储每个库位上每种物料的当前库存数量
 * 唯一约束：同一个物料+同一个库位只能有一条库存记录（uk_part_location）
 */
@Entity
@Table(name = "inventory", uniqueConstraints = {
        @UniqueConstraint(name = "uk_part_location", columnNames = {"partId", "locationId"})
})
public class Inventory {

    /**
     * 主键ID，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
     * 当前库存数量，精度18位，小数位3位，非空
     */
    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal qty;

    /**
     * 最后更新时间，非空
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
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

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}