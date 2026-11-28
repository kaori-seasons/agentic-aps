// ============================================================
// APS 数字孪生 · 数据仿真层（与画面无关）
// 把排程系统的领域模型映射进来（柔性作业车间 FJSP）：
//   工件 Job       = 订单（jvs_production_order，ERP）
//   工序 Operation = 订单 × 工序（CUT/WELD/COAT/WIND/ASSY/TEST）
//   流水线 Line    = 生产资源（jvs_production_resource，8 台）
//   柔性候选       = 每道工序可选多台流水线（QG1/QG2、ZZ1/ZZ2）
//   排程解         = 工序 → 流水线 + 时间窗（jvs_plan_task）
// 两个计划版本：当前已确认计划 / CP-SAT 优化候选（体现排产价值）
// ============================================================

const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const pr = (a, b) => [a, b];

// 时间加速：现实 1 秒 = 虚拟 6 分钟
export const SPEED = 6;
export const PLAN_BASE = '2026-04-16 08:00';

// ---------------- 可替换数据容器 ----------------
// 演示数据是默认值；loadSnapshot() 会用后端快照整体替换。
export const DATA = { machines: null, orders: null, materials: null, planCurrent: null, planCandidate: null };
export const MACHINES = () => DATA.machines;
export const ORDERS = () => DATA.orders;
export const MATERIALS = () => DATA.materials;
export const PLAN_CURRENT = () => DATA.planCurrent;
export const PLAN_CANDIDATE = () => DATA.planCandidate;

// ---------------- 设备台账 DEV（流水线 = 生产资源） ----------------
export const DEV = {};
const def = (id, o) => (DEV[id] = { id, ...o });
def('QG1', { kind: 'machine', tag: 'QG1', name: '切割机 1#', proc: 'CUT', procName: '机壳切割', thru: '10件/时', grp: '切割', cal: '前段两班', spec: [pr('资源编码', 'QG1'), pr('产能', '1 台'), pr('节拍', '10 件/时'), pr('工序', 'CUT 机壳切割')], lock: '机壳线首序；柔性候选 QG1/QG2；耗用钢板 2.5 kg/件' });
def('QG2', { kind: 'machine', tag: 'QG2', name: '切割机 2#', proc: 'CUT', procName: '机壳切割', thru: '12件/时', grp: '切割', cal: '前段两班', spec: [pr('资源编码', 'QG2'), pr('产能', '1 台'), pr('节拍', '12 件/时'), pr('工序', 'CUT 机壳切割')], lock: '柔性候选 QG1/QG2；换型归并后可专做 B 系' });
def('HJ1', { kind: 'machine', tag: 'HJ1', name: '焊接机 1#', proc: 'WELD', procName: '机壳焊接', thru: '8件/时', grp: '焊接', cal: '前段两班', spec: [pr('资源编码', 'HJ1'), pr('产能', '1 台'), pr('节拍', '8 件/时'), pr('工序', 'WELD 机壳焊接')], lock: '机壳线二序；ES 依赖切割完成' });
def('PQ1', { kind: 'machine', tag: 'PQ1', name: '喷涂线 1#', proc: 'COAT', procName: '表面喷涂', thru: '10件/时', grp: '喷涂', cal: '前段两班', spec: [pr('资源编码', 'PQ1'), pr('产能', '1 台'), pr('节拍', '10 件/时'), pr('工序', 'COAT 表面喷涂')], lock: '机壳线三序；耗用粉末 0.15 kg/件；换色换型 30-40 分钟' });
def('RL1', { kind: 'machine', tag: 'RL1', name: '绕线机 1#', proc: 'WIND', procName: '转子绕线', thru: '5件/时', grp: '绕线', cal: '前段两班', spec: [pr('资源编码', 'RL1'), pr('产能', '1 台'), pr('节拍', '5 件/时'), pr('工序', 'WIND 转子绕线')], lock: '转子支路（瓶颈工序）；耗用铜线 1.2 kg/件' });
def('ZZ1', { kind: 'machine', tag: 'ZZ1', name: '总装线 1#', proc: 'ASSY', procName: '总成装配', thru: '6件/时', grp: '总装', cal: '标准日班', spec: [pr('资源编码', 'ZZ1'), pr('产能', '1 台'), pr('节拍', '6 件/时'), pr('工序', 'ASSY 总成装配')], lock: '汇合机壳+转子+轴承；柔性候选 ZZ1/ZZ2' });
def('ZZ2', { kind: 'machine', tag: 'ZZ2', name: '总装线 2#', proc: 'ASSY', procName: '总成装配', thru: '6件/时', grp: '总装', cal: '标准日班', spec: [pr('资源编码', 'ZZ2'), pr('产能', '1 台'), pr('节拍', '6 件/时'), pr('工序', 'ASSY 总成装配')], lock: '柔性候选 ZZ1/ZZ2；负载均衡' });
def('CS1', { kind: 'machine', tag: 'CS1', name: '测试台 1#', proc: 'TEST', procName: '性能测试', thru: '12件/时', grp: '测试', cal: '标准日班', spec: [pr('资源编码', 'CS1'), pr('产能', '1 台'), pr('节拍', '12 件/时'), pr('工序', 'TEST 性能测试')], lock: '末序；ES 依赖总装完成' });

