// ============================================================
// APS 数字孪生 · 三维布局（离散制造车间）
// 布局由「模型」驱动：模型给出工位、原料堆、半成品缓冲、成品库与管线。
//   演示模型 = 原来那套风扇车间（切割 → 焊接 → 喷涂 / 绕线 → 总装 → 测试）
//   真实模型 = sim.loadSnapshot 按当前租户的资源台账、订单工艺链、在途来料生成
// 工位几何是示意（通用机床），落在工位上的设备、名称、提示与管线全部来自模型。
// 单位米，x 向右，z 朝向观察者，y 向上。
// ============================================================
import * as THREE from 'three';
import * as F from './factory.js';
import { DEV } from './sim.js';

// 介质色 = 在制品流转：原料灰 / 铜线铜 / 机壳紫 / 转子橙 / 成品蓝
Object.assign(F.PIPE_COLORS, { steel: '#98a5b3', copper: '#d98a2b', housing: '#b48ce0', rotor: '#e8a94e', fan: '#37b6ff' });
const PY = 0.35, LP = 0.5;   // 地台顶面 0.35，管线铺在地台上（原来的 0.12 会整根埋进地台里）

// 节点位置（x, z）：工位按工艺流向自左向右；原料区在左缘，缓冲在总装前，成品库在右端
export const POS = {
  raw: [[-44, -7], [-44, 7], [-44, -14], [-44, 14]], header: [-38, 0],
  QG1: [-28, -6], QG2: [-28, 4], HJ1: [-17, -1], PQ1: [-5, -1], RL1: [-17, 13],
  buf: [[5, -9], [5, 13]], ZZ1: [16, -6], ZZ2: [16, 6], CS1: [30, 0], fg: [42, 0],
};
// 工位几何（示意）：同一种通用机床，按工位给不同体量
const SHAPE = {
  QG1: { spindle: true, spinId: 'QG1' },
  QG2: { spindle: true, spinId: 'QG2', color: F.M.pumpD },
  HJ1: { spindle: true, spinId: 'HJ1', color: F.M.steelD },
  PQ1: { h: 4.0, w: 4.2, d: 3.6, color: F.M.white },
  RL1: { spindle: true, spinId: 'RL1', color: F.M.green },
  ZZ1: { h: 3.4, color: F.M.pump },
  ZZ2: { h: 3.4, color: F.M.pumpD },
  CS1: { h: 2.6, w: 3.8, d: 3.2, color: F.M.steelD, win: false },
};

