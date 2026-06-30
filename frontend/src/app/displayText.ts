/** 本文件统一维护前端页面使用的中文显示文案映射。 */
const statusMap: Record<string, string> = {
  CREATED: '已创建',
  WAIT_SCAN: '待扫码',
  PARTIAL: '部分入库',
  PARTIAL_OUTBOUND: '部分出库',
  ALLOCATED: '已锁定待出库',
  INBOUND: '已入库',
  OUTBOUND: '已出库',
  FROZEN: '已封存',
  REPACK_OUTBOUND: '转包移出中',
  REPACK_INBOUND: '转包返还中',
  THIRD_PARTY_STOCK: '第三方在库',
  CONSUMED: '已消耗',
  RETURNED: '已退回',
  CANCELLED: '已取消',
  CANCELED: '已取消',
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
  BOX: '周转箱',
  RACK: '料架',
}

const businessTypeMap: Record<string, string> = {
  INBOUND_SCAN: '扫码入库',
  OUTBOUND_SCAN: '扫码出库',
  OUTBOUND_LOCK: '出库锁定',
  OUTBOUND_UNLOCK: '释放出库锁定',
  MANUAL_ENTRY: '手工入账',
  TRANSFER_OUT: '移库出库',
  TRANSFER_IN: '移库入库',
  TRANSFER_LOCK: '迁移锁定',
  TRANSFER_UNLOCK: '释放迁移锁定',
  FREEZE: '封存',
  UNFREEZE: '解封',
  REPACK_OUT: '转包出库',
  REPACK_IN: '转包回库',
  REPACK_THIRD_IN: '第三方仓入库',
  REPACK_THIRD_OUT: '第三方仓出库',
  OUTSOURCE_TRANSFER_OUT: '转包移出',
  OUTSOURCE_TRANSFER_IN: '第三方接收',
  OUTSOURCE_RETURN_OUT: '第三方返还移出',
  OUTSOURCE_RETURN_IN: '转包返还入库',
  INBOUND: '入库',
  OUTBOUND: '出库',
  TRANSFER: '移库',
  OUTSOURCE_TRANSFER: '转包转出',
  OUTSOURCE_RETURN: '转包返还',
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