const DEMO_MACHINES = ['QG1', 'QG2', 'HJ1', 'PQ1', 'RL1', 'ZZ1', 'ZZ2', 'CS1'];

// ---------------- ERP：订单 jvs_production_order（工件 Job） ----------------
const DEMO_ORDERS = [
  { id: 'po-001', code: 'PO-260416-001', mat: 'FG-FAN-A', matName: '变频风扇总成A', qty: 20, due: 2040, dueStr: '04-18 18:00', pri: 5, color: '#8EC5FC' },
  { id: 'po-002', code: 'PO-260416-002', mat: 'FG-FAN-A', matName: '变频风扇总成A', qty: 16, due: 2160, dueStr: '04-18 20:00', pri: 4, color: '#A8E6CF' },
  { id: 'po-003', code: 'PO-260416-003', mat: 'FG-FAN-B', matName: '变频风扇总成B', qty: 12, due: 3120, dueStr: '04-19 12:00', pri: 3, color: '#FFD3B6' },
  { id: 'po-004', code: 'PO-260416-004', mat: 'FG-FAN-A', matName: '变频风扇总成A', qty: 18, due: 3480, dueStr: '04-19 18:00', pri: 2, color: '#D0BFFF' },
  { id: 'po-005', code: 'PO-260416-005', mat: 'FG-FAN-B', matName: '变频风扇总成B', qty: 14, due: 4560, dueStr: '04-20 12:00', pri: 2, color: '#FFAAA5', blocked: '缺静音轴承' },
  { id: 'po-006', code: 'PO-260416-006', mat: 'FG-FAN-A', matName: '变频风扇总成A', qty: 10, due: 4920, dueStr: '04-20 18:00', pri: 1, color: '#B5EAD7', blocked: '缺高纯铜线' },
];

const DEMO_MATERIALS = [
  { code: 'RM-STEEL', name: '冷轧钢板', unit: 'kg', inv: 5000, safety: 500, inbound: 1500, etaStr: '04-17 09:00' },
  { code: 'RM-POWDER', name: '环氧粉末涂料', unit: 'kg', inv: 800, safety: 100, inbound: 180, etaStr: '04-17 14:00' },
  { code: 'RM-COPPER', name: '高纯铜线', unit: 'kg', inv: 3000, safety: 300, inbound: 900, etaStr: '04-18 10:00' },
  { code: 'RM-BEARING', name: '静音轴承', unit: '个', inv: 1500, safety: 200, inbound: 500, etaStr: '04-18 16:00' },
  { code: 'SF-HOUSING', name: '风扇机壳半成品', unit: '件', inv: 120, safety: 20, inbound: 0, etaStr: '' },
  { code: 'SF-ROTOR', name: '风扇转子半成品', unit: '件', inv: 90, safety: 15, inbound: 0, etaStr: '' },
];

export const PROC_NAME = { CUT: '机壳切割', WELD: '机壳焊接', COAT: '表面喷涂', WIND: '转子绕线', ASSY: '总成装配', TEST: '性能测试' };