export function buildPlant(scene, model = demoModel()) {
  const labels = [], selectables = [], pumps = [], spin = {}, lamps = {};
  const add = (obj, x, y, z, ry = 0) => { obj.position.set(x, y, z); obj.rotation.y = ry; scene.add(obj); return obj; };
  const sel = (group, title, rows, ids = [], o = {}) => { const s = { group, title, rows, ids, ...o }; selectables.push(s); return s; };
  const name = (text, x, y, z, o = {}) => labels.push({ kind: 'name', text, pos: new THREE.Vector3(x, y, z), ...o });
  const tasklbl = (key, x, y, z, o = {}) => labels.push({ kind: 'task', key, pos: new THREE.Vector3(x, y, z), ...o });
  const flow = (text, x, y, z, o = {}) => labels.push({ kind: 'flow', text, pos: new THREE.Vector3(x, y, z), ...o });
  const tag = (ic, key, unit, x, y, z, o = {}) => labels.push({ kind: 'tag', ic, key, unit, pos: new THREE.Vector3(x, y, z), ...o });
  // 管线不参与静态合并：合并后无法按条控制流向显隐
  const pipe = (pts, o) => { const g = F.pipe(pts, o); g.userData.actId = o.act || null; g.userData.dynamic = true; scene.add(g); return g; };

  // ---------------- 机床图元 ----------------
  function machine({ w = 3.4, d = 3.0, h = 3.0, color = F.M.pump, win = true, spindle = false, lampColor = 0x22b573 } = {}) {
    const g = new THREE.Group();
    g.add(F.box(w + 0.6, 0.3, d + 0.6, F.M.base, 0, 0.15, 0));           // 底座
    g.add(F.box(w, h, d, color, 0, 0.3 + h / 2, 0));                     // 机身
    g.add(F.box(w + 0.2, 0.25, d + 0.2, F.M.steelD, 0, 0.3 + h + 0.12, 0)); // 顶盖
    if (win) { const wm = new THREE.Mesh(new THREE.BoxGeometry(w * 0.52, h * 0.5, 0.06), F.M.glass); wm.position.set(0, 0.3 + h * 0.52, d / 2 + 0.02); g.add(wm); } // 观察窗
    if (spindle) g.add(F.box(w * 0.66, 0.4, d * 0.5, F.M.steel, 0, 0.3 + h * 0.6, 0)); // 主轴箱
    const lamp = new THREE.Mesh(new THREE.SphereGeometry(0.16, 12, 12), new THREE.MeshBasicMaterial({ color: lampColor }));
    lamp.position.set(0, 0.3 + h + 0.62, 0); lamp.userData.dynamic = true; lamp.userData.noAO = true; g.add(lamp); // 状态灯
    return { g, lamp };
  }
  function spinner(g, x, y, z, id, r = 0.34, color = F.M.red, speed = 6) {
    const s = new THREE.Mesh(new THREE.CylinderGeometry(r, r, 0.14, 18), color);
    s.position.set(x, y, z); s.userData.dynamic = true; g.add(s);
    spin[id] = { obj: s, on: false, speed };
    F.animated.push({ obj: s, update: (t, dt) => { if (spin[id].on) s.rotation.y += dt * speed; } });
    return s;
  }
  // 原料堆两种图元：板材垛 / 卷材
  const boxPile = () => { const g = new THREE.Group(); g.add(F.box(4, 1.2, 3, F.M.steelD, 0, 0.6, 0)); g.add(F.box(4, 0.5, 3, F.M.steel, 0, 1.45, 0)); return g; };
  const coilPile = () => {
    const g = new THREE.Group();
    const coil = new THREE.Mesh(new THREE.TorusGeometry(1.4, 0.5, 12, 24), F.M.greenD); coil.rotation.x = Math.PI / 2; g.add(coil);
    const coil2 = new THREE.Mesh(new THREE.TorusGeometry(1.1, 0.45, 12, 24), F.M.green); coil2.rotation.x = Math.PI / 2; coil2.position.y = 0.55; g.add(coil2);
    return g;
  };

  add(F.platform(100, 42), 8, 0, 0);

  // ---------------- 原料区（在途来料） ----------------
  const rawPos = i => (model.raws[i].pos ? POS[model.raws[i].pos] : POS.raw[i]);
  model.raws.forEach((r, i) => {
    const pos = rawPos(i);
    sel(add(r.shape === 'coil' ? coilPile() : boxPile(), pos[0], PY, pos[1]), r.title, () => r.rows, [], { lamp: null, supply: r.code });
    name(r.name, pos[0], PY, pos[1] + 2.6, { sm: true });
    if (r.tag) tag(r.tag, r.code, r.unit, pos[0], 3.6, pos[1] + 2.4);
  });

  // ---------------- 工位（生产资源） ----------------
  model.stations.forEach(st => {
    const pos = POS[st.slot], dev = DEV[st.slot], shape = SHAPE[st.slot] || {};
    const { g, lamp } = machine(shape);
    add(g, pos[0], PY, pos[1]);
    if (shape.spinId) spinner(g, 0, 3.9, 0, shape.spinId, 0.3, F.M.red, 5);
    const facts = st.facts || [['工序', dev.procName], ['节拍', dev.thru], ['分组', dev.grp]];
    sel(g, st.name || dev.name, () => [['任务', `task:${st.slot}`], ...facts], [st.slot]);
    name(st.name || dev.name, pos[0], PY, pos[1] + 2.6);
    tasklbl(st.slot, pos[0], 0.9, pos[1] + 3.4, { dy: 26 });
    lamps[st.slot] = lamp;
  });

  // ---------------- 半成品缓冲 / 成品库 ----------------
  model.buffers.forEach((b, i) => {
    const pos = POS.buf[i];
    sel(add(F.vTank(1.6, 3.0, { top: 'dome', bottom: 'flat', mat: i ? F.M.steel : F.M.white, legs: 0.4 }), pos[0], PY, pos[1]), b.title, () => b.rows, [], { tank: b.code });
    name(b.name, pos[0], PY, pos[1] + 2.8, { sm: true });
    if (b.tag) tag('M', b.code, b.unit, pos[0], 5.2, pos[1]);
  });
  if (model.fg) {
    const stack = new THREE.Group();
    for (let i = 0; i < 3; i++) stack.add(F.box(3, 0.9, 2.6, F.M.pump, 0, 0.45 + i * 0.95, 0));
    sel(add(stack, POS.fg[0], PY, POS.fg[1]), model.fg.title, () => model.fg.rows, [], { lamp: null });
    name(model.fg.name, POS.fg[0], PY, POS.fg[1] + 3.0, { sm: true });
    flow('成品出库', POS.fg[0] + 6, 2.2, POS.fg[1]);
  }

  // ---------------- 物料流管线（低位输送，绑定设备，设备运行才流动） ----------------
  const nodePos = id => (id === 'raw' ? POS.header : id === 'fg' ? POS.fg : id.startsWith('buf:') ? POS.buf[+id.slice(4)] : POS[id]);
  // 两点间的地面走线：同 z 直铺；否则走 Z 形（中点折弯）。lane 给出起端的平行偏移，避免多条线重叠
  function route(a, b, lane = 0) {
    const mx = (a[0] + b[0]) / 2;
    if (Math.abs(a[1] - b[1]) < 0.5 && lane === 0) return [[a[0], LP, a[1]], [b[0], LP, b[1]]];
    return [[a[0], LP, a[1] + lane], [mx, LP, a[1] + lane], [mx, LP, b[1]], [b[0], LP, b[1]]];
  }
  const pipes = model.pipes || [];
  if (pipes.some(p => p.from === 'raw')) {
    // 原料区集管：各原料堆汇到一条主管，再由主管分往各首道工序
    const [hx, hz] = POS.header, zs = model.raws.map((_, i) => rawPos(i)[1]);
    pipe([[hx, LP, Math.min(...zs) - 0.5], [hx, LP, Math.max(...zs) + 0.5]], { kind: 'steel', r: 0.16, flow: false });
    model.raws.forEach((r, i) => { const p = rawPos(i); if (p[0] !== hx) pipe([[p[0], LP, p[1]], [hx, LP, p[1]]], { kind: 'steel', r: 0.16, flow: false }); });
    flow('来料', POS.raw[0][0] - 4, 1.6, (Math.min(...zs) + Math.max(...zs)) / 2);
  }
  pipes.forEach(p => {
    if (p.pts) return pipe(p.pts.map(q => [q[0], Math.max(q[1], LP), q[2]]), { kind: p.kind, r: p.r || 0.15, act: p.act });
    pipe(route(nodePos(p.from), nodePos(p.to), p.lane), { kind: p.kind, r: 0.15, act: p.act });
  });
  (model.flows || []).forEach(f => flow(f.text, f.pos[0], f.pos[1], f.pos[2]));

  return { labels, selectables, pumps, spin, lamps };
}

