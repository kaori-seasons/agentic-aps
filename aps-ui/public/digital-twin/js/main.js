// ============================================================
// APS 数字孪生 · 页面驱动：引擎 + 三维布局 + 排程仿真 + 界面
// 左侧排程候选计划（甘特图）映射「流水线-工序-工件」柔性车间概念，
// 展示当前计划 vs CP-SAT 优化候选的量化对比，体现排产价值。
// 两种数据态：
//   独立打开  —— 演示数据（demo-seed 那套风扇车间），建厂即渲染
//   嵌入宿主  —— 等宿主注入当前租户的真实快照后再建厂，不让演示数据冒充实时数据
// ============================================================
import { createEngine, defaultLabelEl } from './engine.js';
import * as THREE from 'three';
import { flowTextures } from './factory.js';
import { buildPlant } from './plant.js';
import * as Sim from './sim.js';
import { decisions, supplyRail, reached } from './decisions.js';
const { D, S, DEV, PROC_NAME, activePlan, taskOn, nextTaskOn, orderInfo, kpi, run, fmtMin, fullClock } = Sim;
// 数据容器是可替换的（真实快照会整体换掉演示数据），sim 导出的就是 getter，直接解构使用
const { MACHINES, ORDERS, MATERIALS, PLAN_CURRENT, PLAN_CANDIDATE } = Sim;
const $ = id => document.getElementById(id), two = n => String(n).padStart(2, '0');
const QS = new URLSearchParams(location.search);
const EMBEDDED = window.parent !== window;
// 租户数据进 HTML 一律转义
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
// 工序名 / 工序色：演示六个工序写死，真实工序编码用快照带回的名称，没有名称就显示编码
const procLabel = p => PROC_NAME[p] || S.procNames[p] || p;
const PROC_COLOR = { CUT: '#4a90d9', WELD: '#e8a23d', COAT: '#9b6bd6', WIND: '#e86a4a', ASSY: '#3db98b', TEST: '#5a7a9a' };
const procColor = p => PROC_COLOR[p] || `hsl(${[...String(p)].reduce((h, c) => (h * 31 + c.charCodeAt(0)) % 360, 7)} 52% 52%)`;
const U = () => (S.live ? '' : '台');                    // 数量单位：演示是「台」，真实数据单位不一，不臆造
const orderShort = o => (o ? o.short || o.code.replace('PO-260416-', 'PO-') : '—');

// 全景适配：左栏（决策证据 + 甘特）压住画面左侧约四分之一，场景整体右移半个左栏宽、并缩放到整厂可见
function fitAll() {
  const W = window.innerWidth || 1920;
  const narrow = W <= 980;                                   // 窄屏单列：面板整体盖在场景上，不再偏移
  const col = narrow ? 0 : (parseFloat(getComputedStyle($('ui')).gridTemplateColumns.split(' ')[0]) || $('decision-panel').getBoundingClientRect().width || 0);
  const shift = Math.round((col / 2 + 14) * (1920 / W));     // CSS 像素 → 1920 舞台像素；多让 14px，最左原料堆的标签不被面板压住
  return { offset: [14 - shift, -24], dist: Math.min(240, Math.max(165, 165 * 0.89 * W / Math.max(1, W - col) * 1.03)) };
}
const FIT = fitAll();
const OFF = FIT.offset;
const VIEWS = {
  all: { target: new THREE.Vector3(0, 0, 2), dist: FIT.dist, polar: 0.86, azim: 0, offset: OFF },
  cut: { target: new THREE.Vector3(-28, 1, -1), dist: 62, polar: 0.86, azim: 0 },
  shell: { target: new THREE.Vector3(-11, 1, -1), dist: 58, polar: 0.86, azim: 0 },
  wind: { target: new THREE.Vector3(-17, 1, 13), dist: 50, polar: 0.86, azim: 0 },
  assy: { target: new THREE.Vector3(23, 1, 0), dist: 60, polar: 0.86, azim: 0 },
};
const engine = createEngine({ fill: true, view: { ...VIEWS.all, fov: 19, near: 1, far: 900, limits: { minDistance: 28, maxDistance: 300 } }, exportName: () => 'APS数字孪生排程' });
// 窗口尺寸变化后重新适配（停在全景就跟着重新取景；在近景只改投影偏移）
let fitTimer = null;
function refit() {
  clearTimeout(fitTimer);
  fitTimer = setTimeout(() => {
    const f = fitAll(), cur = engine.getView(), atHome = Math.abs(cur.dist - VIEWS.all.dist) < 6;
    OFF[0] = f.offset[0]; OFF[1] = f.offset[1]; VIEWS.all.dist = f.dist;
    // 停在全景时跟着重新取景（整厂仍完整可见）；用户已飞到工段近景时只改投影偏移，不动镜头
    if (atHome) engine.flyTo(VIEWS.all, 600); else engine.setView({ ...cur, offset: f.offset });
  }, 150);
}
window.addEventListener('resize', () => { if (booted) refit(); });
// 宿主改变 iframe 尺寸不一定触发 resize 事件，直接观察自己的视口容器
new ResizeObserver(() => { if (booted) refit(); }).observe($('viewport'));

// 甘特图时间轴范围（分钟，固定覆盖两个计划便于对比）
const GANTT_T0 = 0;
let GANTT_T1 = 3200;   // 真实快照时装载后按最晚结束自适应（见 boot）

// ---------------- 数值牌（原料堆/缓冲上的库存牌） ----------------
const demo = () => S.demo;
// 演示：库存量；真实：在途量（库存未知就不显示库存）
const val = code => { const m = MATERIALS().find(x => x.code === code); return m ? (demo() ? String(Math.round(S.live ? m.inbound : m.inv)) : '--') : null; };

// ---------------- 标签 / 工厂（boot 时按当前数据态建厂） ----------------
let plant = null, labelRecs = [], tagEls = [], taskEls = [], booted = false;
const nameEls = new Map();
const makeLabel = l => {
  if (l.kind === 'task') { const el = document.createElement('div'); el.className = 'lbl tasklbl'; el.innerHTML = `<span class="o">—</span><span class="p"></span><i class="dot"></i>`; el.dataset.task = l.key; return el; }
  const el = defaultLabelEl(l);
  if (l.kind === 'flow' && l.rev) el.classList.add('rev');
  return el;
};
const stateOf = s => (!demo() ? 'off' : s.ids.some(i => D[i]?.fault) ? 'fault' : 'ok');

function taskText(id) {
  const tk = taskOn(id, S.t);
  if (!tk) return { o: '空闲', p: '', state: 'idle' };
  const o = orderInfo(tk.order);
  return { o: orderShort(o), p: `${procLabel(tk.proc)}·${tk.qty}${U()}`, state: 'run', done: tk };
}
const specValue = spec => {
  if (spec.startsWith('task:')) { const t = taskText(spec.slice(5)); return `<b>${esc(t.o)} ${t.p ? '· ' + esc(t.p) : ''}</b>`; }
  if (spec.startsWith('dev:')) return `<b>${pill(spec.slice(4))}</b>`;
  if (spec.includes('|')) return `<b>${val(spec.split('|')[0]) ?? '--'}</b> ${demo() ? spec.split('|')[1] : ''}`;
  return `<b>${esc(spec)}</b>`;
};
const pill = id => (!demo() ? '离线' : D[id].fault ? '故障' : D[id].run ? '运行中' : '待机') + ' · ' + (D[id].auto ? '自动' : '手动');

// ---------------- 排程候选计划面板（甘特图 + KPI） ----------------
/** 建厂并挂标签。演示态立即调用；嵌入态等宿主注入快照后调用，所以三维里只会出现真实设备。 */
function boot() {
  plant = buildPlant(engine.scene, S.model || undefined);
  engine.optimize(plant.selectables);
  labelRecs = engine.addLabels(plant.labels, makeLabel);
  tagEls = labelRecs.filter(r => r.kind === 'tag');
  taskEls = labelRecs.filter(r => r.kind === 'task');
  nameEls.clear();
  labelRecs.filter(r => r.kind === 'name').forEach(r => (nameEls.get(r.text) || nameEls.set(r.text, []).get(r.text)).push(r.el));
  engine.enableHover(plant.selectables, s => `<h4>${esc(s.title)}</h4>` + s.rows().map(([k, v]) => `<div class="r"><span>${esc(k)}</span><span>${specValue(v)}</span></div>`).join(''));
  renderLegend();
  // 时间轴右端按最晚结束自适应（两版计划取大者）
  GANTT_T1 = Math.max(480, ...activePlan().map(t => t.end), 0) * 1.04;
  if (S.live) {
    document.getElementById('ui').classList.add('live');
    $('plan-clock').hidden = false;
    document.querySelector('.sr-title').textContent = '来料计划 · 在途到货';
    document.querySelector('.plan-title').textContent = '排程计划';
    Sim.step(0);
  }
  renderKPI(); renderGantt(); renderParamCard(); renderDecisions(); renderSupplyRail();
  // 建厂时按当前窗口重新适配一次（模块加载瞬间量到的窗口尺寸/左栏宽度可能还没稳定）
  Object.assign(FIT, fitAll()); OFF[0] = FIT.offset[0]; OFF[1] = FIT.offset[1]; VIEWS.all.dist = FIT.dist;
  const v = QS.get('view');
  engine.setView(VIEWS[v] ? { ...VIEWS[v], offset: VIEWS[v].offset || OFF } : VIEWS.all);
  refresh();
  engine.start();
  booted = true;
}

