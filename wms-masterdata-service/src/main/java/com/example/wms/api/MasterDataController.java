/**
 * 本文件实现 MasterDataController 控制器。
 */
package com.example.wms.api;

import com.example.wms.common.ApiResponse;
import com.example.wms.service.CatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class MasterDataController {

    private final CatalogService catalogService;

    public MasterDataController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/suppliers")
    public ApiResponse<List<SupplierView>> listSuppliers() {
        return ApiResponse.ok(catalogService.listSuppliers());
    }

    @PostMapping("/suppliers")
    public ApiResponse<SupplierView> createSupplier(@Valid @RequestBody SupplierRequest request) {
        return ApiResponse.ok(catalogService.createSupplier(request));
    }

    @DeleteMapping("/suppliers/{id}")
    public ApiResponse<Void> deleteSupplier(@PathVariable Long id) {
        catalogService.deleteSupplier(id);
        return ApiResponse.okMessage("供应商已删除");
    }

    @GetMapping("/customers")
    public ApiResponse<List<CustomerView>> listCustomers() {
        return ApiResponse.ok(catalogService.listCustomers());
    }

    @PostMapping("/customers")
    public ApiResponse<CustomerView> createCustomer(@Valid @RequestBody CustomerRequest request) {
        return ApiResponse.ok(catalogService.createCustomer(request));
    }

    @DeleteMapping("/customers/{id}")
    public ApiResponse<Void> deleteCustomer(@PathVariable Long id) {
        catalogService.deleteCustomer(id);
        return ApiResponse.okMessage("客户已删除");
    }

    @GetMapping("/equipment")
    public ApiResponse<List<EquipmentView>> listEquipment() {
        return ApiResponse.ok(catalogService.listEquipment());
    }

    @PostMapping("/equipment")
    public ApiResponse<EquipmentView> createEquipment(@Valid @RequestBody EquipmentRequest request) {
        return ApiResponse.ok(catalogService.createEquipment(request));
    }

    @GetMapping("/parts")
    public ApiResponse<List<PartView>> listParts() {
        return ApiResponse.ok(catalogService.listParts());
    }

    @PostMapping("/parts")
    public ApiResponse<PartView> createPart(@Valid @RequestBody PartRequest request) {
        return ApiResponse.ok(catalogService.createPart(request));
    }

    @DeleteMapping("/parts/{id}")
    public ApiResponse<Void> deletePart(@PathVariable Long id) {
        catalogService.deletePart(id);
        return ApiResponse.okMessage("零件已删除");
    }

    @GetMapping("/locations")
    public ApiResponse<List<LocationView>> listLocations() {
        return ApiResponse.ok(catalogService.listLocations());
    }

    @PostMapping("/locations")
    public ApiResponse<LocationView> createLocation(@Valid @RequestBody LocationRequest request) {
        return ApiResponse.ok(catalogService.createLocation(request));
    }

    @DeleteMapping("/locations/{id}")
    public ApiResponse<Void> deleteLocation(@PathVariable Long id) {
        catalogService.deleteLocation(id);
        return ApiResponse.okMessage("仓库库区已删除");
    }

    public record SupplierRequest(@NotBlank String supplierCode, @NotBlank String supplierName) {
    }

    public record SupplierView(Long id, String supplierCode, String supplierName) {
    }

    public record CustomerRequest(@NotBlank String customerCode, @NotBlank String customerName) {
    }

    public record CustomerView(Long id, String customerCode, String customerName) {
    }

    public record EquipmentRequest(@NotBlank String equipmentCode,
                                   @NotBlank String equipmentName,
                                   @NotBlank String equipmentType,
                                   @NotBlank String equipmentModel,
                                   @DecimalMin("0.001") BigDecimal capacity,
                                   String warehouseName,
                                   String zoneName,
                                   @NotBlank String status) {
    }

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

    public record PartRequest(@NotBlank String partCode,
                              @NotBlank String partName,
                              @NotBlank String unit,
                              String categoryCode,
                              Long supplierId,
                              String defaultEquipmentCode,
                              @DecimalMin("0.001") BigDecimal defaultUnitPerBox) {
    }

    public record PartView(Long id,
                           String partCode,
                           String partName,
                           String unit,
                           String categoryCode,
                           Long supplierId,
                           String defaultEquipmentCode,
                           BigDecimal defaultUnitPerBox) {
    }

    public record LocationRequest(@NotBlank String locationCode,
                                  @NotBlank String locationName,
                                  @NotBlank String warehouseName,
                                  @NotBlank String zoneName,
                                  String warehouseType) {
    }

    public record LocationView(Long id,
                               String locationCode,
                               String locationName,
                               String warehouseName,
                               String zoneName,
                               String warehouseType) {
    }
}
