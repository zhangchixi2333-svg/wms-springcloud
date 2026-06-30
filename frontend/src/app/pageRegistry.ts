/**
 * 本文件实现前端应用模块 pageRegistry。
 */
import { defineAsyncComponent } from 'vue'
import type { PageModel, WorkspaceTab, FlatMenu } from '../types/app'

const EquipmentPage = defineAsyncComponent(() => import('../components/pages/master-data/EquipmentPage.vue'))
const AgentAssistantPage = defineAsyncComponent(() => import('../components/pages/agent/AgentAssistantPage.vue'))
const HomeDashboardPage = defineAsyncComponent(() => import('../components/pages/dashboard/HomeDashboardPage.vue'))
const InboundPage = defineAsyncComponent(() => import('../components/pages/operations/InboundPage.vue'))
const InventoryOpsPage = defineAsyncComponent(() => import('../components/pages/operations/InventoryOpsPage.vue'))
const InventoryBoardPage = defineAsyncComponent(() => import('../components/pages/inventory/InventoryBoardPage.vue'))
const KanbanInfoPage = defineAsyncComponent(() => import('../components/pages/inventory/KanbanInfoPage.vue'))
const MenuManagementPage = defineAsyncComponent(() => import('../components/pages/system/MenuManagementPage.vue'))
const MobileScanPage = defineAsyncComponent(() => import('../components/pages/operations/MobileScanPage.vue'))
const OutboundPage = defineAsyncComponent(() => import('../components/pages/operations/OutboundPage.vue'))
const PartnersPage = defineAsyncComponent(() => import('../components/pages/partners/PartnersPage.vue'))
const PartsPage = defineAsyncComponent(() => import('../components/pages/master-data/PartsPage.vue'))
const PlaceholderPage = defineAsyncComponent(() => import('../components/shared/PlaceholderPage.vue'))
const RecordsPage = defineAsyncComponent(() => import('../components/pages/records/RecordsPage.vue'))
const RoleManagementPage = defineAsyncComponent(() => import('../components/pages/system/RoleManagementPage.vue'))
const SystemConfigPage = defineAsyncComponent(() => import('../components/pages/system/SystemConfigPage.vue'))
const SystemMonitorPage = defineAsyncComponent(() => import('../components/pages/system/SystemMonitorPage.vue'))
const UserManagementPage = defineAsyncComponent(() => import('../components/pages/system/UserManagementPage.vue'))
const WarehousePage = defineAsyncComponent(() => import('../components/pages/master-data/WarehousePage.vue'))

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
    case 'mobileScan':
      return { component: MobileScanPage, props: { model } }
    case 'agentAssistant':
      return { component: AgentAssistantPage, props: { model } }
    case 'equipmentNormal':
      return { component: EquipmentPage, props: { model, mode: 'normal' } }
    case 'equipmentRepack':
      return { component: EquipmentPage, props: { model, mode: 'repack' } }
    case 'repack':
      return { component: InventoryOpsPage, props: { model, mode: 'repack' } }
    case 'transfer':
      return { component: InventoryOpsPage, props: { model, mode: 'transfer' } }
    case 'freeze':
      return { component: InventoryOpsPage, props: { model, mode: 'freeze' } }
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
      if (pageKey === 'mobileScan') {
        return { component: MobileScanPage, props: { model } }
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
