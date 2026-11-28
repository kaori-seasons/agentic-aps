<template>
  <!--
    数字孪生 · 排产算法在制造流程中的价值可视化。

    阅读路径（自上而下，一眼读完）：
      1. 计划切换      —— 已确认计划 ↔ 优化候选，切的是整套排程解（没有候选时只展示已确认计划）
      2. 决策收益条    —— 算法在本车间做的三件事及其量化结果（改善绿 / 持平灰 / 恶化红），点任一项，孪生画面飞到对应工段给出证据
      3. 三维车间      —— 当前租户的设备工位 + 来料计划时间线 + 排程甘特，全幅

    数据：全部来自 /digital-twin/snapshot（已确认计划 + 候选计划 + 来料 + 资源台账），KPI 与决策在服务端算好。
      没有计划 → 空态如实说明，不用演示数据冒充；孪生 iframe 在快照注入前不渲染任何演示内容。

    设计约束（ui-styling / ui-ux-pro-max）：场景是主角；宿主页不重复孪生内部已有的面板；
    一层只说一件事；数字等宽对齐；不放讲解文案。
  -->
  <section class="digital-twin">
    <div class="dt-toolbar">
      <div class="dt-toggle" role="radiogroup" aria-label="计划版本">
        <button :class="{ on: plan === 'current' }" role="radio" :aria-checked="plan === 'current'" @click="switchPlan('current')">当前计划</button>
        <button :class="{ on: plan === 'candidate' }" role="radio" :aria-checked="plan === 'candidate'" :disabled="!hasCandidate" :title="!hasCandidate ? '当前没有待确认的候选计划，生成一次排产并暂不提交即可对比' : ''" @click="switchPlan('candidate')">优化候选</button>
      </div>
      <div class="dt-kpi" v-if="kpi && kpi.cur" aria-live="polite">
        <span class="k">
          <template v-if="kpi.compare"><em>{{ kpi.cur.orders }}</em>→</template>
          <b :class="kpiClass('orders', true)">{{ side().orders }}</b><i>排入订单</i>
        </span>
        <span class="k">
          <template v-if="kpi.compare"><em>{{ kpi.cur.unsched }}</em>→</template>
          <b :class="kpiClass('unsched', false)">{{ side().unsched }}</b><i>未排订单</i>
        </span>
        <span class="k">
          <template v-if="kpi.compare"><em>{{ kpi.cur.tardy }}</em>→</template>
          <b :class="kpiClass('tardy', false)">{{ side().tardy }}</b><i>逾期分钟</i>
        </span>
      </div>
      <div class="dt-tools">
        <button title="新窗口打开" aria-label="新窗口打开" @click="openStandalone">↗</button>
        <button title="全屏" aria-label="全屏" @click="fullscreen">⛶</button>
      </div>
    </div>

    <!-- 决策收益条：算法做了什么 + 结果多少；点击联动孪生画面 -->
    <div class="dt-decisions" v-if="decisions.length" role="tablist" aria-label="排产决策">
      <button
        v-for="d in decisions"
        :key="d.key"
        class="dt-dec"
        :class="{ on: activeDecision === d.key }"
        role="tab"
        :aria-selected="activeDecision === d.key"
        @click="focusDecision(d.key)"
      >
        <span class="dec-name">{{ d.name }}</span>
        <span class="dec-delta" :class="d.deltaClass">
          <template v-if="d.delta.includes('→')"><i class="from">{{ d.delta.split('→')[0] }}</i>→{{ d.delta.split('→')[1] }}</template>
          <template v-else>{{ d.delta }}</template><i>{{ d.deltaLabel }}</i>
        </span>
      </button>
    </div>

    <div class="dt-source">
      <span v-if="loading" class="src-tag">正在读取当前租户的计划…</span>
      <template v-else-if="error">
        <span class="src-tag err">快照读取失败：{{ error }}</span>
        <button class="src-link" @click="loadSnapshot">重试</button>
      </template>
      <template v-else-if="snapshot && snapshot.live">
        <span class="src-tag ok">当前租户实时数据 · {{ snapshot.current.length }} 项已确认任务<template v-if="hasCandidate"> · 候选 {{ snapshot.candidate.length }} 项（{{ candidateLabel }}）</template> · {{ time(snapshot.generatedAt) }}</span>
        <span v-if="notice" class="src-tag warn">{{ notice }}</span>
        <button class="src-link" @click="loadSnapshot">刷新</button>
      </template>
    </div>

    <div class="dt-frame-wrap">
      <iframe :key="frameKey" ref="frame" class="dt-frame" src="../digital-twin/index.html" title="数字孪生车间" @load="onFrameLoad"></iframe>
      <div v-if="loading" class="dt-overlay">正在读取计划…</div>
      <div v-else-if="error" class="dt-empty">
        <div class="empty-title">快照读取失败</div>
        <div class="empty-body">{{ error }}</div>
        <button class="empty-link" @click="loadSnapshot">重试</button>
      </div>
      <!-- 空态：没有计划就没有可看的东西，如实说明，不用演示数据冒充 -->
      <div v-else-if="snapshot && !snapshot.live" class="dt-empty">
        <div class="empty-title">当前租户还没有可展示的排产计划</div>
        <div class="empty-body">数字孪生展示的是排产算法对「已确认计划」与「优化候选」的求解结果。先在「排产计划」生成一次排产，这里会自动呈现。</div>
        <router-link class="empty-link" to="/productionSchedulingPlan">前往排产计划 →</router-link>
      </div>
      <div v-else-if="!twinReady" class="dt-overlay">场景装载中…</div>
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, toRaw } from 'vue'
import { RouterLink } from 'vue-router'
import request from '@/api/request'

