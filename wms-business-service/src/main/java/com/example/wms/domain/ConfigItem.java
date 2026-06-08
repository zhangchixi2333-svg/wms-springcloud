package com.example.wms.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "config_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_config_module_code", columnNames = {"moduleKey", "itemCode"})
})
public class ConfigItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String moduleKey;

    @Column(nullable = false, length = 64)
    private String itemCode;

    @Column(nullable = false, length = 128)
    private String itemName;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(length = 255)
    private String remark;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getModuleKey() {
        return moduleKey;
    }

    public void setModuleKey(String moduleKey) {
        this.moduleKey = moduleKey;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
