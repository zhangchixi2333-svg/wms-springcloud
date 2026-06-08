/**
 * 应用程序类型定义文件
 * 包含系统中所有数据模型的TypeScript类型定义，确保类型安全
 */

/**
 * 菜单树节点类型
 * 用于构建系统左侧导航菜单的树状结构
 */
export type MenuNode = {
  id: number;                    // 菜单唯一ID
  parentId: number | null;       // 父菜单ID，顶级菜单为null
  menuKey: string;               // 菜单唯一标识键
  menuName: string;              // 菜单显示名称
  menuType: 'PARENT' | 'LEAF';   // 菜单类型：PARENT-父菜单，LEAF-叶子节点
  pathKey: string | null;        // 路由路径
  pageKey: string | null;        // 页面标识键，用于注册和渲染页面
  iconKey: string | null;        // 菜单图标标识
  sortOrder: number;             // 排序顺序，数字越小越靠前
  children: MenuNode[];          // 子菜单数组
}

/**
 * 扁平化菜单类型
 * 用于菜单管理页面展示，去除树状结构
 */
export type FlatMenu = {
  id: number;                    // 菜单唯一ID
  parentId: number | null;       // 父菜单ID，顶级菜单为null
  menuKey: string;               // 菜单唯一标识键
  menuName: string;              // 菜单显示名称
  menuType: 'PARENT' | 'LEAF';   // 菜单类型：PARENT-父菜单，LEAF-叶子节点
  pathKey: string | null;        // 路由路径
  pageKey: string | null;        // 页面标识键
  iconKey: string | null;        // 菜单图标标识
  sortOrder: number;             // 排序顺序
  visible: boolean;              // 是否可见
}

/**
 * 当前登录用户信息
 */
export type CurrentUser = {
  id: number;                    // 用户ID
  username: string;              // 用户名
  displayName: string;           // 用户显示名称
  roleName: string;              // 用户角色名称
  avatarColor: string;           // 头像背景颜色
}

export type AuthSession = {
  token: string;
  user: CurrentUser;
}

export type SystemUser = {
  id: number;
  username: string;
  displayName: string;
  roleName: string;
  roleDisplayName: string;
  avatarColor: string;
}

export type SystemRole = {
  id: number;
  roleCode: string;
  roleName: string;
  permissionLevel: 'ADMIN' | 'MANAGER' | 'OPERATOR' | 'VIEWER';
  description: string;
  enabled: boolean;
  menuIds: number[];
}

/**
 * 供应商类型
 */
export type Supplier = { 
  id: number;                    // 供应商ID
  supplierCode: string;          // 供应商编码
  supplierName: string;          // 供应商名称
}

/**
 * 客户类型
 */
export type Customer = { 
  id: number;                    // 客户ID
  customerCode: string;          // 客户编码
  customerName: string;          // 客户名称
}

/**
 * 物料类型
 */
export type Part = { 
  id: number;                    // 物料ID
  partCode: string;              // 物料编码
  partName: string;              // 物料名称
  unit: string;                  // 物料单位
}

/**
 * 设备类型
 */
export type Equipment = {
  id: number;                    // 设备ID
  equipmentCode: string;         // 设备编码
  equipmentName: string;         // 设备名称
  equipmentType: string;         // 设备类型
  equipmentModel: string;        // 设备型号
  capacity: number;              // 设备容量
  warehouseName: string;         // 所属仓库名称
  zoneName: string;              // 所属区域名称
  status: string;                // 设备状态
}

/**
 * 库位类型
 */
export type Location = {
  id: number;                    // 库位ID
  locationCode: string;          // 库位编码
  locationName: string;          // 库位名称
  warehouseName: string;         // 所属仓库名称
  zoneName: string;              // 所属区域名称
  warehouseType: 'OWN' | 'THIRD_PARTY'; // 仓库性质
}

/**
 * 入库单行项目类型
 */
export type InboundOrderItem = {
  id: number;                    // 行项目ID
  partId: number;                // 物料ID
  partCode: string;              // 物料编码
  partName: string;              // 物料名称
  unit: string;                  // 物料单位
  plannedQty: number;            // 计划入库数量
  receivedQty: number;           // 实际收货数量
  boxCount: number;              // 箱数
  pendingRepack: boolean;        // 是否需要重新包装
  equipmentCode: string;         // 设备编码
  packageCapacity: number;       // 包装容量
  warehouseZone: string;         // 仓库区域
}

/**
 * 入库单类型
 */
export type InboundOrder = {
  id: number;                    // 入库单ID
  inboundNo: string;             // 入库单号
  supplierId: number;            // 供应商ID
  supplierName: string;          // 供应商名称
  status: string;                // 入库单状态
  createdAt: string;             // 创建时间
  items: InboundOrderItem[];     // 入库单行项目数组
}

