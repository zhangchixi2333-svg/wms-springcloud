/**
 * 本文件实现前端应用模块 pageRegistry。
 */
import EquipmentPage from '../components/pages/master-data/EquipmentPage.vue'
import HomeDashboardPage from '../components/pages/dashboard/HomeDashboardPage.vue'
import InboundPage from '../components/pages/operations/InboundPage.vue'
import InventoryOpsPage from '../components/pages/operations/InventoryOpsPage.vue'
import InventoryBoardPage from '../components/pages/inventory/InventoryBoardPage.vue'
import KanbanInfoPage from '../components/pages/inventory/KanbanInfoPage.vue'
import MenuManagementPage from '../components/pages/system/MenuManagementPage.vue'
import OutboundPage from '../components/pages/operations/OutboundPage.vue'
import PartnersPage from '../components/pages/partners/PartnersPage.vue'
import PartsPage from '../components/pages/master-data/PartsPage.vue'
import PlaceholderPage from '../components/shared/PlaceholderPage.vue'
import RecordsPage from '../components/pages/records/RecordsPage.vue'
import RoleManagementPage from '../components/pages/system/RoleManagementPage.vue'
import SystemConfigPage from '../components/pages/system/SystemConfigPage.vue'
import SystemMonitorPage from '../components/pages/system/SystemMonitorPage.vue'
import UserManagementPage from '../components/pages/system/UserManagementPage.vue'
import WarehousePage from '../components/pages/master-data/WarehousePage.vue'
import type { PageModel, WorkspaceTab, FlatMenu } from '../types/app'

export function resolvePage(tab: WorkspaceTab | undefined, flatMenus: FlatMenu[], model: PageModel) {
  const pageKey = tab?.pageKey ?? 'home'
  const flatMenuMap = new Map(flatMenus.map((item) => [item.menuKey, item]))

  switch (pageKey) {
    case 'home':
      return { component: HomeDashboardPage, props: { model } }
    case 'inbound':
      return { component: InboundPage, props: { model } }
    case 'kanbanInfo':
      return { component: KanbanInfoPage, props: { model, mode: 'kanban' } }
    case 'inventoryBoard':
      return { component: InventoryBoardPage, props: { model } }
    case 'records':
      return { component: RecordsPage, props: { model } }
    case 'partManagement':
      return { component: PartsPage, props: { model } }
    case 'categoryManagement':
      return { component: SystemConfigPage, props: { model, moduleKey: 'categoryManagement' } }
    case 'supplierManagement':
      return { component: PartnersPage, props: { model, mode: 'supplier' } }
    case 'customerManagement':
      return { component: PartnersPage, props: { model, mode: 'customer' } }
    case 'warehouseZone':
      return { component: WarehousePage, props: { model } }
    case 'menuManagement':
      return { component: MenuManagementPage, props: { model } }
    case 'userManagement':
      return { component: UserManagementPage, props: { model } }
    case 'roleManagement':
      return { component: RoleManagementPage, props: { model } }
    case 'outbound':
      return { component: OutboundPage, props: { model } }
    case 'equipmentNormal':
      return { component: EquipmentPage, props: { model, mode: 'normal' } }
    case 'equipmentRepack':
      return { component: EquipmentPage, props: { model, mode: 'repack' } }
    case 'repack':
      return { component: InventoryOpsPage, props: { model, mode: 'repack' } }
    case 'repackBalance':
      return { component: InventoryOpsPage, props: { model, mode: 'balance' } }
    case 'transferFreeze':
      return { component: InventoryOpsPage, props: { model, mode: 'transferFreeze' } }
    case 'departmentManagement':
    case 'postManagement':
    case 'dictionaryManagement':
    case 'parameterSettings':
      return { component: SystemConfigPage, props: { model, moduleKey: pageKey } }
    case 'systemTools':
      return { component: SystemConfigPage, props: { model, moduleKey: 'systemTools' } }
    case 'systemMonitor':
      return { component: SystemMonitorPage, props: { model } }
    default: {
      const menu = flatMenuMap.get(tab?.menuKey ?? '')
      if (pageKey === 'inboundScan') {
        return { component: KanbanInfoPage, props: { model, mode: 'inbound-scan' } }
      }
      return {
        component: PlaceholderPage,
        props: {
          title: menu?.menuName ?? '功能建设中',
          description: '这个菜单已经接入标签页和菜单配置体系，后面可以继续补表单、规则和业务流程。',
        },
      }
    }
  }
}
