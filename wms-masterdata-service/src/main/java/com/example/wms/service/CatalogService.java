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

/**
 * 基础数据服务类
 * 负责管理系统中的基础主数据，包括供应商、客户、设备、物料、库位的增删改查操作
 */
@Service
public class CatalogService {

    // 注入各基础数据的仓库接口
    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
    private final EquipmentRepository equipmentRepository;
    private final PartRepository partRepository;
    private final LocationRepository locationRepository;

    /**
     * 构造函数，通过依赖注入初始化所有仓库接口
     * @param supplierRepository 供应商仓库接口
     * @param customerRepository 客户仓库接口
     * @param equipmentRepository 设备仓库接口
     * @param partRepository 物料仓库接口
     * @param locationRepository 库位仓库接口
     */
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

    /**
     * 创建供应商
     * @param request 供应商创建请求，包含供应商编码和名称
     * @return 创建后的供应商视图对象
     * @throws BusinessException 如果供应商编码已存在则抛出业务异常
     */
    public SupplierView createSupplier(SupplierRequest request) {
        supplierRepository.findBySupplierCode(request.supplierCode()).ifPresent(existing -> {
            throw new BusinessException("Supplier code already exists");
        });
        Supplier supplier = new Supplier();
        supplier.setSupplierCode(request.supplierCode());
        supplier.setSupplierName(request.supplierName());
        return toView(supplierRepository.save(supplier));
    }

    /**
     * 查询所有供应商列表
     * @return 供应商视图对象列表
     */
    public List<SupplierView> listSuppliers() {
        return supplierRepository.findAll().stream().map(this::toView).toList();
    }

    /**
     * 创建客户
     * @param request 客户创建请求，包含客户编码和名称
     * @return 创建后的客户视图对象
     * @throws BusinessException 如果客户编码已存在则抛出业务异常
     */
    public CustomerView createCustomer(CustomerRequest request) {
        customerRepository.findByCustomerCode(request.customerCode()).ifPresent(existing -> {
            throw new BusinessException("Customer code already exists");
        });
        Customer customer = new Customer();
        customer.setCustomerCode(request.customerCode());
        customer.setCustomerName(request.customerName());
        return toView(customerRepository.save(customer));
    }

    /**
     * 查询所有客户列表
     * @return 客户视图对象列表
     */
    public List<CustomerView> listCustomers() {
        return customerRepository.findAll().stream().map(this::toView).toList();
    }

    /**
     * 创建设备
     * @param request 设备创建请求，包含设备编码、名称、类型、型号等信息
     * @return 创建后的设备视图对象
     * @throws BusinessException 如果设备编码已存在则抛出业务异常
     */
    public EquipmentView createEquipment(EquipmentRequest request) {
        equipmentRepository.findByEquipmentCode(request.equipmentCode()).ifPresent(existing -> {
            throw new BusinessException("Equipment code already exists");
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

    /**
     * 查询所有设备列表
     * @return 设备视图对象列表
     */
    public List<EquipmentView> listEquipment() {
        return equipmentRepository.findAll().stream().map(this::toView).toList();
    }

    /**
     * 创建物料
     * @param request 物料创建请求，包含物料编码、名称、单位
     * @return 创建后的物料视图对象
     * @throws BusinessException 如果物料编码已存在则抛出业务异常
     */
    public PartView createPart(PartRequest request) {
        partRepository.findByPartCode(request.partCode()).ifPresent(existing -> {
            throw new BusinessException("Part code already exists");
        });
        Part part = new Part();
        part.setPartCode(request.partCode());
        part.setPartName(request.partName());
        part.setUnit(request.unit());
        return toView(partRepository.save(part));
    }

    /**
     * 查询所有物料列表
     * @return 物料视图对象列表
     */
    public List<PartView> listParts() {
        return partRepository.findAll().stream().map(this::toView).toList();
    }

    /**
     * 创建库位
     * @param request 库位创建请求，包含库位编码、名称、所属仓库和库区
     * @return 创建后的库位视图对象
     * @throws BusinessException 如果库位编码已存在则抛出业务异常
     */
    public LocationView createLocation(LocationRequest request) {
        locationRepository.findByLocationCode(request.locationCode()).ifPresent(existing -> {
            throw new BusinessException("Location code already exists");
        });
        Location location = new Location();
        location.setLocationCode(request.locationCode());
        location.setLocationName(request.locationName());
        location.setWarehouseName(request.warehouseName());
        location.setZoneName(request.zoneName());
        location.setWarehouseType(normalizeWarehouseType(request.warehouseType()));
        return toView(locationRepository.save(location));
    }

    /**
     * 查询所有库位列表
     * @return 库位视图对象列表
     */
    public List<LocationView> listLocations() {
        return locationRepository.findAll().stream().map(this::toView).toList();
    }

    /**
     * 将供应商实体转换为视图对象
     * @param supplier 供应商实体
     * @return 供应商视图对象
     */
    public SupplierView toView(Supplier supplier) {
        return new SupplierView(supplier.getId(), supplier.getSupplierCode(), supplier.getSupplierName());
    }

    /**
     * 将客户实体转换为视图对象
     * @param customer 客户实体
     * @return 客户视图对象
     */
    public CustomerView toView(Customer customer) {
        return new CustomerView(customer.getId(), customer.getCustomerCode(), customer.getCustomerName());
    }

    /**
     * 将设备实体转换为视图对象
     * @param equipment 设备实体
     * @return 设备视图对象
     */
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

    /**
     * 将物料实体转换为视图对象
     * @param part 物料实体
     * @return 物料视图对象
     */
    public PartView toView(Part part) {
        return new PartView(part.getId(), part.getPartCode(), part.getPartName(), part.getUnit());
    }

    /**
     * 将库位实体转换为视图对象
     * @param location 库位实体
     * @return 库位视图对象
     */
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
            throw new BusinessException("Warehouse type must be OWN or THIRD_PARTY");
        }
        return normalized;
    }
}
