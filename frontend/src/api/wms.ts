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
  InventoryRow,
  Kanban,
  Location,
  MenuNode,
  OutboundOrder,
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

  listCustomers: () => request<Customer[]>('/customers'),

  createCustomer: (payload: { customerCode: string; customerName: string }) =>
    request<Customer>('/customers', { method: 'POST', body: JSON.stringify(payload) }),

  listParts: () => request<Part[]>('/parts'),

  createPart: (payload: {
    partCode: string
    partName: string
    unit: string
    supplierId?: number | null
    defaultEquipmentCode?: string | null
    defaultUnitPerBox?: number | null
  }) =>
    request<Part>('/parts', { method: 'POST', body: JSON.stringify(payload) }),

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

  listInboundOrders: (filters?: { status?: string; supplierId?: number; inboundNo?: string }) =>
    request<InboundOrder[]>(`/inbound-orders${buildQuery(filters ?? {})}`),

  createInboundOrder: (payload: { supplierId: number; items: InboundDraftItem[] }) =>
    request<InboundOrder>('/inbound-orders', { method: 'POST', body: JSON.stringify(payload) }),

  generateKanbans: (orderId: number) =>
    request<Kanban[]>(`/inbound-orders/${orderId}/kanbans`, { method: 'POST' }),

  listKanbans: (filters?: {
    status?: string
    inboundNo?: string
    outboundNo?: string
    kanbanNo?: string
    supplierId?: number
    partCode?: string
    includeChildren?: boolean
  }) => request<Kanban[]>(`/kanbans${buildQuery(filters ?? {})}`),

  listKanbanChildren: (parentId: number) =>
    request<Kanban[]>(`/kanbans/${parentId}/children`),

  listOutboundOrders: (filters?: { status?: string; customerId?: number; outboundNo?: string }) =>
    request<OutboundOrder[]>(`/outbound-orders${buildQuery(filters ?? {})}`),

  createOutboundOrder: (payload: { customerId: number | null; items: Array<{ partId: number; boxCount: number; locationCode: string }> }) =>
    request<OutboundOrder>('/outbound-orders', { method: 'POST', body: JSON.stringify(payload) }),

  listInventory: (filters?: {
    warehouseName?: string
    zoneName?: string
    materialKeyword?: string
    supplierId?: number
  }) => request<InventoryRow[]>(`/inventory${buildQuery(filters ?? {})}`),

  manualInventoryEntry: (payload: { partId: number; locationId: number; qty: number; remark: string }) =>
    request<InventoryRow>('/inventory/manual-entries', { method: 'POST', body: JSON.stringify(payload) }),

  listTransactions: () => request<TransactionRow[]>('/inventory/transactions'),

  transactionVersion: () => request<TransactionVersion>('/inventory/transactions/version'),

  scanInbound: (payload: { barcode: string; locationCode: string }) =>
    request<ScanResult>('/mobile/scan/inbound', { method: 'POST', body: JSON.stringify(payload) }),

  scanOutbound: (payload: { barcode: string; outboundOrderNo: string }) =>
    request<ScanResult>('/mobile/scan/outbound', { method: 'POST', body: JSON.stringify(payload) }),

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
