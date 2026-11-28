// ============================================================
// 排产决策层：从 sim 数据派生出「算法在本车间做了什么」的量化证据。
//   三张决策卡 ↔ 排产求解的三类价值：
//     1) 齐套门禁   —— 来料到货时刻成为被挡订单的最早开工时刻（notBefore）
//     2) 换型归并   —— 同系物料归并到同一台设备，消灭切换（setup）
//     3) 负载均衡   —— 柔性候选资源分摊，压低负载标准差（balance）
//   来料时间线 ↔ 来料计划（jvs_incoming_material_order）：每个在途到货点在哪一刻。
// 两种数据态：
//   演示态  —— 数字由 sim 现算（切割区换型、三条线负载），讲的是 demo-seed 那套车间
//   真实态  —— 数字取服务端 /digital-twin/snapshot 算好的 decisions，口径与求解审计同源
// 纯函数、无副作用；渲染与联动由 main.js 调用。
// ============================================================
import * as Sim from './sim.js';
import { ORDERS, MATERIALS, PLAN_CURRENT, PLAN_CANDIDATE, kpi, S, DEV, BASE } from './sim.js';

const pct = (a, b) => (a === 0 ? 0 : Math.round((a - b) / a * 100));
// 改善绿 / 持平灰 / 恶化红：只比较，不替数据说好话
const cls = (before, after) => (after === before ? 'flat' : after < before ? 'good' : 'bad');

/** 每张卡：名称、收益数字、当前计划→候选的证据行、联动资源 */
export function decisions() {
  if (S.live) return realDecisions(S.snapshotDecisions);

  const CR = kpi(PLAN_CURRENT()), CD = kpi(PLAN_CANDIDATE());
  const blockedCur = ORDERS().filter(o => !PLAN_CURRENT().some(t => t.order === o.id));
  const blockedCand = ORDERS().filter(o => !PLAN_CANDIDATE().some(t => t.order === o.id));

  // 证据1：哪张单被哪批料挡住，到货后何时开工
  const gateRows = blockedCur.map(o => {
    const eta = MATERIALS().find(m => m.code === (o.id === 'po-005' ? 'RM-BEARING' : 'RM-COPPER'));
    const firstTask = PLAN_CANDIDATE().filter(t => t.order === o.id).sort((a, b) => a.start - b.start)[0];
    return { order: o.code, blocked: o.blocked, eta: eta ? eta.etaStr : '—', start: firstTask ? firstTask.start : null };
  });

  // 证据2：切割区换型发生在哪两单之间
  const cutSeq = res => PLAN_CANDIDATE().filter(t => t.res === res).sort((a, b) => a.start - b.start).map(t => t.order);
  const setupRows = ['QG1', 'QG2'].map(res => {
    const curSeq = PLAN_CURRENT().filter(t => t.res === res).sort((a, b) => a.start - b.start).map(t => t.order);
    const mats = curSeq.map(id => ORDERS().find(o => o.id === id).mat.replace('FG-FAN-', ''));
    return {
      res, name: DEV[res].name,
      cur: mats.join(' → ') || '—',
      cand: cutSeq(res).map(id => ORDERS().find(o => o.id === id).mat.replace('FG-FAN-', '')).join(' → ') || '—'
    };
  });

  // 证据3：两条总装线的负载分摊（分钟）
  const load = (plan, res) => plan.filter(t => t.res === res).reduce((s, t) => s + (t.end - t.start), 0);
  const balanceRows = ['ZZ1', 'ZZ2', 'RL1'].map(res => ({
    res, name: DEV[res].name,
    cur: load(PLAN_CURRENT(), res), cand: load(PLAN_CANDIDATE(), res)
  }));

  return [
    {
      key: 'gate', name: '齐套门禁 · 到货即排', mode: 'compare',
      delta: `${blockedCur.length}→${blockedCand.length}`, deltaClass: cls(blockedCur.length, blockedCand.length),
      deltaLabel: '未排订单',
      why: 'MRP 按在库与可用在途扣减，缺口订单不硬排；来料到货时刻成为其最早开工时刻',
      rows: gateRows.map(r => [`${r.order.replace('PO-260416-', '')} ${r.blocked}`, `到货 ${r.eta} → 开工 ${r.start !== null ? fmt(r.start) : '未排'}`]),
      note: '交期不被“承诺”，而是被物料的真实可用时刻约束——排产回答的是“什么时候真能干”。',
      focus: { resources: ['QG1', 'QG2'], orders: blockedCand.length ? [] : blockedCur.map(o => o.id), view: 'cut' }
    },
    {
      key: 'setup', name: '换型归并 · 同系同机', mode: 'compare',
      delta: `${CR.cutSetups}→${CD.cutSetups}`, deltaClass: cls(CR.cutSetups, CD.cutSetups),
      deltaLabel: '切割换型',
      why: '在柔性候选资源间按物料系归并，把切换次数压到最低，同时不恶化交期',
      rows: setupRows.map(r => [r.name, `${r.cur} ⇒ ${r.cand}`]),
      note: '每次换型都是停机换工装的时间；归并后 QG1 专做 A 系、QG2 专做 B 系，换型归零。',
      focus: { resources: ['QG1', 'QG2'], orders: [], view: 'cut' }
    },
    {
      key: 'balance', name: '负载均衡 · 柔性分摊', mode: 'compare',
      delta: pct(CR.balance, CD.balance) + '%', deltaClass: CD.balance < CR.balance ? 'good' : 'flat',
      deltaLabel: '负载离散',
      why: '两台总装线是可互换的柔性资源，求解器把新增订单分摊到空闲线，瓶颈绕线机连续作业',
      rows: balanceRows.map(r => [r.name, `${r.cur} 分 ⇒ ${r.cand} 分`]),
      note: '均衡不是平均：绕线机是瓶颈，保持满载；总装线分摊，避免一边排队一边空闲。',
      focus: { resources: ['ZZ1', 'ZZ2', 'RL1'], orders: [], view: 'assy' }
    }
  ];
}

