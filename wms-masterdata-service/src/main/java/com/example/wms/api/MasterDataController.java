package com.example.wms.api;

import com.example.wms.common.ApiResponse;
import com.example.wms.service.CatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 基础数据管理控制器
 * 管理系统中的基础主数据：供应商、客户、设备、物料、库位等
 * 提供这些基础数据的查询和创建功能
 */
@RestController
@RequestMapping("/api")
public class MasterDataController {

    /**
     * 基础数据服务层
     */
    private final CatalogService catalogService;

    /**
     * 构造函数，注入CatalogService
     * @param catalogService 基础数据服务实例
     */
    public MasterDataController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * 查询所有供应商列表
     * @return 供应商视图列表ApiResponse
     */
    @GetMapping("/suppliers")
    public ApiResponse<List<SupplierView>> listSuppliers() {
        return ApiResponse.ok(catalogService.listSuppliers());
    }

    /**
     * 创建新供应商
     * @param request 供应商创建请求
     * @return 创建的供应商视图ApiResponse
     */
    @PostMapping("/suppliers")
    public ApiResponse<SupplierView> createSupplier(@Valid @RequestBody SupplierRequest request) {
        return ApiResponse.ok(catalogService.createSupplier(request));
    }

    /**
     * 查询所有客户列表
     * @return 客户视图列表ApiResponse
     */
    @GetMapping("/customers")
    public ApiResponse<List<CustomerView>> listCustomers() {
        return ApiResponse.ok(catalogService.listCustomers());
    }

    /**
     * 创建新客户
     * @param request 客户创建请求
     * @return 创建的客户视图ApiResponse
     */
    @PostMapping("/customers")
    public ApiResponse<CustomerView> createCustomer(@Valid @RequestBody CustomerRequest request) {
        return ApiResponse.ok(catalogService.createCustomer(request));
    }

    /**
     * 查询所有设备列表
     * @return 设备视图列表ApiResponse
     */
    @GetMapping("/equipment")
    public ApiResponse<List<EquipmentView>> listEquipment() {
        return ApiResponse.ok(catalogService.listEquipment());
    }

    /**
     * 创建新设备
     * @param request 设备创建请求
     * @return 创建的设备视图ApiResponse
     */
    @PostMapping("/equipment")
    public ApiResponse<EquipmentView> createEquipment(@Valid @RequestBody EquipmentRequest request) {
        return ApiResponse.ok(catalogService.createEquipment(request));
    }

    /**
     * 查询所有物料列表
     * @return 物料视图列表ApiResponse
     */
    @GetMapping("/parts")
    public ApiResponse<List<PartView>> listParts() {
        return ApiResponse.ok(catalogService.listParts());
    }

    /**
     * 创建新物料
     * @param request 物料创建请求
     * @return 创建的物料视图ApiResponse
     */
    @PostMapping("/parts")
    public ApiResponse<PartView> createPart(@Valid @RequestBody PartRequest request) {
        return ApiResponse.ok(catalogService.createPart(request));
    }

    /**
     * 查询所有库位列表
     * @return 库位视图列表ApiResponse
     */
    @GetMapping("/locations")
    public ApiResponse<List<LocationView>> listLocations() {
        return ApiResponse.ok(catalogService.listLocations());
    }

    /**
     * 创建新库位
     * @param request 库位创建请求
     * @return 创建的库位视图ApiResponse
     */
    @PostMapping("/locations")
    public ApiResponse<LocationView> createLocation(@Valid @RequestBody LocationRequest request) {
        return ApiResponse.ok(catalogService.createLocation(request));
    }

    /**
     * 供应商创建请求记录
     * @param supplierCode 供应商编码
     * @param supplierName 供应商名称
     */
    public record SupplierRequest(@NotBlank String supplierCode, @NotBlank String supplierName) {
    }

    /**
     * 供应商视图记录
     * @param id 主键ID
     * @param supplierCode 供应商编码
     * @param supplierName 供应商名称
     */
    public record SupplierView(Long id, String supplierCode, String supplierName) {
    }

    /**
     * 客户创建请求记录
     * @param customerCode 客户编码
     * @param customerName 客户名称
     */
    public record CustomerRequest(@NotBlank String customerCode, @NotBlank String customerName) {
    }

    /**
     * 客户视图记录
     * @param id 主键ID
     * @param customerCode 客户编码
     * @param customerName 客户名称
     */
    public record CustomerView(Long id, String customerCode, String customerName) {
    }

    /**
     * 设备创建请求记录
     * @param equipmentCode 设备编码
     * @param equipmentName 设备名称
     * @param equipmentType 设备类型
     * @param equipmentModel 设备型号
     * @param capacity 承载容量，最小值0.001
     * @param warehouseName 所属仓库
     * @param zoneName 所属库区
     * @param status 设备状态
     */
    public record EquipmentRequest(@NotBlank String equipmentCode,
                                   @NotBlank String equipmentName,
                                   @NotBlank String equipmentType,
                                   @NotBlank String equipmentModel,
                                   @DecimalMin("0.001") BigDecimal capacity,
                                   String warehouseName,
                                   String zoneName,
                                   @NotBlank String status) {
    }

    /**
     * 设备视图记录
     * @param id 主键ID
     * @param equipmentCode 设备编码
     * @param equipmentName 设备名称
     * @param equipmentType 设备类型
     * @param equipmentModel 设备型号
     * @param capacity 承载容量
     * @param warehouseName 所属仓库
     * @param zoneName 所属库区
     * @param status 设备状态
     */
    public record EquipmentView(Long id,
                                String equipmentCode,
                                String equipmentName,
                                String equipmentType,
                                String equipmentModel,
                                BigDecimal capacity,
                                String warehouseName,
                                String zoneName,
                                String status) {
    }

    /**
     * 物料创建请求记录
     * @param partCode 物料编码
     * @param partName 物料名称
     * @param unit 计量单位
     */
    public record PartRequest(@NotBlank String partCode, @NotBlank String partName, @NotBlank String unit) {
    }

    /**
     * 物料视图记录
     * @param id 主键ID
     * @param partCode 物料编码
     * @param partName 物料名称
     * @param unit 计量单位
     */
    public record PartView(Long id, String partCode, String partName, String unit) {
    }

    /**
     * 库位创建请求记录
     * @param locationCode 库位编码
     * @param locationName 库位名称
     * @param warehouseName 所属仓库
     * @param zoneName 所属库区
     * @param warehouseType 仓库性质：OWN-自己仓库，THIRD_PARTY-第三方仓库
     */
    public record LocationRequest(@NotBlank String locationCode,
                                  @NotBlank String locationName,
                                  @NotBlank String warehouseName,
                                  @NotBlank String zoneName,
                                  String warehouseType) {
    }

    /**
     * 库位视图记录
     * @param id 主键ID
     * @param locationCode 库位编码
     * @param locationName 库位名称
     * @param warehouseName 所属仓库
     * @param zoneName 所属库区
     * @param warehouseType 仓库性质
     */
    public record LocationView(Long id,
                               String locationCode,
                               String locationName,
                               String warehouseName,
                               String zoneName,
                               String warehouseType) {
    }
}
