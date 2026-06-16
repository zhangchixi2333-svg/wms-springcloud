/**
 * 本文件定义 Location 持久化实体。
 */
package com.example.wms.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "location")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String locationCode;

    @Column(nullable = false, length = 128)
    private String locationName;

    @Column(nullable = false, length = 128)
    private String warehouseName;

    @Column(nullable = false, length = 128)
    private String zoneName;

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
