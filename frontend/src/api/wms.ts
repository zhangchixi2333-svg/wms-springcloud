/**
 * WMS系统API接口定义文件
 * 封装了所有与后端交互的API调用方法，统一管理系统所有后端接口
 */

// 导入通用请求函数
import { request } from './client'
// 导入所有数据类型定义
import type {
  CurrentUser,
  AuthSession,
  ConfigItem,
  Customer,
  Equipment,
  FlatMenu,
  InboundDraftItem,
  InboundOrder,
  InventoryRow,
  Kanban,
  Location,
  MenuNode,
  OutboundDraftItem,
  OutboundOrder,
  Part,
  SystemRole,
  SystemUser,
  Supplier,
  TransactionRow,
} from '../types/app'

/**
 * 构建URL查询参数
 * 将参数对象转换为URLSearchParams格式，过滤空值和无效值
 * @param params 参数对象，键值对形式
 * @returns 格式化后的查询字符串，以?开头，如果没有有效参数则返回空字符串
 */
function buildQuery(params: Record<string, string | number | undefined | null>) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    // 只添加非空、非0、非空字符串的参数
    if (value !== undefined && value !== null && value !== '' && value !== 0) {
      search.set(key, String(value))
    }
  })
  const query = search.toString()
  return query ? `?${query}` : ''
}

/**
 * API接口集合对象，包含所有系统功能的API调用方法
 */
