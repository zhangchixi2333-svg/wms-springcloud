<!-- 本文件实现 TopBar 布局组件。 -->
<script setup lang="ts">
import type { CurrentUser } from '../../types/app'

defineProps<{
  title: string
  user: CurrentUser | null
  statusText: string
}>()

const emit = defineEmits<{
  logout: []
}>()
</script>

<template>
  <header class="topbar">
    <div>
      <h2>{{ title }}</h2>
      <p>{{ statusText }}</p>
    </div>

    <div class="account-panel">
      <div v-if="user" class="account-card">
        <span class="avatar" :style="{ background: user.avatarColor }">{{ user.displayName.slice(0, 1) }}</span>
        <div class="account-text">
          <strong>{{ user.displayName }}</strong>
          <span>{{ user.username }} | {{ user.roleName }}</span>
        </div>
      </div>
      <button class="logout-button" @click="emit('logout')">退出登录</button>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 18px 22px 10px;
}

.topbar h2,
.topbar p {
  margin: 0;
}

.topbar p {
  margin-top: 6px;
  color: var(--muted);
  font-size: 14px;
}

.account-panel {
  display: flex;
  align-items: center;
  gap: 12px;
}

.account-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 700;
}

.account-text {
  display: grid;
}

.account-text span {
  color: var(--muted);
  font-size: 13px;
}

.logout-button {
  background: #14323f;
}

@media (max-width: 760px) {
  .topbar {
    align-items: flex-start;
    gap: 10px;
    padding: 12px 12px 8px;
  }

  .topbar h2 {
    font-size: 18px;
  }

  .topbar p {
    font-size: 12px;
  }

  .account-panel {
    flex-direction: column;
    align-items: stretch;
    gap: 6px;
  }

  .account-card {
    padding: 6px 8px;
  }

  .account-text span {
    display: none;
  }

  .logout-button {
    min-height: 34px;
    padding: 0 10px;
  }
}

@media (max-width: 420px) {
  .topbar {
    display: grid;
    grid-template-columns: 1fr;
  }

  .account-panel {
    flex-direction: row;
  }
}
</style>