// ---------------- 当前已确认计划（demo-seed 的 jvs_plan_task，24 任务，4 订单） ----------------
const DEMO_PLAN_CURRENT = [
  { id: 'OD0001-cut', order: 'po-001', proc: 'CUT', res: 'QG1', start: 0, end: 120, qty: 20, frozen: false, color: '#8EC5FC' },
  { id: 'OD0001-weld', order: 'po-001', proc: 'WELD', res: 'HJ1', start: 150, end: 270, qty: 20, frozen: false, color: '#8EC5FC' },
  { id: 'OD0001-coat', order: 'po-001', proc: 'COAT', res: 'PQ1', start: 330, end: 450, qty: 20, frozen: false, color: '#8EC5FC' },
  { id: 'OD0001-wind', order: 'po-001', proc: 'WIND', res: 'RL1', start: 0, end: 240, qty: 20, frozen: false, color: '#8EC5FC' },
  { id: 'OD0001-assy', order: 'po-001', proc: 'ASSY', res: 'ZZ1', start: 1440, end: 1560, qty: 20, frozen: true, color: '#8EC5FC' },
  { id: 'OD0001-test', order: 'po-001', proc: 'TEST', res: 'CS1', start: 1590, end: 1650, qty: 20, frozen: false, color: '#8EC5FC' },
  { id: 'OD0002-cut', order: 'po-002', proc: 'CUT', res: 'QG2', start: 120, end: 210, qty: 16, frozen: false, color: '#A8E6CF' },
  { id: 'OD0002-weld', order: 'po-002', proc: 'WELD', res: 'HJ1', start: 390, end: 480, qty: 16, frozen: false, color: '#A8E6CF' },
  { id: 'OD0002-coat', order: 'po-002', proc: 'COAT', res: 'PQ1', start: 540, end: 630, qty: 16, frozen: false, color: '#A8E6CF' },
  { id: 'OD0002-wind', order: 'po-002', proc: 'WIND', res: 'RL1', start: 240, end: 420, qty: 16, frozen: false, color: '#A8E6CF' },
  { id: 'OD0002-assy', order: 'po-002', proc: 'ASSY', res: 'ZZ1', start: 1560, end: 1680, qty: 16, frozen: false, color: '#A8E6CF' },
  { id: 'OD0002-test', order: 'po-002', proc: 'TEST', res: 'CS1', start: 1770, end: 1830, qty: 16, frozen: false, color: '#A8E6CF' },
  { id: 'OD0003-cut', order: 'po-003', proc: 'CUT', res: 'QG2', start: 0, end: 75, qty: 12, frozen: false, color: '#FFD3B6' },
  { id: 'OD0003-weld', order: 'po-003', proc: 'WELD', res: 'HJ1', start: 300, end: 360, qty: 12, frozen: false, color: '#FFD3B6' },
  { id: 'OD0003-coat', order: 'po-003', proc: 'COAT', res: 'PQ1', start: 450, end: 510, qty: 12, frozen: false, color: '#FFD3B6' },
  { id: 'OD0003-wind', order: 'po-003', proc: 'WIND', res: 'RL1', start: 420, end: 540, qty: 12, frozen: false, color: '#FFD3B6' },
  { id: 'OD0003-assy', order: 'po-003', proc: 'ASSY', res: 'ZZ2', start: 1440, end: 1530, qty: 12, frozen: false, color: '#FFD3B6' },
  { id: 'OD0003-test', order: 'po-003', proc: 'TEST', res: 'CS1', start: 1650, end: 1695, qty: 12, frozen: false, color: '#FFD3B6' },
  { id: 'OD0004-cut', order: 'po-004', proc: 'CUT', res: 'QG1', start: 300, end: 390, qty: 18, frozen: false, color: '#D0BFFF' },
  { id: 'OD0004-weld', order: 'po-004', proc: 'WELD', res: 'HJ1', start: 510, end: 600, qty: 18, frozen: false, color: '#D0BFFF' },
  { id: 'OD0004-coat', order: 'po-004', proc: 'COAT', res: 'PQ1', start: 660, end: 750, qty: 18, frozen: false, color: '#D0BFFF' },
  { id: 'OD0004-wind', order: 'po-004', proc: 'WIND', res: 'RL1', start: 570, end: 750, qty: 18, frozen: false, color: '#D0BFFF' },
  { id: 'OD0004-assy', order: 'po-004', proc: 'ASSY', res: 'ZZ2', start: 1530, end: 1650, qty: 18, frozen: false, color: '#D0BFFF' },
  { id: 'OD0004-test', order: 'po-004', proc: 'TEST', res: 'CS1', start: 1830, end: 1890, qty: 18, frozen: false, color: '#D0BFFF' },
];

