package com.example.wms.domain;

import jakarta.persistence.*;

/**
 * 库位实体类
 * 对应数据库表location，存储仓库中的库位信息
 * 库位是仓库中存储物料的具体物理位置
 */
@Entity
@Table(name = "location")
public class Location {

    /**
     * 主键ID，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 库位编码，唯一非空，最大长度64
     */
    @Column(nullable = false, unique = true, length = 64)
    private String locationCode;

    /**
     * 库位名称，非空，最大长度128
     */
    @Column(nullable = false, length = 128)
    private String locationName;

    /**
     * 所属仓库名称，非空，最大长度128
     */
    @Column(nullable = false, length = 128)
    private String warehouseName;

    /**
     * 所属库区名称，非空，最大长度128
     */
    @Column(nullable = false, length = 128)
    private String zoneName;

    /**
     * 仓库性质：OWN-自己仓库，THIRD_PARTY-第三方仓库
     */
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32) default 'OWN'")
    private String warehouseType = "OWN";

    public Long getId() {
        return id;
    }

    public String getLocationCode() {
        return locationCode;
    }

    public void setLocationCode(String locationCode) {
        this.locationCode = locationCode;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
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

    public String getWarehouseType() {
        return warehouseType;
    }

    public void setWarehouseType(String warehouseType) {
        this.warehouseType = warehouseType;
    }
}