// 工件图例：按主订单生成（补充订单与主订单同色，不单列）
function renderLegend() {
  const items = ORDERS().filter(o => !o.supplement), max = 8;
  $('legend').innerHTML = `<span class="lg-title">工件（订单）</span>`
    + items.slice(0, max).map(o => `<span><i style="--c:${o.color}"></i>${esc(orderShort(o))}${S.live ? '' : ' ' + esc(o.mat.replace('FG-FAN-', ''))}</span>`).join('')
    + (items.length > max ? `<span>… 共 ${items.length} 单</span>` : '');
}

// 演示态的 KPI 四格（真实态的数字在宿主页，取服务端口径）
function renderKPI() {
  const cur = kpi(PLAN_CURRENT()), cand = kpi(PLAN_CANDIDATE());
  const cell = (label, a, b, better) => `<div class="kpi-cell"><span class="kpi-label">${label}</span><div class="kpi-vals"><span class="v1">${a}</span><span class="arr">→</span><span class="v2 ${better ? 'good' : ''}">${b}${better ? ' ✓' : ''}</span></div></div>`;
  $('plan-kpi').innerHTML =
    cell('排入订单', `${cur.orders}`, `${cand.orders}`, cand.orders > cur.orders) +
    cell('未排（缺料）', `${cur.unsched}`, `${cand.unsched}`, cand.unsched < cur.unsched) +
    cell('逾期分钟', `${cur.tardy}`, `${cand.tardy}`, cand.tardy < cur.tardy) +
    cell('切割换型', `${cur.cutSetups}`, `${cand.cutSetups}`, cand.cutSetups < cur.cutSetups);
}

function renderGantt() {
  const plan = activePlan();
  const lanes = $('gantt-lanes');
  // 时间轴刻度（每 480 分钟 = 8 小时）
  const ticks = [];
  const step = S.live ? 1440 : 480;                       // 真实计划常跨多天：按天刻并带日期
  for (let t = GANTT_T0; t <= GANTT_T1; t += step) ticks.push(t);
  $('gantt-axis').innerHTML = ticks.map((t, i) => `<span class="${i === 0 ? 'first' : i === ticks.length - 1 ? 'last' : ''}" style="left:${t / GANTT_T1 * 100}%">${fmtMin(t).slice(S.live ? 0 : 6)}</span>`).join('');
  // 泳道
  const laneHtml = MACHINES().map(res => {
    const tasks = plan.filter(t => t.res === res).sort((a, b) => a.start - b.start);
    const bars = tasks.map(t => {
      const left = t.start / GANTT_T1 * 100, w = Math.max((t.end - t.start) / GANTT_T1 * 100, 0.6);
      const proc = t.proc, frozen = t.frozen ? ' frozen' : '', hit = supplyFocus && (t.inputs || []).some(i => i.code === supplyFocus) ? ' hit' : '';
      return `<div class="gbar${frozen}${hit}" data-task="${esc(t.id)}" data-res="${esc(res)}" style="left:${left}%;width:${w}%;background:${t.color}" title="${esc(orderInfo(t.order)?.code)} · ${esc(proc)} ${esc(procLabel(proc))} · ${fmtMin(t.start)}~${fmtMin(t.end)}${t.frozen ? ' · 冻结' : ''}"><span class="gp">${esc(proc)}</span></div>`;
    }).join('');
    const d = DEV[res];
    return `<div class="lane${d.slot === null ? ' nolane' : ''}" data-res="${esc(res)}" title="${esc(d.name)}${d.slot === null ? '（未在三维场景建模）' : ''}"><div class="lane-label">${esc(d.name)}<em>${esc(d.tag)}</em></div><div class="lane-track">${bars}</div></div>`;
  }).join('');
  lanes.innerHTML = laneHtml;
  // 当前时间红线
  const nowEl = $('gantt-now');
  nowEl.style.left = (Math.min(S.t, GANTT_T1) / GANTT_T1 * 100) + '%';
  nowEl.style.display = S.demo ? '' : 'none';
  // 高亮选中泳道
  lanes.querySelectorAll('.lane').forEach(l => l.classList.toggle('sel', l.dataset.res === selectedRes));
}