// ---------------- CP-SAT 优化候选计划（36 任务，6 订单全排入） ----------------
// 优化点：
//  1) 换型归并：OD0002-cut 由 QG2 移至 QG1 → QG1 专做 A 系、QG2 专做 B 系，切割区换型 1→0
//  2) 缺料到货后排入 PO-005/006：铜线 04-18 10:00(1560)、轴承 04-18 16:00(1920) 到货后自动排入
//  3) 负载均衡：ZZ1/ZZ2 分摊新增订单；瓶颈 RL1 连续作业
const DEMO_PLAN_CANDIDATE = [
  ...DEMO_PLAN_CURRENT.map(t => t.id === 'OD0002-cut' ? { ...t, res: 'QG1' } : t),
  // PO-006（FG-FAN-A，10台，交期 04-20 18:00，铜线 04-18 10:00 到）
  { id: 'OD0006-cut', order: 'po-006', proc: 'CUT', res: 'QG1', start: 1440, end: 1500, qty: 10, frozen: false, color: '#B5EAD7' },
  { id: 'OD0006-weld', order: 'po-006', proc: 'WELD', res: 'HJ1', start: 1530, end: 1605, qty: 10, frozen: false, color: '#B5EAD7' },
  { id: 'OD0006-coat', order: 'po-006', proc: 'COAT', res: 'PQ1', start: 1635, end: 1695, qty: 10, frozen: false, color: '#B5EAD7' },
  { id: 'OD0006-wind', order: 'po-006', proc: 'WIND', res: 'RL1', start: 1560, end: 1680, qty: 10, frozen: false, color: '#B5EAD7' },
  { id: 'OD0006-assy', order: 'po-006', proc: 'ASSY', res: 'ZZ1', start: 2880, end: 2980, qty: 10, frozen: false, color: '#B5EAD7' },
  { id: 'OD0006-test', order: 'po-006', proc: 'TEST', res: 'CS1', start: 3010, end: 3060, qty: 10, frozen: false, color: '#B5EAD7' },
  // PO-005（FG-FAN-B，14台，交期 04-20 12:00，轴承 04-18 16:00 到）
  { id: 'OD0005-cut', order: 'po-005', proc: 'CUT', res: 'QG2', start: 1440, end: 1510, qty: 14, frozen: false, color: '#FFAAA5' },
  { id: 'OD0005-weld', order: 'po-005', proc: 'WELD', res: 'HJ1', start: 1635, end: 1740, qty: 14, frozen: false, color: '#FFAAA5' },
  { id: 'OD0005-coat', order: 'po-005', proc: 'COAT', res: 'PQ1', start: 1725, end: 1809, qty: 14, frozen: false, color: '#FFAAA5' },
  { id: 'OD0005-wind', order: 'po-005', proc: 'WIND', res: 'RL1', start: 1680, end: 1848, qty: 14, frozen: false, color: '#FFAAA5' },
  { id: 'OD0005-assy', order: 'po-005', proc: 'ASSY', res: 'ZZ2', start: 2880, end: 3020, qty: 14, frozen: false, color: '#FFAAA5' },
  { id: 'OD0005-test', order: 'po-005', proc: 'TEST', res: 'CS1', start: 3050, end: 3120, qty: 14, frozen: false, color: '#FFAAA5' },
];

// ---------------- 计划激活与查询 ----------------
export const S = {
  t: 0, demo: true, alarms: [], active: {}, plan: 'candidate',
  // 真实快照态（loadSnapshot 填充）
  live: false, model: null, compare: false, slotOf: {}, unmodeled: [], procNames: {}, window: null,
  snapshotKpi: null, snapshotDecisions: null, candidate: null, generatedAt: null, supplyRows: [], stockBy: {},
};
export const D = {};
DEMO_MACHINES.forEach(id => (D[id] = { run: false, fault: false, auto: true, rt: 0 }));

export const activePlan = () => (S.plan === 'current' ? DATA.planCurrent : DATA.planCandidate);
export const run = id => S.demo && D[id].run && !D[id].fault;
export const orderInfo = id => DATA.orders.find(o => o.id === id);

// 当前激活计划中，某资源此刻执行的任务
export const taskOn = (res, t) => activePlan().find(k => k.res === res && t >= k.start && t < k.end) || null;
export const nextTaskOn = (res, t) => activePlan().filter(k => k.res === res && k.start >= t).sort((a, b) => a.start - b.start)[0] || null;

// ---------------- KPI（体现排产价值） ----------------
export function kpi(plan) {
  const orderSet = new Set(plan.map(t => t.order));
  const orders = orderSet.size;
  const unsched = DATA.orders.filter(o => !o.supplement).length - orders;
  // 逾期：订单最后一道工序的完成晚于交期（按订单聚合，不按工序名写死）
  const lastEnd = {};
  plan.forEach(t => { lastEnd[t.order] = Math.max(lastEnd[t.order] || 0, t.end); });
  const tardy = Object.entries(lastEnd).reduce((sum, [oid, end]) => sum + Math.max(0, end - (orderInfo(oid)?.due || 0)), 0);
  const makespan = plan.length ? Math.max(...plan.map(t => t.end)) : 0;
  // 切割区换型（工装更换最贵，行业方案 S6 关注）。演示数据专用；
  // 真实数据的换型次数由服务端按全设备相邻任务统计，不经此函数。
  let cutSetups = 0;
  ['QG1', 'QG2'].forEach(res => {
    const q = plan.filter(t => t.res === res).sort((a, b) => a.start - b.start);
    for (let i = 1; i < q.length; i++) if (orderInfo(q[i - 1].order)?.mat !== orderInfo(q[i].order)?.mat) cutSetups++;
  });
  // 资源负载（标准差，越小越均衡）
  const loads = DATA.machines.map(res => plan.filter(t => t.res === res).reduce((s, t) => s + (t.end - t.start), 0));
  const avg = loads.reduce((a, b) => a + b, 0) / loads.length;
  const balance = Math.sqrt(loads.reduce((s, l) => s + (l - avg) ** 2, 0) / loads.length);
  return { orders, unsched, tardy, makespan, cutSetups, balance, loads };
}

