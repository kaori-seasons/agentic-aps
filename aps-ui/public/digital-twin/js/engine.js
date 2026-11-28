// 通用 2.5D 组态引擎：渲染器、灯光、后期（AO + 辉光）、主题切换、静态合并、三维标签、悬停 / 点击、
// 镜头飞行、自适应缩放、PNG 导出、时钟。各行业页面只负责搭场景和数据，调用 createEngine(opts) 即可。
// URL 参数：?ao=0 关闭环境光遮蔽，?fx=0 关闭全部后期。
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { GTAOPass } from 'three/addons/postprocessing/GTAOPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js';
import { animated, flowTextures, waterUniforms } from './factory.js';

export const W = 1920, H = 1080;
export const pad = n => String(n).padStart(2, '0');
export const ease = {
  inOut: t => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2),
  out: t => 1 - Math.pow(1 - t, 3),
  back: t => { const c = 1.6; return 1 + (c + 1) * Math.pow(t - 1, 3) + c * Math.pow(t - 1, 2); },
};

const LIGHT = { bg: '#e3ecf5', ground: '#e6eef6', hemi: 0.95, hemiGround: '#a9bcd2', sun: 2.9, sunColor: '#fffaf0', env: 0.55, exposure: 1.0, bloom: 0, fogScale: 1 };
const DARK = { bg: '#040b1c', ground: '#06112a', hemi: 0.45, hemiGround: '#0b2448', sun: 1.35, sunColor: '#bcd4ff', env: 0.42, exposure: 1.05, bloom: 0.62, fogScale: 0.85 };

