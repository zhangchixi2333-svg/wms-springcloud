package com.example.wms.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * 仓库器具实体类
 * 对应数据库表equipment，存储仓库使用的周转箱、料箱等器具信息
 */
@Entity
@Table(name = "equipment")
public class Equipment {

    /**
     * 主键ID，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 器具编码，唯一非空，最大长度64
     */
    @Column(nullable = false, unique = true, length = 64)
    private String equipmentCode;

    /**
     * 器具名称，非空，最大长度128
     */
    @Column(nullable = false, length = 128)
    private String equipmentName;

    /**
     * 器具类型，非空，最大长度32，如NORMAL(普通)/REPACK(转包用)
     */
    @Column(nullable = false, length = 32)
    private String equipmentType;

    /**
     * 器具规格型号，非空，最大长度64，如"600x400"
     */
    @Column(nullable = false, length = 64)
    private String equipmentModel;

    /**
     * 容量，精度18位，小数位3位，可存放的货物数量
     */
    @Column(precision = 18, scale = 3)
    private BigDecimal capacity;

    /**
     * 所属仓库名称，最大长度128
     */
    @Column(length = 128)
    private String warehouseName;

    /**
     * 所属库区名称，最大长度128
     */
    @Column(length = 128)
    private String zoneName;

    /**
     * 器具状态，非空，最大长度32，如ENABLED(启用)/DISABLED(停用)
     */
    @Column(nullable = false, length = 32)
    private String status;

    public Long getId() {
        return id;
    }

    public String getEquipmentCode() {
        return equipmentCode;
    }

    public void setEquipmentCode(String equipmentCode) {
        this.equipmentCode = equipmentCode;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public void setEquipmentName(String equipmentName) {
        this.equipmentName = equipmentName;
    }

    public String getEquipmentType() {
        return equipmentType;
    }

    public void setEquipmentType(String equipmentType) {
        this.equipmentType = equipmentType;
    }

    public String getEquipmentModel() {
        return equipmentModel;
    }

    public void setEquipmentModel(String equipmentModel) {
        this.equipmentModel = equipmentModel;
    }

    public BigDecimal getCapacity() {
        return capacity;
    }

    public void setCapacity(BigDecimal capacity) {
        this.capacity = capacity;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}