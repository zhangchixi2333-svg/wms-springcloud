/**
 * 本文件集中放置看板扫码、库位匹配和看板展示相关的纯函数。
 */
import type { Kanban, Location } from '../types/app'

export function normalizeScanCode(value: string) {
  return value.trim()
}

export function findKanbanByScanCode(kanbans: Kanban[], scanCode: string) {
  const normalized = normalizeScanCode(scanCode)
  if (!normalized) return null
  const direct = kanbans.find((item) =>
    item.barcode === normalized
    || item.qrContent === normalized
    || item.kanbanNo.toLowerCase() === normalized.toLowerCase(),
  )
  if (direct) return direct
  const parts = normalized.split('|')
  if (parts.length === 3 && parts[0] === 'WMS-KANBAN') {
    return kanbans.find((item) =>
      item.barcode === parts[2]
      || item.kanbanNo.toLowerCase() === parts[1].toLowerCase(),
    ) ?? null
  }
  return null
}

export function findLocationForKanban(locations: Location[], kanban: Kanban | null) {
  if (!kanban) return null
  return locations.find((item) => item.warehouseName === kanban.warehouseName && item.zoneName === kanban.zoneName) ?? null
}

export function compareKanbanFifo(left: Kanban, right: Kanban) {
  return `${left.inboundTime ?? left.createdAt}-${left.boxIndex}-${left.id}`
    .localeCompare(`${right.inboundTime ?? right.createdAt}-${right.boxIndex}-${right.id}`)
}

export function formatDateTime(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

export function splitBusinessNos(value: string | null | undefined) {
  return Array.from(new Set((value ?? '')
    .split(/[,\uFF0C;\uFF1B\s]+/)
    .map((item) => item.trim())
    .filter((item) => item && item !== '-')))
}