// ---------------- 详情（任务 / 设备） ----------------
let selectedRes = null;
function buildTaskDetail(t) {
  const o = orderInfo(t.order);
  // 逾期按订单末道工序判（不按工序名写死）；补充订单的交期继承自主订单，不单独计逾期
  const lastOp = !activePlan().some(x => x.order === t.order && x.end > t.end);
  const tardy = lastOp && o && !o.supplement && t.end > o.due ? Math.round(t.end - o.due) : 0;
  // 只有主订单的末道工序才对交期下结论；中间工序、补充订单不下「按期 / 逾期」的判断
  const verdict = !(lastOp && o && !o.supplement) ? '' : tardy ? `（逾期 ${tardy} 分）` : '（按期）';
  return `
  <div class="dt-head"><span class="dt-tag">${esc(t.id)}</span><b>${esc(o ? o.code : t.order)} · ${esc(procLabel(t.proc))}</b>${t.frozen ? '<span class="dt-frozen">冻结</span>' : ''}</div>
  <div class="dt-sec"><div class="dt-sec-t">工件 → 工序 → 流水线（柔性车间）</div>
    <div class="kv"><span>工件（订单）</span><b style="color:${t.color}">${esc(o ? o.code : t.order)}${o && !S.live ? `（${esc(o.matName)}）` : ''}</b></div>
    <div class="kv"><span>工序</span><b>${esc(procLabel(t.proc))} · ${t.qty}${U()}</b></div>
    <div class="kv"><span>流水线（资源）</span><b>${esc(DEV[t.res].name)}（${esc(DEV[t.res].tag)}）</b></div>
    <div class="kv"><span>时间窗</span><b>${fmtMin(t.start)} ~ ${fmtMin(t.end)}</b></div>
    <div class="kv"><span>订单交期</span><b>${o ? o.dueStr : '—'}${verdict}</b></div>
  </div>`;
}
// 演示态：原 demo 的三层映射（ERP/MES → 排程 → 回写），带演示用的节拍/日历/柔性候选文案
function buildDeviceDetail(s) {
  const id = s.ids[0], dev = DEV[id];
  const tk = taskOn(id, S.t), nxt = nextTaskOn(id, S.t), order = tk ? orderInfo(tk.order) : null;
  const st = !demo() ? ['离线', 'off'] : D[id].fault ? ['故障', 'fault'] : D[id].run ? ['运行中', 'ok'] : ['待机', 'ok'];
  const q = activePlan().filter(t => t.res === id).sort((a, b) => a.start - b.start);
  const taskRows = q.map(t => {
    const o = orderInfo(t.order);
    const now = S.t >= t.end ? '已完成' : S.t >= t.start ? '执行中' : '待排';
    return `<div class="dt-row"><span class="chip" style="background:${o.color}">${o.code}</span><span class="proc">${t.proc}</span><span class="win">${fmtMin(t.start).slice(6)}~${fmtMin(t.end).slice(6)}</span><span class="st ${now === '执行中' ? 'on' : ''}">${now}${t.frozen ? '·冻结' : ''}</span></div>`;
  }).join('');
  return `
  <div class="dt-head"><span class="dt-tag">${dev.tag}</span><b>${dev.name}</b><span class="st ${st[1]}">${st[0]}</span></div>
  <div class="dt-sec"><div class="dt-sec-t">① 数据源 ERP / MES</div>
    <div class="kv"><span>生产订单</span><b>${order ? order.code : '—'}</b></div>
    <div class="kv"><span>订单交期</span><b>${order ? order.dueStr : '—'}</b></div>
    <div class="kv"><span>优先级</span><b>${order ? order.pri : '—'}</b></div>
    <div class="kv"><span>设备状态</span><b>${D[id].fault ? 'MAINTENANCE 停机' : 'NORMAL'}</b></div>
  </div>
  <div class="dt-sec"><div class="dt-sec-t">② 排程计算（CP-SAT / Timefold）</div>
    <div class="kv"><span>当前任务</span><b>${tk ? tk.proc + ' · ' + tk.qty + ' 台' : '无'}</b></div>
    <div class="kv"><span>柔性候选</span><b>${dev.lock}</b></div>
    <div class="kv"><span>资源日历</span><b>${dev.cal}</b></div>
    <div class="kv"><span>节拍</span><b>${dev.thru}</b></div>
  </div>
  <div class="dt-sec"><div class="dt-sec-t">③ 结果回写 MES / ERP</div>
    <div class="kv"><span>MES 报工</span><b>${tk ? (S.t >= tk.end ? tk.qty + '/' + tk.qty + ' 台完成' : '执行中') : '—'}</b></div>
    <div class="kv"><span>下一任务</span><b>${nxt ? nxt.proc + ' ' + fmtMin(nxt.start).slice(6) : '—'}</b></div>
    <div class="kv"><span>交期建议</span><b>${order ? '回写 ERP（CTP）' : '—'}</b></div>
  </div>
  <div class="dt-tasks"><div class="dt-sec-t">本流水线排程队列（jvs_plan_task）</div>${taskRows || '<div class="empty">暂无</div>'}</div>`;
}
// 真实态：只陈述台账事实与计划里的排程队列；「此刻在做什么」是计划推演，不冒充 MES 报工
function buildDeviceDetailLive(s) {
  const id = s.ids[0], dev = DEV[id];
  const tk = taskOn(id, S.t), nxt = nextTaskOn(id, S.t);
  const st = D[id].fault ? ['故障', 'fault'] : tk ? ['推演中执行', 'ok'] : ['空闲', 'ok'];
  const q = activePlan().filter(t => t.res === id).sort((a, b) => a.start - b.start);
  const taskRows = q.map(t => {
    const o = orderInfo(t.order), now = S.t >= t.end ? '已完成' : S.t >= t.start ? '执行中' : '待开工';
    return `<div class="dt-row" data-task="${esc(t.id)}"><span class="chip" style="background:${t.color}">${esc(orderShort(o))}</span><span class="proc">${esc(procLabel(t.proc))}</span><span class="win">${fmtMin(t.start).slice(6)}~${fmtMin(t.end).slice(6)}</span><span class="st ${now === '执行中' ? 'on' : ''}">${now}${t.frozen ? '·冻结' : ''}</span></div>`;
  }).join('');
  const planName = S.plan === 'current' ? '已确认计划' : '优化候选';
  return `
  <div class="dt-head"><span class="dt-tag">${esc(dev.tag)}</span><b>${esc(dev.name)}</b><span class="st ${st[1]}">${st[0]}</span></div>
  <div class="dt-sec"><div class="dt-sec-t">设备台账（jvs_production_resource）</div>
    <div class="kv"><span>工序</span><b>${esc(dev.procName || '—')}</b></div>
    <div class="kv"><span>分组</span><b>${esc(dev.grp || '—')}</b></div>
    <div class="kv"><span>节拍</span><b>${esc(dev.thru || '—')}</b></div>
    <div class="kv"><span>状态</span><b>${esc(dev.status || '—')}</b></div>
  </div>
  <div class="dt-sec"><div class="dt-sec-t">计划推演 ${fullClock(S.t).replace(/\s+/g, ' ').slice(5, 16)}</div>
    <div class="kv"><span>推演中任务</span><b>${tk ? `${esc(procLabel(tk.proc))} · ${esc(orderShort(orderInfo(tk.order)))} · ${tk.qty}${U()}` : '无'}</b></div>
    <div class="kv"><span>下一任务</span><b>${nxt ? `${esc(procLabel(nxt.proc))} ${fmtMin(nxt.start).slice(6)}` : '—'}</b></div>
  </div>
  <div class="dt-tasks"><div class="dt-sec-t">本设备排程队列（${planName}，共 ${q.length} 项）</div>${taskRows || '<div class="empty">暂无</div>'}</div>`;
}
// kind='task'：点甘特条/来料节点弹出，只占证据区，甘特保持可见可点；设备详情占满左栏
function showDetail(html, kind = 'device') {
  const el = $('plan-detail');
  if (html) {
    el.innerHTML = '<button type="button" class="dt-close" aria-label="关闭详情" title="关闭">✕</button>' + html; el.classList.toggle('task', kind === 'task'); el.classList.add('show'); el.scrollTop = 0;
    // 任务 / 来料明细按内容撑高，上限是整个左栏行高：短的不盖甘特，长的才压住甘特上半部分（可滚动）；窄屏堆叠布局不限高
    const pp = $('plan-panel'), dp = $('decision-panel');
    el.style.maxHeight = kind === 'task' && window.innerWidth > 980 && pp && dp ? Math.max(120, pp.getBoundingClientRect().bottom - dp.getBoundingClientRect().top) + 'px' : '';
  }
  else el.classList.remove('show');
}
// 关闭明细时同步清掉来料高亮（甘特命中 + 原料堆 + 时间线节点）
function closeDetail() {
  clearSupplyFocus();
  selectedRes = null; showDetail(null); renderGantt(); renderSupplyRail();
}
$('plan-detail').addEventListener('click', e => {
  if (e.target.closest('.dt-close')) { closeDetail(); return; }
  // 明细里的工序行 → 该工序的任务详情，并飞到所在工位
  const row = e.target.closest('.dt-row[data-task]');
  if (!row) return;
  const t = activePlan().find(x => x.id === row.dataset.task);
  if (!t) return;
  selectedRes = t.res; renderGantt(); showDetail(buildTaskDetail(t), 'task');
  const sel = plant.selectables.find(x => x.ids?.includes(t.res)), v = DEV[t.res]?.view || 'all';
  if (VIEWS[v]) engine.flyTo({ ...VIEWS[v], offset: VIEWS[v].offset || OFF }, 1100);
  if (sel) { engine.highlight(sel, true); setTimeout(() => engine.highlight(sel, false), 1600); }
});

// 甘特图任务条点击 → 高亮设备 + 任务详情
$('gantt-lanes').addEventListener('click', e => {
  const bar = e.target.closest('.gbar'); if (!bar) return;
  const t = activePlan().find(x => x.id === bar.dataset.task); if (!t) return;
  selectedRes = t.res;
  showDetail(buildTaskDetail(t), 'task');
  const s = plant.selectables.find(x => x.ids?.includes(t.res));
  if (s) { engine.flyTo({ ...VIEWS[resView(t.res)], offset: VIEWS[resView(t.res)].offset || OFF }, 1100); engine.highlight(s, true); setTimeout(() => engine.highlight(s, false), 1600); }
  renderGantt();
});
function resView(res) { const m = { QG1: 'cut', QG2: 'cut', HJ1: 'shell', PQ1: 'shell', RL1: 'wind', ZZ1: 'assy', ZZ2: 'assy', CS1: 'assy' }; return m[res] || 'all'; }

// 设备点击 → 高亮泳道 + 设备详情
engine.onClick(s => {
  if (s && s.supply) { focusSupply(s.supply); }
  else if (s && s.ids?.length) { clearSupplyFocus(); selectedRes = s.ids[0]; showDetail(S.live ? buildDeviceDetailLive(s) : buildDeviceDetail(s)); }
  else if (!$('plan-detail').classList.contains('show')) return;
  else closeDetail();
  renderGantt();
});

// 计划切换
document.querySelectorAll('.plan-tabs button').forEach(b => b.addEventListener('click', () => {
  S.plan = b.dataset.plan;
  document.querySelectorAll('.plan-tabs button').forEach(x => x.classList.toggle('on', x === b));
  Sim.evalAlarms(); renderGantt(); renderKPI(); refresh();
  if (supplyFocus) focusSupply(supplyFocus, supplyFocusMin);
  engine.toast(S.live ? `已切换到${S.plan === 'current' ? '已确认计划' : '优化候选'}` : S.plan === 'current' ? '已切换到当前计划（4 订单，2 单缺料未排）' : '已切换到 CP-SAT 优化候选（6 订单全排，换型归并）');
}));

