/**
 * 本文件集中持有 WMS 业务服务共享的数据访问依赖。
 */
package com.example.wms.service;

import com.example.wms.repo.CustomerRepository;
import com.example.wms.repo.EquipmentRepository;
import com.example.wms.repo.InboundOrderItemRepository;
import com.example.wms.repo.InboundOrderRepository;
import com.example.wms.repo.InventoryOperationOrderRepository;
import com.example.wms.repo.InventoryRepository;
import com.example.wms.repo.InventoryTransactionRepository;
import com.example.wms.repo.KanbanRepository;
import com.example.wms.repo.LocationRepository;
import com.example.wms.repo.OutboundAllocationRepository;
import com.example.wms.repo.OutboundOrderItemRepository;
import com.example.wms.repo.OutboundOrderRepository;
import com.example.wms.repo.PartRepository;
import com.example.wms.repo.SupplierRepository;
import org.springframework.stereotype.Component;


@Component
public class WmsServiceDependencies {
    final SupplierRepository supplierRepository;
    final CustomerRepository customerRepository;
    final EquipmentRepository equipmentRepository;
    final PartRepository partRepository;
    final LocationRepository locationRepository;
    final InboundOrderRepository inboundOrderRepository;
    final InboundOrderItemRepository inboundOrderItemRepository;
    final OutboundOrderRepository outboundOrderRepository;
    final OutboundOrderItemRepository outboundOrderItemRepository;
    final OutboundAllocationRepository outboundAllocationRepository;
    final KanbanRepository kanbanRepository;
    final InventoryRepository inventoryRepository;
    final InventoryTransactionRepository inventoryTransactionRepository;
    final InventoryOperationOrderRepository inventoryOperationOrderRepository;

    public WmsServiceDependencies(SupplierRepository supplierRepository,
                                 CustomerRepository customerRepository,
                                 EquipmentRepository equipmentRepository,
                                 PartRepository partRepository,
                                 LocationRepository locationRepository,
                                 InboundOrderRepository inboundOrderRepository,
                                 InboundOrderItemRepository inboundOrderItemRepository,
                                 OutboundOrderRepository outboundOrderRepository,
                                 OutboundOrderItemRepository outboundOrderItemRepository,
                                 OutboundAllocationRepository outboundAllocationRepository,
                                 KanbanRepository kanbanRepository,
                                 InventoryRepository inventoryRepository,
                                 InventoryTransactionRepository inventoryTransactionRepository,
                                 InventoryOperationOrderRepository inventoryOperationOrderRepository) {
        this.supplierRepository = supplierRepository;
        this.customerRepository = customerRepository;
        this.equipmentRepository = equipmentRepository;
        this.partRepository = partRepository;
        this.locationRepository = locationRepository;
        this.inboundOrderRepository = inboundOrderRepository;
        this.inboundOrderItemRepository = inboundOrderItemRepository;
        this.outboundOrderRepository = outboundOrderRepository;
        this.outboundOrderItemRepository = outboundOrderItemRepository;
        this.outboundAllocationRepository = outboundAllocationRepository;
        this.kanbanRepository = kanbanRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.inventoryOperationOrderRepository = inventoryOperationOrderRepository;
    }
}
