/** 本文件统一维护前端页面使用的中文显示文案映射。 */
const statusMap: Record<string, string> = {
  CREATED: '已创建',
  WAIT_SCAN: '待扫码',
  PARTIAL: '部分完成',
  PARTIAL_INBOUND: '部分入库',
  PARTIAL_OUTBOUND: '部分出库',
  INBOUND: '已入库',
  OUTBOUND: '已出库',
  FROZEN: '已封存',
  REPACK_OUTBOUND: '转包出库中',
  REPACK_INBOUND: '转包回库',
  RETURNED: '已退回',
  VOID: '已作废',
  COMPLETED: '已完成',
  ENABLED: '启用',
  DISABLED: '停用',
  OWN: '自有仓库',
  THIRD_PARTY: '第三方仓库',
  PARENT: '父级菜单',
  LEAF: '叶子菜单',
  ADMIN: '管理员',
  MANAGER: '主管',
  OPERATOR: '操作员',
  VIEWER: '查看员',
  NORMAL: '常规器具',
  REPACK: '转包器具',
}

const businessTypeMap: Record<string, string> = {
  INBOUND_SCAN: '扫码入库',
  OUTBOUND_SCAN: '扫码出库',
  MANUAL_ENTRY: '手工入账',
  TRANSFER_OUT: '移库出库',
  TRANSFER_IN: '移库入库',
  FREEZE: '封存',
  UNFREEZE: '解封',
  REPACK_OUT: '转包出库',
  REPACK_IN: '转包回库',
  REPACK_THIRD_IN: '第三方仓入库',
  REPACK_THIRD_OUT: '第三方仓出库',
  BALANCE_ADJUST: '结余调整',
}

const scanResultCodeMap: Record<string, string> = {
  INBOUND_OK: '入库成功',
  OUTBOUND_OK: '出库成功',
}

export function toDisplayText(value: string | null | undefined, mapping: Record<string, string>) {
  if (!value) return '-'
  return mapping[value] ?? value
}

export function formatStatus(value: string | null | undefined) {
  return toDisplayText(value, statusMap)
}

export function formatBusinessType(value: string | null | undefined) {
  return toDisplayText(value, businessTypeMap)
}

export function formatWarehouseType(value: string | null | undefined) {
  return toDisplayText(value, statusMap)
}

export function formatPermissionLevel(value: string | null | undefined) {
  return toDisplayText(value, statusMap)
}

export function formatMenuType(value: string | null | undefined) {
  return toDisplayText(value, statusMap)
}

export function formatEquipmentType(value: string | null | undefined) {
  return toDisplayText(value, statusMap)
}

export function formatScanResultCode(value: string | null | undefined) {
  return toDisplayText(value, scanResultCodeMap)
}