const frame = ref(null)
const frameKey = ref(0)
const twinReady = ref(false)
const plan = ref('candidate')
const activeDecision = ref('gate')
const kpi = ref(null)
const decisions = ref([])
const snapshot = ref(null)
const loading = ref(true)
const error = ref('')
// 孪生回传的如实提示（如「N 台设备未在三维场景建模」）
const notice = ref('')
let readyTimer = null
let injected = false

const hasCandidate = computed(() => !!(snapshot.value && snapshot.value.hasCandidate))
const candidateLabel = computed(() => (snapshot.value && snapshot.value.candidateSource === 'AUDIT' ? '最近一次求解' : '待确认'))

// 拉后端聚合快照：已确认计划 + 候选计划 + 来料 + 资源，KPI 与决策在服务端算好
async function loadSnapshot() {
  loading.value = true
  error.value = ''
  notice.value = ''
  try {
    const res = await request({ url: '/mgr/jvs-aps/digital-twin/snapshot', method: 'get', skipServerErrorRedirect: true })
    if (res.data && res.data.code === 0) {
      const replaced = !!snapshot.value
      snapshot.value = res.data.data
      plan.value = hasCandidate.value ? 'candidate' : 'current'
      // 已经装过一版：换新快照时重建孪生，由它 ready 后重新注入（三维工位绑定只在建厂时做一次）
      if (replaced) resetTwin()
      injectSnapshot()
    } else {
      error.value = (res.data && res.data.msg) || '请求失败'
    }
  } catch (e) {
    error.value = (e && e.message) || '请求失败'
  } finally {
    loading.value = false
  }
}

// 重建孪生：换 key 让 Vue 重新创建 iframe，等待它重新 ready
function resetTwin() {
  twinReady.value = false
  injected = false
  kpi.value = null
  decisions.value = []
  frameKey.value++
}

function injectSnapshot() {
  const snap = snapshot.value
  if (!snap || injected || !twinReady.value) return
  // Vue 的响应式代理不能被结构化克隆，postMessage 会抛 DataCloneError；必须发原始对象
  const plain = JSON.parse(JSON.stringify(toRaw(snap)))
  injected = post({ type: snap.live ? 'twin:snapshot' : 'twin:empty', snapshot: plain })
  if (injected && snap.live) {
    plan.value = hasCandidate.value ? 'candidate' : 'current'
    post({ type: 'twin:plan', plan: plan.value })
  }
}

function time(iso) { return iso ? String(iso).replace('T', ' ').slice(0, 16) : '' }