// 演示模型：原风扇车间（demo-seed 口径），管线与标签沿用原来的手工走线
export function demoModel() {
  const R = POS.raw;
  return {
    live: false,
    stations: ['QG1', 'QG2', 'HJ1', 'PQ1', 'RL1', 'ZZ1', 'ZZ2', 'CS1'].map(id => ({ slot: id, name: DEV[id].name })),
    raws: [
      { name: '冷轧钢板', title: '冷轧钢板（原料）', code: 'RM-STEEL', unit: 'kg', tag: 'M', shape: 'box',
        rows: [['库存', 'RM-STEEL'], ['安全库存', '500 kg'], ['在途', '1500 kg @04-17']] },
      { name: '高纯铜线', title: '高纯铜线（原料）', code: 'RM-COPPER', unit: 'kg', tag: 'M', shape: 'coil',
        rows: [['库存', 'RM-COPPER'], ['安全库存', '300 kg'], ['在途', '900 kg @04-18']] },
    ],
    buffers: [
      { code: 'SF-HOUSING', name: '机壳缓冲', title: '机壳半成品缓冲', unit: '件', tag: true, rows: [['库存', 'SF-HOUSING'], ['安全库存', '20 件']] },
      { code: 'SF-ROTOR', name: '转子缓冲', title: '转子半成品缓冲', unit: '件', tag: true, rows: [['库存', 'SF-ROTOR'], ['安全库存', '15 件']] },
    ],
    fg: { name: '风扇成品', title: '风扇总成（成品）', rows: [['FG-FAN-A', '18 台'], ['FG-FAN-B', '6 台']] },
    pipes: [
      { pts: [[R[0][0], LP, R[0][1]], [-31, LP, POS.QG1[1]]], kind: 'steel', r: 0.16, act: 'QG1' },
      { pts: [[R[0][0], LP, R[0][1]], [-36, 0.5, -5], [-31, LP, POS.QG2[1]]], kind: 'steel', r: 0.16, act: 'QG2' },
      { pts: [[POS.QG1[0], LP, POS.QG1[1]], [POS.HJ1[0], LP, POS.HJ1[1]]], kind: 'housing', act: 'HJ1' },
      { pts: [[POS.QG2[0], LP, POS.QG2[1]], [-22, 0.5, 1], [POS.HJ1[0], LP, POS.HJ1[1]]], kind: 'housing', act: 'HJ1' },
      { pts: [[POS.HJ1[0], LP, POS.HJ1[1]], [POS.PQ1[0], LP, POS.PQ1[1]]], kind: 'housing', act: 'PQ1' },
      { pts: [[POS.PQ1[0], LP, POS.PQ1[1]], [POS.buf[0][0], LP, POS.buf[0][1]]], kind: 'housing', act: 'PQ1' },
      { pts: [[R[1][0], LP, R[1][1]], [POS.RL1[0], LP, POS.RL1[1]]], kind: 'copper', act: 'RL1' },
      { pts: [[POS.RL1[0], LP, POS.RL1[1]], [POS.buf[1][0], LP, POS.buf[1][1]]], kind: 'rotor', act: 'RL1' },
      { pts: [[POS.buf[0][0], LP, POS.buf[0][1]], [POS.ZZ1[0], LP, POS.ZZ1[1]]], kind: 'housing', act: 'ZZ1' },
      { pts: [[POS.buf[1][0], LP, POS.buf[1][1]], [10, 0.5, 10], [POS.ZZ2[0], LP, POS.ZZ2[1]]], kind: 'rotor', act: 'ZZ2' },
      { pts: [[POS.ZZ1[0], LP, POS.ZZ1[1]], [POS.CS1[0], LP, POS.CS1[1]]], kind: 'fan', act: 'CS1' },
      { pts: [[POS.ZZ2[0], LP, POS.ZZ2[1]], [22, 0.5, 3], [POS.CS1[0], LP, POS.CS1[1]]], kind: 'fan', act: 'CS1' },
      { pts: [[POS.CS1[0], LP, POS.CS1[1]], [POS.fg[0], LP, POS.fg[1]]], kind: 'fan', r: 0.16, act: 'CS1' },
    ],
    flows: [
      { text: '钢板来料', pos: [R[0][0] - 4, 1.6, R[0][1]] },
      { text: '机壳线', pos: [-11, 1.6, -1.4] },
      { text: '铜线来料', pos: [R[1][0] - 4, 1.6, R[1][1]] },
      { text: '总装测试', pos: [23, 1.6, 0] },
    ],
  };
}