// ---------------- 排产决策侧栏（算法价值本体） ----------------
let activeDecision = 'gate';
function renderDecisions() {
  const items = decisions();
  const curPlan = S.plan === 'current';
  const d = items.find(x => x.key === activeDecision) || items[0];
  $('dec-sub').textContent = S.live ? (S.compare ? (curPlan ? '已确认计划' : '优化候选 vs 已确认计划') : '已确认计划') : curPlan ? '当前计划（优化前）' : '优化候选 vs 当前计划';
  // 卡片在宿主页；孪生内部只呈现当前决策的证据明细，避免重复。
  $('dec-list').innerHTML = `
    <div class="dec-focus">
      <span class="dec-name">${d.name}</span>
      <span class="dec-delta ${d.deltaClass}">${d.delta}<i>${d.deltaLabel}</i></span>
    </div>
    <div class="dec-why">${d.why}</div>`;
  const el = $('dec-detail');
  el.innerHTML = d.rows.map(([k, v]) => `<div class="kv"><span>${k}</span><b>${v}</b></div>`).join('')
    + `<div class="note">${d.note}</div>`;
  el.classList.add('show');
}
// 关联路径：宿主页点决策卡 → twin:decision → 镜头飞到该决策涉及的工段 → 相关设备高亮 → 甘特泳道高亮
function focusDecision(key) {
  const d = decisions().find(x => x.key === key);
  if (!d) return;
  activeDecision = key;
  renderDecisions();
  // auto：证据涉及的工位同属一个工段就飞近看，跨工段就回全景
  const views = new Set(d.focus.resources.map(r => DEV[r]?.view).filter(Boolean));
  const v = d.focus.view === 'auto' ? (views.size === 1 ? [...views][0] : 'all') : d.focus.view;
  engine.flyTo({ ...VIEWS[v], offset: VIEWS[v].offset || OFF }, 1200);
  d.focus.resources.forEach(res => {
    const s = plant.selectables.find(x => x.ids?.includes(res));
    if (s) engine.highlight(s, true);
  });
  selectedRes = d.focus.resources[0];
  renderGantt();
}

// ---------------- 来料计划时间线（齐套门禁的物证） ----------------
// 窗口覆盖「当前时刻 / 最早到货」到计划结束：到货点落在计划开工之前还是之后，一眼可见
function renderSupplyRail() {
  const rail = supplyRail();
  const W = rail.window || { w0: 0, w1: rail.horizon || 3200 };
  const span = Math.max(1, W.w1 - W.w0);
  const x = m => Math.max(0, Math.min(100, (m - W.w0) / span * 100));
  const clock = rail.replay ?? rail.now ?? W.w0;
  const marks =
    (rail.planStart !== null ? `<div class="sr-mark start" style="left:${x(rail.planStart)}%" title="计划最早开工 ${fmtMin(rail.planStart)}"></div>` : '') +
    (rail.now !== null ? `<div class="sr-now" style="left:${x(rail.now)}%" title="当前 ${fmtMin(rail.now)}"></div>` : '') +
    (rail.replay !== null ? `<div class="sr-mark replay" style="left:${x(rail.replay)}%" title="推演 ${fmtMin(rail.replay)}"></div>` : '');
  const points = rail.points.map(p => `<button type="button" class="sr-eta${reached(p.min, clock) ? ' reached' : ''}${p.code === supplyFocusKey ? ' on' : ''}" data-material="${esc(p.material || p.code)}" data-min="${p.min}" style="left:${x(p.min)}%"
        title="${esc(p.name)} ${p.qty}${esc(p.unit)} · 到货 ${esc(p.eta)}${(rail.unlocks[p.code] || []).length ? ' · 解锁 ' + rail.unlocks[p.code].map(id => orderShort(orderInfo(id))).join('、') : S.live ? ' · 点击查看被哪些工序消耗' : ''}"
        aria-label="${esc(p.name)} 到货 ${esc(p.eta)}"></button>`).join('');
  // 未排订单：演示里挂在缺口物料的到货点上（候选计划到货即排）；真实数据没有缺口物料信息，挂在「当前」时刻，只陈述「未排」
  const locks = rail.blocked.map((o, i) => {
    const mat = S.live ? null : o.id === 'po-005' ? 'RM-BEARING' : o.id === 'po-006' ? 'RM-COPPER' : null;
    const p = mat ? rail.points.find(q => q.code === mat) : null;
    const at = p ? p.min : rail.now ?? rail.planStart ?? W.w0;
    const open = !S.live && S.plan === 'candidate';
    const text = S.live ? `${orderShort(o)} 未排` : `${o.code.replace('PO-260416-', '')} ${open ? '已排入' : '缺料'}`;
    return `<button type="button" class="sr-lock${open ? ' open' : ''}" data-order="${esc(o.id)}" style="left:${x(at)}%;top:${-14 - i * 20}px"
          title="${esc(o.code)}${o.blocked ? ' ' + esc(o.blocked) : ''}${open ? ' · 候选计划已排入' : ''}">${esc(text)}</button>`;
  }).join('');
  const step = S.live ? (span > 2 * 1440 ? 1440 : 480) : 480;
  const ticks = [];
  for (let t = Math.ceil(W.w0 / step) * step; t <= W.w1; t += step) ticks.push(t);
  $('sr-track').innerHTML = `<div class="sr-line"></div>` + marks + points + locks;
  $('sr-axis').innerHTML = ticks.map((t, i) => `<span class="${i === 0 ? 'first' : i === ticks.length - 1 ? 'last' : ''}" style="left:${x(t)}%">${fmtMin(t).slice(S.live ? 0 : 6)}</span>`).join('');
  $('sr-sub').textContent = S.live
    ? `${rail.points.length} 批在途到货 · ${rail.blocked.length} 单未排 · 点到货点看它被哪些工序消耗`
    : `${rail.points.length} 批在途 · ${rail.blocked.length} 单受门禁约束`;
}
// ---------------- 来料节点：到货 → 齐套 → 开工 ----------------
// 点时间线上的到货点（或三维里的原料堆）展开三件事：
//   ① 这批来料是什么、何时到（来料计划）；② 物料够不够（库存 + 在途 − 计划消耗）；③ 哪些工序消耗它、开工时料到位了没有
let supplyFocus = null, supplyFocusKey = null, supplyFocusMin = null, pileSel = null;
const fmtDur = min => {
  const m = Math.round(Math.abs(min)), d = Math.floor(m / 1440), h = Math.floor((m % 1440) / 60);
  return d ? `${d} 天${h ? ` ${h} 小时` : ''}` : h ? `${h} 小时${m % 60 ? ` ${m % 60} 分` : ''}` : `${m} 分钟`;
};
const qtyText = (n, unit) => `${Number(Number(n || 0).toFixed(2))}${unit ? ' ' + unit : ''}`;

// 同一物料按时间对账：库存 + 到货 − 工序消耗，逐道工序判断开工那一刻料是否到位
//   upto：只算到某一批到货为止的可用量（判断工序是否还得等后面的批次）
function materialLedger(material, upto) {
  const uses = activePlan().filter(t => (t.inputs || []).some(i => i.code === material))
    .sort((a, b) => a.start - b.start).map(t => ({ t, qty: t.inputs.find(i => i.code === material).qty }));
  const arrivals = (S.supplyRows || []).filter(p => p.materialCode === material && p.etaMin !== null).slice().sort((a, b) => a.etaMin - b.etaMin);
  let avail = S.stockBy[material]?.stock || 0, k = 0;
  return { arrivals, rows: uses.map(({ t, qty }) => {
    while (k < arrivals.length && arrivals[k].etaMin <= t.start && (upto === undefined || arrivals[k].etaMin <= upto)) avail += arrivals[k++].qty;
    const ok = avail + 1e-9 >= qty, gap = ok ? 0 : qty - avail;     // 开工时料是否到位
    avail -= qty;
    return { t, qty, ok, gap };
  }) };
}

