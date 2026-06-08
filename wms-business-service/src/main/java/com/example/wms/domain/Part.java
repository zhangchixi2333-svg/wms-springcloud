package com.example.wms.domain;

import jakarta.persistence.*;

/**
 * 物料/零件实体类
 * 对应数据库表part，存储仓库中管理的物料/零件基本信息
 */
@Entity
@Table(name = "part")
public class Part {

    /**
     * 主键ID，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 物料编码，唯一非空，最大长度64
     */
    @Column(nullable = false, unique = true, length = 64)
    private String partCode;

    /**
     * 物料名称，非空，最大长度128
     */
    @Column(nullable = false, length = 128)
    private String partName;

    /**
     * 计量单位，非空，最大长度32，如"个"/"件"/"千克"
     */
    @Column(nullable = false, length = 32)
    private String unit;

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
}