export function createEngine(opts = {}) {
  const o = {
    fog: [420, 760], groundSize: 1400,
    sun: { pos: [-70, 130, 90], bounds: [-125, 125, 80, -80], far: 400, normalBias: 0.04 },
    view: { fov: 19, near: 5, far: 1600, target: [0, 0, 0], dist: 300, polar: 0.8, azim: 0, offset: [0, 0], limits: {} },
    ao: { radius: 2.2, distanceExponent: 1.2, thickness: 2.5, scale: 1.25 },
    bloom: { radius: 0.55, threshold: 1.0 },
    exportName: () => '组态画面',
    ...opts,
  };
  o.themes = { light: { ...LIGHT, ...(opts.themes?.light || {}) }, dark: { ...DARK, ...(opts.themes?.dark || {}) } };
  const qs = new URLSearchParams(location.search);
  const stage = document.getElementById('stage');
  const canvas = document.getElementById('gl');
  const labelLayer = document.getElementById('labels');
  const tooltip = document.getElementById('tooltip');

  // ---------------------------------------------------------------- 渲染器 / 场景
  let SW = W, SH = H;                                // 舞台的逻辑尺寸（fill 模式下随窗口比例变化）
  const FILL = !!opts.fill;
  if (FILL) stage.classList.add('fill');
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, preserveDrawingBuffer: true });
  renderer.setSize(SW, SH, false);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.NeutralToneMapping;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.localClippingEnabled = true;

  // GPU 进程重置后贴图/后期缓冲无法完整恢复，直接重载页面（1 分钟内最多一次）
  canvas.addEventListener('webglcontextrestored', () => {
    const last = +sessionStorage.getItem('glReload') || 0;
    if (Date.now() - last > 60000) { try { sessionStorage.setItem('glReload', Date.now()); } catch (e) {} location.reload(); }
  });

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(LIGHT.bg);
  scene.fog = new THREE.Fog(LIGHT.bg, o.fog[0], o.fog[1]);
  scene.environment = new THREE.PMREMGenerator(renderer).fromScene(new RoomEnvironment(), 0.04).texture;

  const hemi = new THREE.HemisphereLight('#ffffff', LIGHT.hemiGround, LIGHT.hemi);
  scene.add(hemi);
  const sun = new THREE.DirectionalLight(LIGHT.sunColor, LIGHT.sun);
  sun.position.set(...o.sun.pos);
  sun.castShadow = true;
  sun.shadow.mapSize.set(4096, 4096);
  const [l, r, t, b] = o.sun.bounds;
  Object.assign(sun.shadow.camera, { left: l, right: r, top: t, bottom: b, near: 1, far: o.sun.far });
  sun.shadow.bias = -0.0004;
  sun.shadow.normalBias = o.sun.normalBias;
  sun.shadow.radius = 3;
  scene.add(sun);

  const groundMat = new THREE.MeshStandardMaterial({ color: LIGHT.ground, roughness: 1 });
  const ground = new THREE.Mesh(new THREE.PlaneGeometry(o.groundSize, o.groundSize), groundMat);
  ground.rotation.x = -Math.PI / 2; ground.receiveShadow = true;
  scene.add(ground);

  // ---------------------------------------------------------------- 相机
  const v = o.view;
  const camera = new THREE.PerspectiveCamera(v.fov, SW / SH, v.near, v.far);
  const baseFov = v.fov;
  const controls = new OrbitControls(camera, canvas);
  const HOME = { target: new THREE.Vector3(...v.target), dist: v.dist, polar: v.polar, azim: v.azim, offset: [...v.offset] };
  const offset = [...v.offset];
  function setView(vw) {
    if (vw.offset) { offset[0] = vw.offset[0]; offset[1] = vw.offset[1]; camera.setViewOffset(SW, SH, offset[0], offset[1], SW, SH); }
    controls.target.copy(vw.target);
    camera.position.copy(new THREE.Vector3().setFromSphericalCoords(vw.dist, vw.polar, vw.azim).add(vw.target));
    camera.lookAt(vw.target);
  }
  function getView() {
    const sp = new THREE.Spherical().setFromVector3(camera.position.clone().sub(controls.target));
    return { target: controls.target.clone(), dist: sp.radius, polar: sp.phi, azim: sp.theta, offset: [...offset] };
  }
  camera.setViewOffset(SW, SH, v.offset[0], v.offset[1], SW, SH);
  Object.assign(controls, {
    enableDamping: true, dampingFactor: 0.08,
    minPolarAngle: 0.3, maxPolarAngle: 1.25, minAzimuthAngle: -1.0, maxAzimuthAngle: 1.0,
    screenSpacePanning: false, rotateSpeed: 0.5, zoomSpeed: 0.9,
    ...v.limits,
  });
  setView(HOME); controls.update();
  // 交互限位：飞到限位外的特写视角时临时放宽，否则交还控制器时会被瞬间拉回（画面跳一下）
  const LIM = { minDistance: controls.minDistance, maxDistance: controls.maxDistance, minPolarAngle: controls.minPolarAngle,
    maxPolarAngle: controls.maxPolarAngle, minAzimuthAngle: controls.minAzimuthAngle, maxAzimuthAngle: controls.maxAzimuthAngle };
  function fitLimits(vw) {
    Object.assign(controls, {
      minDistance: Math.min(LIM.minDistance, vw.dist), maxDistance: Math.max(LIM.maxDistance, vw.dist),
      minPolarAngle: Math.min(LIM.minPolarAngle, vw.polar), maxPolarAngle: Math.max(LIM.maxPolarAngle, vw.polar),
      minAzimuthAngle: Math.min(LIM.minAzimuthAngle, vw.azim), maxAzimuthAngle: Math.max(LIM.maxAzimuthAngle, vw.azim),
    });
  }

  // ---------------------------------------------------------------- 补间动画
  const tweens = new Set();
  function tween(dur, fn, ez = ease.inOut) {
    return new Promise(res => {
      const tw = { t0: performance.now(), dur, fn, ez, res };
      tweens.add(tw);
      fn(0);
    });
  }
  function runTweens() {
    const now = performance.now();
    for (const tw of tweens) {
      const k = Math.min(1, (now - tw.t0) / tw.dur);
      tw.fn(tw.ez(k));
      if (k >= 1) { tweens.delete(tw); tw.res(); }
    }
  }
  let flying = 0;
  // 镜头飞行：方位角取最短路径，飞行时暂停阻尼和用户操作
  async function flyTo(to, dur = 1800) {
    const from = getView();
    const tgt = { target: to.target ? new THREE.Vector3().copy(to.target) : from.target, dist: to.dist ?? from.dist, polar: to.polar ?? from.polar, azim: to.azim ?? from.azim, offset: to.offset ?? from.offset };
    let dA = tgt.azim - from.azim; dA = Math.atan2(Math.sin(dA), Math.cos(dA));
    const id = ++flying;
    controls.enabled = false; controls.enableDamping = false;
    await tween(dur, k => {
      if (id !== flying) return;
      setView({
        target: from.target.clone().lerp(tgt.target, k),
        dist: from.dist + (tgt.dist - from.dist) * k,
        polar: from.polar + (tgt.polar - from.polar) * k,
        azim: from.azim + dA * k,
        offset: [from.offset[0] + (tgt.offset[0] - from.offset[0]) * k, from.offset[1] + (tgt.offset[1] - from.offset[1]) * k],
      });
    });
    if (id === flying) { fitLimits({ ...tgt, azim: from.azim + dA }); controls.enabled = true; controls.enableDamping = true; controls.update(); }
  }
  const home = (dur = 1400) => flyTo(HOME, dur);

  // ---------------------------------------------------------------- 后期：AO + 辉光
  const fx = qs.get('fx') !== '0';
  let composer = null, bloom = null;
  if (fx) {
    composer = new EffectComposer(renderer, new THREE.WebGLRenderTarget(SW, SH, { type: THREE.HalfFloatType, samples: 4 }));
    composer.addPass(new RenderPass(scene, camera));
    if (qs.get('ao') !== '0' && opts.ao !== false) {           // opts.ao = false：页面整体不要 AO（例如夜景大面积发光的场景）
      const ao = new GTAOPass(scene, camera, SW, SH);
      ao.blendIntensity = 0.85;
      ao.updateGtaoMaterial({ ...o.ao, samples: 16 });
      ao.updatePdMaterial({ lumaPhi: 10, depthPhi: 2, normalPhi: 3, radius: 5, rings: 2, samples: 16 });
      // AO 只隐藏点和线；精灵（发光小球、光晕）也要排除，否则会在画面上压出一块黑色方框
      ao.overrideVisibility = function () {
        const cache = this._visibilityCache;
        this.scene.traverse(obj => { cache.set(obj, obj.visible); if (obj.isPoints || obj.isLine || obj.isSprite || obj.userData.noAO) obj.visible = false; });
      };
      composer.addPass(ao);
    }
    bloom = new UnrealBloomPass(new THREE.Vector2(SW / 2, SH / 2), 0, o.bloom.radius, o.bloom.threshold);
    composer.addPass(bloom);
    composer.addPass(new OutputPass());
  }
  function setQuality(pr) {
    renderer.setPixelRatio(pr); renderer.setSize(SW, SH, false);
    if (composer) { composer.setPixelRatio(pr); composer.setSize(SW, SH); }
  }

  // ---------------------------------------------------------------- 主题
  let theme = 'light';
  const themeHooks = [];
  function setTheme(name) {
    theme = name;
    const th = o.themes[name];
    scene.background.set(th.bg);
    scene.fog.color.set(th.bg);
    scene.fog.near = o.fog[0] * th.fogScale; scene.fog.far = o.fog[1] * th.fogScale;
    groundMat.color.set(th.ground);
    hemi.intensity = th.hemi; hemi.groundColor.set(th.hemiGround);
    sun.intensity = th.sun; sun.color.set(th.sunColor);
    scene.environmentIntensity = th.env;
    renderer.toneMappingExposure = th.exposure;
    if (bloom) { bloom.strength = th.bloom; bloom.enabled = th.bloom > 0; }
    stage.classList.toggle('dark', name === 'dark');
    for (const h of themeHooks) h(name, th);
  }

  // ---------------------------------------------------------------- 静态合并
  // 相同材质的静态网格合并，减少绘制调用；可选中设备单独合并并克隆材质以便高亮；管线单独放一组
  const pipeHolder = new THREE.Group();
  function optimize(selectables) {
    const selGroups = new Set(selectables.map(s => s.group));
    const staticRoots = [], pipeRoots = [];
    for (const child of [...scene.children]) {
      if (selGroups.has(child)) mergeInto(child);
      else if (child.isGroup && !child.userData.dynamic) (child.userData.pipe ? pipeRoots : staticRoots).push(child);
    }
    const holder = new THREE.Group(); scene.add(holder); scene.add(pipeHolder);
    [...staticRoots, ...pipeRoots].forEach(r => r.updateMatrixWorld(true));
    mergeList(staticRoots, holder, false);
    mergeList(pipeRoots, pipeHolder, false);
  }
  function collect(root, out) {
    for (const c of root.children) {
      if (c.userData.dynamic) continue;
      if (c.isMesh && !Array.isArray(c.material) && !c.material.isShaderMaterial && !c.material.transparent) out.push(c);
      collect(c, out);
    }
  }
  function mergeInto(group) {
    const cache = new Map();
    group.traverse(m => {
      if (!m.isMesh) return;
      const cl = x => (x.isShaderMaterial ? x : (cache.get(x) || cache.set(x, x.clone()).get(x)));
      m.material = Array.isArray(m.material) ? m.material.map(cl) : cl(m.material);
    });
    group.updateMatrixWorld(true);
    mergeList([group], group, true);
  }
  function mergeList(roots, target, local) {
    const meshes = [];
    roots.forEach(rt => collect(rt, meshes));
    const inv = local ? new THREE.Matrix4().copy(target.matrixWorld).invert() : new THREE.Matrix4();
    const buckets = new Map();
    for (const m of meshes) {
      const geo = m.geometry.clone();
      geo.applyMatrix4(new THREE.Matrix4().multiplyMatrices(inv, m.matrixWorld));
      for (const k of Object.keys(geo.attributes)) if (!['position', 'normal', 'uv'].includes(k)) geo.deleteAttribute(k);
      const key = m.material.uuid + (m.castShadow ? 'c' : '');
      if (!buckets.has(key)) buckets.set(key, { mat: m.material, cast: m.castShadow, geos: [] });
      buckets.get(key).geos.push(geo);
      m.removeFromParent();
    }
    for (const { mat, cast, geos } of buckets.values()) {
      for (const list of [geos.filter(g => g.index), geos.filter(g => !g.index)]) {
        if (!list.length) continue;
        const mesh = new THREE.Mesh(mergeGeometries(list, false), mat);
        mesh.castShadow = cast; mesh.receiveShadow = true;
        target.add(mesh);
      }
    }
  }

  // ---------------------------------------------------------------- 三维标签
  const labelEls = [];
  function addLabels(list, makeEl = defaultLabelEl) {
    const out = [];
    for (const lb of list) {
      const el = makeEl(lb);
      labelLayer.appendChild(el);
      const rec = { ...lb, el, v: new THREE.Vector3() };
      labelEls.push(rec); out.push(rec);
    }
    return out;
  }
  function removeLabel(rec) {
    rec.el.remove();
    const i = labelEls.indexOf(rec); if (i >= 0) labelEls.splice(i, 1);
  }
  function project(pos) {
    const p = pos.clone().project(camera);
    return { x: (p.x + 1) / 2 * SW, y: (1 - p.y) / 2 * SH, z: p.z };
  }
  function placeLabels() {
    for (const lb of labelEls) {
      const p = lb.obj ? lb.v.setFromMatrixPosition(lb.obj.matrixWorld).add(lb.pos) : lb.v.copy(lb.pos);
      p.project(camera);
      if (p.z > 1 || lb.hidden) { lb.el.style.display = 'none'; continue; }
      lb.el.style.display = '';
      const x = (p.x + 1) / 2 * SW + (lb.dx || 0), y = (1 - p.y) / 2 * SH + (lb.dy || 0);
      lb.el.style.transform = `translate(${x.toFixed(1)}px, ${y.toFixed(1)}px) translate(-50%, -50%)`;
    }
  }

  // ---------------------------------------------------------------- 悬停 / 点击
  const raycaster = new THREE.Raycaster();
  const ndc = new THREE.Vector2();
  let selectables = [], hovered = null, tooltipHtml = null, clickCb = null, lastInput = performance.now();
  const highlight = (s, on, color = '#2f86f6') => s.group.traverse(m => {
    if (!m.isMesh) return;
    for (const mt of [].concat(m.material)) if (mt.emissive && !mt.userData.keepEmissive) { mt.emissive.set(on ? color : '#000000'); mt.emissiveIntensity = on ? 0.28 : 1; }
  });
  function pick(e) {
    const rc = canvas.getBoundingClientRect();
    ndc.set((e.clientX - rc.left) / rc.width * 2 - 1, -(e.clientY - rc.top) / rc.height * 2 + 1);
    raycaster.setFromCamera(ndc, camera);
    const groups = selectables.map(s => s.group).filter(g => g.visible);
    const hit = raycaster.intersectObjects(groups, true)[0];
    if (!hit) return null;
    let g = hit.object; while (g && !groups.includes(g)) g = g.parent;
    return selectables.find(x => x.group === g) || null;
  }
  function enableHover(list, html) { selectables = list; tooltipHtml = html; }
  canvas.addEventListener('pointermove', e => {
    lastInput = performance.now();
    if (!tooltipHtml) return;
    const s = pick(e);
    if (s !== hovered) { if (hovered && !hovered.locked) highlight(hovered, false); if (s) highlight(s, true); hovered = s; }
    const rc = canvas.getBoundingClientRect();
    if (s) {
      const sc = rc.width / SW;
      tooltip.innerHTML = tooltipHtml(s);
      tooltip.style.display = 'block';
      let x = (e.clientX - rc.left) / sc + 16, y = (e.clientY - rc.top) / sc + 16;
      if (x > SW - 240) x -= 260;
      if (y > SH - 200) y -= 180;
      tooltip.style.left = x + 'px'; tooltip.style.top = y + 'px';
      canvas.style.cursor = 'pointer';
    } else { tooltip.style.display = 'none'; canvas.style.cursor = ''; }
  });
  canvas.addEventListener('pointerleave', () => { if (hovered && !hovered.locked) highlight(hovered, false); hovered = null; tooltip.style.display = 'none'; });
  let down = null;
  canvas.addEventListener('pointerdown', e => { lastInput = performance.now(); down = { x: e.clientX, y: e.clientY }; });
  canvas.addEventListener('wheel', () => { lastInput = performance.now(); }, { passive: true });
  canvas.addEventListener('pointerup', e => {
    if (!down || Math.hypot(e.clientX - down.x, e.clientY - down.y) > 5) return;
    if (clickCb) clickCb(pick(e), e);
  });

  // ---------------------------------------------------------------- 自适应缩放
  // 适配窗口：默认按 1920×1080 等比缩放（窗口比例不同会留边）；fill 模式按窗口比例扩展画布，
  // 界面缩放比例不变、三维场景铺满整个窗口。变高时同步放大竖直视场角，保证水平方向看到的范围不变。
  function resize() {
    const vw = window.innerWidth, vh = window.innerHeight;
    const fit = Math.min(vw / W, vh / H);
    if (FILL) {
      SW = Math.max(W, Math.round(vw / fit)); SH = Math.max(H, Math.round(vh / fit));
      stage.style.width = SW + 'px'; stage.style.height = SH + 'px';
      canvas.style.width = SW + 'px'; canvas.style.height = SH + 'px';
      camera.fov = THREE.MathUtils.radToDeg(2 * Math.atan(Math.tan(THREE.MathUtils.degToRad(baseFov) / 2) * SH / H));
      camera.aspect = SW / SH;
      camera.setViewOffset(SW, SH, offset[0], offset[1], SW, SH);
      stage.style.transform = `scale(${fit})`;
    } else {
      stage.style.transform = `translate(${(vw - W * fit) / 2}px, ${(vh - H * fit) / 2}px) scale(${fit})`;
    }
    setQuality(Math.min(fit * (window.devicePixelRatio || 1), 2));
  }
  window.addEventListener('resize', resize);
  resize();

  // ---------------------------------------------------------------- 渲染循环
  const clock = new THREE.Clock();
  const frameHooks = [];
  const afterHooks = [];                             // 主画面渲染完之后再画的东西（如把小画面直接画在主画布上）
  function render() {
    if (composer) composer.render(); else renderer.render(scene, camera);
    for (const h of afterHooks) h();
    placeLabels();
  }
  function loop() {
    const dt = Math.min(clock.getDelta(), 0.1), tm = clock.elapsedTime;
    waterUniforms.uTime.value = tm;
    runTweens();
    for (const a of animated) if (!a.paused) a.update(tm, dt);      // paused：设备离线时由页面置位
    for (const f of flowTextures) f.tex.offset.x -= dt * 0.55 * f.speed;
    for (const h of frameHooks) h(tm, dt);
    if (controls.enabled) controls.update();
    // 近裁剪面随镜头距离走：取到注视点距离的 4%（不小于配置值）。固定 near = 2 时，拉远到 600–1100 m 深度精度只剩 2–4 cm，
    // 地面 / 草地 / 硬地 / 道路这些只差 1–3 cm 的平面会互相穿插，整片画面横纹闪烁（z-fighting）。特写视角距离近，仍是原值。
    const nearK = Math.max(v.near, Math.min(camera.position.distanceTo(controls.target) * 0.04, 60));
    if (Math.abs(nearK - camera.near) > camera.near * 0.02) { camera.near = nearK; camera.updateProjectionMatrix(); }
    render();
    requestAnimationFrame(loop);
  }

  // ---------------------------------------------------------------- 提示 / 导出
  const toastEl = document.getElementById('toast');
  function toast(msg, ms = 1800) {
    toastEl.textContent = msg; toastEl.style.display = 'block';
    clearTimeout(toast.t); if (ms) toast.t = setTimeout(() => (toastEl.style.display = 'none'), ms);
  }
  async function exportPNG(scale = 2) {
    const btn = document.getElementById('btn-export');
    if (btn) btn.disabled = true;
    try {
      setQuality(scale);
      stage.classList.add('exporting');                 // 导出时背景模糊无法渲染，页面样式可据此换成实底
      render();
      const url = await window.htmlToImage.toPng(stage, {
        width: SW, height: SH, pixelRatio: scale, cacheBust: true,
        style: { transform: 'none', left: '0', top: '0' },
        filter: n => !(n.id === 'toast' || n.id === 'tooltip' || (n.classList && n.classList.contains('nav-tools'))),
      });
      const d = new Date();
      const a = document.createElement('a');
      a.download = `${o.exportName()}_${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}.png`;
      a.href = url; a.click();
      toast(`已导出 PNG（${SW * scale}×${SH * scale}）`);
      return url;
    } catch (err) {
      console.error(err); toast('导出失败：' + err.message, 4000);
    } finally {
      stage.classList.remove('exporting');
      resize();
      if (btn) btn.disabled = false;
    }
  }
  document.getElementById('btn-export')?.addEventListener('click', () => exportPNG(2));

  const api = {
    scene, camera, controls, renderer, HOME, pipeHolder, labelLayer,
    home, flyTo, setView, getView, tween, project, render, optimize, addLabels, removeLabel, enableHover, highlight, toast, exportPNG,
    setTheme, get theme() { return theme; }, get size() { return { w: SW, h: SH }; }, onTheme: h => themeHooks.push(h),
    onClick: cb => { clickCb = cb; },
    idle: () => performance.now() - lastInput,
    onFrame: h => frameHooks.push(h),
    onAfterRender: h => afterHooks.push(h),
    start: () => { loop(); startClock(); },
  };
  document.getElementById('btn-reset')?.addEventListener('click', () => home());
  // 全屏：按钮或 F 键切换；退出后按钮文字复原（窗口尺寸变化由 resize 自动适配）
  const fsBtn = document.getElementById('btn-fullscreen');
  const fsEl = () => document.fullscreenElement || document.webkitFullscreenElement;
  function toggleFullscreen() {
    if (fsEl()) (document.exitFullscreen || document.webkitExitFullscreen).call(document);
    else { const el = document.documentElement; (el.requestFullscreen || el.webkitRequestFullscreen).call(el); }
  }
  const syncFs = () => {
    if (!fsBtn) return;
    const on = !!fsEl();
    fsBtn.querySelector('span').textContent = on ? '退出全屏' : '全屏';
    fsBtn.querySelector('svg').innerHTML = on ? '<path d="M9 4v5H4 M15 4v5h5 M9 20v-5H4 M15 20v-5h5"/>' : '<path d="M4 9V4h5 M20 9V4h-5 M4 15v5h5 M20 15v5h-5"/>';
  };
  fsBtn?.addEventListener('click', toggleFullscreen);
  document.addEventListener('fullscreenchange', syncFs);
  document.addEventListener('webkitfullscreenchange', syncFs);
  window.addEventListener('keydown', e => {
    if ((e.key === 'f' || e.key === 'F') && !e.ctrlKey && !e.metaKey && !e.altKey && !/INPUT|SELECT|TEXTAREA/.test(document.activeElement?.tagName)) toggleFullscreen();
  });
  setTheme('light');
  window.__app = api;
  return api;
}