function buildSupplyDetail(point) {
  const st = S.stockBy[point.material] || {};
  const planName = S.plan === 'current' ? '已确认计划' : '优化候选';
  const { rows, arrivals } = materialLedger(point.material);
  // 只算到本批为止：不够的工序，说明它实际在等后面批次的料
  const selfRows = materialLedger(point.material, point.min).rows;
  const others = arrivals.filter(a => a.code !== point.code);
  const rel = point.min - S.window.start, toNow = point.min - S.window.now;
  const need = rows.reduce((sum, r) => sum + r.qty, 0);
  // 每道工序相对本批的四种关系：开工时缺料 / 本批到货前开工（靠库存）/ 还得等后续批次 / 本批到货后开工
  const state = rows.map((r, i) => !r.ok ? ['bad', `缺 ${qtyText(r.gap, point.unit)}`, '开工时库存加已到货不足以覆盖本工序需求']
    : r.t.start < point.min ? ['stock', '靠库存', '在本批到货前开工，由库存 / 更早批次支撑']
    : !selfRows[i].ok ? ['stock', '待后续批', '只算到本批（含库存）不够本工序用，实际开工还依赖更晚的批次']
    : ['on', '到货后', '本批到货后开工，料已到位']);
  const count = k => state.filter(x => x[0] === k).length;
  const early = rows.filter(r => r.ok && r.t.start < point.min).length, wait = state.filter(x => x[1] === '待后续批').length;
  const meta = (k, v) => `<div class="sup-meta"><span>${esc(k)}</span><b>${v}</b></div>`;
  return `
  <div class="dt-head sup-head"><b title="来料单 ${esc(point.code)}">${esc(point.code)}</b><span class="st ok">在途</span></div>
  <div class="sup-meta"><span>来料 / 物料</span><b>${esc(point.name)}（${esc(point.material)}）</b></div>
  ${meta('本批 / 到货', `${qtyText(point.qty, point.unit)} · ${esc(point.eta)}`)}
  ${meta('相对计划', `${rel === 0 ? '计划开工时到货' : `${rel > 0 ? '开工后 ' : '开工前 '}${fmtDur(rel)}到货`} · ${toNow < 0 ? '已过预计到货时刻（仍在途）' : `${fmtDur(toNow)}后到货`}`)}
  ${meta('库存 / 安全库存', `${qtyText(st.stock, point.unit)} / ${qtyText(st.safety, point.unit)}`)}
  ${meta('在途 / 计划消耗', `${qtyText(arrivals.reduce((sum, a) => sum + a.qty, 0), point.unit)}（${arrivals.length} 批）· ${qtyText(need, point.unit)}（${rows.length} 道工序）`)}
  ${others.length ? meta('其他批次', others.map(a => `${qtyText(a.qty, point.unit)} @${a.etaStr}`).join('；')) : ''}
  <div class="sup-note" title="来料计划 jvs_incoming_material_order · 物料台账 jvs_material · 任务投入 input_materials">来源：来料计划 · 物料台账 · ${planName}的工序投入</div>
  <div class="dt-tasks"><div class="dt-sec-t" title="消耗该物料的工序，按开工排序${rows.length ? `：到货后 ${count('on')} · 靠库存 ${early} · 待后续批次 ${wait} · 缺料 ${count('bad')}` : ''}">消耗工序${rows.length ? ` ${rows.length}` : ''}${[['到货后', count('on')], ['靠库存', early], ['待后续批', wait], ['缺料', count('bad')]].filter(x => x[1]).map(x => ` · ${x[0]} ${x[1]}`).join('')}</div>
    ${rows.length ? rows.map((r, i) => `<div class="dt-row" data-task="${esc(r.t.id)}" title="${esc(state[i][2])}"><span class="chip" style="background:${r.t.color}">${esc(orderShort(orderInfo(r.t.order)))}</span><span class="proc">${esc(procLabel(r.t.proc))}</span><span class="win">${fmtMin(r.t.start)}</span><span class="st ${state[i][0]}">${state[i][1]}</span></div>`).join('')
      : `<div class="empty">${planName}里没有工序消耗该物料</div>`}
  </div>`;
}

// 演示态：没有工序级消耗数据，按演示设定的「缺口物料 → 解锁订单」给证据
function buildSupplyDetailDemo(point) {
  const m = MATERIALS().find(x => x.code === point.code), orders = (supplyRail().unlocks[point.code] || []).map(orderInfo);
  return `
  <div class="dt-head"><span class="dt-tag">来料</span><b>${esc(point.code)}</b><span class="st ok">在途</span></div>
  <div class="dt-sec"><div class="dt-sec-t">来料计划</div>
    <div class="kv"><span>物料</span><b>${esc(point.name)}</b></div>
    <div class="kv"><span>本批数量</span><b>${qtyText(point.qty, point.unit)}</b></div>
    <div class="kv"><span>预计到货</span><b>${esc(point.eta)}</b></div>
    <div class="kv"><span>相对推演时刻</span><b>${reached(point.min, S.t) ? '已到货' : fmtDur(point.min - S.t) + '后到货'}</b></div>
  </div>
  <div class="dt-sec"><div class="dt-sec-t">物料供需</div>
    <div class="kv"><span>库存 / 安全库存</span><b>${qtyText(m?.inv, point.unit)} / ${qtyText(m?.safety, point.unit)}</b></div>
  </div>
  <div class="dt-tasks"><div class="dt-sec-t">到货后解锁的订单</div>
    ${orders.length ? orders.map(o => {
      const inCand = PLAN_CANDIDATE().some(t => t.order === o.id), inCur = PLAN_CURRENT().some(t => t.order === o.id);
      const first = PLAN_CANDIDATE().filter(t => t.order === o.id).sort((a, b) => a.start - b.start)[0];
      return `<div class="dt-row"><span class="chip" style="background:${o.color}">${esc(orderShort(o))}</span><span class="proc">${esc(o.mat.replace('FG-FAN-', ''))} 系</span><span class="st ${inCand ? 'on' : 'bad'}">${inCur && inCand ? '两版均已排入' : inCand ? '候选已排入' : '未排入'}</span></div>${first ? `<div class="kv"><span>候选最早开工</span><b>${DEV[first.res].name} ${fmtMin(first.start)}</b></div>` : ''}`;
    }).join('') : '<div class="empty">没有订单被这批料约束</div>'}
  </div>`;
}

function clearSupplyFocus() {
  if (pileSel) engine.highlight(pileSel, false);
  pileSel = null; supplyFocus = null; supplyFocusKey = null; supplyFocusMin = null;
}
function focusSupply(material, etaMin) {
  const rail = supplyRail();
  const point = rail.points.find(p => (p.material || p.code) === material && (etaMin === undefined || p.min === etaMin)) || rail.points.find(p => (p.material || p.code) === material);
  if (!point) return;
  clearSupplyFocus();
  supplyFocus = material; supplyFocusKey = point.code; supplyFocusMin = point.min;
  // 三维里点亮该物料的原料堆
  pileSel = plant.selectables.find(x => x.supply === material) || null;
  if (pileSel) engine.highlight(pileSel, true, '#d98a2b');
  selectedRes = null;
  showDetail(S.live ? buildSupplyDetail(point) : buildSupplyDetailDemo(point), 'task');
  renderGantt(); renderSupplyRail();
}

// 点到货点 → 展开这批来料的证据；点未排订单标签 → 聚焦该订单在当前计划里的任务链
$('sr-track').addEventListener('click', e => {
  const eta = e.target.closest('.sr-eta');
  if (eta) { focusSupply(eta.dataset.material, Number(eta.dataset.min)); return; }
  const lock = e.target.closest('.sr-lock[data-order]');
  if (!lock) return;
  const oid = lock.dataset.order;
  const first = activePlan().find(t => t.order === oid);
  if (first) { selectedRes = first.res; renderGantt(); showDetail(buildTaskDetail(first), 'task'); }
  const v = first ? (DEV[first.res]?.view || 'all') : 'all';
  if (VIEWS[v]) engine.flyTo({ ...VIEWS[v], offset: VIEWS[v].offset || OFF }, 1100);
});

// ---------------- 参数卡 ----------------
function renderParamCard() {
  const kp = kpi(activePlan());
  const running = MACHINES().filter(id => D[id].run).length;
  const faults = MACHINES().filter(id => D[id].fault).length;
  const frozen = activePlan().filter(t => t.frozen).length;
  const rows = [
    ['排程引擎', 'CP-SAT', S.plan === 'current' ? '已确认计划' : '优化候选'],
    ['虚拟时钟', fullClock(S.t), `${Math.round(S.t / 60)}h 已过`],
    ['工件（订单）', `${kp.orders}`, `共 ${ORDERS().length} 单`],
    ['工序任务', `${activePlan().length}`, '订单×工序'],
    ['运行流水线', `${running}/${MACHINES().length}`, ''],
    ['冻结任务', `${frozen}`, '不可动'],
    ['设备异常', `${faults}`, ''],
    ['逾期分钟', `${kp.tardy}`, ''],
  ];
  $('prows').innerHTML = rows.map(([n, v, u]) => `<tr><td class="k">${n}</td><td class="v"><span class="val">${v}</span>${u ? `<span class="unit">${u}</span>` : ''}</td></tr>`).join('');
}

