<!-- 本文件实现 RoleManagementPage 页面组件。 -->
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import PageModal from '../../shared/PageModal.vue'
import type { FlatMenu, PageModel, SystemRole } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()
const editorOpen = ref(false)

const form = reactive({
  id: 0,
  roleCode: '',
  roleName: '',
  permissionLevel: 'VIEWER',
  description: '',
  enabled: true,
  menuIds: [] as number[],
})

const rootMenus = computed(() => props.model.state.flatMenus.filter((menu) => menu.parentId === null))

function childrenOf(menu: FlatMenu) {
  return props.model.state.flatMenus.filter((item) => item.parentId === menu.id)
}

function resetForm() {
  form.id = 0
  form.roleCode = ''
  form.roleName = ''
  form.permissionLevel = 'VIEWER'
  form.description = ''
  form.enabled = true
  form.menuIds = []
}

function openCreateRole() {
  resetForm()
  editorOpen.value = true
}

function closeEditor() {
  editorOpen.value = false
  resetForm()
}

function editRole(role: SystemRole) {
  form.id = role.id
  form.roleCode = role.roleCode
  form.roleName = role.roleName
  form.permissionLevel = role.permissionLevel
  form.description = role.description
  form.enabled = role.enabled
  form.menuIds = [...role.menuIds]
  editorOpen.value = true
}

function toggleMenu(menuId: number, checked: boolean) {
  const next = new Set(form.menuIds)
  if (checked) next.add(menuId)
  else next.delete(menuId)
  form.menuIds = Array.from(next)
}

function handleMenuToggle(menuId: number, event: Event) {
  toggleMenu(menuId, (event.target as HTMLInputElement).checked)
}

function isChecked(menuId: number) {
  return form.menuIds.includes(menuId)
}

function selectAllMenus() {
  form.menuIds = props.model.state.flatMenus.map((menu) => menu.id)
}

function clearMenus() {
  form.menuIds = []
}

async function submit() {
  const payload = {
    roleCode: form.roleCode,
    roleName: form.roleName,
    permissionLevel: form.permissionLevel,
    description: form.description,
    enabled: form.enabled,
    menuIds: form.menuIds,
  }
  if (form.id) {
    await props.model.actions.updateRole(form.id, payload)
  } else {
    await props.model.actions.createRole(payload)
  }
  closeEditor()
}

async function removeRole(id: number) {
  await props.model.actions.deleteRole(id)
  if (form.id === id) resetForm()
}
</script>

<template>
  <section class="stack">
    <PageModal :open="editorOpen" wide @close="closeEditor">
      <section class="panel">
      <div class="section-head">
        <div>
          <h3>角色管理</h3>
          <p>角色菜单保存后，会决定该角色用户登录后看到的左侧菜单。</p>
        </div>
      </div>

      <div class="form-grid five">
        <input v-model="form.roleCode" placeholder="角色编码，如 QUALITY_VIEWER" />
        <input v-model="form.roleName" placeholder="角色名称" />
        <select v-model="form.permissionLevel">
          <option value="ADMIN">ADMIN</option>
          <option value="MANAGER">MANAGER</option>
          <option value="OPERATOR">OPERATOR</option>
          <option value="VIEWER">VIEWER</option>
        </select>
        <input v-model="form.description" placeholder="角色说明" />
        <label class="toggle">
          <input v-model="form.enabled" type="checkbox" />
          <span>启用</span>
        </label>
      </div>

      <div class="menu-picker">
        <div class="picker-head">
          <strong>菜单授权</strong>
          <div class="actions">
            <button class="secondary-button" @click="selectAllMenus">全选</button>
            <button class="secondary-button" @click="clearMenus">清空</button>
          </div>
        </div>
        <div class="menu-groups">
          <div v-for="menu in rootMenus" :key="menu.id" class="menu-group">
            <label class="check-line">
              <input type="checkbox" :checked="isChecked(menu.id)" @change="handleMenuToggle(menu.id, $event)" />
              <span>{{ menu.menuName }}</span>
              <small>{{ menu.menuKey }}</small>
            </label>
            <label v-for="child in childrenOf(menu)" :key="child.id" class="check-line child">
              <input type="checkbox" :checked="isChecked(child.id)" @change="handleMenuToggle(child.id, $event)" />
              <span>{{ child.menuName }}</span>
              <small>{{ child.menuKey }}</small>
            </label>
          </div>
        </div>
      </div>

      <div class="button-row">
        <button @click="submit">{{ form.id ? '更新角色' : '新增角色' }}</button>
        <button class="secondary-button" @click="closeEditor">返回列表</button>
      </div>
      </section>
    </PageModal>

    <section class="panel">
      <div class="section-head">
        <div>
          <h3>角色列表</h3>
          <p>新增或编辑角色会在当前页面浮窗中完成。</p>
        </div>
        <div class="actions">
          <button @click="openCreateRole">新增角色</button>
        </div>
      </div>
      <table class="table">
        <thead>
          <tr>
            <th>角色编码</th>
            <th>角色名称</th>
            <th>权限级别</th>
            <th>状态</th>
            <th>菜单数</th>
            <th>说明</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="role in model.state.roles" :key="role.id">
            <td class="mono">{{ role.roleCode }}</td>
            <td>{{ role.roleName }}</td>
            <td>{{ role.permissionLevel }}</td>
            <td>{{ role.enabled ? '启用' : '停用' }}</td>
            <td>{{ role.menuIds.length }}</td>
            <td>{{ role.description }}</td>
            <td>
              <div class="actions">
                <button class="secondary-button" @click="editRole(role)">编辑</button>
                <button class="danger-button" @click="removeRole(role.id)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<style scoped>
.button-row,
.actions,
.picker-head {
  display: flex;
  gap: 10px;
  align-items: center;
}

.button-row {
  margin-top: 14px;
}

.picker-head {
  justify-content: space-between;
}

.toggle,
.check-line {
  display: flex;
  gap: 8px;
  align-items: center;
}

.toggle input,
.check-line input {
  width: 18px;
  min-height: 18px;
}

.menu-picker {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}

.menu-groups {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.menu-group {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px;
  display: grid;
  gap: 8px;
}

.check-line.child {
  padding-left: 18px;
}

.check-line small {
  color: var(--muted);
}

@media (max-width: 1180px) {
  .menu-groups {
    grid-template-columns: 1fr;
  }
}
</style>
