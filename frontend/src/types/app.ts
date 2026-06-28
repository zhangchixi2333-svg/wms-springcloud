/**
 * 本文件集中定义前端应用的数据类型。
 */
export type MenuNode = {
  id: number;
  parentId: number | null;
  menuKey: string;
  menuName: string;
  menuType: 'PARENT' | 'LEAF';
  pathKey: string | null;
  pageKey: string | null;
  iconKey: string | null;
  sortOrder: number;
  children: MenuNode[];
}

export type FlatMenu = {
  id: number;
  parentId: number | null;
  menuKey: string;
  menuName: string;
  menuType: 'PARENT' | 'LEAF';
  pathKey: string | null;
  pageKey: string | null;
  iconKey: string | null;
  sortOrder: number;
  visible: boolean;
}

export type CurrentUser = {
  id: number;
  username: string;
  displayName: string;
  roleName: string;
  avatarColor: string;
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

export type Supplier = {
  id: number;
  supplierCode: string;
  supplierName: string;
}

export type Customer = {
  id: number;
  customerCode: string;
  customerName: string;
}

export type Part = {
  id: number;
  partCode: string;
  partName: string;
  unit: string;
  supplierId: number | null;
  defaultEquipmentCode: string | null;
  defaultUnitPerBox: number | null;
}

export type Equipment = {
  id: number;
  equipmentCode: string;
  equipmentName: string;
  equipmentType: string;
  equipmentModel: string;
  capacity: number;
  warehouseName: string;
  zoneName: string;
  status: string;
}

export type Location = {
  id: number;
  locationCode: string;
  locationName: string;
  warehouseName: string;
  zoneName: string;
  warehouseType: 'OWN' | 'THIRD_PARTY';
}

export type InboundOrderItem = {
  id: number;
  partId: number;
  partCode: string;
  partName: string;
  unit: string;
  plannedQty: number;
  receivedQty: number;
  boxCount: number;
  pendingRepack: boolean;
  equipmentCode: string;
  unitPerBox: number;
  warehouseZone: string;
}

export type InboundOrder = {
  id: number;
  inboundNo: string;
  supplierId: number;
  supplierName: string;
  status: string;
  createdAt: string;
  items: InboundOrderItem[];
}

export type OutboundOrderItem = {
  id: number;
  kanbanId: number | null;
  kanbanNo: string;
  partId: number;
  partCode: string;
  partName: string;
  unit: string;
  plannedQty: number;
  scannedQty: number;
  warehouseName: string;
  zoneName: string;
}

export type OutboundOrder = {
  id: number;
  outboundNo: string;
  customerId: number | null;
  customerName: string;
  inboundOrderNos: string[];
  status: string;
  createdAt: string;
  items: OutboundOrderItem[];
}

export type Kanban = {
  id: number;
  kanbanNo: string;
  barcode: string;
  qrContent: string;
  parentKanbanId: number | null;
  parentKanban: boolean;
  boxIndex: number;
  inboundNo: string;
  outboundNo: string;
  partId: number;
  partCode: string;
  partName: string;
  unit: string;
  supplierId: number | null;
  supplierName: string;
  batchNo: string;
  qty: number;
  boxCount: number;
  pendingRepack: boolean;
  equipmentCode: string;
  equipmentModel: string;
  unitPerBox: number;
  warehouseName: string;
  zoneName: string;
  status: string;
  locationId: number | null;
  locationCode: string;
  createdAt: string;
  inboundTime: string | null;
  outboundTime: string | null;
  children: Kanban[];
}

export type ScanResult = {
  code: string;
  message: string;
  barcode: string;
  scannedKanbanNo: string;
  parentKanbanNo: string | null;
  outboundOrderNo: string | null;
  status: string;
  affectedCount: number;
  affectedKanbanNos: string[];
}

export type InventoryRow = {
  id: number;
  partId: number;
  partCode: string;
  partName: string;
  supplierName: string;
  locationId: number;
  locationCode: string;
  warehouseName: string;
  zoneName: string;
  qty: number;
  updatedAt: string;
}

export type TransactionRow = {
  id: number;
  transactionNo: string;
  businessType: string;
  businessNo: string;
  barcode: string;
  partCode: string;
  locationCode: string;
  qtyChange: number;
  remark: string;
  createdAt: string;
}

export type TransactionVersion = {
  total: number;
  latestId: number | null;
  latestTransactionNo: string;
  latestCreatedAt: string | null;
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

export type AgentSuggestion = {
  id: number | null;
  runId: number | null;
  suggestionType: string;
  riskLevel: string;
  partId: number | null;
  partCode: string | null;
  title: string;
  content: string;
  actionKey: string | null;
  targetPageKey: string | null;
  targetBusinessNo: string | null;
  status: string;
  createdAt: string;
}

export type AgentOverview = {
  callApi: boolean;
  ragEnabled: boolean;
  ragProvider: string;
  partCount: number;
  currentQty: number;
  criticalCount: number;
  lowCount: number;
  attentionCount: number;
  topSuggestions: AgentSuggestion[];
}

export type AgentForecastRow = {
  partId: number;
  partCode: string;
  partName: string;
  currentQty: number;
  avgDailyOutQty: number;
  forecastDays: number;
  forecastQty: number;
  estimatedStockoutDate: string | null;
  riskLevel: string;
  riskLabel: string;
  suggestedReplenishQty: number;
  criticalThreshold: number;
  lowThreshold: number;
  attentionThreshold: number;
}

export type AgentRun = {
  id: number;
  runNo: string;
  status: string;
  callApi: boolean;
  forecastDays: number;
  suggestionCount: number;
  startedAt: string;
  finishedAt: string | null;
  errorMessage: string | null;
}

export type AgentAnswer = {
  answer: string;
  callApi: boolean;
  suggestions: AgentSuggestion[];
}

export type AgentHealth = {
  status: string;
  callApi: boolean;
  ragEnabled: boolean;
  ragProvider: string;
  mode: string;
}

export type AgentDashboard = {
  health: AgentHealth;
  overview: AgentOverview;
  forecastRows: AgentForecastRow[];
  suggestions: AgentSuggestion[];
  latestRun: AgentRun | null;
  ragDocuments: RagDocument[];
}

export type RagDocument = {
  id: number;
  docKey: string;
  title: string;
  sourceType: string;
  content: string;
  metadataJson: string;
  enabled: boolean;
  createdAt: string;
}

export type WorkspaceTab = {
  menuKey: string;
  title: string;
  pageKey: string;
}

export type InboundDraftItem = {
  partId: number;
  plannedQty: number;
  boxCount: number;
  pendingRepack: boolean;
  equipmentCode: string;
  unitPerBox: number;
  warehouseZone: string;
}

export type OutboundDraftItem = {
  partId: number;
  boxCount: number;
  locationCode: string;
}

export type AppState = {
  authenticated: boolean;
  user: CurrentUser | null;
  menuTree: MenuNode[];
  flatMenus: FlatMenu[];
  users: SystemUser[];
  roles: SystemRole[];
  suppliers: Supplier[];
  customers: Customer[];
  parts: Part[];
  equipment: Equipment[];
  locations: Location[];
  inboundOrders: InboundOrder[];
  outboundOrders: OutboundOrder[];
  kanbans: Kanban[];
  inventory: InventoryRow[];
  transactions: TransactionRow[];
  loading: boolean;
  message: string;
  error: string;
}

export type AppActions = {
  login: (payload: { username: string; password: string }) => Promise<void>;
  register: (payload: { username: string; password: string; displayName: string; roleName?: string }) => Promise<void>;
  refreshAll: () => Promise<void>;
  refreshMenus: () => Promise<void>;
  refreshSystemSecurity: () => Promise<void>;
  loadKanbanChildren: (parentId: number) => Promise<Kanban[]>;
  createUser: (payload: { username: string; password?: string; displayName: string; roleName: string; avatarColor?: string }) => Promise<void>;
  updateUser: (id: number, payload: { username: string; password?: string; displayName: string; roleName: string; avatarColor?: string }) => Promise<void>;
  deleteUser: (id: number) => Promise<void>;
  createRole: (payload: { roleCode: string; roleName: string; permissionLevel: string; description?: string; enabled: boolean; menuIds: number[] }) => Promise<void>;
  updateRole: (id: number, payload: { roleCode: string; roleName: string; permissionLevel: string; description?: string; enabled: boolean; menuIds: number[] }) => Promise<void>;
  deleteRole: (id: number) => Promise<void>;
  assignRoleMenus: (roleCode: string, menuIds: number[]) => Promise<void>;
  createSupplier: (payload: { supplierCode: string; supplierName: string }) => Promise<void>;
  createCustomer: (payload: { customerCode: string; customerName: string }) => Promise<void>;
  createPart: (payload: {
    partCode: string;
    partName: string;
    unit: string;
    supplierId?: number | null;
    defaultEquipmentCode?: string | null;
    defaultUnitPerBox?: number | null;
  }) => Promise<void>;
  createEquipment: (payload: {
    equipmentCode: string
    equipmentName: string
    equipmentType: string
    equipmentModel: string
    capacity: number
    warehouseName: string
    zoneName: string
    status: string
  }) => Promise<void>;
  createLocation: (payload: {
    locationCode: string
    locationName: string
    warehouseName: string
    zoneName: string
    warehouseType: 'OWN' | 'THIRD_PARTY'
  }) => Promise<void>;
  createInboundOrder: (payload: { supplierId: number; items: InboundDraftItem[] }) => Promise<void>;
  createOutboundOrder: (payload: { customerId: number | null; items: OutboundDraftItem[] }) => Promise<void>;
  manualInventoryEntry: (payload: { partId: number; locationId: number; qty: number; remark: string }) => Promise<void>;
  generateKanbans: (orderId: number) => Promise<void>;
  scanInbound: (payload: { barcode: string; locationCode: string }) => Promise<ScanResult>;
  scanOutbound: (payload: { barcode: string; outboundOrderNo: string }) => Promise<ScanResult>;
  transferKanban: (payload: { barcode: string; inboundOrderNo: string; locationCode: string; remark?: string }) => Promise<void>;
  freezeKanban: (payload: { barcode: string; frozen: boolean; remark?: string }) => Promise<void>;
  repackOutbound: (payload: { barcode: string; locationCode: string; remark?: string }) => Promise<void>;
  repackInbound: (payload: { barcode: string; locationCode: string; qty: number; remark?: string }) => Promise<void>;
  adjustKanbanBalance: (payload: { barcode: string; qty: number; remark?: string }) => Promise<void>;
  createMenu: (payload: Omit<FlatMenu, 'id' | 'visible'> & { visible?: boolean }) => Promise<void>;
  updateMenu: (id: number, payload: Omit<FlatMenu, 'id'>) => Promise<void>;
  deleteMenu: (id: number) => Promise<void>;
  logout: () => Promise<void>;
  setNotice: (message: string) => void;
}

export type PageModel = {
  state: AppState;
  actions: AppActions;
}
