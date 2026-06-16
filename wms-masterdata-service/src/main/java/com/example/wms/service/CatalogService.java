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
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CatalogService {

    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
    private final EquipmentRepository equipmentRepository;
    private final PartRepository partRepository;
    private final LocationRepository locationRepository;

    public CatalogService(SupplierRepository supplierRepository,
                          CustomerRepository customerRepository,
                          EquipmentRepository equipmentRepository,
                          PartRepository partRepository,
                          LocationRepository locationRepository) {
        this.supplierRepository = supplierRepository;
        this.customerRepository = customerRepository;
        this.equipmentRepository = equipmentRepository;
        this.partRepository = partRepository;
        this.locationRepository = locationRepository;
    }

    public SupplierView createSupplier(SupplierRequest request) {
        supplierRepository.findBySupplierCode(request.supplierCode()).ifPresent(existing -> {
            throw new BusinessException("????????");
        });
        Supplier supplier = new Supplier();
        supplier.setSupplierCode(request.supplierCode());
        supplier.setSupplierName(request.supplierName());
        return toView(supplierRepository.save(supplier));
    }

    public List<SupplierView> listSuppliers() {
        return supplierRepository.findAll().stream().map(this::toView).toList();
    }

    public CustomerView createCustomer(CustomerRequest request) {
        customerRepository.findByCustomerCode(request.customerCode()).ifPresent(existing -> {
            throw new BusinessException("???????");
        });
        Customer customer = new Customer();
        customer.setCustomerCode(request.customerCode());
        customer.setCustomerName(request.customerName());
        return toView(customerRepository.save(customer));
    }

    public List<CustomerView> listCustomers() {
        return customerRepository.findAll().stream().map(this::toView).toList();
    }

    public EquipmentView createEquipment(EquipmentRequest request) {
        equipmentRepository.findByEquipmentCode(request.equipmentCode()).ifPresent(existing -> {
            throw new BusinessException("???????");
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
            throw new BusinessException("???????");
        });
        if (request.supplierId() != null) {
            supplierRepository.findById(request.supplierId())
                    .orElseThrow(() -> new BusinessException("??????"));
        }
        if (request.defaultEquipmentCode() != null && !request.defaultEquipmentCode().isBlank()) {
            equipmentRepository.findByEquipmentCode(request.defaultEquipmentCode())
                    .orElseThrow(() -> new BusinessException("?????????"));
        }
        Part part = new Part();
        part.setPartCode(request.partCode());
        part.setPartName(request.partName());
        part.setUnit(request.unit());
        part.setSupplierId(request.supplierId());
        part.setDefaultEquipmentCode(blankToNull(request.defaultEquipmentCode()));
        part.setDefaultPackageCapacity(request.defaultUnitPerBox());
        return toView(partRepository.save(part));
    }

    public List<PartView> listParts() {
        return partRepository.findAll().stream().map(this::toView).toList();
    }

    public LocationView createLocation(LocationRequest request) {
        locationRepository.findByLocationCode(request.locationCode()).ifPresent(existing -> {
            throw new BusinessException("???????");
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
            throw new BusinessException("??????? OWN ? THIRD_PARTY");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
