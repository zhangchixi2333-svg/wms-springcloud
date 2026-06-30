/**
 * 本文件实现前端应用模块 optionHelpers。
 */
import type { Equipment, Kanban, Location } from '../types/app'

export type WarehouseZoneOption = {
  label: string
  value: string
  warehouseName: string
  zoneName: string
  warehouseType: 'OWN' | 'THIRD_PARTY'
}

function uniqueValues(values: string[]) {
  return Array.from(new Set(values.filter(Boolean)))
}

export function warehouseOptions(locations: Location[]) {
  return uniqueValues(locations.map((item) => item.warehouseName))
}

export function zoneOptions(locations: Location[], warehouseName?: string) {
  return uniqueValues(
    locations
      .filter((item) => !warehouseName || item.warehouseName === warehouseName)
      .map((item) => item.zoneName),
  )
}

export function warehouseZoneOptions(locations: Location[]): WarehouseZoneOption[] {
  const optionMap = new Map<string, WarehouseZoneOption>()
  locations.forEach((item) => {
    const value = `${item.warehouseName} / ${item.zoneName}`
    optionMap.set(value, {
      label: value,
      value,
      warehouseName: item.warehouseName,
      zoneName: item.zoneName,
      warehouseType: item.warehouseType,
    })
  })
  return Array.from(optionMap.values())
}

export function equipmentCodeOptions(equipment: Equipment[], equipmentType?: string) {
  return equipment
    .filter((item) => !equipmentType || item.equipmentType === equipmentType)
    .filter((item) => item.status !== 'DISABLED')
    .map((item) => ({
      label: `${item.equipmentCode} | ${item.equipmentName} | ${item.equipmentModel}`,
      value: item.equipmentCode,
    }))
}

export function kanbanScanOptions(kanbans: Kanban[], statuses?: string[]) {
  return kanbans
    .filter((item) => !statuses || statuses.includes(item.status))
    .map((item) => ({
      label: `${item.kanbanNo} | 条码 ${item.barcode} | 二维码 ${item.qrContent}`,
      value: item.qrContent || item.barcode,
    }))
}
