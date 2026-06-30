/**
 * 本文件实现前端应用模块 useWorkspaceApp。
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { api } from '../api/wms'
import { ApiRequestError, clearAuthToken, getAuthToken, setAuthToken } from '../api/client'
import { resolvePage } from './pageRegistry'
import type { AppActions, AppState, FlatMenu, Kanban, MenuNode, PageModel, TransactionVersion, WorkspaceTab } from '../types/app'

export function useWorkspaceApp() {
  /*
   * state 是整个工作区的核心状态容器：
   * 1. 认证态决定显示登录页还是工作台。
   * 2. menuTree / flatMenus 决定左侧菜单、标签页和页面路由来源。
   * 3. users/roles/.../transactions 保存各业务页面共用的数据快照。
   * 4. message / error / loading 为顶部状态提示和加载态提供统一入口。
   */
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
  const BUSINESS_SYNC_INTERVAL_MS = 6000
  let businessSyncTimer: number | undefined
  let lastTransactionSignature = ''

  function setNotice(message: string) {
    state.message = message
    state.error = ''
  }

  function mergeKanbans(kanbans: Kanban[]) {
    const existing = new Map(state.kanbans.map((item) => [item.id, item]))
    kanbans.forEach((item) => {
      existing.set(item.id, {
        ...(existing.get(item.id) ?? {}),
        ...item,
      })
    })
    state.kanbans = Array.from(existing.values())
  }

  function isLoginExpired(error: unknown) {
    return error instanceof ApiRequestError && error.status === 401
  }

  function clearSessionState(message = '登录已过期，请重新登录') {
    clearAuthToken()
    state.authenticated = false
    state.user = null
    state.menuTree = []
    state.flatMenus = []
    tabs.value = []
    state.error = message
  }

  function toErrorMessage(error: unknown, fallback: string) {
    return error instanceof Error ? error.message : fallback
  }

  function handleRefreshError(error: unknown, fallback: string, silent = false) {
    if (isLoginExpired(error)) {
      clearSessionState()
      return
    }
    if (silent) {
      console.warn(fallback, error)
      return
    }
    state.error = toErrorMessage(error, fallback)
  }

  /*
   * 菜单来自后端树结构。
   * findFirstLeaf 用来在首次进入系统时找到默认可打开的叶子菜单；
   * findLeaf 用来按 menuKey 在当前菜单树里定位具体节点。
   */
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

  /*
   * openLeaf / closeTab 负责维护“当前打开了哪些标签页”。
   * tabs 保存已打开页签集合，activeMenuKey 指向当前激活页签；
   * 因此左侧菜单点击、页签切换、页签关闭最终都会收敛到这两个状态。
   */
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

  /*
   * 后端菜单可能被角色、权限或菜单管理操作改变。
   * syncTabsWithMenus 会把“已有页签”和“最新菜单定义”重新对齐：
   * 1. 菜单已删除的页签会被移除；
   * 2. 菜单名称 / pageKey 变化后同步更新页签显示；
   * 3. 保证首页页签始终存在；
   * 4. 如果当前激活页签失效，就回退到第一个可用页签。
   */
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

  /*
   * 用户、角色、菜单是系统管理侧最常变化的数据。
   * 单独拆出 refreshSystemSecurity，可以在新增/修改用户角色后局部刷新，
   * 避免每次都把整套业务数据重新加载一遍。
   */
  async function refreshSystemSecurity() {
    const [users, roles] = await Promise.all([
      api.listUsers().catch(() => []),
      api.listRoles().catch(() => []),
    ])
    state.users = users
    state.roles = roles
  }

  function transactionVersionSignature(version: TransactionVersion) {
    return `${version.latestId ?? ''}:${version.latestCreatedAt ?? ''}:${version.latestTransactionNo ?? ''}`
  }

  async function refreshReferenceData(silent = false) {
    try {
      const [suppliers, customers, parts, equipment, locations] = await Promise.all([
        api.listSuppliers(),
        api.listCustomers(),
        api.listParts(),
        api.listEquipment(),
        api.listLocations(),
      ])
      state.suppliers = suppliers
      state.customers = customers
      state.parts = parts
      state.equipment = equipment
      state.locations = locations
      if (!silent) setNotice('基础数据已刷新')
    } catch (error) {
      handleRefreshError(error, '基础数据刷新失败', silent)
      if (!silent) throw error
    }
  }

  function refreshBusinessDataInBackground() {
    if (typeof window === 'undefined') return
    window.dispatchEvent(new CustomEvent('wms-business-changed'))
  }

  async function pollBusinessChanges() {
    if (!getAuthToken() || !state.authenticated || state.loading) return
    if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return
    try {
      const version = await api.transactionVersion()
      const nextSignature = transactionVersionSignature(version)
      if (nextSignature !== lastTransactionSignature) {
        lastTransactionSignature = nextSignature
        refreshBusinessDataInBackground()
      }
    } catch (error) {
      handleRefreshError(error, '自动同步业务数据失败', true)
    }
  }

  function startBusinessAutoSync() {
    if (businessSyncTimer !== undefined || typeof window === 'undefined') return
    businessSyncTimer = window.setInterval(() => {
      void pollBusinessChanges()
    }, BUSINESS_SYNC_INTERVAL_MS)
  }

  function stopBusinessAutoSync() {
    if (businessSyncTimer === undefined || typeof window === 'undefined') return
    window.clearInterval(businessSyncTimer)
    businessSyncTimer = undefined
  }

  /*
   * refreshAll 是整个工作区的总入口：
   * 1. 先根据 token 判断是否已登录；
   * 2. 并发拉取用户信息、菜单、系统数据和业务数据；
   * 3. 用接口结果一次性回填到 state；
   * 4. 首次进入时自动打开首页；
   * 5. 统一处理过期登录、错误提示和 loading 收尾。
   *
   * 这个函数执行完以后，页面显示所需的大部分状态都已经就绪。
   */
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
      state.inboundOrders = []
      state.outboundOrders = []
      state.kanbans = []
      state.inventory = []
      state.transactions = []
      lastTransactionSignature = ''

      if (tabs.value.length === 0) {
        openLeaf(findLeaf('home') ?? findFirstLeaf(state.menuTree))
      }
      syncTabsWithMenus(flatMenus)
      setNotice('数据已刷新')
    } catch (error) {
      handleRefreshError(error, '加载失败')
    } finally {
      state.loading = false
    }
  }

  /*
   * actions 是页面层唯一直接调用的动作集合。
   * 每个页面并不自己拼接口和状态回填，而是通过 actions：
   * 1. 调接口完成增删改查或业务动作；
   * 2. 按需调用 refreshAll / refreshMenus / refreshSystemSecurity；
   * 3. 最后写入统一提示信息。
   *
   * 这样页面组件主要负责表单和展示，状态收口在这里统一维护。
   */
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
      await refreshReferenceData()
      setNotice('供应商已保存')
    },
    deleteSupplier: async (id) => {
      await api.deleteSupplier(id)
      await refreshReferenceData()
      setNotice('供应商已删除')
    },
    createCustomer: async (payload) => {
      await api.createCustomer(payload)
      await refreshReferenceData()
      setNotice('客户已保存')
    },
    deleteCustomer: async (id) => {
      await api.deleteCustomer(id)
      await refreshReferenceData()
      setNotice('客户已删除')
    },
    createPart: async (payload) => {
      await api.createPart(payload)
      await refreshReferenceData()
      setNotice('零件已保存')
    },
    deletePart: async (id) => {
      await api.deletePart(id)
      await refreshReferenceData()
      setNotice('零件已删除')
    },
    createEquipment: async (payload) => {
      await api.createEquipment(payload)
      await refreshReferenceData()
      setNotice('器具已保存')
    },
    createLocation: async (payload) => {
      await api.createLocation(payload)
      await refreshReferenceData()
      setNotice('仓库库区已保存')
    },
    deleteLocation: async (id) => {
      await api.deleteLocation(id)
      await refreshReferenceData()
      setNotice('仓库库区已删除')
    },
    createInboundOrder: async (payload) => {
      const order = await api.createInboundOrder(payload)
      state.inboundOrders = [order, ...state.inboundOrders.filter((item) => item.id !== order.id)]
      const kanbans = await api.listKanbansPage({ inboundNo: order.inboundNo, page: 1, size: 500 })
      mergeKanbans(kanbans.records)
      refreshBusinessDataInBackground()
      setNotice('入库单已创建')
    },
    returnInboundOrder: async (orderId) => {
      await api.returnInboundOrder(orderId)
      refreshBusinessDataInBackground()
      setNotice('入库单已退回')
    },
    createOutboundOrder: async (payload) => {
      await api.createOutboundOrder(payload)
      refreshBusinessDataInBackground()
      setNotice('出库单已创建')
    },
    cancelOutboundOrder: async (orderId) => {
      await api.cancelOutboundOrder(orderId)
      refreshBusinessDataInBackground()
      setNotice('出库单已取消，锁定库存已释放')
    },
    manualInventoryEntry: async (payload) => {
      await api.manualInventoryEntry(payload)
      refreshBusinessDataInBackground()
      setNotice('手工入账已完成')
    },
    generateKanbans: async (orderId) => {
      await api.generateKanbans(orderId)
      refreshBusinessDataInBackground()
      setNotice('看板已生成')
    },
    scanInbound: async (payload) => {
      const result = await api.scanInbound(payload)
      refreshBusinessDataInBackground()
      setNotice(result.message || `扫码入库成功：${payload.barcode}`)
      return result
    },
    scanInboundBatch: async (payload) => {
      const result = await api.scanInboundBatch(payload)
      refreshBusinessDataInBackground()
      setNotice(result.message || `扫码入库成功：${payload.scanCode}`)
      return result
    },
    scanOutbound: async (payload) => {
      const result = await api.scanOutbound(payload)
      refreshBusinessDataInBackground()
      setNotice(result.message || `扫码出库成功：${payload.barcode}`)
      return result
    },
    transferKanban: async (payload) => {
      const kanban = await api.transferKanban(payload)
      mergeKanbans([kanban])
      refreshBusinessDataInBackground()
      setNotice(`移库完成：${payload.barcode}`)
    },
    transferKanbans: async (payload) => {
      const kanbans = await api.transferKanbans(payload)
      mergeKanbans(kanbans)
      refreshBusinessDataInBackground()
      setNotice(`批量移库完成：${kanbans.length} 个看板`)
    },
    freezeKanban: async (payload) => {
      const kanban = await api.freezeKanban(payload)
      mergeKanbans([kanban])
      refreshBusinessDataInBackground()
      setNotice(payload.frozen ? `看板已封存：${payload.barcode}` : `看板已解封：${payload.barcode}`)
    },
    freezeKanbans: async (payload) => {
      const kanbans = await api.freezeKanbans(payload)
      mergeKanbans(kanbans)
      refreshBusinessDataInBackground()
      setNotice(payload.frozen ? `批量封存完成：${kanbans.length} 个看板` : `批量解封完成：${kanbans.length} 个看板`)
    },
    repackOutbound: async (payload) => {
      const kanban = await api.repackOutbound(payload)
      mergeKanbans([kanban])
      refreshBusinessDataInBackground()
      setNotice(`转包到第三方完成：${payload.barcode}`)
    },
    repackOutboundBatch: async (payload) => {
      const kanbans = await api.repackOutboundBatch(payload)
      mergeKanbans(kanbans)
      refreshBusinessDataInBackground()
      setNotice(`批量转包完成：${kanbans.length} 个看板`)
    },
    repackInbound: async (payload) => {
      const kanban = await api.repackInbound(payload)
      mergeKanbans([kanban])
      refreshBusinessDataInBackground()
      setNotice(`第三方返还完成：${payload.barcode}`)
    },
    repackInboundBatch: async (payload) => {
      const kanbans = await api.repackInboundBatch(payload)
      mergeKanbans(kanbans)
      refreshBusinessDataInBackground()
      setNotice(`批量返还完成：${kanbans.length} 个看板`)
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
      lastTransactionSignature = ''
      setNotice('已退出登录')
    },
    setNotice,
  }

  const model: PageModel = { state, actions }

  /*
   * 这几个 computed 把底层状态转换成界面真正使用的“派生状态”：
   * 1. activeTab：当前激活页签对象；
   * 2. title：顶部标题；
   * 3. statusText：优先显示错误，否则显示普通提示；
   * 4. currentPage：把当前页签映射成真正要渲染的 Vue 页面组件。
   */
  const activeTab = computed(() => tabs.value.find((tab) => tab.menuKey === activeMenuKey.value) ?? tabs.value[0])
  const title = computed(() => activeTab.value?.title ?? '首页')
  const statusText = computed(() => state.error || state.message)
  const currentPage = computed(() => resolvePage(activeTab.value, state.flatMenus, model))

  // 组件挂载后，如果本地还保留 token，就自动恢复一次完整工作区状态。
  onMounted(() => {
    startBusinessAutoSync()
    if (getAuthToken()) {
      refreshAll()
    }
  })

  onBeforeUnmount(() => {
    stopBusinessAutoSync()
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