// 时间基准：演示数据固定 04-16 08:00；真实快照取计划最早开始时刻
export const BASE = { at: new Date('2026-04-16T08:00:00') };
const pad = n => String(n).padStart(2, '0');
function fmtAt(date) { return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`; }
export function fmtMin(m) { return fmtAt(new Date(BASE.at.getTime() + m * 60000)); }
export function fmtClock(m) { return fmtAt(new Date(BASE.at.getTime() + m * 60000)); }
export function fullClock(m) { const d = new Date(BASE.at.getTime() + m * 60000); return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())}  ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`; }

export function emit(l, text) { S.alarms.unshift({ id: 'e' + Math.random(), l, text, t: new Date(), ack: l === 'i', clr: l === 'i' }); S.alarms.length = Math.min(S.alarms.length, 40); }

// ---------------- 报警判定（映射排程约束） ----------------
export function evalAlarms() {
  const now = {};
  // 缺料：激活计划中未排入的订单（齐套不满足 → 门禁拦截）
  const scheduledOrders = new Set(activePlan().map(t => t.order));
  DATA.orders.filter(o => !o.supplement && !scheduledOrders.has(o.id)).forEach(o => {
    now['block-' + o.id] = { l: 'w', text: `${o.short || o.code} ${o.blocked ? '齐套不足（' + o.blocked + '）' : S.live ? '未排入当前计划' : '未进入排产'}` };
  });
  DATA.machines.forEach(id => { if (D[id]?.fault) now['f' + id] = { l: 'a', text: `${DEV[id].tag} ${DEV[id].name} 故障停机（MES 设备状态）` }; });
  Object.keys(now).forEach(k => { if (!S.active[k]) { const a = { id: k, ...now[k], t: new Date(), ack: false, clr: false }; S.alarms.unshift(a); S.active[k] = a; } else S.active[k].l = now[k].l; });
  Object.keys(S.active).forEach(k => { if (!now[k]) { S.active[k].clr = true; delete S.active[k]; } });
}

// ---------------- 仿真推进 ----------------
export function step(dt) {
  if (!S.demo) return;
  S.t += dt * SPEED;
  DATA.machines.forEach(id => { const tk = taskOn(id, S.t); if (D[id]) D[id].run = !!tk && !D[id].fault; });
  evalAlarms();
}

export function warmup(targetMin = 330) {
  S.t = 0;
  for (let i = 0; i < 2000 && S.t < targetMin; i++) { S.t += SPEED * 1.0; }
  DATA.machines.forEach(id => { const tk = taskOn(id, S.t); if (D[id]) D[id].run = !!tk && !D[id].fault; });
  S.alarms.length = 0; S.active = {};
  evalAlarms();
}


// ---------------- 装入演示数据（默认） ----------------
DATA.machines = DEMO_MACHINES; DATA.orders = DEMO_ORDERS; DATA.materials = DEMO_MATERIALS;
DATA.planCurrent = DEMO_PLAN_CURRENT; DATA.planCandidate = DEMO_PLAN_CANDIDATE;

// ---------------- 真实快照装载 ----------------
// 后端 /digital-twin/snapshot：resources / orders / supplies / current / candidate / kpi* / decisions。
// 时间统一折成相对「计划最早开始」的分钟数，复用演示数据的时间轴逻辑。

/**
 * 三维场景的 8 个工位（示意几何，按工艺流向排列）。真实设备按三步落位：
 *   ① 资源编码 = 工位编码（e2e 种子即按此车间建模：QG1…CS1）
 *   ② 分组 / 名称 / 工序编码命中工位关键字（切割、焊接、喷涂、总装、测试…）
 *   ③ 其余设备按各自在订单工艺链中的平均序位，顺工艺流填入空工位
 * 三步都落不下的设备如实记为「未建模」，只出现在甘特里，不出现在三维场景里。
 */
export const SLOTS = [
  { id: 'QG1', view: 'cut', kw: ['切割', '下料', '开料', 'CUT'] },
  { id: 'QG2', view: 'cut', kw: ['切割', '下料', '开料', 'CUT'] },
  { id: 'HJ1', view: 'shell', kw: ['焊接', 'WELD'] },
  { id: 'PQ1', view: 'shell', kw: ['喷涂', '涂装', '喷漆', '表面', 'COAT', 'PAINT'] },
  { id: 'RL1', view: 'wind', kw: ['绕线', '预装', '线圈', '部件', 'WIND'] },
  { id: 'ZZ1', view: 'assy', kw: ['总装', '装配', '组装', 'ASSY'] },
  { id: 'ZZ2', view: 'assy', kw: ['总装', '装配', '组装', 'ASSY'] },
  { id: 'CS1', view: 'assy', kw: ['测试', '检测', '检验', 'TEST'] },
];

const fmtNum = v => (v === null || v === undefined || v === '' ? '' : String(Number(v)));
const withUnit = (v, unit) => (`${fmtNum(v)} ${unit || ''}`).trim();