function refresh() {
  renderParamCard();
  taskEls.forEach(r => { const t = taskText(r.key); r.el.querySelector('.o').textContent = t.o; r.el.querySelector('.p').textContent = t.p; r.el.classList.toggle('busy', t.state === 'run'); });
  tagEls.forEach(r => { r.el.querySelector('.v').textContent = val(r.key) ?? '--'; });
  for (const s of plant.selectables) (nameEls.get(s.title) || []).forEach(el => { el.classList.remove('st-ok', 'st-fault', 'st-off'); el.classList.add('st-' + stateOf(s)); });
  MACHINES().forEach(id => {
    const lamp = plant.lamps[id];
    if (lamp) lamp.material.color.set(!demo() ? 0x9aa5b3 : D[id].fault ? 0xe5484d : D[id].run ? 0x22b573 : 0xc9d3df);
    if (plant.spin[id]) plant.spin[id].on = run(id);
  });
  for (const f of flowTextures) { const u = f.mesh.parent?.userData || {}; f.mesh.visible = u.actId ? run(u.actId) : false; }
  const p = $('plc1');
  if (S.live) {
    // 真实态：通信灯陈述的是快照事实，不是模拟的 PLC 链路
    p.className = 'plc ok'; p.querySelector('span').textContent = '实时快照 ' + String(S.generatedAt || '').slice(11, 19);
    $('plan-clock').textContent = '计划推演 ' + fullClock(S.t).replace(/\s+/g, ' ').slice(5, 16);
  } else {
    p.className = 'plc' + (demo() ? ' ok' : ''); p.querySelector('span').textContent = demo() ? 'ERP/MES 通信正常' : '通信失败';
    // 演示态明示「演示数据」，不被误认成当前租户的实时计划
    $('plan-clock').hidden = false; $('plan-clock').textContent = '演示数据 · 推演 ' + fullClock(S.t).replace(/\s+/g, ' ').slice(5, 16);
  }
  const sb = document.querySelector('.sb-warn'); sb.classList.toggle('ok', demo()); sb.lastChild.textContent = demo() ? 'ERP/MES 数据链路正常。' : '所有服务器连接失败。';
  const list = demo() ? Sim.S.alarms.slice(0, 5) : [], un = Sim.S.alarms.filter(a => demo() && !a.clr && a.l !== 'i');
  $('alarm-rows').innerHTML = list.length ? list.map(a => `<tr data-id="${a.id}"><td>${a.t.getFullYear()}/${two(a.t.getMonth() + 1)}/${two(a.t.getDate())}</td><td>${two(a.t.getHours())}:${two(a.t.getMinutes())}:${two(a.t.getSeconds())}</td><td${a.clr || a.l === 'i' ? '' : ' style="color:#c62f34;font-weight:600"'}>${a.text}</td><td>${a.ack ? '已确认' : '未确认'}</td><td>${a.clr ? '已恢复' : { a: '报警中', w: '预警', i: '信息' }[a.l]}</td></tr>`).join('') : '<tr><td colspan="5" class="empty">暂无报警信息</td></tr>';
  $('alarm-count').textContent = un.length;
  renderGantt(); renderDecisions(); renderSupplyRail();
  // 视图覆盖层刷新
  if (currentView === 'gantt') renderGanttFull(ganttMode);
  else if (currentView === 'alarms') renderAlarms();
  // 详情若开着则刷新（任务详情是快照，不刷新）
}
$('alarm-rows').addEventListener('click', e => { const tr = e.target.closest('tr[data-id]'); const a = tr && S.alarms.find(x => x.id === tr.dataset.id); if (a) { a.ack = true; refresh(); } });

// ---------------- 导航视图切换 ----------------
let currentView = 'plan', ganttMode = 'res';

function switchView(view) {
  currentView = view;
  document.querySelectorAll('#nav .navitem').forEach(a => a.classList.toggle('active', a.dataset.view === view));
  const layer = $('view-layer');
  if (view === 'plan') { layer.classList.remove('show'); return; }
  layer.classList.add('show');
  layer.querySelectorAll('.view').forEach(v => v.classList.toggle('on', v.dataset.view === view));
  if (view === 'gantt') renderGanttFull(ganttMode);
  else if (view === 'lineage') renderLineage();
  else if (view === 'process') renderProcess();
  else if (view === 'alarms') renderAlarms();
  else if (view === 'params') renderParams();
}
// 导航事件委托（按钮，兼容所有环境）
$('nav').addEventListener('click', e => {
  const item = e.target.closest('.navitem[data-view]');
  if (item) switchView(item.dataset.view);
});
document.querySelectorAll('[data-close]').forEach(b => b.addEventListener('click', () => switchView('plan')));
window.addEventListener('keydown', e => { if (e.key === 'Escape' && currentView !== 'plan') switchView('plan'); });
// 甘特视图内的模式切换（资源/订单）
$('view-layer').addEventListener('click', e => {
  const gt = e.target.closest('.view-tabs button[data-gt]');
  if (gt && currentView === 'gantt') { ganttMode = gt.dataset.gt; document.querySelectorAll('.view-tabs button[data-gt]').forEach(x => x.classList.toggle('on', x === gt)); renderGanttFull(ganttMode); }
});

// ---- 全屏排程甘特图 ----
function renderGanttFull(mode) {
  const plan = activePlan();
  const body = $('gantt-full');
  const kp = kpi(plan);
  const kpiHtml = `<div class="fg-kpi"><span>工件 ${kp.orders} · 工序 ${plan.length} · 流水线 ${MACHINES().length}</span><span>逾期 ${kp.tardy} 分</span><span>换型 ${S.live ? (S.snapshotKpi?.cur?.setups ?? 0) : '切割 ' + kp.cutSetups} 次</span><span>完工 ${fmtMin(kp.makespan)}</span></div>`;
  if (mode === 'res') {
    const ticks = []; for (let t = GANTT_T0; t <= GANTT_T1; t += 240) ticks.push(t);
    const axis = `<div class="fg-axis">${ticks.map(t => `<span style="left:${t / GANTT_T1 * 100}%">${fmtMin(t).slice(6)}</span>`).join('')}</div>`;
    const lanes = MACHINES().map(res => {
      const tasks = plan.filter(t => t.res === res).sort((a, b) => a.start - b.start);
      const bars = tasks.map(t => `<div class="fg-bar${t.frozen ? ' frozen' : ''}" style="left:${t.start / GANTT_T1 * 100}%;width:${Math.max((t.end - t.start) / GANTT_T1 * 100, 0.4)}%;background:${t.color}" title="${esc(orderInfo(t.order)?.code)} · ${esc(t.proc)} ${esc(procLabel(t.proc))} · ${fmtMin(t.start)}~${fmtMin(t.end)}"><span>${esc(orderInfo(t.order)?.code.slice(-3))} ${esc(t.proc)}</span></div>`).join('');
      return `<div class="fg-lane"><div class="fg-lbl">${esc(DEV[res].name)}<em>${esc(DEV[res].tag)}</em></div><div class="fg-track">${bars}</div></div>`;
    }).join('');
    body.innerHTML = kpiHtml + `<div class="fg-gantt">${axis}${lanes}<div class="fg-now" style="left:${Math.min(S.t, GANTT_T1) / GANTT_T1 * 100}%"></div></div>`;
  } else {
    const ticks = []; for (let t = GANTT_T0; t <= GANTT_T1; t += 240) ticks.push(t);
    const axis = `<div class="fg-axis">${ticks.map(t => `<span style="left:${t / GANTT_T1 * 100}%">${fmtMin(t).slice(6)}</span>`).join('')}</div>`;
    const lanes = ORDERS().filter(o => plan.some(t => t.order === o.id)).map(o => {
      const tasks = plan.filter(t => t.order === o.id).sort((a, b) => a.start - b.start);
      const bars = tasks.map(t => `<div class="fg-bar" style="left:${t.start / GANTT_T1 * 100}%;width:${Math.max((t.end - t.start) / GANTT_T1 * 100, 0.4)}%;background:${procColor(t.proc)}" title="${esc(o.code)} · ${esc(t.proc)} ${esc(procLabel(t.proc))} · ${fmtMin(t.start)}~${fmtMin(t.end)}"><span>${esc(t.proc)}</span></div>`).join('');
      const last = tasks.reduce((m, t) => Math.max(m, t.end), 0), overdue = !o.supplement && last > o.due;
      return `<div class="fg-lane"><div class="fg-lbl"><b style="color:${o.color}">${esc(orderShort(o))}</b><em>${esc(o.matName)} · ${o.qty}${U()} · 交期${o.dueStr}${overdue ? ' ⚠' : ''}</em></div><div class="fg-track">${bars}</div></div>`;
    }).join('');
    const procs = S.live ? Object.keys(S.procNames) : Object.keys(PROC_COLOR);
    const legend = `<div class="fg-proc-legend">${procs.map(p => `<span><i style="background:${procColor(p)}"></i>${esc(p)} ${esc(procLabel(p))}</span>`).join('')}</div>`;
    body.innerHTML = kpiHtml + `<div class="fg-gantt">${axis}${lanes}<div class="fg-now" style="left:${Math.min(S.t, GANTT_T1) / GANTT_T1 * 100}%"></div></div>` + legend;
  }
}

