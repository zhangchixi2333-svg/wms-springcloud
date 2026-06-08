<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { AppActions } from '../../types/app'

const props = defineProps<{ actions: AppActions; message: string }>()

const mode = ref<'login' | 'register'>('login')
const form = reactive({
  username: 'admin',
  password: 'admin123',
  displayName: '',
  roleName: 'WAREHOUSE_OPERATOR',
})
const busy = ref(false)
const error = ref('')

async function submit() {
  busy.value = true
  error.value = ''
  try {
    if (mode.value === 'login') {
      await props.actions.login({ username: form.username, password: form.password })
    } else {
      await props.actions.register({
        username: form.username,
        password: form.password,
        displayName: form.displayName || form.username,
        roleName: form.roleName,
      })
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '操作失败'
  } finally {
    busy.value = false
  }
}

function switchMode(nextMode: 'login' | 'register') {
  mode.value = nextMode
  error.value = ''
  if (nextMode === 'login') {
    form.username = 'admin'
    form.password = 'admin123'
  } else {
    form.username = ''
    form.password = ''
    form.displayName = ''
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-panel">
      <div class="auth-head">
        <p>WMS Admin</p>
        <h1>仓储管理系统</h1>
      </div>

      <div class="segmented">
        <button :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</button>
        <button :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</button>
      </div>

      <form class="auth-form" @submit.prevent="submit">
        <input v-model="form.username" autocomplete="username" placeholder="用户名" />
        <input v-model="form.password" autocomplete="current-password" type="password" placeholder="密码" />
        <input v-if="mode === 'register'" v-model="form.displayName" placeholder="显示名称" />
        <select v-if="mode === 'register'" v-model="form.roleName">
          <option value="WAREHOUSE_OPERATOR">仓库操作员</option>
          <option value="WAREHOUSE_MANAGER">仓库主管</option>
          <option value="VIEWER">只读查看</option>
        </select>
        <button :disabled="busy">{{ mode === 'login' ? '登录系统' : '创建账号' }}</button>
      </form>

      <p class="auth-message" :class="{ danger: error }">{{ error || message || '默认管理员：admin / admin123' }}</p>
    </section>
  </main>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    linear-gradient(180deg, rgba(15, 118, 110, 0.08), transparent 28%),
    var(--bg);
}

.auth-panel {
  width: min(420px, 100%);
  display: grid;
  gap: 18px;
  padding: 24px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
}

.auth-head p,
.auth-head h1,
.auth-message {
  margin: 0;
}

.auth-head p {
  color: var(--accent);
  font-weight: 700;
}

.auth-head h1 {
  margin-top: 6px;
  font-size: 28px;
}

.auth-form {
  display: grid;
  gap: 12px;
}

.auth-message {
  color: var(--muted);
  font-size: 13px;
}

.auth-message.danger {
  color: var(--danger);
}
</style>
