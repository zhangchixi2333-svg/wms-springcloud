/**
 * 本文件定义 Part 持久化实体。
 */
package com.example.wms.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "part")
public class Part {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String partCode;

    @Column(nullable = false, length = 128)
    private String partName;

    @Column(nullable = false, length = 32)
    private String unit;

    @Column(name = "category_code", length = 64)
    private String categoryCode;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "default_equipment_code", length = 64)
    private String defaultEquipmentCode;

    @Column(name = "default_package_capacity", precision = 18, scale = 3)
    private BigDecimal defaultPackageCapacity;

    public Long getId() {
        return id;
    }

    public String getPartCode() {
        return partCode;
    }

    public void setPartCode(String partCode) {
        this.partCode = partCode;
    }

    public String getPartName() {
        return partName;
    }

    public void setPartName(String partName) {
        this.partName = partName;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getDefaultEquipmentCode() {
        return defaultEquipmentCode;
    }

    public void setDefaultEquipmentCode(String defaultEquipmentCode) {
        this.defaultEquipmentCode = defaultEquipmentCode;
    }

    public BigDecimal getDefaultPackageCapacity() {
        return defaultPackageCapacity;
    }

    public void setDefaultPackageCapacity(BigDecimal defaultPackageCapacity) {
        this.defaultPackageCapacity = defaultPackageCapacity;
    }
}
