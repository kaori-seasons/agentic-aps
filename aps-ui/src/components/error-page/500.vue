<template>
  <div class="error500">
    <div class="error500-card">
      <div class="code">500</div>
      <div class="title">后端暂时不可用，前端已进入可回退状态</div>
      <div class="desc">
        <p>{{ commonStore.runtimeError?.message || '运行时请求失败' }}</p>
        <ul>
          <li v-if="commonStore.runtimeError?.traceId">链路追踪：{{ commonStore.runtimeError.traceId }}</li>
          <li>接口路径：{{ commonStore.runtimeError?.path || '--' }}</li>
          <li>页面位置：{{ commonStore.runtimeError?.route || '--' }}</li>
          <li>错误时间：{{ commonStore.runtimeError?.occurredAt || '--' }}</li>
        </ul>
      </div>
      <div class="actions">
        <el-button @click="goLogin">重新登录</el-button>
        <el-button @click="retryLastRoute">重试原页面</el-button>
        <el-button type="primary" @click="goHome">返回默认首页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import useCommonStore from '@/store/common'
import { getDefaultAuthenticatedRoute } from '@/util/runtimeAuth'

const router = useRouter()
const commonStore = useCommonStore()

async function retryLastRoute() {
  const route = commonStore.runtimeError?.route
  commonStore.CLEAR_RUNTIME_ERROR()
  await router.replace((typeof route === 'string' && route) || getDefaultAuthenticatedRoute())
}

async function goHome() {
  commonStore.CLEAR_RUNTIME_ERROR()
  await router.replace(getDefaultAuthenticatedRoute())
}

async function goLogin() {
  await router.replace('/login')
}
</script>

<style lang="scss" scoped>
.error500{
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(circle at top, rgba(251, 146, 60, 0.16), transparent 30%),
    linear-gradient(180deg, #fff7ed 0%, #fff 42%, #f8fafc 100%);
}
.error500-card{
  width: min(720px, 100%);
  padding: 42px;
  border-radius: 26px;
  background: rgba(255,255,255,0.92);
  border: 1px solid rgba(251,146,60,0.18);
  box-shadow: 0 28px 80px rgba(15, 23, 42, 0.1);
}
.code{
  font-size: 64px;
  font-weight: 800;
  color: #ea580c;
  line-height: 1;
}
.title{
  margin-top: 16px;
  font-size: 26px;
  font-weight: 700;
  color: #1f2937;
}
.desc{
  margin-top: 16px;
  color: #4b5563;
  line-height: 1.8;
  ul{
    margin: 12px 0 0;
    padding-left: 18px;
  }
}
.actions{
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 28px;
}
</style>
