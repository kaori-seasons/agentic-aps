<template>
  <div class="settings-page">
    <div class="settings-hero">
      <div>
        <div class="hero-tag">个人设置</div>
        <h1>当前账号、租户上下文与界面偏好</h1>
        <p>这里负责把"当前是谁、默认进哪、用什么主题"从隐式约定改成显式配置。</p>
      </div>
      <div class="hero-meta">
        <div class="meta-card">
          <span>租户</span>
          <strong>{{ tenantInfo?.systemName || commonStore.tenantInfo?.systemName || '--' }}</strong>
        </div>
        <div class="meta-card">
          <span>认证来源</span>
          <strong>{{ commonStore.userProfile?.authSource || 'session' }}</strong>
        </div>
      </div>
    </div>

    <div class="settings-grid">
      <section class="profile-card">
        <h2>当前账号</h2>
        <div class="profile-row">
          <span>显示名</span>
          <strong>{{ commonStore.userProfile?.displayName || '--' }}</strong>
        </div>
        <div class="profile-row">
          <span>用户名</span>
          <strong>{{ commonStore.userProfile?.username || '--' }}</strong>
        </div>
        <div class="profile-row">
          <span>用户 ID</span>
          <strong>{{ commonStore.userProfile?.userId || '--' }}</strong>
        </div>
        <div class="profile-row">
          <span>角色</span>
          <div class="chips">
            <el-tag v-for="role in commonStore.authContext?.roleCodes || []" :key="role" type="info">{{ role }}</el-tag>
          </div>
        </div>
      </section>

      <div class="right-column">
        <section class="profile-card">
          <h2>个人偏好</h2>
          <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
            <el-form-item label="主题" prop="theme">
              <el-select v-model="form.theme" placeholder="请选择主题">
                <el-option v-for="item in themeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="语言" prop="language">
              <el-select v-model="form.language" placeholder="请选择语言">
                <el-option label="简体中文" value="zh_CN" />
                <el-option label="English" value="en" />
              </el-select>
            </el-form-item>
            <el-form-item label="默认首页" prop="defaultRoute">
              <el-select v-model="form.defaultRoute" placeholder="请选择默认首页">
                <el-option v-for="item in routeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <div class="actions">
              <el-button @click="resetForm">重置</el-button>
              <el-button type="primary" :loading="saving" @click="submit">保存设置</el-button>
            </div>
          </el-form>
        </section>

        <section class="profile-card">
          <h2>租户信息</h2>
          <div v-if="tenantLoading" class="tenant-loading">加载中...</div>
          <div v-else>
            <div class="profile-row">
              <span>租户 ID</span>
              <strong>{{ tenantInfo?.id || '--' }}</strong>
            </div>
            <div class="profile-row">
              <span>系统名称</span>
              <strong>{{ tenantInfo?.systemName || '--' }}</strong>
            </div>
          </div>
        </section>

        <section class="profile-card">
          <h2>修改密码</h2>
          <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-position="top">
            <el-form-item label="旧密码" prop="oldPassword">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="请输入新密码（6~50 位）" />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
            </el-form-item>
            <div class="actions">
              <el-button type="primary" :loading="pwdSaving" @click="submitPassword">修改密码</el-button>
            </div>
          </el-form>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElNotification, type FormInstance, type FormRules } from 'element-plus'
import useCommonStore from '@/store/common'
import { savePreferences, fetchCurrentTenant, changePassword } from '@/api/auth'
import { applyPreferencePayload } from '@/util/runtimeAuth'
import { themeOptions } from '@/util/theme'

const commonStore = useCommonStore()
const formRef = ref<FormInstance>()
const pwdFormRef = ref<FormInstance>()
const saving = ref(false)
const pwdSaving = ref(false)
const tenantLoading = ref(false)
const tenantInfo = ref<{ id: string; systemName: string } | null>(null)

const routeOptions = [
  { label: '首页', value: '/wel/index' },
  { label: '排产计划', value: '/productionSchedulingPlan' },
  { label: '排产策略', value: '/productionSchedulingStrategy' },
  { label: '生产日历', value: '/productionCalendar' },
  { label: '操作审计', value: '/operationAudit' },
]

const form = reactive({
  theme: commonStore.preferences?.theme || 'darkblue',
  language: commonStore.preferences?.language || 'zh_CN',
  defaultRoute: commonStore.preferences?.defaultRoute || '/productionSchedulingPlan',
})

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const rules: FormRules = {
  theme: [{ required: true, message: '请选择主题', trigger: 'change' }],
  language: [{ required: true, message: '请选择语言', trigger: 'change' }],
  defaultRoute: [{ required: true, message: '请选择默认首页', trigger: 'change' }],
}

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 50, message: '密码长度需在 6 ~ 50 之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: Function) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