// 真实快照的时刻：相对计划最早开始的分钟数 → MM-DD HH:mm
const clock = m => Sim.fmtMin(m);
function fmt(m) {
  const total = 8 * 60 + m, d = 16 + Math.floor(total / 1440), hh = Math.floor((total % 1440) / 60), mm = total % 60;
  return `04-${d} ${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}`;
}

// 真实快照：服务端已算好三类决策的数字与证据，这里只映射成卡片，并把证据落到三维工位上
function realDecisions(d) {
  d = d || { evidence: [] };
  const ev = k => d.evidence.filter(r => r.decision === k);
  const slots = rows => [...new Set(rows.map(r => r.resourceId && S.slotOf[r.resourceId]).filter(Boolean))];
  const cmp = S.compare;
  const pair = (before, after) => (cmp ? `${before}→${after}` : String(before));
  const gate = ev('gate'), setup = ev('setup'), bal = ev('balance');
  const mode = cmp ? 'compare' : 'single';
  // 门禁的另一面：计划期内到货的物料，把首个（到货后才开工的）消耗工序压在到货时刻之后
  const w = S.window || {};
  const plan = S.plan === 'current' ? PLAN_CURRENT() : PLAN_CANDIDATE();
  const arrivals = (S.supplyRows || []).filter(p => p.etaMin !== null && p.etaMin >= (w.start ?? 0) && p.etaMin <= (w.end ?? Infinity));
  const gateArrivals = arrivals.map(a => ({
    a, first: plan.filter(t => t.start >= a.etaMin && (t.inputs || []).some(i => i.code === a.materialCode)).sort((x, y) => x.start - y.start)[0],
  }));
  const gateRows = gate.length ? gate.map(r => [r.order, `${r.material || ''} · 交期 ${(r.delivery || '—').replace('T', ' ').slice(5, 16)}${cmp ? (r.scheduledAfter ? ' · 候选已排入' : ' · 候选仍未排') : ''}`])
    : gateArrivals.length ? gateArrivals.map(({ a, first }) => [`${a.name} · 到货 ${a.etaStr}`, first ? `首个消耗工序 ${S.procNames[first.proc] || first.proc} · ${DEV[first.res]?.name || first.res} 开工 ${clock(first.start)}` : '计划内没有工序消耗它'])
    : [['—', '没有未排订单，计划期内也没有在途到货']];
  return [
    { key: 'gate', name: '齐套门禁 · 到货即排', mode,
      delta: pair(d.gateBlocked, d.gateBlockedAfter), deltaClass: cls(d.gateBlocked, cmp ? d.gateBlockedAfter : d.gateBlocked), deltaLabel: '未排订单',
      why: 'MRP 按在库与可用在途扣减，缺口订单不硬排；来料到货时刻成为其最早开工时刻',
      rows: gateRows,
      note: '交期不被“承诺”，而是被物料的真实可用时刻约束；点时间线上的到货点可看这批料被哪些工序消耗。',
      focus: { resources: gateArrivals.map(g => g.first?.res).filter(Boolean), orders: [], view: 'auto' } },
    { key: 'setup', name: '换型归并 · 同系同机', mode,
      delta: pair(d.setupsBefore, d.setupsAfter), deltaClass: cls(d.setupsBefore, cmp ? d.setupsAfter : d.setupsBefore), deltaLabel: '换型次数',
      why: '在柔性候选资源间按产品归并，压低切换次数，同时不恶化交期',
      rows: setup.length ? setup.map(r => [r.resource, cmp ? `换型 ${r.before} ⇒ ${r.after}` : `换型 ${r.before}`]) : [['—', '当前计划没有换型']],
      note: '每次换型都是停机换工装的时间。',
      focus: { resources: slots(setup), orders: [], view: 'auto' } },
    { key: 'balance', name: '负载均衡 · 柔性分摊', mode,
      delta: cmp ? `${d.balanceDeltaPercent}%` : `${Math.round(d.balanceBefore)} 分`, deltaClass: cls(d.balanceBefore, cmp ? d.balanceAfter : d.balanceBefore), deltaLabel: cmp ? '负载离散' : '负载标准差',
      why: '可互换的柔性资源分摊负载，瓶颈设备保持连续作业',
      rows: bal.map(r => [r.resource, cmp ? `${r.before} 分 ⇒ ${r.after} 分` : `${r.before} 分`]),
      note: '均衡不是平均：瓶颈满载，其余分摊。',
      focus: { resources: slots(bal), orders: [], view: 'auto' } }
  ];
}

