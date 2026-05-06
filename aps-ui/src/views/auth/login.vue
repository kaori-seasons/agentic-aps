<template>
  <div class="login-page">
    <div class="factory-grid"></div>
    <div class="factory-glow factory-glow-left"></div>
    <div class="factory-glow factory-glow-right"></div>
    <div class="login-shell">
      <section class="login-intro">
        <div class="eyebrow">JVS APS</div>
        <h1>让排产更智能，让计划更方便。</h1>
        <p>
          统一处理租户与账号上下文，进入后自动准备排产所需能力，
          用更少步骤完成任务编排、计划确认和现场协同。
        </p>
        <div class="login-hints">
          <div class="hint-card">
            <span class="hint-label">演示租户</span>
            <strong>demo</strong>
          </div>
          <div class="hint-card">
            <span class="hint-label">演示账号</span>
            <strong>admin</strong>
          </div>
          <div class="hint-card">
            <span class="hint-label">演示口令</span>
            <strong>admin123</strong>
          </div>
        </div>
        <div class="gantt-preview" aria-hidden="true">
          <div class="gantt-row">
            <span class="machine">冲压产线 A1</span>
            <div class="bar-track">
              <span class="bar bar-a"></span>
              <span class="bar bar-b"></span>
            </div>
          </div>
          <div class="gantt-row">
            <span class="machine">焊接产线 B2</span>
            <div class="bar-track">
              <span class="bar bar-c"></span>
              <span class="bar bar-d"></span>
            </div>
          </div>
          <div class="gantt-row">
            <span class="machine">装配产线 C1</span>
            <div class="bar-track">
              <span class="bar bar-e"></span>
              <span class="bar bar-f"></span>
            </div>
          </div>
        </div>
      </section>
      <section class="login-card">
        <div class="card-header">
          <span class="header-tag">Smart Planning</span>
          <h2>开始排产协同</h2>
          <p>提交后自动加载运行环境、租户信息与个人偏好设置。</p>
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
            进入系统并准备上下文
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
      throw new Error(res.data?.msg || '账号信息不正确')
    }
    if (Number(res.data?.code) !== 0) {
      throw new Error(res.data?.msg || '进入失败')
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
      title: '进入失败',
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
  background: linear-gradient(135deg, #0b1620 0%, #101f2c 44%, #152a3b 100%);
}
.factory-grid{
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(117, 149, 177, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(117, 149, 177, 0.08) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: radial-gradient(circle at 25% 10%, #000 40%, transparent 85%);
}
.factory-glow{
  position: absolute;
  width: 34rem;
  height: 34rem;
  border-radius: 999px;
  filter: blur(42px);
  opacity: 0.3;
}
.factory-glow-left{
  top: -12rem;
  left: -10rem;
  background: rgba(29, 78, 216, 0.42);
}
.factory-glow-right{
  right: -8rem;
  bottom: -14rem;
  background: rgba(56, 189, 248, 0.28);
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
    background: rgba(30, 64, 175, 0.28);
    color: #bfdbfe;
    font-size: 13px;
    letter-spacing: 0.08em;
    border: 1px solid rgba(147, 197, 253, 0.24);
  }
  h1{
    margin: 22px 0 18px;
    font-size: clamp(34px, 5vw, 54px);
    line-height: 1.08;
    color: #eff6ff;
  }
  p{
    max-width: 640px;
    font-size: 16px;
    line-height: 1.9;
    color: #cbd5e1;
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
  background: rgba(15, 23, 42, 0.45);
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: 0 14px 30px rgba(2, 6, 23, 0.3);
  strong{
    display: block;
    margin-top: 8px;
    color: #f8fafc;
    font-size: 22px;
    letter-spacing: 0.04em;
  }
}
.hint-label{
  color: #94a3b8;
  font-size: 13px;
}
.gantt-preview{
  margin-top: 22px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: rgba(15, 23, 42, 0.42);
}
.gantt-row{
  display: grid;
  grid-template-columns: 106px 1fr;
  align-items: center;
  gap: 12px;
  & + .gantt-row{
    margin-top: 10px;
  }
}
.machine{
  color: #bfd4e7;
  font-size: 12px;
}
.bar-track{
  position: relative;
  height: 18px;
  border-radius: 10px;
  background:
    repeating-linear-gradient(
      90deg,
      rgba(148, 163, 184, 0.08) 0px,
      rgba(148, 163, 184, 0.08) 18px,
      transparent 18px,
      transparent 24px
    );
}
.bar{
  position: absolute;
  top: 3px;
  height: 12px;
  border-radius: 999px;
  box-shadow: 0 4px 10px rgba(2, 132, 199, 0.4);
}
.bar-a{ left: 5%; width: 31%; background: linear-gradient(90deg, #38bdf8, #0ea5e9); }
.bar-b{ left: 42%; width: 46%; background: linear-gradient(90deg, #22d3ee, #14b8a6); }
.bar-c{ left: 2%; width: 24%; background: linear-gradient(90deg, #60a5fa, #3b82f6); }
.bar-d{ left: 32%; width: 52%; background: linear-gradient(90deg, #2dd4bf, #10b981); }
.bar-e{ left: 12%; width: 20%; background: linear-gradient(90deg, #38bdf8, #3b82f6); }
.bar-f{ left: 36%; width: 56%; background: linear-gradient(90deg, #06b6d4, #0ea5e9); }
.login-card{
  padding: 30px 28px 28px;
  border-radius: 24px;
  background: rgba(248, 250, 252, 0.96);
  border: 1px solid rgba(148, 163, 184, 0.28);
  box-shadow: 0 24px 80px rgba(3, 7, 18, 0.42);
  backdrop-filter: blur(12px);
}
.card-header{
  margin-bottom: 18px;
  .header-tag{
    color: #1d4ed8;
    font-size: 12px;
    letter-spacing: 0.08em;
    font-weight: 600;
  }
  h2{
    margin: 10px 0 8px;
    color: #0f172a;
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
  border: none;
  background: linear-gradient(90deg, #1d4ed8, #0284c7);
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
  .gantt-row{
    grid-template-columns: 1fr;
    gap: 6px;
  }
}
</style>