// ---- 数据血缘 ----
function renderLineage() {
  const src = (title, rows) => `<div class="lg-card"><div class="lg-card-t">${title}</div>${rows.map(r => `<div class="lg-row"><span>${r[0]}</span><em>${r[1]}</em></div>`).join('')}</div>`;
  $('lineage-body').innerHTML = `
  <div class="lineage">
    <div class="lg-col">
      <div class="lg-col-t">数据源（ERP / PLM / WMS / MES）</div>
      ${src('ERP 主数据与交易', [
        ['生产订单', 'jvs_production_order'], ['物料 / 库存', 'jvs_material'], ['制造 BOM', 'jvs_manufacture_bom'],
        ['生产资源', 'jvs_production_resource'], ['工作日历', 'jvs_work_calendar'], ['采购在途', 'jvs_incoming_material_order'],
      ])}
      ${src('PLM · WMS · MES', [
        ['工艺路线', 'jvs_process_route'], ['工序', 'jvs_process'], ['库存可用量', 'jvs_inventory_snapshot'],
        ['报工', 'jvs_work_report'], ['设备停机', 'RESOURCE_STATUS 事件'],
      ])}
    </div>
    <div class="lg-flow"><div class="lg-arrow">▶</div><div class="lg-flow-t">数据接入<br>三通道：快照 / 事件 / 拉取</div></div>
    <div class="lg-col">
      <div class="lg-col-t hl">排程计算（APS）</div>
      ${src('前置算法', [['MRP 齐套', 'BOM 展开 + 库存/在途扣减'], ['欠料 DAG', '→ notBefore 时间下界'], ['订单排序', '优先级↓ → 交期↑']])}
      ${src('求解引擎', [['任务生成', '订单 × 工序'], ['CP-SAT', '精确联合排程'], ['Timefold', 'FFD + 禁忌搜索'], ['约束', '7 硬约束 / 6 软约束'], ['冻结窗', '在制任务不可动']])}
      ${src('结果门禁', [['硬分数', '≥ 0 才可发布'], ['独立校验', 'Protocol.verify']])}
    </div>
    <div class="lg-flow"><div class="lg-arrow">▶</div><div class="lg-flow-t">回写</div></div>
    <div class="lg-col">
      <div class="lg-col-t hl">结果回写（MES / ERP）</div>
      ${src('向下游投递', [['排程结果', 'jvs_plan_task'], ['计划发布', 'PLAN_PUBLICATION'], ['工单下达', 'MES 执行']])}
      ${src('向上游应答', [['交期建议', 'CTP → ERP'], ['采购建议', 'PURCHASE_PROPOSAL'], ['报工回传', '→ 触发动态重排']])}
    </div>
  </div>`;
}

// ---- 工艺流程与 BOM ----
function renderProcess() {
  $('process-body').innerHTML = `
  <div class="proc-flow">
    <div class="pf-col">
      <div class="pf-t">原材料</div>
      <div class="pf-node raw">冷轧钢板<em>RM-STEEL · 5000kg</em></div>
      <div class="pf-node raw">高纯铜线<em>RM-COPPER · 3000kg</em></div>
      <div class="pf-node raw">环氧粉末<em>RM-POWDER · 800kg</em></div>
      <div class="pf-node raw">静音轴承<em>RM-BEARING · 1500个</em></div>
    </div>
    <div class="pf-col">
      <div class="pf-t">机壳支路 / 转子支路</div>
      <div class="pf-chain"><span class="pfc" style="--c:#4a90d9">CUT 切割</span><i>→</i><span class="pfc" style="--c:#e8a23d">WELD 焊接</span><i>→</i><span class="pfc" style="--c:#9b6bd6">COAT 喷涂</span></div>
      <div class="pf-node semi">机壳半成品<em>SF-HOUSING · 120件</em></div>
      <div class="pf-chain"><span class="pfc" style="--c:#e86a4a">WIND 绕线</span></div>
      <div class="pf-node semi">转子半成品<em>SF-ROTOR · 90件</em></div>
    </div>
    <div class="pf-col">
      <div class="pf-t">总装 / 测试</div>
      <div class="pf-chain"><span class="pfc" style="--c:#3db98b">ASSY 总装</span><i>→</i><span class="pfc" style="--c:#5a7a9a">TEST 测试</span></div>
      <div class="pf-node fg">变频风扇总成 A/B<em>FG-FAN-A · FG-FAN-B</em></div>
    </div>
  </div>
  <div class="proc-bom">
    <div class="bom-t">BOM 展开（jvs_manufacture_bom）</div>
    <div class="bom-tree">
      <div class="bom-node root">FG-FAN-A 风扇总成</div>
      <div class="bom-kids">
        <div class="bom-branch"><span>机壳 SF-HOUSING ×1</span><div class="bom-kids"><span class="bom-leaf">钢板 RM-STEEL ×2.5kg</span><span class="bom-leaf">粉末 RM-POWDER ×0.15kg</span></div></div>
        <div class="bom-branch"><span>转子 SF-ROTOR ×1</span><div class="bom-kids"><span class="bom-leaf">铜线 RM-COPPER ×1.2kg</span></div></div>
        <div class="bom-branch"><span>轴承 RM-BEARING ×2</span></div>
      </div>
    </div>
  </div>`;
}

// ---- 报警查询 ----
let alarmFilter = 'all';
function renderAlarms() {
  const all = Sim.S.alarms;
  const list = alarmFilter === 'all' ? all : all.filter(a => a.l === alarmFilter);
  const map = { a: '报警', w: '预警', i: '信息' };
  const rows = list.map(a => `<tr data-id="${a.id}"><td>${a.t.getFullYear()}/${two(a.t.getMonth() + 1)}/${two(a.t.getDate())}</td><td>${two(a.t.getHours())}:${two(a.t.getMinutes())}:${two(a.t.getSeconds())}</td><td>${a.text}</td><td>${map[a.l]}</td><td>${a.ack ? '已确认' : '未确认'}</td><td>${a.clr ? '已恢复' : map[a.l] + '中'}</td></tr>`).join('');
  $('alarms-body').innerHTML = `
    <div class="alarm-filters">${['all', 'w', 'a', 'i'].map(f => `<button data-f="${f}" class="${alarmFilter === f ? 'on' : ''}">${f === 'all' ? '全部' : map[f]}</button>`).join('')}</div>
    <table class="atable full"><thead><tr><th style="width:110px">日期</th><th style="width:100px">时间</th><th>消息文本</th><th style="width:70px">级别</th><th style="width:90px">确认</th><th style="width:90px">状态</th></tr></thead>
    <tbody>${rows || '<tr><td colspan="6" class="empty">暂无报警信息</td></tr>'}</tbody></table>`;
}
$('alarms-body')?.addEventListener('click', e => { const b = e.target.closest('button[data-f]'); if (b) { alarmFilter = b.dataset.f; renderAlarms(); } });