/** 设备 → 工位：编码 → 关键字 → 工艺顺序；返回 slotOf（资源 id → 工位）与未建模资源 id。 */
function bindStations(resources, tasks) {
  const used = new Set(tasks.map(t => t.resourceId));
  const res = resources.filter(r => used.has(r.id)).slice().sort((a, b) => String(a.code).localeCompare(String(b.code)));
  const slotOf = {}, taken = new Set();
  for (const r of res) {
    const s = SLOTS.find(x => x.id === String(r.code || '').toUpperCase() && !taken.has(x.id));
    if (s) { slotOf[r.id] = s.id; taken.add(s.id); }
  }
  const procsOn = {};
  tasks.forEach(t => (procsOn[t.resourceId] = procsOn[t.resourceId] || new Set()).add(t.processCode));
  for (const r of res) {
    if (slotOf[r.id]) continue;
    const hay = `${r.group || ''} ${r.name || ''} ${[...(procsOn[r.id] || [])].join(' ')}`.toUpperCase();
    const s = SLOTS.find(x => !taken.has(x.id) && x.kw.some(k => hay.includes(k.toUpperCase())));
    if (s) { slotOf[r.id] = s.id; taken.add(s.id); }
  }
  const rest = res.filter(r => !slotOf[r.id]);
  if (rest.length) {
    // 平均序位：设备上的任务在各自订单工艺链里排第几（0 起），越小越靠前道
    const chains = {};
    tasks.forEach(t => (chains[t.orderCode] = chains[t.orderCode] || []).push(t));
    const sum = {}, cnt = {};
    Object.values(chains).forEach(q => q.slice().sort((a, b) => a.start.localeCompare(b.start))
      .forEach((t, i) => { sum[t.resourceId] = (sum[t.resourceId] || 0) + i; cnt[t.resourceId] = (cnt[t.resourceId] || 0) + 1; }));
    rest.sort((a, b) => (sum[a.id] || 0) / (cnt[a.id] || 1) - (sum[b.id] || 0) / (cnt[b.id] || 1) || String(a.code).localeCompare(String(b.code)));
    for (const r of rest) {
      const s = SLOTS.find(x => !taken.has(x.id));
      if (s) { slotOf[r.id] = s.id; taken.add(s.id); }
    }
  }
  return { slotOf, unmodeled: res.filter(r => !slotOf[r.id]).map(r => r.id) };
}