/**
 * 出库单行项目类型
 */
export type OutboundOrderItem = {
  id: number;                    // 行项目ID
  partId: number;                // 物料ID
  partCode: string;              // 物料编码
  partName: string;              // 物料名称
  unit: string;                  // 物料单位
  plannedQty: number;            // 计划出库数量
  scannedQty: number;            // 实际扫码出库数量
  warehouseName: string;         // 仓库名称
  zoneName: string;              // 区域名称
}

/**
 * 出库单类型
 */
export type OutboundOrder = {
  id: number;                    // 出库单ID
  outboundNo: string;            // 出库单号
  customerId: number | null;     // 客户ID，可为null
  customerName: string;          // 客户名称
  inboundOrderNos: string[];     // 来源入库单号
  status: string;                // 出库单状态
  createdAt: string;             // 创建时间
  items: OutboundOrderItem[];    // 出库单行项目数组
}

/**
 * 看板/批次卡类型
 */
export type Kanban = {
  id: number;                    // 看板ID
  kanbanNo: string;              // 看板编号
  barcode: string;               // 条码内容
  qrContent: string;             // 二维码内容
  inboundNo: string;             // 关联的入库单号
  outboundNo: string;            // 关联的出库单号
  partCode: string;              // 物料编码
  partName: string;              // 物料名称
  unit: string;                  // 物料单位
  supplierId: number | null;     // 供应商ID，可为null
  supplierName: string;          // 供应商名称
  batchNo: string;               // 批次号
  qty: number;                   // 数量
  boxCount: number;              // 箱数
  pendingRepack: boolean;        // 是否需要重新包装
  equipmentCode: string;         // 设备编码
  equipmentModel: string;        // 设备型号
  packageCapacity: number;       // 包装容量
  warehouseName: string;         // 仓库名称
  zoneName: string;              // 区域名称
  status: string;                // 看板状态
  locationCode: string;          // 当前库位编码
  createdAt: string;             // 创建时间
  inboundTime: string | null;    // 入库时间，可为null
  outboundTime: string | null;   // 出库时间，可为null
}

/**
 * 库存行项目类型
 */
export type InventoryRow = {
  id: number;                    // 库存记录ID
  partId: number;                // 物料ID
  partCode: string;              // 物料编码
  partName: string;              // 物料名称
  supplierName: string;          // 供应商名称
  locationId: number;            // 库位ID
  locationCode: string;          // 库位编码
  warehouseName: string;         // 仓库名称
  zoneName: string;              // 区域名称
  qty: number;                   // 库存数量
  updatedAt: string;             // 更新时间
}

/**
 * 库存交易记录类型
 */
export type TransactionRow = {
  id: number;                    // 交易记录ID
  transactionNo: string;         // 交易单号
  businessType: string;          // 业务类型
  businessNo: string;            // 关联的业务单号
  barcode: string;               // 条码内容
  partCode: string;              // 物料编码
  locationCode: string;          // 库位编码
  qtyChange: number;             // 数量变化（正数增加，负数减少）
  remark: string;                // 备注
  createdAt: string;             // 交易时间
}

export type ConfigItem = {
  id: number;
  moduleKey: string;
  itemCode: string;
  itemName: string;
  status: string;
  remark: string;
  createdAt: string;
}

/**
 * 工作区标签页类型
 */
export type WorkspaceTab = {
  menuKey: string;               // 关联的菜单标识键
  title: string;                 // 标签页标题
  pageKey: string;               // 页面标识键
}

/**
 * 创建入库单时的草稿行项目类型
 */
export type InboundDraftItem = {
  partId: number;                // 物料ID
  plannedQty: number;            // 计划入库数量
  boxCount: number;              // 箱数
  pendingRepack: boolean;        // 是否需要重新包装
  equipmentCode: string;         // 设备编码
  packageCapacity: number;       // 包装容量
  warehouseZone: string;         // 仓库区域
}

/**
 * 创建出库单时的草稿行项目类型
 */
export type OutboundDraftItem = {
  partId: number;                // 物料ID
  plannedQty: number;            // 计划出库数量
  warehouseName: string;         // 仓库名称
  zoneName: string;              // 区域名称
}

/**
 * 应用程序全局状态
 */