// ---- 参数设置 ----
function renderParams() {
  $('params-body').innerHTML = `
  <div class="params-grid">
    <div class="pg-card"><div class="pg-t">求解引擎</div>
      <div class="pg-row"><span>默认引擎</span><b>Timefold（FFD + 禁忌搜索）</b></div>
      <div class="pg-row"><span>精确引擎</span><b>OR-Tools CP-SAT（≤300 任务）</b></div>
      <div class="pg-row"><span>自适应</span><b>jDE × Timefold（参数搜索）</b></div>
      <div class="pg-row"><span>排产方向</span><b>正排 FORWARD</b></div>
      <div class="pg-row"><span>求解预算</span><b>120 秒（上限 3600）</b></div>
    </div>
    <div class="pg-card"><div class="pg-t">软约束权重（ConstraintWeightConfig）</div>
      <div class="pg-row"><span>优先级 priorityWeight</span><b>50</b></div>
      <div class="pg-row"><span>尽早完工 finishEarly</span><b>1</b></div>
      <div class="pg-row"><span>最小逾期 tardiness</span><b>1</b></div>
      <div class="pg-row"><span>负载均衡 balanceLoad</span><b>1</b></div>
      <div class="pg-row"><span>依赖间隙 minimizeGap</span><b>1</b></div>
      <div class="pg-row"><span>最小换型 setupTime</span><b>0（关闭）</b></div>
    </div>
    <div class="pg-card"><div class="pg-t">硬约束（7 条，违反即不可发布）</div>
      <div class="pg-row"><span>H1</span><b>任务必须分配资源</b></div>
      <div class="pg-row"><span>H2</span><b>资源资格</b></div>
      <div class="pg-row"><span>H3</span><b>同资源时间不重叠</b></div>
      <div class="pg-row"><span>H4</span><b>工序依赖 ES/EE</b></div>
      <div class="pg-row"><span>H5</span><b>物料最早可用 notBefore</b></div>
      <div class="pg-row"><span>H6</span><b>停机时间窗</b></div>
      <div class="pg-row"><span>H7</span><b>冻结任务不变</b></div>
    </div>
  </div>`;
}

// ---------------- 工具条 ----------------
function setDemo(v) { S.demo = v; $('demo-toggle').checked = v; if (v) Sim.warmup(330); refresh(); }
$('demo-toggle').addEventListener('change', e => setDemo(e.target.checked));
$('btn-fault').addEventListener('click', () => {
  if (!demo()) setDemo(true);
  const cand = MACHINES().find(i => run(i)) || MACHINES()[0];
  D[cand].fault = true; D[cand].run = false; Sim.evalAlarms(); refresh();
  engine.toast(`${DEV[cand].tag} ${DEV[cand].name} 模拟停机（MES 设备状态 → 触发动态重排）`);
});
$('btn-reset-fault').addEventListener('click', () => { MACHINES().forEach(i => (D[i].fault = false)); S.alarms.forEach(a => a.id.startsWith('f') && (a.clr = true)); Object.keys(S.active).forEach(k => k.startsWith('f') && delete S.active[k]); refresh(); });
document.querySelectorAll('.plan-views button').forEach(b => b.addEventListener('click', () => { engine.flyTo({ ...VIEWS[b.dataset.v], offset: VIEWS[b.dataset.v].offset || OFF }, 1400); document.querySelectorAll('.plan-views button').forEach(x => x.classList.toggle('on', x === b)); }));
$('fold').addEventListener('click', () => $('param-card').classList.toggle('collapsed'));
const stageEl = $('stage');
engine.onFrame(() => { const far = engine.getView().dist > 165; if (far !== stageEl.classList.contains('far')) stageEl.classList.toggle('far', far); });
{ const fsEl = () => document.fullscreenElement || document.webkitFullscreenElement; let saved = null;
  const sync = () => { const on = !!fsEl(); if (on === stageEl.classList.contains('only')) return; stageEl.classList.toggle('only', on);
    if (on) { saved = engine.getView(); engine.flyTo({ ...saved, dist: saved.dist * 0.7, offset: [0, 10] }, 700); } else if (saved) { engine.flyTo(saved, 700); saved = null; } };
  document.addEventListener('fullscreenchange', sync); document.addEventListener('webkitfullscreenchange', sync);
  $('fs-exit').addEventListener('click', () => (document.exitFullscreen || document.webkitExitFullscreen).call(document)); }

// ---------------- 启动 ----------------
// 演示态：URL 参数 ?plan=current / ?view=cut 直接进入对应画面
function bootDemo() {
  S.demo = QS.get('demo') !== '0';
  if (QS.get('plan')) S.plan = QS.get('plan') === 'current' ? 'current' : 'candidate';
  if (S.demo) Sim.warmup(+330);
  $('demo-toggle').checked = S.demo;
  document.querySelectorAll('.plan-tabs button').forEach(x => x.classList.toggle('on', x.dataset.plan === S.plan));
  boot();
}
// 嵌入宿主：等宿主注入当前租户的快照再建厂（见 twin:snapshot），这期间三维不渲染任何演示内容
if (EMBEDDED) $('wait').hidden = false; else bootDemo();
let last = performance.now();
setInterval(() => {
  if (!booted) return;
  const now = performance.now(), dt = Math.min((now - last) / 1000, 1); last = now;
  // 计划推演：虚拟时钟沿计划时间轴前进，设备/管线/甘特按计划时刻呈现
  if (demo() && QS.get('freeze') !== '1') Sim.step(dt);
  refresh();
}, 500);
if (QS.get('tab')) switchView(QS.get('tab'));


// ---------------- 宿主嵌入 API（iframe postMessage）----------------
// 宿主页（aps-ui 数字孪生 tab）通过这些指令驱动画面；独立打开时本段不生效，不影响原有交互。
const post = extra => { try { parent.postMessage({ twin: 'ack', ...extra }, '*'); } catch (_) {} };
// 决策卡摘要：卡片在宿主页，证据明细在孪生内，两边同一份 decisions()
const decisionSummary = () => decisions().map(x => ({ key: x.key, name: x.name, delta: x.delta, deltaClass: x.deltaClass, deltaLabel: x.deltaLabel, mode: x.mode }));
// KPI 对比：真实态用服务端算好的（与求解审计同源），演示态现算
function postKpi() {
  const norm = k => (k ? { orders: k.orders, unsched: k.unscheduled ?? k.unsched, tardy: k.tardyMinutes ?? k.tardy } : null);
  const k = S.live ? { cur: norm(S.snapshotKpi.cur), cand: norm(S.snapshotKpi.cand) } : { cur: kpi(PLAN_CURRENT()), cand: kpi(PLAN_CANDIDATE()) };
  post({ handled: 'kpi', kpi: { ...k, compare: S.live ? S.compare : true }, decisions: decisionSummary() });
}

window.addEventListener('message', e => {
  const d = e.data || {};
  switch (d.type) {
    case 'twin:ping': post({ ready: true }); break;
    case 'twin:view':
      if (VIEWS[d.view]) {
        engine.flyTo({ ...VIEWS[d.view], offset: VIEWS[d.view].offset || OFF }, 1200);
        document.querySelectorAll('.plan-views button').forEach(x => x.classList.toggle('on', x.dataset.v === d.view));
      }
      post({ handled: d.type }); break;
    case 'twin:tab': switchView(d.view || 'plan'); post({ handled: d.type }); break;
    case 'twin:plan': {
      // 没有候选计划时只认「当前计划」
      const next = d.plan === 'current' || (S.live && !S.compare) ? 'current' : 'candidate';
      const b = document.querySelector(`.plan-tabs button[data-plan="${next}"]`);
      if (b) b.click();
      postKpi();
      break;
    }
    case 'twin:fault': $('btn-fault').click(); post({ handled: d.type }); break;
    case 'twin:resetFault': $('btn-reset-fault').click(); post({ handled: d.type }); break;
    case 'twin:demo': setDemo(!!d.on); post({ handled: d.type }); break;
    case 'twin:empty': $('wait').hidden = true; post({ handled: d.type, loaded: false, live: false }); break;
    case 'twin:snapshot': {
      // 宿主注入当前租户的真实快照：第一次直接按它建厂；已建厂又来新快照则重载，由宿主在 ready 后重发
      if (booted) { post({ handled: d.type, reload: true }); location.reload(); break; }
      $('wait').hidden = true;
      Sim.loadSnapshot(d.snapshot);
      S.demo = true;                                  // 真实态默认开计划推演（设备/管线按计划时刻动起来）
      document.querySelectorAll('.plan-tabs button').forEach(x => x.classList.toggle('on', x.dataset.plan === S.plan));
      boot();
      post({ handled: d.type, loaded: true, live: true, compare: S.compare, candidate: S.candidate,
        unmodeled: Sim.S.unmodeled, stations: plant.selectables.filter(x => x.ids.length).length });
      postKpi();
      break;
    }
    case 'twin:decision':
      focusDecision(d.key);
      post({ handled: d.type, decisions: decisionSummary() });
      break;
  }
});
try { parent.postMessage({ twin: 'ready' }, '*'); } catch (_) {}