export function loadSnapshot(snap) {
  const hasCandidate = !!snap.hasCandidate && (snap.candidate || []).length > 0;
  const current = (snap.current || []).filter(t => t.start && t.end);
  const candidate = hasCandidate ? (snap.candidate || []).filter(t => t.start && t.end) : current;
  const all = [...current, ...candidate];
  const base = all.length ? Math.min(...all.map(t => Date.parse(t.start.replace(' ', 'T')))) : Date.now();
  BASE.at = new Date(base);
  const toMin = iso => (iso ? Math.round((Date.parse(iso.replace(' ', 'T')) - base) / 60000) : null);

  // 订单族：补充订单由父订单的 MRP 生成，与主订单同色，甘特里一眼看出属于哪条交付
  const palette = ['#8EC5FC', '#A8E6CF', '#FFD3B6', '#D0BFFF', '#FFAAA5', '#B5EAD7', '#F6C9FF', '#FFE6A7'];
  const colorOf = {};
  (snap.orders || []).filter(o => !o.supplement).forEach((o, i) => (colorOf[o.code] = palette[i % palette.length]));
  const orders = (snap.orders || []).map(o => {
    const parent = o.supplement ? o.parentOrderCode || o.code : o.code;
    const suffix = o.supplement && o.parentOrderCode ? o.code.slice(o.parentOrderCode.length).replace(/^[-_]/, '') : '';
    const due = toMin(o.deliveryTime);
    return {
      id: o.code, code: o.code, parent, supplement: !!o.supplement,
      short: suffix ? `${o.parentOrderCode} ${suffix.replace(/^SF-/, '')}` : o.code,
      mat: o.materialCode, matName: o.materialName || o.materialCode || '', unit: o.unit || '', qty: Number(o.quantity || 0),
      due, dueStr: due === null ? '—' : fmtMin(due), pri: o.priority ?? 0, color: colorOf[parent] || '#C2C5CF',
    };
  });
  const orderById = Object.fromEntries(orders.map(o => [o.id, o]));

  // 设备 → 工位；任务统一用工位 id 表示落点（未建模的设备保留真实 id，只在甘特里出现）
  const resources = snap.resources || [];
  const resById = Object.fromEntries(resources.map(r => [r.id, r]));
  const { slotOf, unmodeled } = bindStations(resources, all);
  S.procNames = {};
  all.forEach(t => { if (t.processCode && t.processName) S.procNames[t.processCode] = t.processName; });
  const procsOn = {};
  const toTask = t => {
    const res = slotOf[t.resourceId] || t.resourceId;
    if (t.processCode) (procsOn[res] = procsOn[res] || new Set()).add(t.processCode);
    return {
      id: t.id, order: t.orderCode, proc: t.processCode || '—', res, start: toMin(t.start), end: toMin(t.end),
      qty: Number(t.quantity || 0), frozen: !!t.frozen, status: t.status || '', color: orderById[t.orderCode]?.color || '#C2C5CF',
      // 消耗物料：MRP 展开后写在任务上（{物料编码, 数量}），到货 → 齐套 → 开工 的对账依据
      inputs: (t.inputs || []).map(i => ({ code: i.materialCode, qty: Number(i.quantity || 0) })).filter(i => i.code),
    };
  };
  DATA.orders = orders;
  DATA.planCurrent = current.map(toTask);
  DATA.planCandidate = candidate.map(toTask);
  const slotIds = SLOTS.map(x => x.id).filter(id => Object.values(slotOf).includes(id));
  DATA.machines = [...slotIds, ...unmodeled];
  const bySlot = Object.fromEntries(Object.entries(slotOf).map(([rid, slot]) => [slot, rid]));
  DATA.machines.forEach(id => {
    const r = resById[bySlot[id] || id] || {};
    const procs = [...(procsOn[id] || [])];
    const slot = SLOTS.find(x => x.id === id);
    DEV[id] = {
      id, kind: 'machine', tag: r.code || id, name: r.name || r.code || id, slot: slot ? id : null, view: slot ? slot.view : 'all',
      proc: procs.join('/'), procName: procs.map(p => `${p} ${S.procNames[p] || ''}`.trim()).join(' / '),
      thru: r.throughput || '', grp: r.group || '', cal: '', spec: [], lock: '',
      capacity: r.capacity, unit: r.unit || '', status: r.status || '',
    };
    if (!D[id]) D[id] = { run: false, fault: false, auto: true, rt: 0 };
  });

  // 来料：同一物料多批在途归并，取最早到货时刻；库存未知就不编（inv 留 0，界面不展示库存牌）
  const byMat = {};
  S.supplyRows = [];
  (snap.supplies || []).forEach(s => {
    const etaMin = toMin(s.eta);
    S.supplyRows.push({ code: s.code, materialCode: s.materialCode, name: s.materialName || s.materialCode, unit: s.unit || '', qty: Number(s.quantity || 0), etaMin, etaStr: etaMin === null ? '' : fmtMin(etaMin) });
    const m = (byMat[s.materialCode] = byMat[s.materialCode] || {
      code: s.materialCode, name: s.materialName || s.materialCode, unit: s.unit || '', inv: 0, safety: 0, inbound: 0, eta: null, etaStr: '', etaMin: null,
    });
    m.inbound += Number(s.quantity || 0);
    if (s.eta && (!m.eta || s.eta < m.eta)) { m.eta = s.eta; m.etaMin = toMin(s.eta); m.etaStr = fmtMin(m.etaMin); }
  });
  DATA.materials = Object.values(byMat);
  // 物料台账（库存 / 安全库存 / 采购周期），按编码索引
  S.stockBy = Object.fromEntries((snap.materials || []).map(m => [m.code, {
    code: m.code, name: m.name || m.code, unit: m.unit || '', stock: Number(m.stock || 0), safety: Number(m.safetyStock || 0), leadTime: m.leadTime || '',
  }]));
  DATA.materials.forEach(m => { m.inv = S.stockBy[m.code]?.stock || 0; m.safety = S.stockBy[m.code]?.safety || 0; });

  // 时间窗：从「更早的 当前时刻/到货时刻」到计划结束。来料时间线按这个窗口画，到货点才不会被挤到起点
  const end = Math.max(0, ...DATA.planCurrent.map(t => t.end), ...DATA.planCandidate.map(t => t.end));
  const etas = DATA.materials.map(m => m.etaMin).filter(m => m !== null);
  const now = Math.round((Date.now() - base) / 60000);
  S.window = { now, w0: Math.min(0, now, ...etas), w1: Math.max(end * 1.04, ...etas, 480), start: 0, end };
  S.t = 0; S.alarms.length = 0; S.active = {};
  S.plan = hasCandidate ? 'candidate' : 'current';
  S.compare = hasCandidate;
  S.slotOf = slotOf; S.unmodeled = unmodeled.map(id => DEV[id].name);
  S.snapshotKpi = { cur: snap.kpiCurrent, cand: hasCandidate ? snap.kpiCandidate : null };
  S.snapshotDecisions = snap.decisions;
  S.candidate = { source: snap.candidateSource || 'PENDING', at: snap.candidateAt };
  S.generatedAt = snap.generatedAt;
  S.live = true;
  S.model = liveModel({ orders, orderById });
  return DATA;
}
export function hasSnapshot() { return !!S.live; }

