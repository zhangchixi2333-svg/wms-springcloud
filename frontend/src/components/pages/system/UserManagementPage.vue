<!-- 本文件实现 UserManagementPage 页面组件。 -->
<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import PageModal from '../../shared/PageModal.vue'
import type { PageModel, SystemUser } from '../../../types/app'

const props = defineProps<{ model: PageModel }>()
const editorOpen = ref(false)

const form = reactive({
  id: 0,
  username: '',
  password: '',
  displayName: '',
  roleName: '',
  avatarColor: '',
})

const roleOptions = computed(() => props.model.state.roles.filter((role) => role.enabled))

function ensureRoleSelected() {
  if (!form.roleName && roleOptions.value.length > 0) {
    form.roleName = roleOptions.value[0].roleCode
  }
}

function resetForm() {
  form.id = 0
  form.username = ''
  form.password = ''
  form.displayName = ''
  form.roleName = roleOptions.value[0]?.roleCode ?? ''
  form.avatarColor = ''
}

function openCreateUser() {
  resetForm()
  editorOpen.value = true
}

function closeEditor() {
  editorOpen.value = false
  resetForm()
}

function editUser(user: SystemUser) {
  form.id = user.id
  form.username = user.username
  form.password = ''
  form.displayName = user.displayName
  form.roleName = user.roleName
  form.avatarColor = user.avatarColor
  editorOpen.value = true
}

async function submit() {
  const payload = {
    username: form.username,
    password: form.password || undefined,
    displayName: form.displayName,
    roleName: form.roleName,
    avatarColor: form.avatarColor || undefined,
  }
  if (form.id) {
    await props.model.actions.updateUser(form.id, payload)
  } else {
    await props.model.actions.createUser(payload)
  }
  closeEditor()
}

async function removeUser(id: number) {
  await props.model.actions.deleteUser(id)
  if (form.id === id) resetForm()
}

onMounted(async () => {
  await props.model.actions.refreshSystemSecurity()
  ensureRoleSelected()
})

watch(roleOptions, ensureRoleSelected, { immediate: true })
</script>

<template>
  <section class="stack">
    <PageModal :open="editorOpen" @close="closeEditor">
      <section class="panel">
      <div class="section-head">
        <div>
          <h3>用户管理</h3>
          <p>用户保存后会直接影响登录账号和角色归属。</p>
        </div>
      </div>

      <div class="form-grid five">
        <input v-model="form.username" placeholder="用户名" />
        <input v-model="form.displayName" placeholder="显示名称" />
        <input v-model="form.password" type="password" :placeholder="form.id ? '留空不改密码' : '登录密码'" />
        <select v-model="form.roleName">
          <option value="" disabled>选择角色</option>
          <option v-for="role in roleOptions" :key="role.id" :value="role.roleCode">
            {{ role.roleCode }} | {{ role.roleName }}
          </option>
        </select>
        <input v-model="form.avatarColor" placeholder="头像颜色，如 #2563eb" />
      </div>
      <p v-if="!roleOptions.length" class="muted role-empty">暂无可用角色，请先在角色管理中创建并启用角色。</p>
      <div class="button-row">
        <button @click="submit">{{ form.id ? '更新用户' : '新增用户' }}</button>
        <button class="secondary-button" @click="closeEditor">返回列表</button>
      </div>
      </section>
    </PageModal>

    <section class="panel">
      <div class="section-head">
        <div>
          <h3>用户列表</h3>
          <p>新增或编辑用户会在当前页面浮窗中完成。</p>
        </div>
        <div class="actions">
          <button @click="openCreateUser">新增用户</button>
        </div>
      </div>
      <table class="table">
        <thead>
          <tr>
            <th>用户名</th>
            <th>显示名称</th>
            <th>角色</th>
            <th>头像色</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in model.state.users" :key="user.id">
            <td class="mono">{{ user.username }}</td>
            <td>{{ user.displayName }}</td>
            <td>{{ user.roleDisplayName }} / {{ user.roleName }}</td>
            <td><span class="color-dot" :style="{ background: user.avatarColor }" /> {{ user.avatarColor }}</td>
            <td>
              <div class="actions">
                <button class="secondary-button" @click="editUser(user)">编辑</button>
                <button class="danger-button" @click="removeUser(user.id)">删除</button>
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
.actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
}

.actions {
  margin-top: 0;
}

.color-dot {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  vertical-align: middle;
  margin-right: 6px;
}
</style>
