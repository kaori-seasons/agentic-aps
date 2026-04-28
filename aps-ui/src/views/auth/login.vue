<template>
  <div class="login-page">
    <div class="login-aurora login-aurora-left"></div>
    <div class="login-aurora login-aurora-right"></div>
    <div class="login-shell">
      <section class="login-intro">
        <div class="eyebrow">JVS APS</div>
        <h1>让排产系统先正常登录，再谈生产闭环。</h1>
        <p>
          当前版本同时支持两种接入方式：
          独立登录模式用于本地开发与演示，Header 注入模式继续兼容宿主平台集成。
        </p>
        <div class="login-hints">
          <div class="hint-card">
            <span class="hint-label">默认租户</span>
            <strong>demo</strong>
          </div>
          <div class="hint-card">
            <span class="hint-label">默认账号</span>
            <strong>admin</strong>
          </div>
          <div class="hint-card">
            <span class="hint-label">默认密码</span>
            <strong>admin123</strong>
          </div>
        </div>
      </section>
      <section class="login-card">
        <div class="card-header">
          <span class="header-tag">P1 / Auth</span>
          <h2>登录 APS</h2>
          <p>登录成功后会自动拉取 bootstrap、租户信息与个人偏好。</p>
        </div>
        <el-alert
          v-if="commonStore.runtimeError?.message"
          class="runtime-alert"
          type="warning"
          :closable="false"
          :title="`最近一次运行时错误：${commonStore.runtimeError.message}`"
        />
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @keyup.enter="handleSubmit"
        >
          <el-form-item label="租户 ID" prop="tenantId">
            <el-input v-model="form.tenantId" placeholder="demo" />
          </el-form-item>
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="admin" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
          </el-form-item>
          <el-button class="submit-btn" type="primary" :loading="submitting" @click="handleSubmit">
            登录并拉取运行时上下文
          </el-button>
        </el-form>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElNotification, type FormInstance, type FormRules } from 'element-plus'
import { login } from '@/api/auth'
import useCommonStore from '@/store/common'
import { ensureRuntimeBootstrap, getDefaultAuthenticatedRoute } from '@/util/runtimeAuth'

const router = useRouter()
const route = useRoute()
const commonStore = useCommonStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive({
  tenantId: 'demo',
  username: 'admin',
  password: 'admin123',
})

const rules: FormRules = {
  tenantId: [{ required: true, message: '请输入租户 ID', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleSubmit() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    const res = await login(form)
    if (Number(res.status) === 401 || Number(res.data?.code) === 401) {
      throw new Error(res.data?.msg || '用户名或密码错误')
    }
    if (Number(res.data?.code) !== 0) {
      throw new Error(res.data?.msg || '登录失败')
    }
    await ensureRuntimeBootstrap(true)
    ElNotification.success({
      title: '成功',
      message: '运行时上下文已准备完成',
      position: 'bottom-right',
    })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    await router.replace(redirect || getDefaultAuthenticatedRoute())
  } catch (error: any) {
    ElNotification.error({
      title: '登录失败',
      message: error?.message || '请检查账号、密码或后端运行状态',
      position: 'bottom-right',
    })
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-page{
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(30, 111, 255, 0.16), transparent 38%),
    radial-gradient(circle at bottom right, rgba(8, 145, 178, 0.18), transparent 36%),
    linear-gradient(135deg, #f6f9ff 0%, #edf3fb 45%, #f8fbff 100%);
}
.login-aurora{
  position: absolute;
  width: 32rem;
  height: 32rem;
  border-radius: 999px;
  filter: blur(24px);
  opacity: 0.42;
}
.login-aurora-left{
  top: -12rem;
  left: -10rem;
  background: rgba(30, 111, 255, 0.22);
}
.login-aurora-right{
  right: -8rem;
  bottom: -14rem;
  background: rgba(22, 163, 74, 0.16);
}
.login-shell{
  position: relative;
  z-index: 1;
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(320px, 1.1fr) minmax(360px, 460px);
  gap: 36px;
  align-items: center;
  max-width: 1180px;
  margin: 0 auto;
  padding: 40px 24px;
}
.login-intro{
  padding-right: 24px;
  .eyebrow{
    display: inline-flex;
    padding: 8px 14px;
    border-radius: 999px;
    background: rgba(30, 111, 255, 0.1);
    color: #1e6fff;
    font-size: 13px;
    letter-spacing: 0.08em;
  }
  h1{
    margin: 22px 0 18px;
    font-size: clamp(34px, 5vw, 54px);
    line-height: 1.08;
    color: #1f2937;
  }
  p{
    max-width: 640px;
    font-size: 16px;
    line-height: 1.9;
    color: #4b5563;
  }
}
.login-hints{
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 28px;
}
.hint-card{
  padding: 18px 18px 16px;
  border-radius: 18px;
  background: rgba(255,255,255,0.72);
  border: 1px solid rgba(30,111,255,0.08);
  box-shadow: 0 16px 40px rgba(54,59,76,0.06);
  strong{
    display: block;
    margin-top: 8px;
    color: #111827;
    font-size: 22px;
  }
}
.hint-label{
  color: #6b7280;
  font-size: 13px;
}
.login-card{
  padding: 30px 28px 28px;
  border-radius: 24px;
  background: rgba(255,255,255,0.86);
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.1);
  backdrop-filter: blur(10px);
}
.card-header{
  margin-bottom: 18px;
  .header-tag{
    color: #1e6fff;
    font-size: 12px;
    letter-spacing: 0.08em;
  }
  h2{
    margin: 10px 0 8px;
    color: #111827;
    font-size: 28px;
  }
  p{
    margin: 0;
    color: #6b7280;
    line-height: 1.7;
  }
}
.runtime-alert{
  margin-bottom: 16px;
}
.submit-btn{
  width: 100%;
  margin-top: 8px;
  height: 44px;
}
@media (max-width: 960px) {
  .login-shell{
    grid-template-columns: 1fr;
  }
  .login-intro{
    padding-right: 0;
  }
}
@media (max-width: 640px) {
  .login-hints{
    grid-template-columns: 1fr;
  }
}
</style>