/** 来料计划时间线：每个在途到货点的时刻；窗口覆盖「当前时刻 / 最早到货」到计划结束 */
export function supplyRail() {
  if (S.live) {
    const w = S.window || { now: 0, w0: 0, w1: 3200 };
    const points = (S.supplyRows || []).filter(p => p.etaMin !== null)
      .map(p => ({ code: p.code, material: p.materialCode, name: p.name, qty: p.qty, unit: p.unit, eta: p.etaStr, min: p.etaMin })).sort((a, b) => a.min - b.min);
    const active = S.plan === 'current' ? PLAN_CURRENT() : PLAN_CANDIDATE();
    const blocked = ORDERS().filter(o => !o.supplement && !active.some(t => t.order === o.id));
    return { base: BASE.at, points, unlocks: {}, blocked, window: w, now: w.now, replay: S.t, planStart: 0 };
  }
  const base = new Date('2026-04-16T08:00:00');
  const toMin = etaStr => {
    const m = /(\d\d)-(\d\d) (\d\d):(\d\d)/.exec(etaStr || '');
    if (!m) return null;
    return (parseInt(m[2]) - 16) * 1440 + parseInt(m[3]) * 60 + parseInt(m[4]) - 8 * 60;
  };
  const points = MATERIALS().filter(m => m.inbound > 0).map(m => ({ code: m.code, name: m.name, qty: m.inbound, unit: m.unit, eta: m.etaStr, min: toMin(m.etaStr) }))
    .filter(p => p.min !== null).sort((a, b) => a.min - b.min);
  // 被挡订单：其缺口物料到达后，该订单才进入排产
  const unlocks = { 'RM-BEARING': ['po-005'], 'RM-COPPER': ['po-006'] };
  const blocked = ORDERS().filter(o => o.blocked);
  return { base, points, unlocks, blocked, window: { w0: 0, w1: 3480 }, now: S.t, replay: null, planStart: null };
}

/** 当前虚拟时刻是否已越过到货点（用于点亮） */
export function reached(pointMin, nowMin) { return nowMin >= pointMin; }