export const api = {
  /**
   * 获取当前登录用户信息
   * @returns Promise<CurrentUser> 当前用户信息
   */
  currentUser: () => request<CurrentUser>('/auth/me'),

  login: (payload: { username: string; password: string }) =>
    request<AuthSession>('/auth/login', { method: 'POST', body: JSON.stringify(payload) }),

  register: (payload: { username: string; password: string; displayName: string; roleName?: string }) =>
    request<AuthSession>('/auth/register', { method: 'POST', body: JSON.stringify(payload) }),
  
  /**
   * 用户登出
   * @returns Promise<void>
   */
  logout: () => request<void>('/auth/logout', { method: 'POST' }),
  
  /**
   * 获取菜单树结构
   * @returns Promise<MenuNode[]> 菜单树节点数组
   */
  menuTree: () => request<MenuNode[]>('/menus/tree'),
  
  /**
   * 获取扁平化的菜单列表
   * @returns Promise<FlatMenu[]> 扁平化菜单数组
   */
  listMenus: () => request<FlatMenu[]>('/menus'),
  
  /**
   * 创建新菜单
   * @param payload 菜单数据对象
   * @returns Promise<FlatMenu> 创建后的菜单对象
   */
  createMenu: (payload: Partial<FlatMenu> & Record<string, unknown>) =>
    request<FlatMenu>('/menus', { method: 'POST', body: JSON.stringify(payload) }),
  
  /**
   * 更新菜单信息
   * @param id 菜单ID
   * @param payload 更新的菜单数据
   * @returns Promise<FlatMenu> 更新后的菜单对象
   */
  updateMenu: (id: number, payload: Partial<FlatMenu> & Record<string, unknown>) =>
    request<FlatMenu>(`/menus/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  
  /**
   * 删除菜单
   * @param id 菜单ID
   * @returns Promise<void>
   */
  deleteMenu: (id: number) => request<void>(`/menus/${id}`, { method: 'DELETE' }),

  listUsers: () => request<SystemUser[]>('/users'),

  createUser: (payload: { username: string; password?: string; displayName: string; roleName: string; avatarColor?: string }) =>
    request<SystemUser>('/users', { method: 'POST', body: JSON.stringify(payload) }),

  updateUser: (id: number, payload: { username: string; password?: string; displayName: string; roleName: string; avatarColor?: string }) =>
    request<SystemUser>(`/users/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),

  deleteUser: (id: number) => request<void>(`/users/${id}`, { method: 'DELETE' }),

  listRoles: () => request<SystemRole[]>('/roles'),

  createRole: (payload: { roleCode: string; roleName: string; permissionLevel: string; description?: string; enabled: boolean; menuIds: number[] }) =>
    request<SystemRole>('/roles', { method: 'POST', body: JSON.stringify(payload) }),

  updateRole: (id: number, payload: { roleCode: string; roleName: string; permissionLevel: string; description?: string; enabled: boolean; menuIds: number[] }) =>
    request<SystemRole>(`/roles/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),

  deleteRole: (id: number) => request<void>(`/roles/${id}`, { method: 'DELETE' }),

  assignRoleMenus: (roleCode: string, menuIds: number[]) =>
    request<SystemRole>(`/roles/${roleCode}/menus`, { method: 'PUT', body: JSON.stringify({ menuIds }) }),
  
  /**
   * 获取供应商列表
   * @returns Promise<Supplier[]> 供应商数组
   */
  listSuppliers: () => request<Supplier[]>('/suppliers'),
  
  /**
   * 创建供应商
   * @param payload 供应商数据，包含编码和名称
   * @returns Promise<Supplier> 创建后的供应商对象
   */
  createSupplier: (payload: { supplierCode: string; supplierName: string }) =>
    request<Supplier>('/suppliers', { method: 'POST', body: JSON.stringify(payload) }),
  
  /**
   * 获取客户列表
   * @returns Promise<Customer[]> 客户数组
   */
  listCustomers: () => request<Customer[]>('/customers'),
  
  /**
   * 创建客户
   * @param payload 客户数据，包含编码和名称
   * @returns Promise<Customer> 创建后的客户对象
   */
  createCustomer: (payload: { customerCode: string; customerName: string }) =>
    request<Customer>('/customers', { method: 'POST', body: JSON.stringify(payload) }),
  
  /**
   * 获取物料列表
   * @returns Promise<Part[]> 物料数组
   */
  listParts: () => request<Part[]>('/parts'),
  
  /**
   * 创建物料
   * @param payload 物料数据，包含编码、名称和单位
   * @returns Promise<Part> 创建后的物料对象
   */
  createPart: (payload: { partCode: string; partName: string; unit: string }) =>
    request<Part>('/parts', { method: 'POST', body: JSON.stringify(payload) }),
  
  /**
   * 获取设备列表
   * @returns Promise<Equipment[]> 设备数组
   */
  listEquipment: () => request<Equipment[]>('/equipment'),
  
  /**
   * 创建设备
   * @param payload 设备完整数据对象
   * @returns Promise<Equipment> 创建后的设备对象
   */
  createEquipment: (payload: {
    equipmentCode: string
    equipmentName: string
    equipmentType: string
    equipmentModel: string
    capacity: number
    warehouseName: string
    zoneName: string
    status: string
  }) => request<Equipment>('/equipment', { method: 'POST', body: JSON.stringify(payload) }),
  
  /**
   * 获取库位列表
   * @returns Promise<Location[]> 库位数组
   */
  listLocations: () => request<Location[]>('/locations'),
  
  /**
   * 创建库位
   * @param payload 库位数据，包含编码、名称、仓库和区域
   * @returns Promise<Location> 创建后的库位对象
   */
  createLocation: (payload: {
    locationCode: string
    locationName: string
    warehouseName: string
    zoneName: string
    warehouseType: 'OWN' | 'THIRD_PARTY'
  }) => request<Location>('/locations', { method: 'POST', body: JSON.stringify(payload) }),
  
  /**
   * 获取入库单列表
   * @param filters 过滤条件，支持状态、供应商ID、入库单号
   * @returns Promise<InboundOrder[]> 入库单数组
   */
  listInboundOrders: (filters?: { status?: string; supplierId?: number; inboundNo?: string }) =>
    request<InboundOrder[]>(`/inbound-orders${buildQuery(filters ?? {})}`),
  
  /**
   * 创建入库单
   * @param payload 入库单数据，包含供应商ID和入库明细项
   * @returns Promise<InboundOrder> 创建后的入库单对象
   */
  createInboundOrder: (payload: { supplierId: number; items: InboundDraftItem[] }) =>
    request<InboundOrder>('/inbound-orders', { method: 'POST', body: JSON.stringify(payload) }),
  
  /**
   * 为入库单生成看板
   * @param orderId 入库单ID
   * @returns Promise<Kanban[]> 生成的看板数组
   */
  generateKanbans: (orderId: number) =>
    request<Kanban[]>(`/inbound-orders/${orderId}/kanbans`, { method: 'POST' }),
  
  /**
   * 获取看板列表
   * @param filters 过滤条件，支持状态、入库单号、出库单号、看板编号、供应商ID、物料编码
   * @returns Promise<Kanban[]> 看板数组
   */
  listKanbans: (filters?: {
    status?: string
    inboundNo?: string
    outboundNo?: string
    kanbanNo?: string
    supplierId?: number
    partCode?: string
  }) => request<Kanban[]>(`/kanbans${buildQuery(filters ?? {})}`),
  
  /**
   * 获取出库单列表
   * @param filters 过滤条件，支持状态、客户ID、出库单号
   * @returns Promise<OutboundOrder[]> 出库单数组
   */
  listOutboundOrders: (filters?: { status?: string; customerId?: number; outboundNo?: string }) =>
    request<OutboundOrder[]>(`/outbound-orders${buildQuery(filters ?? {})}`),
  
  /**
   * 创建出库单
   * @param payload 出库单数据，包含客户ID和出库明细项
   * @returns Promise<OutboundOrder> 创建后的出库单对象
   */
  createOutboundOrder: (payload: { customerId: number | null; inboundOrderNos: string[]; items: OutboundDraftItem[] }) =>
    request<OutboundOrder>('/outbound-orders', { method: 'POST', body: JSON.stringify(payload) }),
  
  /**
   * 获取库存列表
   * @param filters 过滤条件，支持仓库名称、区域名称、物料关键词、供应商ID
   * @returns Promise<InventoryRow[]> 库存行项目数组
   */
  listInventory: (filters?: {
    warehouseName?: string
    zoneName?: string
    materialKeyword?: string
    supplierId?: number
  }) => request<InventoryRow[]>(`/inventory${buildQuery(filters ?? {})}`),
  
  /**
   * 手动录入库存
   * @param payload 库存录入数据，包含物料ID、库位ID、数量和备注
   * @returns Promise<InventoryRow> 创建后的库存行项目
   */
  manualInventoryEntry: (payload: { partId: number; locationId: number; qty: number; remark: string }) =>
    request<InventoryRow>('/inventory/manual-entries', { method: 'POST', body: JSON.stringify(payload) }),
  
  /**
   * 获取库存交易记录列表
   * @returns Promise<TransactionRow[]> 交易记录数组
   */
  listTransactions: () => request<TransactionRow[]>('/inventory/transactions'),
  
  /**
   * 入库扫码处理
   * @param payload 扫码数据，包含条码和库位编码
   * @returns Promise<unknown> 处理结果
   */
  scanInbound: (payload: { barcode: string; locationCode: string }) =>
    request('/mobile/scan/inbound', { method: 'POST', body: JSON.stringify(payload) }),
  
  /**
   * 出库扫码处理
   * @param payload 扫码数据，包含条码和可选的出库单号
   * @returns Promise<unknown> 处理结果
   */
  scanOutbound: (payload: { barcode: string; outboundOrderNo: string }) =>
    request('/mobile/scan/outbound', { method: 'POST', body: JSON.stringify(payload) }),

  transferKanban: (payload: { barcode: string; inboundOrderNo: string; locationCode: string; remark?: string }) =>
    request<Kanban>('/inventory/kanbans/transfer', { method: 'POST', body: JSON.stringify(payload) }),

  freezeKanban: (payload: { barcode: string; frozen: boolean; remark?: string }) =>
    request<Kanban>('/inventory/kanbans/freeze', { method: 'POST', body: JSON.stringify(payload) }),

  repackOutbound: (payload: { barcode: string; locationCode: string; remark?: string }) =>
    request<Kanban>('/inventory/kanbans/repack-outbound', { method: 'POST', body: JSON.stringify(payload) }),

  repackInbound: (payload: { barcode: string; locationCode: string; qty: number; remark?: string }) =>
    request<Kanban>('/inventory/kanbans/repack-inbound', { method: 'POST', body: JSON.stringify(payload) }),

  adjustKanbanBalance: (payload: { barcode: string; qty: number; remark?: string }) =>
    request<Kanban>('/inventory/kanbans/balance', { method: 'POST', body: JSON.stringify(payload) }),

  listConfigItems: (moduleKey: string) =>
    request<ConfigItem[]>(`/config-items${buildQuery({ moduleKey })}`),

  createConfigItem: (payload: {
    moduleKey: string
    itemCode: string
    itemName: string
    status: string
    remark: string
  }) => request<ConfigItem>('/config-items', { method: 'POST', body: JSON.stringify(payload) }),

  deleteConfigItem: (id: number) => request<void>(`/config-items/${id}`, { method: 'DELETE' }),
}
