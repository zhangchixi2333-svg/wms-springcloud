/**
 * 本文件实现 CatalogService 业务服务。
 */
package com.example.wms.service;

import com.example.wms.api.MasterDataController.CustomerRequest;
import com.example.wms.api.MasterDataController.CustomerView;
import com.example.wms.api.MasterDataController.EquipmentRequest;
import com.example.wms.api.MasterDataController.EquipmentView;
import com.example.wms.api.MasterDataController.LocationRequest;
import com.example.wms.api.MasterDataController.LocationView;
import com.example.wms.api.MasterDataController.PartRequest;
import com.example.wms.api.MasterDataController.PartView;
import com.example.wms.api.MasterDataController.SupplierRequest;
import com.example.wms.api.MasterDataController.SupplierView;
import com.example.wms.common.BusinessException;
import com.example.wms.domain.Customer;
import com.example.wms.domain.Equipment;
import com.example.wms.domain.Location;
import com.example.wms.domain.Part;
import com.example.wms.domain.Supplier;
import com.example.wms.repo.CustomerRepository;
import com.example.wms.repo.EquipmentRepository;
import com.example.wms.repo.LocationRepository;
import com.example.wms.repo.PartRepository;
import com.example.wms.repo.SupplierRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CatalogService {

    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
    private final EquipmentRepository equipmentRepository;
    private final PartRepository partRepository;
    private final LocationRepository locationRepository;
    private final JdbcTemplate jdbcTemplate;

    public CatalogService(SupplierRepository supplierRepository,
                          CustomerRepository customerRepository,
                          EquipmentRepository equipmentRepository,
                          PartRepository partRepository,
                          LocationRepository locationRepository,
                          JdbcTemplate jdbcTemplate) {
        this.supplierRepository = supplierRepository;
        this.customerRepository = customerRepository;
        this.equipmentRepository = equipmentRepository;
        this.partRepository = partRepository;
        this.locationRepository = locationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public SupplierView createSupplier(SupplierRequest request) {
        supplierRepository.findBySupplierCode(request.supplierCode()).ifPresent(existing -> {
            throw new BusinessException("供应商编码已存在");
        });
        Supplier supplier = new Supplier();
        supplier.setSupplierCode(request.supplierCode());
        supplier.setSupplierName(request.supplierName());
        return toView(supplierRepository.save(supplier));
    }

    public List<SupplierView> listSuppliers() {
        return supplierRepository.findAll().stream().map(this::toView).toList();
    }

    public void deleteSupplier(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new BusinessException("供应商不存在：" + id));
        boolean usedByPart = partRepository.findAll().stream()
                .anyMatch(part -> id.equals(part.getSupplierId()));
        boolean usedByInboundOrder = countById("SELECT COUNT(*) FROM inbound_order WHERE supplier_id = ?", id) > 0;
        boolean usedByKanban = countById("SELECT COUNT(*) FROM kanban WHERE supplier_id = ?", id) > 0;
        if (usedByPart || usedByInboundOrder || usedByKanban) {
            throw new BusinessException("该供应商已被零件绑定，不能删除");
        }
        supplierRepository.delete(supplier);
    }

    public CustomerView createCustomer(CustomerRequest request) {
        customerRepository.findByCustomerCode(request.customerCode()).ifPresent(existing -> {
            throw new BusinessException("客户编码已存在");
        });
        Customer customer = new Customer();
        customer.setCustomerCode(request.customerCode());
        customer.setCustomerName(request.customerName());
        return toView(customerRepository.save(customer));
    }

    public List<CustomerView> listCustomers() {
        return customerRepository.findAll().stream().map(this::toView).toList();
    }

    public void deleteCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new BusinessException("客户不存在：" + id));
        boolean usedByOutboundOrder = countById("SELECT COUNT(*) FROM outbound_order WHERE customer_id = ?", id) > 0;
        if (usedByOutboundOrder) {
            throw new BusinessException("该客户已被出库单引用，不能删除");
        }
        customerRepository.delete(customer);
    }

    public EquipmentView createEquipment(EquipmentRequest request) {
        equipmentRepository.findByEquipmentCode(request.equipmentCode()).ifPresent(existing -> {
            throw new BusinessException("器具编码已存在");
        });
        Equipment equipment = new Equipment();
        equipment.setEquipmentCode(request.equipmentCode());
        equipment.setEquipmentName(request.equipmentName());
        equipment.setEquipmentType(request.equipmentType());
        equipment.setEquipmentModel(request.equipmentModel());
        equipment.setCapacity(request.capacity());
        equipment.setWarehouseName(request.warehouseName());
        equipment.setZoneName(request.zoneName());
        equipment.setStatus(request.status());
        return toView(equipmentRepository.save(equipment));
    }

    public List<EquipmentView> listEquipment() {
        return equipmentRepository.findAll().stream().map(this::toView).toList();
    }

    public PartView createPart(PartRequest request) {
        partRepository.findByPartCode(request.partCode()).ifPresent(existing -> {
            throw new BusinessException("零件编码已存在");
        });
        if (request.supplierId() != null) {
            supplierRepository.findById(request.supplierId())
                    .orElseThrow(() -> new BusinessException("供应商不存在"));
        }
        if (request.defaultEquipmentCode() != null && !request.defaultEquipmentCode().isBlank()) {
            equipmentRepository.findByEquipmentCode(request.defaultEquipmentCode())
                    .orElseThrow(() -> new BusinessException("默认器具不存在"));
        }
        String categoryCode = resolveCategoryCode(request);
        Part part = new Part();
        part.setPartCode(request.partCode());
        part.setPartName(request.partName());
        part.setUnit(request.unit());
        part.setCategoryCode(categoryCode);
        part.setSupplierId(request.supplierId());
        part.setDefaultEquipmentCode(blankToNull(request.defaultEquipmentCode()));
        part.setDefaultPackageCapacity(request.defaultUnitPerBox());
        return toView(partRepository.save(part));
    }

    public List<PartView> listParts() {
        return partRepository.findAll().stream().map(this::toView).toList();
    }

    public void deletePart(Long id) {
        Part part = partRepository.findById(id)
                .orElseThrow(() -> new BusinessException("零件不存在：" + id));
        partRepository.delete(part);
    }

    public LocationView createLocation(LocationRequest request) {
        locationRepository.findByLocationCode(request.locationCode()).ifPresent(existing -> {
            throw new BusinessException("库位编码已存在");
        });
        Location location = new Location();
        location.setLocationCode(request.locationCode());
        location.setLocationName(request.locationName());
        location.setWarehouseName(request.warehouseName());
        location.setZoneName(request.zoneName());
        location.setWarehouseType(normalizeWarehouseType(request.warehouseType()));
        return toView(locationRepository.save(location));
    }

    public List<LocationView> listLocations() {
        return locationRepository.findAll().stream().map(this::toView).toList();
    }

    public void deleteLocation(Long id) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("仓库库区不存在：" + id));
        boolean usedByEquipment = equipmentRepository.findAll().stream()
                .anyMatch(equipment -> sameText(location.getWarehouseName(), equipment.getWarehouseName())
                        && sameText(location.getZoneName(), equipment.getZoneName()));
        boolean usedByInventory = countById("SELECT COUNT(*) FROM inventory WHERE location_id = ?", id) > 0;
        boolean usedByKanban = countById("SELECT COUNT(*) FROM kanban WHERE location_id = ?", id) > 0;
        if (usedByEquipment || usedByInventory || usedByKanban) {
            throw new BusinessException("该仓库库区已被器具、库存或看板引用，不能删除");
        }
        locationRepository.delete(location);
    }

    public SupplierView toView(Supplier supplier) {
        return new SupplierView(supplier.getId(), supplier.getSupplierCode(), supplier.getSupplierName());
    }

    public CustomerView toView(Customer customer) {
        return new CustomerView(customer.getId(), customer.getCustomerCode(), customer.getCustomerName());
    }

    public EquipmentView toView(Equipment equipment) {
        return new EquipmentView(
                equipment.getId(),
                equipment.getEquipmentCode(),
                equipment.getEquipmentName(),
                equipment.getEquipmentType(),
                equipment.getEquipmentModel(),
                equipment.getCapacity(),
                equipment.getWarehouseName(),
                equipment.getZoneName(),
                equipment.getStatus()
        );
    }

    public PartView toView(Part part) {
        return new PartView(
                part.getId(),
                part.getPartCode(),
                part.getPartName(),
                part.getUnit(),
                blankToNull(part.getCategoryCode()),
                part.getSupplierId(),
                blankToNull(part.getDefaultEquipmentCode()),
                part.getDefaultPackageCapacity()
        );
    }

    public LocationView toView(Location location) {
        return new LocationView(
                location.getId(),
                location.getLocationCode(),
                location.getLocationName(),
                location.getWarehouseName(),
                location.getZoneName(),
                normalizeWarehouseType(location.getWarehouseType())
        );
    }

    private String normalizeWarehouseType(String value) {
        if (value == null || value.isBlank()) {
            return "OWN";
        }
        String normalized = value.trim().toUpperCase();
        if (!List.of("OWN", "THIRD_PARTY").contains(normalized)) {
            throw new BusinessException("仓库性质只能为 OWN 或 THIRD_PARTY");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String resolveCategoryCode(PartRequest request) {
        String requested = blankToNull(request.categoryCode());
        String normalized = requested == null ? "DEFAULT" : requested.toUpperCase();
        if (isOutsourcedDefault(request) && (requested == null || "DEFAULT".equals(normalized))) {
            normalized = "OUTSOURCED";
        }
        assertCategoryEnabled(normalized);
        return normalized;
    }

    private boolean isOutsourcedDefault(PartRequest request) {
        String equipmentCode = blankToNull(request.defaultEquipmentCode());
        if (equipmentCode != null && isThirdPartyEquipment(equipmentCode)) {
            return true;
        }
        if (request.supplierId() == null) {
            return false;
        }
        return supplierRepository.findById(request.supplierId())
                .map(Supplier::getSupplierName)
                .map(name -> name.contains("外协") || name.contains("委外") || name.contains("第三方"))
                .orElse(false);
    }

    private boolean isThirdPartyEquipment(String equipmentCode) {
        return equipmentRepository.findByEquipmentCode(equipmentCode)
                .map(equipment -> locationRepository.findAll().stream()
                        .anyMatch(location -> sameText(location.getWarehouseName(), equipment.getWarehouseName())
                                && sameText(location.getZoneName(), equipment.getZoneName())
                                && "THIRD_PARTY".equals(normalizeWarehouseType(location.getWarehouseType()))))
                .orElse(false);
    }

    private void assertCategoryEnabled(String categoryCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM config_item WHERE module_key = 'categoryManagement' AND item_code = ? AND status <> 'DISABLED'",
                Integer.class,
                categoryCode
        );
        if (count == null || count == 0) {
            throw new BusinessException("零件分类不存在或已禁用：" + categoryCode);
        }
    }

    private boolean sameText(String left, String right) {
        return blankToNull(left) != null && blankToNull(left).equals(blankToNull(right));
    }

    private long countById(String sql, Long id) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, id);
        return count == null ? 0L : count;
    }
}
