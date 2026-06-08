import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../api/wms'
import { clearAuthToken, getAuthToken, setAuthToken } from '../api/client'
import { resolvePage } from './pageRegistry'
import type { AppActions, AppState, FlatMenu, MenuNode, PageModel, WorkspaceTab } from '../types/app'

export function useWorkspaceApp() {
  const state = reactive<AppState>({
    authenticated: Boolean(getAuthToken()),
    user: null,
    menuTree: [],
    flatMenus: [],
    users: [],
    roles: [],
    suppliers: [],
    customers: [],
    parts: [],
    equipment: [],
    locations: [],
    inboundOrders: [],
    outboundOrders: [],
    kanbans: [],
    inventory: [],
    transactions: [],
    loading: false,
    message: '系统已就绪',
    error: '',
  })

  const tabs = ref<WorkspaceTab[]>([])
  const activeMenuKey = ref('home')

  function setNotice(message: string) {
    state.message = message
    state.error = ''
  }

  function findFirstLeaf(nodes: MenuNode[]): MenuNode {
    for (const node of nodes) {
      if (node.menuType === 'LEAF') return node
      if (node.children.length > 0) return findFirstLeaf(node.children)
    }
    return {
      id: 0,
      parentId: null,
      menuKey: 'home',
      menuName: '首页',
      menuType: 'LEAF',
      pathKey: 'home',
      pageKey: 'home',
      iconKey: 'home',
      sortOrder: 0,
      children: [],
    }
  }

  function findLeaf(menuKey: string): MenuNode | undefined {
    const walk = (nodes: MenuNode[]): MenuNode | undefined => {
      for (const node of nodes) {
        if (node.menuKey === menuKey) return node
        const child = walk(node.children)
        if (child) return child
      }
    }
    return walk(state.menuTree)
  }

  function openLeaf(menu?: MenuNode) {
    if (!menu || menu.menuType !== 'LEAF' || !menu.pageKey) return
    if (!tabs.value.some((tab) => tab.menuKey === menu.menuKey)) {
      tabs.value.push({
        menuKey: menu.menuKey,
        title: menu.menuName,
        pageKey: menu.pageKey,
      })
    }
    activeMenuKey.value = menu.menuKey
  }

  function closeTab(menuKey: string) {
    if (menuKey === 'home') return
    const nextTabs = tabs.value.filter((tab) => tab.menuKey !== menuKey)
    tabs.value = nextTabs
    if (activeMenuKey.value === menuKey) {
      activeMenuKey.value = nextTabs[nextTabs.length - 1]?.menuKey ?? 'home'
    }
  }

  function syncTabsWithMenus(flatMenus: FlatMenu[]) {
    const menuMap = new Map(flatMenus.map((item) => [item.menuKey, item]))
    tabs.value = tabs.value
      .filter((tab) => tab.menuKey === 'home' || menuMap.has(tab.menuKey))
      .map((tab) => {
        const menu = menuMap.get(tab.menuKey)
        if (!menu) return tab
        return {
          menuKey: tab.menuKey,
          title: menu.menuName,
          pageKey: menu.pageKey ?? tab.pageKey,
        }
      })

    if (!tabs.value.some((tab) => tab.menuKey === 'home')) {
      const home = menuMap.get('home')
      if (home?.pageKey) {
        tabs.value.unshift({ menuKey: 'home', title: home.menuName, pageKey: home.pageKey })
      }
    }

    if (!tabs.value.some((tab) => tab.menuKey === activeMenuKey.value)) {
      activeMenuKey.value = tabs.value[0]?.menuKey ?? 'home'
    }
  }

  async function refreshMenus() {
    const [menuTree, flatMenus] = await Promise.all([api.menuTree(), api.listMenus()])
    state.menuTree = menuTree
    state.flatMenus = flatMenus
    syncTabsWithMenus(flatMenus)
  }

  async function refreshSystemSecurity() {
    const [users, roles] = await Promise.all([
      api.listUsers().catch(() => []),
      api.listRoles().catch(() => []),
    ])
    state.users = users
    state.roles = roles
  }

  async function refreshAll() {
    if (!getAuthToken()) {
      state.authenticated = false
      state.user = null
      state.message = '请先登录'
      return
    }
    state.loading = true
    state.error = ''

    try {
      const [
        user,
        menuTree,
        flatMenus,
        users,
        roles,
        suppliers,
        customers,
        parts,
        equipment,
        locations,
        inboundOrders,
        outboundOrders,
        kanbans,
        inventory,
        transactions,
      ] = await Promise.all([
        api.currentUser(),
        api.menuTree(),
        api.listMenus(),
        api.listUsers().catch(() => []),
        api.listRoles().catch(() => []),
        api.listSuppliers(),
        api.listCustomers(),
        api.listParts(),
        api.listEquipment(),
        api.listLocations(),
        api.listInboundOrders(),
        api.listOutboundOrders(),
        api.listKanbans(),
        api.listInventory(),
        api.listTransactions(),
      ])

      state.user = user
      state.menuTree = menuTree
      state.flatMenus = flatMenus
      state.users = users
      state.roles = roles
      state.suppliers = suppliers
      state.customers = customers
      state.parts = parts
      state.equipment = equipment
      state.locations = locations
      state.inboundOrders = inboundOrders
      state.outboundOrders = outboundOrders
      state.kanbans = kanbans
      state.inventory = inventory
      state.transactions = transactions

      if (tabs.value.length === 0) {
        openLeaf(findLeaf('home') ?? findFirstLeaf(state.menuTree))
      }
      syncTabsWithMenus(flatMenus)
      setNotice('数据已刷新')
    } catch (error) {
      state.error = error instanceof Error ? error.message : '加载失败'
      if (state.error.includes('未登录') || state.error.includes('登录已过期')) {
        clearAuthToken()
        state.authenticated = false
        state.user = null
      }
    } finally {
      state.loading = false
    }
  }

  const actions: AppActions = {
    login: async (payload) => {
      const session = await api.login(payload)
      setAuthToken(session.token)
      state.authenticated = true
      state.user = session.user
      await refreshAll()
      setNotice('登录成功')
    },
    register: async (payload) => {
      const session = await api.register(payload)
      setAuthToken(session.token)
      state.authenticated = true
      state.user = session.user
      await refreshAll()
      setNotice('注册成功')
    },
    refreshAll,
    refreshMenus,
    refreshSystemSecurity,
    createUser: async (payload) => {
      await api.createUser(payload)
      await refreshSystemSecurity()
      setNotice('用户已创建')
    },
    updateUser: async (id, payload) => {
      await api.updateUser(id, payload)
      await refreshSystemSecurity()
      setNotice('用户已更新')
    },
    deleteUser: async (id) => {
      await api.deleteUser(id)
      await refreshSystemSecurity()
      setNotice('用户已删除')
    },
    createRole: async (payload) => {
      await api.createRole(payload)
      await Promise.all([refreshMenus(), refreshSystemSecurity()])
      setNotice('角色已创建')
    },
    updateRole: async (id, payload) => {
      await api.updateRole(id, payload)
      await Promise.all([refreshMenus(), refreshSystemSecurity()])
      setNotice('角色已更新')
    },
    deleteRole: async (id) => {
      await api.deleteRole(id)
      await refreshSystemSecurity()
      setNotice('角色已删除')
    },
    assignRoleMenus: async (roleCode, menuIds) => {
      await api.assignRoleMenus(roleCode, menuIds)
      await Promise.all([refreshMenus(), refreshSystemSecurity()])
      setNotice('角色菜单已分配')
    },
    createSupplier: async (payload) => {
      await api.createSupplier(payload)
      await refreshAll()
      setNotice('供应商已保存')
    },
    createCustomer: async (payload) => {
      await api.createCustomer(payload)
      await refreshAll()
      setNotice('客户已保存')
    },
    createPart: async (payload) => {
      await api.createPart(payload)
      await refreshAll()
      setNotice('零件已保存')
    },
    createEquipment: async (payload) => {
      await api.createEquipment(payload)
      await refreshAll()
      setNotice('器具已保存')
    },
    createLocation: async (payload) => {
      await api.createLocation(payload)
      await refreshAll()
      setNotice('仓库库区已保存')
    },
    createInboundOrder: async (payload) => {
      await api.createInboundOrder(payload)
      await refreshAll()
      setNotice('入库单已创建')
    },
    createOutboundOrder: async (payload) => {
      await api.createOutboundOrder(payload)
      await refreshAll()
      setNotice('出库单已创建')
    },
    manualInventoryEntry: async (payload) => {
      await api.manualInventoryEntry(payload)
      await refreshAll()
      setNotice('手工入账已完成')
    },
    generateKanbans: async (orderId) => {
      await api.generateKanbans(orderId)
      await refreshAll()
      setNotice('看板已生成')
    },
    scanInbound: async (payload) => {
      await api.scanInbound(payload)
      await refreshAll()
      setNotice(`扫码入库成功：${payload.barcode}`)
    },
    scanOutbound: async (payload) => {
      await api.scanOutbound(payload)
      await refreshAll()
      setNotice(`扫码出库成功：${payload.barcode}`)
    },
    transferKanban: async (payload) => {
      await api.transferKanban(payload)
      await refreshAll()
      setNotice(`移库完成：${payload.barcode}`)
    },
    freezeKanban: async (payload) => {
      await api.freezeKanban(payload)
      await refreshAll()
      setNotice(payload.frozen ? `看板已封存：${payload.barcode}` : `看板已解封：${payload.barcode}`)
    },
    repackOutbound: async (payload) => {
      await api.repackOutbound(payload)
      await refreshAll()
      setNotice(`转包出库完成：${payload.barcode}`)
    },
    repackInbound: async (payload) => {
      await api.repackInbound(payload)
      await refreshAll()
      setNotice(`转包入库完成：${payload.barcode}`)
    },
    adjustKanbanBalance: async (payload) => {
      await api.adjustKanbanBalance(payload)
      await refreshAll()
      setNotice(`结余调整完成：${payload.barcode}`)
    },
    createMenu: async (payload) => {
      await api.createMenu(payload)
      await refreshMenus()
      setNotice('菜单已新增')
    },
    updateMenu: async (id, payload) => {
      await api.updateMenu(id, payload)
      await refreshMenus()
      setNotice('菜单已更新')
    },
    deleteMenu: async (id) => {
      await api.deleteMenu(id)
      await refreshMenus()
      setNotice('菜单已删除')
    },
    logout: async () => {
      if (getAuthToken()) {
        await api.logout().catch(() => undefined)
      }
      clearAuthToken()
      state.authenticated = false
      state.user = null
      state.menuTree = []
      state.flatMenus = []
      tabs.value = []
      setNotice('已退出登录')
    },
    setNotice,
  }

  const model: PageModel = { state, actions }

  const activeTab = computed(() => tabs.value.find((tab) => tab.menuKey === activeMenuKey.value) ?? tabs.value[0])
  const title = computed(() => activeTab.value?.title ?? '首页')
  const statusText = computed(() => state.error || state.message)
  const currentPage = computed(() => resolvePage(activeTab.value, state.flatMenus, model))

  onMounted(() => {
    if (getAuthToken()) {
      refreshAll()
    }
  })

  return {
    state,
    tabs,
    activeMenuKey,
    title,
    statusText,
    currentPage,
    actions,
    openLeaf,
    closeTab,
  }
}