export type AppState = {
  authenticated: boolean;                       // 是否已登录
  user: CurrentUser | null;                      // 当前登录用户，未登录时为null
  menuTree: MenuNode[];                         // 菜单树结构
  flatMenus: FlatMenu[];                        // 扁平化菜单数组
  users: SystemUser[];                          // 系统用户列表
  roles: SystemRole[];                          // 系统角色列表
  suppliers: Supplier[];                        // 供应商列表
  customers: Customer[];                        // 客户列表
  parts: Part[];                                // 物料列表
  equipment: Equipment[];                       // 设备列表
  locations: Location[];                        // 库位列表
  inboundOrders: InboundOrder[];                // 入库单列表
  outboundOrders: OutboundOrder[];              // 出库单列表
  kanbans: Kanban[];                            // 看板列表
  inventory: InventoryRow[];                    // 库存列表
  transactions: TransactionRow[];               // 交易记录列表
  loading: boolean;                             // 是否正在加载数据
  message: string;                              // 提示消息
  error: string;                                // 错误消息
}

/**
 * 应用程序操作方法集合
 */
export type AppActions = {
  login: (payload: { username: string; password: string }) => Promise<void>;
  register: (payload: { username: string; password: string; displayName: string; roleName?: string }) => Promise<void>;
  refreshAll: () => Promise<void>;                                          // 刷新所有数据
  refreshMenus: () => Promise<void>;                                       // 刷新菜单数据
  refreshSystemSecurity: () => Promise<void>;                              // 刷新用户角色数据
  createUser: (payload: { username: string; password?: string; displayName: string; roleName: string; avatarColor?: string }) => Promise<void>;
  updateUser: (id: number, payload: { username: string; password?: string; displayName: string; roleName: string; avatarColor?: string }) => Promise<void>;
  deleteUser: (id: number) => Promise<void>;
  createRole: (payload: { roleCode: string; roleName: string; permissionLevel: string; description?: string; enabled: boolean; menuIds: number[] }) => Promise<void>;
  updateRole: (id: number, payload: { roleCode: string; roleName: string; permissionLevel: string; description?: string; enabled: boolean; menuIds: number[] }) => Promise<void>;
  deleteRole: (id: number) => Promise<void>;
  assignRoleMenus: (roleCode: string, menuIds: number[]) => Promise<void>;
  createSupplier: (payload: { supplierCode: string; supplierName: string }) => Promise<void>;  // 创建供应商
  createCustomer: (payload: { customerCode: string; customerName: string }) => Promise<void>;  // 创建客户
  createPart: (payload: { partCode: string; partName: string; unit: string }) => Promise<void>;  // 创建物料
  createEquipment: (payload: {
    equipmentCode: string
    equipmentName: string
    equipmentType: string
    equipmentModel: string
    capacity: number
    warehouseName: string
    zoneName: string
    status: string
  }) => Promise<void>;                                                      // 创建设备
  createLocation: (payload: {
    locationCode: string
    locationName: string
    warehouseName: string
    zoneName: string
    warehouseType: 'OWN' | 'THIRD_PARTY'
  }) => Promise<void>;                                                      // 创建库位
  createInboundOrder: (payload: { supplierId: number; items: InboundDraftItem[] }) => Promise<void>;  // 创建入库单
  createOutboundOrder: (payload: { customerId: number | null; inboundOrderNos: string[]; items: OutboundDraftItem[] }) => Promise<void>;  // 创建出库单
  manualInventoryEntry: (payload: { partId: number; locationId: number; qty: number; remark: string }) => Promise<void>;  // 手动录入库存
  generateKanbans: (orderId: number) => Promise<void>;                      // 为入库单生成看板
  scanInbound: (payload: { barcode: string; locationCode: string }) => Promise<void>;  // 处理入库扫码
  scanOutbound: (payload: { barcode: string; outboundOrderNo: string }) => Promise<void>;  // 处理出库扫码
  transferKanban: (payload: { barcode: string; inboundOrderNo: string; locationCode: string; remark?: string }) => Promise<void>;
  freezeKanban: (payload: { barcode: string; frozen: boolean; remark?: string }) => Promise<void>;
  repackOutbound: (payload: { barcode: string; locationCode: string; remark?: string }) => Promise<void>;
  repackInbound: (payload: { barcode: string; locationCode: string; qty: number; remark?: string }) => Promise<void>;
  adjustKanbanBalance: (payload: { barcode: string; qty: number; remark?: string }) => Promise<void>;
  createMenu: (payload: Omit<FlatMenu, 'id' | 'visible'> & { visible?: boolean }) => Promise<void>;  // 创建菜单
  updateMenu: (id: number, payload: Omit<FlatMenu, 'id'>) => Promise<void>;  // 更新菜单
  deleteMenu: (id: number) => Promise<void>;                                 // 删除菜单
  logout: () => Promise<void>;                                               // 用户登出
  setNotice: (message: string) => void;                                     // 设置提示消息
}

/**
 * 页面模型类型，供页面组件使用
 */
export type PageModel = {
  state: AppState;              // 应用状态
  actions: AppActions;          // 应用操作方法
}