function resetForm() {
  form.theme = commonStore.preferences?.theme || 'darkblue'
  form.language = commonStore.preferences?.language || 'zh_CN'
  form.defaultRoute = commonStore.preferences?.defaultRoute || '/productionSchedulingPlan'
}

async function submit() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  saving.value = true
  try {
    const res = await savePreferences(form)
    if (Number(res.data?.code) !== 0) {
      throw new Error(res.data?.msg || '保存失败')
    }
    applyPreferencePayload(res.data.data)
    ElNotification.success({
      title: '成功',
      message: '个人偏好已保存',
      position: 'bottom-right',
    })
  } catch (error: any) {
    ElNotification.error({
      title: '保存失败',
      message: error?.message || '请稍后重试',
      position: 'bottom-right',
    })
  } finally {
    saving.value = false
  }
}

async function submitPassword() {
  if (!pwdFormRef.value) {
    return
  }
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  pwdSaving.value = true
  try {
    const res = await changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    if (Number(res.data?.code) !== 0) {
      throw new Error(res.data?.msg || '修改失败')
    }
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    pwdFormRef.value?.resetFields()
    ElNotification.success({
      title: '成功',
      message: '密码已修改',
      position: 'bottom-right',
    })
  } catch (error: any) {
    ElNotification.error({
      title: '修改失败',
      message: error?.message || '请稍后重试',
      position: 'bottom-right',
    })
  } finally {
    pwdSaving.value = false
  }
}

async function loadTenantInfo() {
  tenantLoading.value = true
  try {
    const res = await fetchCurrentTenant()
    if (Number(res.data?.code) === 0 && res.data?.data) {
      tenantInfo.value = res.data.data
    }
  } catch {
    // 静默失败，使用 store 中的 tenantInfo
  } finally {
    tenantLoading.value = false
  }
}

onMounted(() => {
  loadTenantInfo()
})
</script>

<style lang="scss" scoped>
.settings-page{
  min-height: 100%;
  padding: 28px;
  background: linear-gradient(180deg, #f8fbff 0%, #f3f6fb 100%);
}
.settings-hero{
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 28px;
  border-radius: 24px;
  background: linear-gradient(135deg, #0f172a 0%, #1d4ed8 55%, #2563eb 100%);
  color: #fff;
  h1{
    margin: 12px 0 10px;
    font-size: 32px;
  }
  p{
    max-width: 680px;
    margin: 0;
    color: rgba(255,255,255,0.82);
    line-height: 1.8;
  }
}
.hero-tag{
  display: inline-flex;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(255,255,255,0.16);
  font-size: 12px;
  letter-spacing: 0.08em;
}
.hero-meta{
  display: grid;
  grid-template-columns: repeat(2, minmax(120px, 1fr));
  gap: 14px;
  min-width: 280px;
}
.meta-card{
  padding: 18px;
  border-radius: 18px;
  background: rgba(255,255,255,0.12);
  span{
    display: block;
    color: rgba(255,255,255,0.72);
    font-size: 12px;
  }
  strong{
    display: block;
    margin-top: 10px;
    font-size: 22px;
    word-break: break-all;
  }
}
.settings-grid{
  margin-top: 22px;
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 20px;
}
.right-column{
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.profile-card{
  padding: 24px;
  border-radius: 22px;
  background: rgba(255,255,255,0.92);
  border: 1px solid rgba(15, 23, 42, 0.05);
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.06);
  h2{
    margin: 0 0 18px;
    font-size: 20px;
    color: #111827;
  }
}
.profile-row{
  padding: 14px 0;
  border-bottom: 1px solid #eef2f7;
  span{
    display: block;
    color: #6b7280;
    font-size: 13px;
  }
  strong{
    display: block;
    margin-top: 8px;
    color: #111827;
    word-break: break-all;
  }
  &:last-child{
    border-bottom: 0;
    padding-bottom: 0;
  }
}
.chips{
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.tenant-loading{
  color: #6b7280;
  font-size: 14px;
  padding: 20px 0;
  text-align: center;
}
.actions{
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}
@media (max-width: 960px) {
  .settings-hero,
  .settings-grid{
    grid-template-columns: 1fr;
  }
  .settings-hero{
    flex-direction: column;
  }
  .hero-meta{
    min-width: 0;
  }
}
</style>