// 返回是否送达；iframe 未就绪时静默，等 ready 消息再注入
function post(message) {
  const win = frame.value && frame.value.contentWindow
  if (!win) return false
  try { win.postMessage(message, '*'); return true } catch (e) { return false }
}

// 没有候选时只展示当前计划的数字
function side() { return kpi.value && kpi.value.compare ? kpi.value.cand : kpi.value.cur }

// 候选相对当前：改善绿 / 持平灰 / 恶化红。只比较，不替数据说好话
function kpiClass(field, biggerIsBetter) {
  const k = kpi.value
  if (!k || !k.compare || !k.cand) return 'flat'
  const a = Number(k.cur[field] || 0), b = Number(k.cand[field] || 0)
  if (a === b) return 'flat'
  return (b > a) === biggerIsBetter ? 'good' : 'bad'
}

function switchPlan(next) {
  if (next === 'candidate' && !hasCandidate.value) return
  plan.value = next
  post({ type: 'twin:plan', plan: next })
}

function focusDecision(key) {
  activeDecision.value = key
  post({ type: 'twin:decision', key })
}

function onMessage(e) {
  const d = e.data || {}
  if (d.twin === 'ready') {
    // 孪生（重新）启动：它会从头等快照，这里重置注入标记后重发
    twinReady.value = true
    if (readyTimer) clearTimeout(readyTimer)
    injected = false
    injectSnapshot()
    return
  }
  if (d.twin !== 'ack') return
  if (d.handled === 'twin:snapshot') {
    twinReady.value = true
    const un = (d.unmodeled || []).filter(Boolean)
    notice.value = un.length ? `${un.length} 台设备未在三维场景中建模，仅见于甘特：${un.join('、')}` : ''
  }
  // 孪生回传：KPI 对比 + 决策摘要（与孪生内部证据同源）
  if (d.kpi) kpi.value = d.kpi
  if (d.decisions) decisions.value = d.decisions
}

function onFrameLoad() {
  // 正常路径是孪生脚本就绪后发 ready；这里兜底：2 秒内没等到也尝试注入一次
  readyTimer = setTimeout(() => { twinReady.value = true; injectSnapshot() }, 2000)
}

// 新窗口打开的是本页（带登录态、真实数据），不是脱离宿主的演示页
function openStandalone() {
  const route = window.location.href.split('#')[0] + '#/digitalTwin'
  window.open(route, '_blank')
}

function fullscreen() {
  const el = frame.value
  if (!el) return
  if (document.fullscreenElement) document.exitFullscreen()
  else el.requestFullscreen().catch(() => el.webkitRequestFullscreen?.())
}

onMounted(() => { window.addEventListener('message', onMessage); loadSnapshot() })
onBeforeUnmount(() => { window.removeEventListener('message', onMessage); if (readyTimer) clearTimeout(readyTimer) })
</script>

<style scoped>
.digital-twin { height: 100%; display: flex; flex-direction: column; box-sizing: border-box; padding: 8px 12px 12px; }