// ---------------------------------------------------------------- 默认标签样式
export function defaultLabelEl(l) {
  const el = document.createElement('div');
  if (l.kind === 'name') { el.className = 'lbl name' + (l.sm ? ' sm' : ''); el.textContent = l.text; }
  else if (l.kind === 'tag') { el.className = 'lbl tag'; el.innerHTML = `<span class="ic">${l.ic}</span><span class="v" data-k="${l.key}">0.00</span><span class="u">${l.unit}</span>`; }
  else if (l.kind === 'pump') { el.className = 'lbl pump'; el.dataset.pid = l.pid; el.innerHTML = `<i class="d"></i>${l.text}<span class="m">M</span>`; }
  else { el.className = 'lbl flow'; el.innerHTML = `<svg viewBox="0 0 22 12"><path d="M1 6h17 M13 1.5l5 4.5-5 4.5" stroke="#1d6ee6" stroke-width="2.4" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>${l.text}`; }
  return el;
}

// ---------------------------------------------------------------- 时钟（页面上有哪些元素就更新哪些）
function startClock() {
  const $ = id => document.getElementById(id);
  const ticks = $('ticks');
  if (ticks && !ticks.childElementCount) {
    for (let i = 0; i < 60; i++) {
      const a = i / 60 * Math.PI * 2, big = i % 5 === 0, r1 = big ? 36 : 39.5;
      ticks.insertAdjacentHTML('beforeend', `<line x1="${50 + Math.sin(a) * r1}" y1="${50 - Math.cos(a) * r1}" x2="${50 + Math.sin(a) * 42}" y2="${50 - Math.cos(a) * 42}" stroke="${big ? '#1b3f78' : '#9fb3cc'}" stroke-width="${big ? 2 : 0.8}"/>`);
    }
  }
  const tick = () => {
    const d = new Date();
    const ds = `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())}`, ts = `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
    if ($('clock-text')) $('clock-text').innerHTML = `${ds}&nbsp;&nbsp;${ts}`;
    if ($('sb-time')) $('sb-time').textContent = `${ds} ${ts}`;
    if ($('clock-date')) $('clock-date').textContent = ds;
    const rot = (id, deg) => $(id)?.setAttribute('transform', `rotate(${deg} 50 50)`);
    rot('h-hand', (d.getHours() % 12) * 30 + d.getMinutes() * 0.5);
    rot('m-hand', d.getMinutes() * 6 + d.getSeconds() * 0.1);
    rot('s-hand', d.getSeconds() * 6);
  };
  tick(); setInterval(tick, 1000);
}
