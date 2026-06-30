/**
 * 本文件集中定义前端调用 WMS 后端接口的方法。
 */
import { request } from './client'

import type {
  CurrentUser,
  AuthSession,
  ConfigItem,
  Customer,
  Equipment,
  FlatMenu,
  InboundDraftItem,
  InboundOrder,
  InventoryPartSummary,
  InventoryOperationOrderRow,
  InventoryRow,
  Kanban,
  Location,
  MenuNode,
  OutboundOrder,
  PageResult,
  Part,
  ScanResult,
  SystemRole,
  SystemUser,
  Supplier,
  TransactionVersion,
  TransactionRow,
  AgentAnswer,
  AgentDashboard,
  AgentForecastRow,
  AgentHealth,
  AgentOverview,
  AgentRun,
  AgentSuggestion,
  RagDocument,
} from '../types/app'

function buildQuery(params: Record<string, string | number | boolean | undefined | null>) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {

    if (value !== undefined && value !== null && value !== '' && value !== 0) {
      search.set(key, String(value))
    }
  })
  const query = search.toString()
  return query ? `?${query}` : ''
}

export const api = {

  currentUser: () => request<CurrentUser>('/auth/me'),

  login: (payload: { username: string; password: string }) =>
    request<AuthSession>('/auth/login', { method: 'POST', body: JSON.stringify(payload) }),

  register: (payload: { username: string; password: string; displayName: string; roleName?: string }) =>
    request<AuthSession>('/auth/register', { method: 'POST', body: JSON.stringify(payload) }),

  logout: () => request<void>('/auth/logout', { method: 'POST' }),

  menuTree: () => request<MenuNode[]>('/menus/tree'),

  listMenus: () => request<FlatMenu[]>('/menus'),

  createMenu: (payload: Partial<FlatMenu> & Record<string, unknown>) =>
    request<FlatMenu>('/menus', { method: 'POST', body: JSON.stringify(payload) }),

  updateMenu: (id: number, payload: Partial<FlatMenu> & Record<string, unknown>) =>
    request<FlatMenu>(`/menus/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),

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

  listSuppliers: () => request<Supplier[]>('/suppliers'),

  createSupplier: (payload: { supplierCode: string; supplierName: string }) =>
    request<Supplier>('/suppliers', { method: 'POST', body: JSON.stringify(payload) }),

  deleteSupplier: (id: number) => request<void>(`/suppliers/${id}`, { method: 'DELETE' }),

  listCustomers: () => request<Customer[]>('/customers'),

  createCustomer: (payload: { customerCode: string; customerName: string }) =>
    request<Customer>('/customers', { method: 'POST', body: JSON.stringify(payload) }),

  deleteCustomer: (id: number) => request<void>(`/customers/${id}`, { method: 'DELETE' }),

  listParts: () => request<Part[]>('/parts'),

  createPart: (payload: {
    partCode: string
    partName: string
    unit: string
    categoryCode?: string | null
    supplierId?: number | null
    defaultEquipmentCode?: string | null
    defaultUnitPerBox?: number | null
  }) =>
    request<Part>('/parts', { method: 'POST', body: JSON.stringify(payload) }),

  deletePart: (id: number) => request<void>(`/parts/${id}`, { method: 'DELETE' }),

  listEquipment: () => request<Equipment[]>('/equipment'),

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

  listLocations: () => request<Location[]>('/locations'),

  createLocation: (payload: {
    locationCode: string
    locationName: string
    warehouseName: string
    zoneName: string
    warehouseType: 'OWN' | 'THIRD_PARTY'
  }) => request<Location>('/locations', { method: 'POST', body: JSON.stringify(payload) }),

  deleteLocation: (id: number) => request<void>(`/locations/${id}`, { method: 'DELETE' }),

  listInboundOrders: (filters?: { status?: string; supplierId?: number; inboundNo?: string }) =>
    request<InboundOrder[]>(`/inbound-orders${buildQuery(filters ?? {})}`),

  listInboundOrdersPage: (filters?: { status?: string; supplierId?: number; inboundNo?: string; page?: number; size?: number }) =>
    request<PageResult<InboundOrder>>(`/inbound-orders/page${buildQuery(filters ?? {})}`),

  createInboundOrder: (payload: { supplierId: number; items: InboundDraftItem[] }) =>
    request<InboundOrder>('/inbound-orders', { method: 'POST', body: JSON.stringify(payload) }),

  generateKanbans: (orderId: number) =>
    request<Kanban[]>(`/inbound-orders/${orderId}/kanbans`, { method: 'POST' }),

  returnInboundOrder: (orderId: number) =>
    request<InboundOrder>(`/inbound-orders/${orderId}/return`, { method: 'POST' }),

  listKanbans: (filters?: {
    status?: string
    inboundNo?: string
    outboundNo?: string
    kanbanNo?: string
    supplierId?: number
    partCode?: string
    warehouseName?: string
    zoneName?: string
    warehouseType?: 'OWN' | 'THIRD_PARTY' | ''
    includeChildren?: boolean
  }) => request<Kanban[]>(`/kanbans${buildQuery(filters ?? {})}`),

  listKanbansPage: (filters?: {
    status?: string
    inboundNo?: string
    outboundNo?: string
    kanbanNo?: string
    supplierId?: number
    partCode?: string
    warehouseName?: string
    zoneName?: string
    warehouseType?: 'OWN' | 'THIRD_PARTY' | ''
    includeChildren?: boolean
    page?: number
    size?: number
  }) => request<PageResult<Kanban>>(`/kanbans/page${buildQuery(filters ?? {})}`),

  listOutboundOrders: (filters?: { status?: string; customerId?: number; outboundNo?: string }) =>
    request<OutboundOrder[]>(`/outbound-orders${buildQuery(filters ?? {})}`),

  listOutboundOrdersPage: (filters?: { status?: string; customerId?: number; outboundNo?: string; page?: number; size?: number }) =>
    request<PageResult<OutboundOrder>>(`/outbound-orders/page${buildQuery(filters ?? {})}`),

  createOutboundOrder: (payload: { customerId: number | null; items: Array<{ partId: number; plannedQty: number; boxCount: number; equipmentCode?: string | null; unitPerBox: number; locationCode: string }> }) =>
    request<OutboundOrder>('/outbound-orders', { method: 'POST', body: JSON.stringify(payload) }),

  cancelOutboundOrder: (orderId: number) =>
    request<OutboundOrder>(`/outbound-orders/${orderId}/cancel`, { method: 'POST' }),

  listInventory: (filters?: {
    warehouseName?: string
    zoneName?: string
    materialKeyword?: string
    supplierId?: number
  }) => request<InventoryRow[]>(`/inventory${buildQuery(filters ?? {})}`),

  listInventoryPage: (filters?: {
    warehouseName?: string
    zoneName?: string
    materialKeyword?: string
    supplierId?: number
    page?: number
    size?: number
  }) => request<PageResult<InventoryPartSummary>>(`/inventory/page${buildQuery(filters ?? {})}`),

  listInventoryDetailsPage: (filters?: {
    partCode?: string
    warehouseName?: string
    zoneName?: string
    materialKeyword?: string
    supplierId?: number
    page?: number
    size?: number
  }) => request<PageResult<InventoryRow>>(`/inventory/details/page${buildQuery(filters ?? {})}`),

  listInventoryKanbansPage: (filters?: {
    partCode?: string
    warehouseName?: string
    zoneName?: string
    kanbanNo?: string
    supplierId?: number
    page?: number
    size?: number
  }) => request<PageResult<Kanban>>(`/inventory/kanbans/page${buildQuery(filters ?? {})}`),

  manualInventoryEntry: (payload: { partId: number; locationId: number; qty: number; remark: string }) =>
    request<InventoryRow>('/inventory/manual-entries', { method: 'POST', body: JSON.stringify(payload) }),

  listTransactions: () => request<TransactionRow[]>('/inventory/transactions'),

  listTransactionsPage: (filters?: {
    partCode?: string
    businessType?: string
    businessNo?: string
    operationNo?: string
    barcode?: string
    locationCode?: string
    page?: number
    size?: number
  }) => request<PageResult<TransactionRow>>(`/inventory/transactions/page${buildQuery(filters ?? {})}`),

  listInventoryOperationOrders: (operationNo: string, barcode?: string) =>
    request<InventoryOperationOrderRow[]>(`/inventory/operation-orders${buildQuery({ operationNo, barcode })}`),

  transactionVersion: () => request<TransactionVersion>('/inventory/transactions/version'),

  scanInbound: (payload: { barcode: string; locationCode: string }) =>
    request<ScanResult>('/mobile/scan/inbound', { method: 'POST', body: JSON.stringify(payload) }),

  scanInboundBatch: (payload: { scanCode: string; locationCode: string; kanbanIds: number[] }) =>
    request<ScanResult>('/mobile/scan/inbound-batch', {
      method: 'POST',
      body: JSON.stringify({
        scanCode: payload.scanCode,
        locationCode: payload.locationCode,
        childKanbanIds: payload.kanbanIds,
      }),
    }),

  scanOutbound: (payload: { barcode: string; outboundOrderNo: string }) =>
    request<ScanResult>('/mobile/scan/outbound', { method: 'POST', body: JSON.stringify(payload) }),

  transferKanban: (payload: { barcode: string; inboundOrderNo: string; locationCode: string; qty?: number | null; remark?: string }) =>
    request<Kanban>('/inventory/kanbans/transfer', { method: 'POST', body: JSON.stringify(payload) }),

  transferKanbans: (payload: { barcodes: string[]; locationCode: string; remark?: string }) =>
    request<Kanban[]>('/inventory/kanbans/transfer-batch', { method: 'POST', body: JSON.stringify(payload) }),

  freezeKanban: (payload: { barcode: string; frozen: boolean; remark?: string }) =>
    request<Kanban>('/inventory/kanbans/freeze', { method: 'POST', body: JSON.stringify(payload) }),

  freezeKanbans: (payload: { barcodes: string[]; frozen: boolean; remark?: string }) =>
    request<Kanban[]>('/inventory/kanbans/freeze-batch', { method: 'POST', body: JSON.stringify(payload) }),

  repackOutbound: (payload: { barcode: string; locationCode: string; qty?: number | null; remark?: string }) =>
    request<Kanban>('/inventory/kanbans/repack-outbound', { method: 'POST', body: JSON.stringify(payload) }),

  repackOutboundBatch: (payload: { barcodes: string[]; locationCode: string; remark?: string }) =>
    request<Kanban[]>('/inventory/kanbans/repack-outbound-batch', { method: 'POST', body: JSON.stringify(payload) }),

  repackInbound: (payload: { barcode: string; locationCode: string; qty: number; remark?: string }) =>
    request<Kanban>('/inventory/kanbans/repack-inbound', { method: 'POST', body: JSON.stringify(payload) }),

  repackInboundBatch: (payload: { barcodes: string[]; locationCode: string; remark?: string }) =>
    request<Kanban[]>('/inventory/kanbans/repack-inbound-batch', { method: 'POST', body: JSON.stringify(payload) }),

  listConfigItems: (moduleKey: string) =>
    request<ConfigItem[]>(`/config-items${buildQuery({ moduleKey })}`),

  createConfigItem: (payload: {
    moduleKey: string
    itemCode: string
    itemName: string
    status: string
    remark: string
  }) => request<ConfigItem>('/config-items', { method: 'POST', body: JSON.stringify(payload) }),

  updateConfigItem: (id: number, payload: {
    moduleKey: string
    itemCode: string
    itemName: string
    status: string
    remark: string
  }) => request<ConfigItem>(`/config-items/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),

  deleteConfigItem: (id: number) => request<void>(`/config-items/${id}`, { method: 'DELETE' }),

  agentHealth: () => request<AgentHealth>('/agent/health'),

  agentOverview: () => request<AgentOverview>('/agent/overview'),

  agentDashboard: (days?: number) =>
    request<AgentDashboard>(`/agent/dashboard${buildQuery({ days })}`),

  agentForecast: (days?: number) =>
    request<AgentForecastRow[]>(`/agent/forecast/inventory${buildQuery({ days })}`),

  agentSuggestions: () => request<AgentSuggestion[]>('/agent/suggestions'),

  runAgentAnalyze: (days?: number) =>
    request<AgentRun>(`/agent/analyze${buildQuery({ days })}`, { method: 'POST' }),

  askAgent: (payload: { sessionId?: string; question: string }) =>
    request<AgentAnswer>('/agent/ask', { method: 'POST', body: JSON.stringify(payload) }),

  listRagDocuments: () => request<RagDocument[]>('/agent/rag/documents'),

  createRagDocument: (payload: {
    docKey?: string
    title: string
    sourceType?: string
    content: string
    metadataJson?: string
    enabled?: boolean
  }) => request<RagDocument>('/agent/rag/documents', { method: 'POST', body: JSON.stringify(payload) }),
}
