<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { FlatMenu, PageModel } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()

const selectedMenuId = ref<number | null>(null)

const emptyForm = () => ({
  id: 0,
  parentId: null as number | null,
  menuKey: '',
  menuName: '',
  menuType: 'LEAF' as 'LEAF' | 'PARENT',
  pathKey: '',
  pageKey: '',
  iconKey: '',
  sortOrder: nextRootSort(),
  visible: true,
})

const form = reactive(emptyForm())
const selectedRoleCodes = reactive<string[]>([])

const rootMenus = computed(() => props.model.state.flatMenus.filter((item) => item.parentId === null))
const selectedMenu = computed(() => props.model.state.flatMenus.find((item) => item.id === selectedMenuId.value) ?? null)
const childMenus = computed(() =>
  selectedMenu.value
    ? props.model.state.flatMenus.filter((item) => item.parentId === selectedMenu.value?.id)
    : rootMenus.value,
)
const parentMenus = computed(() => props.model.state.flatMenus.filter((item) => item.menuType === 'PARENT' && item.id !== form.id))
const currentTitle = computed(() => selectedMenu.value ? selectedMenu.value.menuName : '顶级菜单')
const pageOptions = [
  { value: '', label: '无页面（父级/目录）' },
  { value: 'home', label: '首页' },
  { value: 'inbound', label: '入库' },
  { value: 'outbound', label: '出库' },
  { value: 'repack', label: '转包' },
  { value: 'repackBalance', label: '转包结余' },
  { value: 'transferFreeze', label: '移库/封存' },
  { value: 'inventoryBoard', label: '库存看板' },
  { value: 'kanbanInfo', label: '看板信息' },
  { value: 'records', label: '出入记录' },
  { value: 'equipmentNormal', label: '普通器具' },
  { value: 'equipmentRepack', label: '转包器具' },
  { value: 'partManagement', label: '零件管理' },
  { value: 'categoryManagement', label: '分类管理' },
  { value: 'supplierManagement', label: '供应商管理' },
  { value: 'customerManagement', label: '客户管理' },
  { value: 'warehouseZone', label: '仓库/库区' },
  { value: 'userManagement', label: '用户管理' },
  { value: 'roleManagement', label: '角色管理' },
  { value: 'menuManagement', label: '菜单管理' },
  { value: 'systemTools', label: '系统工具' },
  { value: 'systemMonitor', label: '系统监控' },
]
const iconOptions = [
  'home',
  'workflow',
  'inbound',
  'outbound',
  'repack',
  'balance',
  'transfer',
  'board',
  'kanban',
  'records',
  'equipment',
  'box',
  'package',
  'parts',
  'category',
  'partner',
  'supplier',
  'customer',
  'warehouse',
  'system',
  'user',
  'role',
  'menu',
  'settings',
  'tools',
  'monitor',
]

function childrenOf(menuId: number) {
  return props.model.state.flatMenus.filter((item) => item.parentId === menuId)
}

function parentName(menu: FlatMenu) {
  if (menu.parentId === null) return '顶级'
  return props.model.state.flatMenus.find((item) => item.id === menu.parentId)?.menuName ?? String(menu.parentId)
}

function nextRootSort() {
  const roots = props?.model?.state?.flatMenus?.filter((item) => item.parentId === null) ?? []
  return nextSort(roots)
}

function nextSort(siblings: FlatMenu[]) {
  if (!siblings.length) return 100
  return Math.max(...siblings.map((item) => Number(item.sortOrder) || 0)) + 10
}

