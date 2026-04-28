<template>
  <div class="user-panel">
    <div class="tenant-pill">
      <span>{{ commonStore.tenantInfo?.systemName || '智能APS' }}</span>
    </div>
    <el-dropdown trigger="click" @command="handleCommand">
      <button class="user-trigger">
        <span class="avatar">{{ initials }}</span>
        <span class="identity">
          <strong>{{ commonStore.userProfile?.displayName || commonStore.userProfile?.username || '未登录' }}</strong>
          <small>{{ commonStore.userProfile?.authSource || 'session' }}</small>
        </span>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="settings">个人设置</el-dropdown-item>
          <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElNotification } from 'element-plus'
import useCommonStore from '@/store/common'
import { logout } from '@/api/auth'
import { clearAuthContext } from '@/util/authContext'
import { clearRuntimeBootstrapState } from '@/util/runtimeAuth'

const router = useRouter()
const commonStore = useCommonStore()

const initials = computed(() => {
  const source = commonStore.userProfile?.displayName || commonStore.userProfile?.username || 'A'
  return source.slice(0, 1).toUpperCase()
})

async function handleCommand(command: string) {
  if (command === 'settings') {
    await router.push('/account/settings')
    return
  }
  if (command !== 'logout') {
    return
  }
  try {
    await logout()
  } catch (error) {
    // logout 接口失败时仍继续清理本地态
  }
  clearRuntimeBootstrapState()
  clearAuthContext()
  ElNotification.success({
    title: '已退出',
    message: '本地上下文已清理',
    position: 'bottom-right',
  })
  await router.replace('/login')
}
</script>

<style lang="scss" scoped>
.user-panel{
  display: flex;
  align-items: center;
  gap: 12px;
}
.tenant-pill{
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: #f4f7fb;
  color: #475569;
  font-size: 12px;
}
.user-trigger{
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 6px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
}
.avatar{
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e6fff 0%, #0ea5e9 100%);
  color: #fff;
  font-weight: 700;
}
.identity{
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  strong{
    color: #1f2937;
    font-size: 14px;
    line-height: 1.2;
  }
  small{
    margin-top: 3px;
    color: #64748b;
    font-size: 12px;
    line-height: 1;
  }
}
</style>