/* ── 工具条：计划切换 + 结果数字（窄窗口自动换行，不挤压） ── */
.dt-toolbar { flex-shrink: 0; display: flex; align-items: center; flex-wrap: wrap; gap: 8px 20px; min-height: 40px; padding: 0 4px; }
.dt-toggle { display: flex; border: 1px solid #dcdfe6; border-radius: 6px; overflow: hidden; flex-shrink: 0; }
.dt-toggle button { border: none; background: #fff; color: #606266; font-size: 13px; padding: 5px 16px; cursor: pointer; transition: background .15s, color .15s; }
.dt-toggle button.on { background: #1E6FFF; color: #fff; font-weight: 600; }
.dt-toggle button:not(.on):hover { background: #ecf5ff; }
.dt-toggle button:disabled { color: #c0c4cc; cursor: not-allowed; background: #f5f7fa; }

.dt-kpi { display: flex; flex-wrap: wrap; gap: 6px 18px; margin-left: auto; font-variant-numeric: tabular-nums; }
.dt-kpi .k { display: flex; align-items: baseline; gap: 3px; white-space: nowrap; }
.dt-kpi em { font-style: normal; font-size: 14px; color: #909399; }
.dt-kpi b { font-size: 17px; color: #303133; margin: 0 1px; }
.dt-kpi b.good, .dec-delta.good { color: #1a9e62; }
.dt-kpi b.flat, .dec-delta.flat { color: #8a97ab; }
.dt-kpi b.bad, .dec-delta.bad { color: #d92d20; }
.dt-kpi i { font-style: normal; font-size: 11px; color: #909399; margin-left: 3px; }

.dt-tools { display: flex; gap: 4px; flex-shrink: 0; }
.dt-tools button { width: 28px; height: 28px; border: 1px solid #e4e7ed; border-radius: 4px; background: #fff; color: #909399; font-size: 14px; cursor: pointer; display: flex; align-items: center; justify-content: center; }
.dt-tools button:hover { border-color: #1E6FFF; color: #1E6FFF; }

/* ── 决策收益条：算法做了什么，数字是多少（窄窗口自动换行） ── */
.dt-decisions { flex-shrink: 0; display: flex; flex-wrap: wrap; gap: 8px 10px; margin: 4px 0 6px; }
.dt-dec {
  flex: 1 1 220px; min-width: 0; display: flex; align-items: center; justify-content: space-between; gap: 12px;
  border: 1px solid #e4e7ed; border-radius: 8px; background: #fff; padding: 8px 14px;
  cursor: pointer; text-align: left; font: inherit; color: inherit;
  transition: border-color .15s, box-shadow .15s;
}
.dt-dec:hover { border-color: #9db9e6; }
.dt-dec.on { border-color: #1E6FFF; box-shadow: 0 0 0 2px rgba(30,111,255,.16); }
.dt-dec:focus-visible { outline: 2px solid #1E6FFF; outline-offset: 2px; }
.dec-name { font-size: 13px; font-weight: 600; color: #303133; white-space: nowrap; }
.dec-delta { font-size: 16px; font-weight: 700; color: #303133; font-variant-numeric: tabular-nums; white-space: nowrap; }
.dec-delta i { font-style: normal; font-size: 11px; font-weight: 400; color: #909399; margin-left: 5px; }
.dec-delta i.from { font-size: 14px; color: #909399; margin: 0 2px 0 0; }

/* ── 数据来源条 ── */
.dt-source { flex-shrink: 0; display: flex; align-items: center; flex-wrap: wrap; gap: 4px 10px; min-height: 22px; margin-top: 2px; padding: 0 4px; font-size: 12px; }
.src-tag { color: #606266; }
.src-tag.ok { color: #1a9e62; }
.src-tag.warn { color: #b26a00; }
.src-tag.err { color: #b42318; }
.src-link { border: none; background: none; color: #1E6FFF; cursor: pointer; font-size: 12px; padding: 2px 6px; }
.src-link:hover { text-decoration: underline; }

/* ── 场景：全幅 ── */
.dt-frame-wrap { position: relative; flex: 1; min-height: 0; border-radius: 8px; overflow: hidden; border: 1px solid #e4e7ed; background: #e9eff6; }
.dt-frame { width: 100%; height: 100%; border: 0; display: block; }
.dt-overlay { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: #8a97ab; font-size: 13px; background: linear-gradient(180deg, #eef4fa, #e2eaf4); }

/* ── 空态 / 错误态 ── */
.dt-empty { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; background: rgba(255,255,255,.94); text-align: center; padding: 24px; }
.empty-title { font-size: 17px; font-weight: 600; color: #303133; }
.empty-body { max-width: 460px; font-size: 13px; line-height: 1.7; color: #606266; }
.empty-link { color: #1E6FFF; font-size: 13px; background: none; border: none; cursor: pointer; padding: 4px 8px; font-family: inherit; }
.empty-link:hover { text-decoration: underline; }

/* 窄屏：决策卡纵向排满，数字不换行溢出 */
@media (max-width: 760px) {
  .dt-dec { flex-basis: 100%; }
  .dt-kpi { margin-left: 0; width: 100%; }
}
</style>