/** 把装载好的真实数据整理成三维布局模型：工位、原料堆、半成品缓冲、成品库，管线按真实工艺链生成。 */
function liveModel({ orders, orderById }) {
  const stations = SLOTS.filter(x => DATA.machines.includes(x.id)).map(x => {
    const d = DEV[x.id];
    const facts = [['工序', d.procName], ['分组', d.grp], ['产能', withUnit(d.capacity, d.unit)], ['节拍', d.thru], ['状态', d.status]].filter(r => r[1]);
    return { slot: x.id, name: d.name, tag: d.tag, facts };
  });
  const raws = DATA.materials.filter(m => m.inbound > 0).sort((a, b) => (a.etaMin ?? Infinity) - (b.etaMin ?? Infinity))
    .map(m => ({ name: m.name, code: m.code, unit: m.unit, qty: m.inbound, eta: m.etaStr, rows: [
      ['库存 / 安全库存', `${withUnit(m.inv, m.unit)} / ${withUnit(m.safety, m.unit)}`], ['在途', `${withUnit(m.inbound, m.unit)}（${S.supplyRows.filter(p => p.materialCode === m.code).length} 批）`], ['到货', m.etaStr || '—'],
    ] }));
  // 半成品 = 补充订单的产出物料；成品 = 主订单的产出物料
  const semi = {};
  orders.filter(o => o.supplement).forEach(o => {
    const b = (semi[o.mat] = semi[o.mat] || { code: o.mat, name: o.matName, unit: o.unit, qty: 0, parents: [] });
    b.qty += o.qty;
    if (o.parent && !b.parents.includes(o.parent)) b.parents.push(o.parent);
  });
  const buffers = Object.values(semi).slice(0, 2).map(b => ({ ...b, rows: [['来源', '补充订单生产'], ['计划产出', withUnit(b.qty, b.unit)]] }));
  const finished = {};
  orders.filter(o => !o.supplement).forEach(o => (finished[o.mat] = finished[o.mat] || { code: o.mat, name: o.matName, unit: o.unit, qty: 0 }).qty += o.qty);
  const fgList = Object.values(finished);
  const fg = fgList.length ? { name: fgList.length === 1 ? fgList[0].name : '成品', rows: fgList.map(f => [f.code, withUnit(f.qty, f.unit)]) } : null;

  // 管线：按两版计划里的真实工艺链生成（同一条链在两版里落到不同工位时两条都画）
  const bufOf = Object.fromEntries(buffers.map((b, i) => [b.code, 'buf:' + i]));
  const pairs = new Map(), entries = new Set();
  const add = (from, to, act = to) => { if (from !== to) pairs.set(from + '>' + to, { from, to, act }); };
  for (const plan of [DATA.planCurrent, DATA.planCandidate]) {
    const chains = {};
    plan.forEach(t => (chains[t.order] = chains[t.order] || []).push(t));
    for (const [oid, q] of Object.entries(chains)) {
      const o = orderById[oid], seq = q.slice().sort((a, b) => a.start - b.start), buf = o && o.supplement ? bufOf[o.mat] : null;
      seq.forEach((t, i) => {
        if (seq[i + 1]) add(t.res, seq[i + 1].res);
        if (buf && i === seq.length - 1) add(t.res, buf, t.res);       // 补充订单末道工序 → 半成品缓冲
        if (i === 0) entries.add(t.res);                                 // 首道工序从原料区进料
      });
    }
  }
  buffers.forEach((b, i) => b.parents.forEach(p => [DATA.planCurrent, DATA.planCandidate].forEach(plan => {
    const first = plan.filter(t => t.order === p).sort((a, b) => a.start - b.start)[0];
    if (first) add('buf:' + i, first.res, first.res);                    // 缓冲 → 消耗它的主订单首道工序
  })));
  const hasOut = new Set([...pairs.values()].map(p => p.from));
  const kindOf = p => (p.to === 'fg' || p.to === 'CS1' ? 'fan' : p.to === 'buf:1' || p.from === 'buf:1' ? 'rotor' : 'housing');
  const pipes = [...entries].sort().map((to, i) => ({ from: 'raw', to, act: to, kind: 'steel', lane: -0.9 + i * 0.36 }))
    .concat([...pairs.values()].map(p => ({ ...p, kind: kindOf(p) })));
  new Set([...DATA.planCurrent, ...DATA.planCandidate].map(t => t.res).filter(r => !hasOut.has(r))).forEach(r => pipes.push({ from: r, to: 'fg', act: r, kind: 'fan' }));
  return { live: true, stations, raws, buffers, fg, pipes };
}