function makeKey(seed: string) {
  return seed
    .trim()
    .replace(/([a-z])([A-Z])/g, '$1-$2')
    .replace(/[^a-zA-Z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .toLowerCase()
}

function setRolesForMenu(menuId: number | null) {
  selectedRoleCodes.splice(
    0,
    selectedRoleCodes.length,
    ...props.model.state.roles.filter((role) => menuId !== null && role.menuIds.includes(menuId)).map((role) => role.roleCode),
  )
}

function selectMenu(menu: FlatMenu | null) {
  selectedMenuId.value = menu?.id ?? null
  resetForm()
}

function startCreateRoot() {
  selectedMenuId.value = null
  Object.assign(form, {
    ...emptyForm(),
    parentId: null,
    menuType: 'PARENT',
    sortOrder: nextSort(rootMenus.value),
  })
  selectedRoleCodes.splice(0, selectedRoleCodes.length, ...adminRoleCodes())
}

function startCreateChild(parent: FlatMenu | null = selectedMenu.value) {
  if (!parent) {
    startCreateRoot()
    return
  }
  const baseKey = makeKey(parent.menuKey || parent.menuName)
  Object.assign(form, {
    ...emptyForm(),
    parentId: parent.id,
    menuKey: `${baseKey}-new`,
    menuName: `${parent.menuName}子菜单`,
    menuType: 'LEAF',
    pathKey: `${baseKey}-new`,
    pageKey: '',
    iconKey: parent.iconKey || '',
    sortOrder: nextSort(childrenOf(parent.id)),
    visible: true,
  })
  selectedRoleCodes.splice(0, selectedRoleCodes.length, ...rolesForMenu(parent.id))
}

function editMenu(menu: FlatMenu) {
  selectedMenuId.value = menu.id
  Object.assign(form, {
    id: menu.id,
    parentId: menu.parentId,
    menuKey: menu.menuKey,
    menuName: menu.menuName,
    menuType: menu.menuType,
    pathKey: menu.pathKey ?? '',
    pageKey: menu.pageKey ?? '',
    iconKey: menu.iconKey ?? '',
    sortOrder: menu.sortOrder,
    visible: menu.visible,
  })
  setRolesForMenu(menu.id)
}

function resetForm() {
  Object.assign(form, emptyForm())
  selectedRoleCodes.splice(0, selectedRoleCodes.length)
}

function rolesForMenu(menuId: number) {
  return props.model.state.roles.filter((role) => role.menuIds.includes(menuId)).map((role) => role.roleCode)
}

function adminRoleCodes() {
  return props.model.state.roles.filter((role) => role.permissionLevel === 'ADMIN').map((role) => role.roleCode)
}

function fillFromName() {
  const key = makeKey(form.menuName || form.menuKey)
  if (!form.menuKey) form.menuKey = key
  if (!form.pathKey) form.pathKey = key
  if (form.menuType === 'LEAF' && !form.pageKey) form.pageKey = key
}

function applyPageOption(value: string) {
  form.pageKey = value
  if (value && !form.pathKey) {
    form.pathKey = makeKey(value)
  }
}

function handlePageOptionChange(event: Event) {
  applyPageOption((event.target as HTMLSelectElement).value)
}

async function submit() {
  fillFromName()
  const payload = {
    parentId: form.parentId,
    menuKey: form.menuKey,
    menuName: form.menuName,
    menuType: form.menuType,
    pathKey: form.pathKey,
    pageKey: form.menuType === 'PARENT' ? '' : form.pageKey,
    iconKey: form.iconKey,
    sortOrder: Number(form.sortOrder),
    visible: form.visible,
  }

  if (form.id) {
    await props.model.actions.updateMenu(form.id, payload)
  } else {
    await props.model.actions.createMenu(payload)
  }
  await syncRoleAssignments(form.menuKey)
  const saved = props.model.state.flatMenus.find((item) => item.menuKey === form.menuKey)
  selectedMenuId.value = saved?.id ?? selectedMenuId.value
  resetForm()
}

async function syncRoleAssignments(menuKey: string) {
  await props.model.actions.refreshMenus()
  await props.model.actions.refreshSystemSecurity()
  const menu = props.model.state.flatMenus.find((item) => item.menuKey === menuKey)
  if (!menu) return
  const selected = new Set(selectedRoleCodes)
  for (const role of props.model.state.roles) {
    const menuIds = new Set(role.menuIds)
    if (selected.has(role.roleCode)) menuIds.add(menu.id)
    else menuIds.delete(menu.id)
    await props.model.actions.assignRoleMenus(role.roleCode, Array.from(menuIds))
  }
}

async function removeMenu(menu: FlatMenu) {
  await props.model.actions.deleteMenu(menu.id)
  if (selectedMenuId.value === menu.id) {
    selectedMenuId.value = menu.parentId
  }
  resetForm()
}
</script>

<template>
  <section class="stack">
    <section class="panel">
      <div class="section-head">
        <div>
          <h3>菜单管理</h3>
          <p>按父子关系查看和维护菜单；先清理子菜单，再删除父菜单。</p>
        </div>
        <div class="actions">
          <button @click="startCreateRoot">新增顶级菜单</button>
          <button class="secondary-button" :disabled="!selectedMenu" @click="startCreateChild()">新增子菜单</button>
        </div>
      </div>

      <div class="menu-workspace">
        <aside class="menu-tree">
          <button class="tree-node" :class="{ active: selectedMenuId === null }" @click="selectMenu(null)">
            顶级菜单
          </button>
          <button
            v-for="menu in rootMenus"
            :key="menu.id"
            class="tree-node"
            :class="{ active: selectedMenuId === menu.id }"
            @click="selectMenu(menu)"
          >
            <span>{{ menu.menuName }}</span>
            <small>{{ childrenOf(menu.id).length }}</small>
          </button>
        </aside>

        <section class="child-list">
          <div class="sub-head">
            <div>
              <strong>{{ currentTitle }}</strong>
              <span class="muted">子节点 / 同级列表</span>
            </div>
            <button class="secondary-button" @click="startCreateChild(selectedMenu)">在此新增</button>
          </div>
          <table class="table">
            <thead>
              <tr>
                <th>名称</th>
                <th>类型</th>
                <th>Key</th>
                <th>页面</th>
                <th>排序</th>
                <th>角色数</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!childMenus.length">
                <td colspan="7">暂无子节点</td>
              </tr>
              <tr v-for="item in childMenus" :key="item.id" :class="{ selected: selectedMenuId === item.id }">
                <td>
                  <button class="link-button" @click="selectMenu(item)">{{ item.menuName }}</button>
                </td>
                <td>{{ item.menuType }}</td>
                <td class="mono">{{ item.menuKey }}</td>
                <td>{{ item.pageKey || '-' }}</td>
                <td>{{ item.sortOrder }}</td>
                <td>{{ rolesForMenu(item.id).length }}</td>
                <td>
                  <div class="actions">
                    <button class="secondary-button" @click="editMenu(item)">编辑</button>
                    <button class="secondary-button" :disabled="item.menuType !== 'PARENT'" @click="startCreateChild(item)">子菜单</button>
                    <button class="danger-button" :disabled="childrenOf(item.id).length > 0" @click="removeMenu(item)">删除</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </section>

        <aside class="menu-detail">
          <div v-if="selectedMenu" class="detail-box">
            <strong>当前节点</strong>
            <p>{{ selectedMenu.menuName }} / {{ selectedMenu.menuKey }}</p>
            <p>父级：{{ parentName(selectedMenu) }}，子节点：{{ childrenOf(selectedMenu.id).length }}</p>
            <div class="actions">
              <button class="secondary-button" @click="editMenu(selectedMenu)">编辑当前</button>
              <button class="secondary-button" :disabled="selectedMenu.menuType !== 'PARENT'" @click="startCreateChild(selectedMenu)">新增子菜单</button>
            </div>
          </div>
          <div v-else class="detail-box">
            <strong>当前节点</strong>
            <p>顶级菜单列表</p>
            <button class="secondary-button" @click="startCreateRoot">新增顶级菜单</button>
          </div>
        </aside>
      </div>
    </section>

    <section class="panel">
      <div class="section-head">
        <div>
          <h3>{{ form.id ? '编辑菜单' : '新增菜单' }}</h3>
          <p>通过上方操作按钮进入表单，会自动带入父级和排序位置。</p>
        </div>
      </div>
      <div class="form-grid menu-form">
        <label class="field-block">
          <span>父级菜单</span>
          <select v-model="form.parentId">
            <option :value="null">顶级菜单</option>
            <option v-for="item in parentMenus" :key="item.id" :value="item.id">
              {{ item.menuName }}
            </option>
          </select>
          <small>决定菜单显示在哪个父节点下面；顶级菜单会直接显示在左侧主菜单。</small>
        </label>

        <label class="field-block">
          <span>菜单唯一 Key</span>
          <input v-model="form.menuKey" placeholder="例如 inbound-report" />
          <small>数据库唯一标识，不能重复；用于标签页、权限分配和前端定位。</small>
        </label>

        <label class="field-block">
          <span>菜单名称</span>
          <input v-model="form.menuName" placeholder="例如 入库报表" @blur="fillFromName" />
          <small>用户在左侧菜单和页签上看到的中文名称。</small>
        </label>

        <label class="field-block">
          <span>菜单类型</span>
          <select v-model="form.menuType">
            <option value="PARENT">PARENT / 父级目录</option>
            <option value="LEAF">LEAF / 可打开页面</option>
          </select>
          <small>父级目录用于分组；叶子菜单会打开一个具体页面。</small>
        </label>

        <label class="field-block">
          <span>路径 Key</span>
          <input v-model="form.pathKey" placeholder="例如 inbound-report" />
          <small>用于内部路径标识，通常与菜单 Key 保持一致即可。</small>
        </label>

        <label class="field-block">
          <span>页面标识 pageKey</span>
          <select :value="form.pageKey" :disabled="form.menuType === 'PARENT'" @change="handlePageOptionChange">
            <option v-for="item in pageOptions" :key="item.value" :value="item.value">
              {{ item.label }}{{ item.value ? ` / ${item.value}` : '' }}
            </option>
          </select>
          <input v-model="form.pageKey" :disabled="form.menuType === 'PARENT'" placeholder="也可填写自定义 pageKey" />
          <small>决定点击菜单后渲染哪个前端页面；已有页面建议从下拉中选择。</small>
        </label>

        <label class="field-block">
          <span>图标标识</span>
          <input v-model="form.iconKey" list="menu-icon-options" placeholder="例如 warehouse" />
          <datalist id="menu-icon-options">
            <option v-for="icon in iconOptions" :key="icon" :value="icon" />
          </datalist>
          <small>左侧菜单的图标名称；可用现有图标标识，也可以先留空。</small>
        </label>

        <label class="field-block">
          <span>排序号</span>
          <input v-model.number="form.sortOrder" type="number" min="0" />
          <small>同级菜单按数字从小到大排序；系统会自动给新节点建议位置。</small>
        </label>

        <label class="field-block switch-field">
          <span>是否显示</span>
          <label class="toggle">
            <input v-model="form.visible" type="checkbox" />
            <span>{{ form.visible ? '显示在菜单中' : '隐藏此菜单' }}</span>
          </label>
          <small>关闭后即使角色有权限，左侧菜单也不会显示。</small>
        </label>
      </div>
      <div class="role-assign">
        <div class="assign-title">
          <strong>分配给角色</strong>
          <small>控制哪些角色登录后能看到这个菜单。</small>
        </div>
        <div class="role-options">
          <label v-for="role in model.state.roles" :key="role.id" class="toggle">
            <input v-model="selectedRoleCodes" type="checkbox" :value="role.roleCode" />
            <span>{{ role.roleName }}</span>
          </label>
        </div>
      </div>
      <div class="button-row">
        <button @click="submit">{{ form.id ? '更新菜单' : '保存新增菜单' }}</button>
        <button class="secondary-button" @click="resetForm">清空表单</button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.menu-workspace {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr) 280px;
  gap: 16px;
}

.menu-tree,
.menu-detail,
.child-list {
  min-width: 0;
}

.menu-tree {
  display: grid;
  gap: 8px;
  align-content: start;
}

.tree-node {
  background: #f8fbfc;
  color: var(--text);
  border: 1px solid var(--border);
  justify-content: space-between;
  display: flex;
  width: 100%;
}

.tree-node.active,
.table tr.selected {
  background: #eaf5f3;
}

.tree-node small {
  color: var(--muted);
}

.sub-head,
.button-row,
.actions,
.role-assign,
.toggle {
  display: flex;
  gap: 10px;
  align-items: center;
}

.sub-head {
  justify-content: space-between;
  margin-bottom: 10px;
}

.sub-head div {
  display: grid;
  gap: 4px;
}

.button-row {
  margin-top: 16px;
}

.actions {
  flex-wrap: wrap;
}

.detail-box {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px;
  background: #f8fbfc;
  display: grid;
  gap: 8px;
}

.detail-box p {
  margin: 0;
  color: var(--muted);
}

.link-button {
  padding: 0;
  min-height: 0;
  background: transparent;
  color: var(--accent);
  border: 0;
}

.menu-form {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.field-block {
  display: grid;
  gap: 6px;
  align-content: start;
}

.field-block > span {
  font-weight: 700;
  color: var(--text);
}

.field-block small,
.assign-title small {
  color: var(--muted);
  line-height: 1.45;
}

.field-block input,
.field-block select {
  width: 100%;
}

.switch-field {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px;
  background: #f8fbfc;
}

.toggle input {
  width: 18px;
  min-height: 18px;
}

.role-assign {
  align-items: flex-start;
  gap: 14px;
  margin-top: 14px;
}

.assign-title {
  display: grid;
  gap: 4px;
  min-width: 160px;
}

.role-options {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}

@media (max-width: 1180px) {
  .menu-workspace,
  .menu-form {
    grid-template-columns: 1fr;
  }
}
</style>
