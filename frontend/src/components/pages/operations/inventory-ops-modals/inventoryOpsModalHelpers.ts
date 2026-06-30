/** 本文件提供库存操作浮窗共用的看板、库位显示和扫码匹配工具。 */
import { formatWarehouseType } from '../../../../app/displayText'
import type { Kanban, Location } from '../../../../types/app'

export function findKanbanByScanValue(kanbans: Kanban[], value: string) {
  if (!value) return null
  return kanbans.find((item) => item.barcode === value || item.qrContent === value || item.kanbanNo === value) ?? null
}

export function kanbanOptionValue(item: Kanban) {
  return item.qrContent || item.barcode || item.kanbanNo
}

export function kanbanOptionLabel(item: Kanban) {
  return `${item.kanbanNo} | ${item.partCode} | 可用 ${item.availableQty} | ${item.warehouseName}/${item.zoneName}`
}

export function locationOptionLabel(location: Location) {
  return `${location.locationCode} | ${location.warehouseName}/${location.zoneName} | ${formatWarehouseType(location.warehouseType)}`
}
