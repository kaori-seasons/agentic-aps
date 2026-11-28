// 设备模型工厂：所有设备都由基本几何体拼装，统一材质，供 plant.js 布置。
import * as THREE from 'three';

export const animated = [];           // { update(t, dt) }
export const flowTextures = [];       // 管道流动贴图

// ---------------------------------------------------------------- 贴图
function canvasTex(w, h, draw, { repeat = false, srgb = true } = {}) {
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  draw(c.getContext('2d'), w, h);
  const t = new THREE.CanvasTexture(c);
  if (srgb) t.colorSpace = THREE.SRGBColorSpace;
  if (repeat) t.wrapS = t.wrapT = THREE.RepeatWrapping;
  t.anisotropy = 8;
  return t;
}

const gridTex = canvasTex(256, 256, (g, w, h) => {
  g.fillStyle = '#eef2f6'; g.fillRect(0, 0, w, h);
  g.strokeStyle = '#d3dbe4'; g.lineWidth = 2;
  for (let i = 0; i <= 8; i++) {
    const p = i * 32;
    g.beginPath(); g.moveTo(p, 0); g.lineTo(p, h); g.stroke();
    g.beginPath(); g.moveTo(0, p); g.lineTo(w, p); g.stroke();
  }
}, { repeat: true });

const flowTex = canvasTex(256, 16, (g, w, h) => {
  const grd = g.createLinearGradient(0, 0, w, 0);
  grd.addColorStop(0.0, 'rgba(255,255,255,0)');
  grd.addColorStop(0.55, 'rgba(255,255,255,0.0)');
  grd.addColorStop(0.85, 'rgba(255,255,255,0.85)');
  grd.addColorStop(0.9, 'rgba(255,255,255,1)');
  grd.addColorStop(1.0, 'rgba(255,255,255,0)');
  g.fillStyle = grd; g.fillRect(0, 0, w, h);
}, { repeat: true });

const ventTex = canvasTex(256, 256, (g, w, h) => {
  g.fillStyle = '#e9eef4'; g.fillRect(0, 0, w, h);
  g.fillStyle = '#c9d3df';
  for (let y = 26; y < h - 20; y += 12) g.fillRect(22, y, w - 44, 6);
  g.strokeStyle = '#b8c4d2'; g.lineWidth = 4; g.strokeRect(10, 10, w - 20, h - 20);
});

const finTex = canvasTex(128, 128, (g, w, h) => {
  g.fillStyle = '#d9e0e8'; g.fillRect(0, 0, w, h);
  g.fillStyle = '#9eabba';
  for (let x = 4; x < w; x += 10) g.fillRect(x, 0, 4, h);
}, { repeat: true });

export function textTex(lines, { w = 1024, h = 512, logo = null, color = '#16407f' } = {}) {
  const tex = canvasTex(w, h, () => {});
  const c = tex.image, g = c.getContext('2d');
  const paint = () => {
    g.clearRect(0, 0, w, h);
    let y = h * 0.42;
    if (logo) {
      const s = h * 0.36;
      g.drawImage(logo, (w - s * logo.width / logo.height) / 2, h * 0.08, s * logo.width / logo.height, s);
      y = h * 0.62;
    }
    g.fillStyle = color; g.textAlign = 'center'; g.textBaseline = 'middle';
    lines.forEach((l, i) => {
      g.font = `700 ${l.size || 90}px "PingFang SC","Microsoft YaHei","Noto Sans CJK SC",sans-serif`;
      g.fillText(l.text, w / 2, y + i * (l.size || 90) * 1.25);
    });
    tex.needsUpdate = true;
  };
  paint();
  if (logo && !logo.complete) logo.onload = paint;
  return tex;
}

// ---------------------------------------------------------------- 材质
export const M = {
  concrete: new THREE.MeshStandardMaterial({ color: '#dfe6ee', roughness: 0.85 }),
  concreteTop: new THREE.MeshStandardMaterial({ color: '#f6f8fb', roughness: 0.85 }),
  concreteIn: new THREE.MeshStandardMaterial({ color: '#c9d6e2', roughness: 0.95 }),
  plinth: new THREE.MeshStandardMaterial({ color: '#d5dee8', roughness: 0.95 }),
  grid: new THREE.MeshStandardMaterial({ map: gridTex, roughness: 0.95 }),
  steel: new THREE.MeshStandardMaterial({ color: '#dfe6ed', metalness: 0.55, roughness: 0.28 }),
  steelD: new THREE.MeshStandardMaterial({ color: '#a7b4c3', metalness: 0.6, roughness: 0.35 }),
  white: new THREE.MeshStandardMaterial({ color: '#eef2f6', metalness: 0.3, roughness: 0.32 }),
  rail: new THREE.MeshStandardMaterial({ color: '#c3ccd6', metalness: 0.5, roughness: 0.4 }),
  railY: new THREE.MeshStandardMaterial({ color: '#f2c230', metalness: 0.2, roughness: 0.5 }),
  walk: new THREE.MeshStandardMaterial({ color: '#b9c5d2', metalness: 0.4, roughness: 0.5 }),
  pump: new THREE.MeshStandardMaterial({ color: '#2a78dc', metalness: 0.45, roughness: 0.32 }),
  pumpD: new THREE.MeshStandardMaterial({ color: '#1a56b0', metalness: 0.5, roughness: 0.35 }),
  red: new THREE.MeshStandardMaterial({ color: '#e0383e', metalness: 0.3, roughness: 0.4 }),
  base: new THREE.MeshStandardMaterial({ color: '#6f7d8e', metalness: 0.5, roughness: 0.5 }),
  green: new THREE.MeshStandardMaterial({ color: '#a9dcb4', metalness: 0.15, roughness: 0.45 }),
  greenD: new THREE.MeshStandardMaterial({ color: '#7cc493', metalness: 0.15, roughness: 0.5 }),
  vent: new THREE.MeshStandardMaterial({ map: ventTex, roughness: 0.6, metalness: 0.2 }),
  fin: new THREE.MeshStandardMaterial({ map: finTex, roughness: 0.5, metalness: 0.4 }),
  dark: new THREE.MeshStandardMaterial({ color: '#33465e', metalness: 0.4, roughness: 0.5 }),
  flame: new THREE.MeshBasicMaterial({ color: '#ff9d2e' }),
  glass: new THREE.MeshStandardMaterial({ color: '#7fc3ea', metalness: 0.2, roughness: 0.1, transparent: true, opacity: 0.7 }),
};

export const PIPE_COLORS = {
  water: '#58bde6', sludge: '#c98a3c', gas: '#efb528', air: '#9aaabd', dose: '#5cc28c',
};
const pipeMats = {};
export function pipeMat(kind) {
  if (!pipeMats[kind]) pipeMats[kind] = new THREE.MeshStandardMaterial({ color: PIPE_COLORS[kind], metalness: 0.35, roughness: 0.35 });
  return pipeMats[kind];
}

// ---------------------------------------------------------------- 水面着色器
const NOISE = /* glsl */`
float hash(vec2 p){ p = fract(p*vec2(123.34, 456.21)); p += dot(p, p+45.32); return fract(p.x*p.y); }
float noise(vec2 p){ vec2 i=floor(p), f=fract(p); vec2 u=f*f*(3.-2.*f);
  return mix(mix(hash(i),hash(i+vec2(1,0)),u.x), mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),u.x), u.y); }
float fbm(vec2 p){ float v=0., a=.5; for(int i=0;i<5;i++){ v+=a*noise(p); p=p*2.03+vec2(1.7,9.2); a*=.5; } return v; }
`;
export const waterUniforms = { uTime: { value: 0 } };

export function waterMaterial({ deep = '#1597cf', light = '#63d3f2', foam = 0.15, bubbles = 0, round = false, size = [10, 10], speed = 1 } = {}) {
  return new THREE.ShaderMaterial({
    uniforms: {
      uTime: waterUniforms.uTime,
      uDeep: { value: new THREE.Color(deep) },
      uLight: { value: new THREE.Color(light) },
      uFoam: { value: foam }, uBubbles: { value: bubbles },
      uRound: { value: round ? 1 : 0 }, uSize: { value: new THREE.Vector2(...size) }, uSpeed: { value: speed },
    },
    vertexShader: /* glsl */`
      varying vec2 vW; varying vec2 vUv;
      void main(){ vec4 wp = modelMatrix*vec4(position,1.); vW = wp.xz; vUv = uv; gl_Position = projectionMatrix*viewMatrix*wp; }`,
    fragmentShader: /* glsl */`
      uniform float uTime, uFoam, uBubbles, uRound, uSpeed; uniform vec3 uDeep, uLight; uniform vec2 uSize;
      varying vec2 vW; varying vec2 vUv;
      ${NOISE}
      void main(){
        float t = uTime*uSpeed;
        vec2 p = vW*0.42;
        float n = fbm(p + vec2(t*0.05, t*0.035));
        float n2 = fbm(p*2.2 - vec2(t*0.09, -t*0.05));
        vec3 col = mix(uDeep, uLight, smoothstep(0.28, 0.82, n));
        float c = abs(sin(n2*7.0 + t*0.5));
        col += vec3(pow(1.0-c, 8.0)*0.22);
        float e;
        if (uRound > 0.5) { e = smoothstep(0.80, 1.0, length(vUv-0.5)*2.0); }
        else { vec2 d = min(vUv, 1.0-vUv)*uSize; e = 1.0 - smoothstep(0.0, 1.4, min(d.x, d.y)); }
        col = mix(col, uLight*1.12 + 0.06, e*0.55);
        float f = smoothstep(0.52, 0.78, fbm(p*1.7 + vec2(-t*0.13, t*0.11)));
        float f2 = smoothstep(0.62, 0.7, fbm(p*4.0 + vec2(t*0.2, t*0.07)));
        col = mix(col, vec3(0.96, 0.99, 1.0), clamp(f*uFoam + f2*uFoam*0.7, 0., 0.92));
        if (uBubbles > 0.0) {
          vec2 g = vW*1.5; vec2 id = floor(g); vec2 fr = fract(g) - 0.5;
          float h = hash(id); float ph = fract(h*7.0 + t*0.55*(0.6+h));
          vec2 o = (vec2(hash(id+3.1), hash(id+7.7)) - 0.5)*0.4;
          float ring = smoothstep(0.045, 0.0, abs(length(fr+o) - (0.06+0.2*ph))) * (1.0-ph) * step(0.35, h);
          float dotb = smoothstep(0.07, 0.02, length(fr+o)) * step(0.35, h) * (0.6 + 0.4*sin(t*3.+h*20.));
          col = mix(col, vec3(1.0), clamp((ring + dotb*0.8)*uBubbles, 0., 1.));
        }
        gl_FragColor = vec4(col, 1.0);
        #include <colorspace_fragment>
      }`,
  });
}

// ---------------------------------------------------------------- 基础工具
export function box(w, h, d, mat, x = 0, y = 0, z = 0) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat);
  m.position.set(x, y, z); m.castShadow = true; m.receiveShadow = true;
  return m;
}
export function cyl(rt, rb, h, mat, seg = 32, open = false) {
  const m = new THREE.Mesh(new THREE.CylinderGeometry(rt, rb, h, seg, 1, open), mat);
  m.castShadow = true; m.receiveShadow = true;
  return m;
}
function shadow(o) { o.traverse(m => { if (m.isMesh) { m.castShadow = true; m.receiveShadow = true; } }); return o; }

// 网格平台
export function platform(w, d, h = 0.35, cell = 2) {
  const g = new THREE.Group();
  const tex = M.grid.map.clone(); tex.needsUpdate = true;
  tex.repeat.set(w / (8 * cell), d / (8 * cell));
  const top = new THREE.MeshStandardMaterial({ map: tex, roughness: 0.95 });
  const mats = [M.plinth, M.plinth, top, M.plinth, M.plinth, M.plinth];
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mats);
  m.position.y = h / 2; m.receiveShadow = true;
  g.add(m);
  return g;
}

// 直线栏杆：pts 为 [x,z] 列表（局部坐标），y 为底部高度
export function railing(pts, y, { h = 1.0, mat = M.rail, step = 1.6, closed = false } = {}) {
  const g = new THREE.Group();
  const P = pts.map(p => new THREE.Vector3(p[0], y, p[1]));
  if (closed) P.push(P[0].clone());
  const postGeo = new THREE.CylinderGeometry(0.04, 0.04, h, 6);
  for (let i = 0; i < P.length - 1; i++) {
    const a = P[i], b = P[i + 1];
    const len = a.distanceTo(b);
    const n = Math.max(1, Math.round(len / step));
    for (let k = 0; k <= n; k++) {
      if (k === n && i < P.length - 2) continue;
      const p = a.clone().lerp(b, k / n);
      const post = new THREE.Mesh(postGeo, mat); post.position.set(p.x, y + h / 2, p.z); g.add(post);
    }
    for (const hh of [h, h * 0.5]) {
      const r = new THREE.Mesh(new THREE.CylinderGeometry(0.045, 0.045, len, 6), mat);
      r.position.copy(a.clone().lerp(b, 0.5)); r.position.y = y + hh;
      r.lookAt(b.x, y + hh, b.z); r.rotateX(Math.PI / 2);
      g.add(r);
    }
  }
  return shadow(g);
}

export function ringRailing(r, y, { h = 1.0, mat = M.rail, posts = 48 } = {}) {
  const g = new THREE.Group();
  const postGeo = new THREE.CylinderGeometry(0.04, 0.04, h, 6);
  for (let i = 0; i < posts; i++) {
    const a = i / posts * Math.PI * 2;
    const p = new THREE.Mesh(postGeo, mat); p.position.set(Math.cos(a) * r, y + h / 2, Math.sin(a) * r); g.add(p);
  }
  for (const hh of [h, h * 0.5]) {
    const t = new THREE.Mesh(new THREE.TorusGeometry(r, 0.045, 6, 96), mat);
    t.rotation.x = Math.PI / 2; t.position.y = y + hh; g.add(t);
  }
  return shadow(g);
}

export function ladder(h, mat = M.rail) {
  const g = new THREE.Group();
  for (const x of [-0.25, 0.25]) { const r = box(0.06, h, 0.06, mat, x, h / 2, 0); g.add(r); }
  for (let y = 0.3; y < h; y += 0.35) g.add(box(0.5, 0.04, 0.04, mat, 0, y, 0));
  // 护笼
  for (let y = 2.2; y < h; y += 0.9) {
    const t = new THREE.Mesh(new THREE.TorusGeometry(0.42, 0.025, 4, 16, Math.PI), mat);
    t.rotation.x = Math.PI / 2; t.position.set(0, y, 0); t.rotation.z = Math.PI; g.add(t);
  }
  return shadow(g);
}

// ---------------------------------------------------------------- 水池
// 矩形混凝土水池（开口），返回 group；opts.dividers=[x...] 隔墙
export function rectTank(w, d, h, { water = {}, dividers = [], rail = true, wall = 0.45, waterInset = 0.55, waters = null } = {}) {
  const g = new THREE.Group();
  const t = wall;
  g.add(box(w, h, t, M.concrete, 0, h / 2, -d / 2 + t / 2));
  g.add(box(w, h, t, M.concrete, 0, h / 2, d / 2 - t / 2));
  g.add(box(t, h, d - 2 * t, M.concrete, -w / 2 + t / 2, h / 2, 0));
  g.add(box(t, h, d - 2 * t, M.concrete, w / 2 - t / 2, h / 2, 0));
  // 顶部压顶
  g.add(box(w + 0.12, 0.12, t + 0.12, M.concreteTop, 0, h, -d / 2 + t / 2));
  g.add(box(w + 0.12, 0.12, t + 0.12, M.concreteTop, 0, h, d / 2 - t / 2));
  g.add(box(t + 0.12, 0.12, d, M.concreteTop, -w / 2 + t / 2, h, 0));
  g.add(box(t + 0.12, 0.12, d, M.concreteTop, w / 2 - t / 2, h, 0));
  for (const x of dividers) {
    g.add(box(t, h, d - 2 * t, M.concrete, x, h / 2, 0));
    g.add(box(t + 0.12, 0.12, d - 2 * t, M.concreteTop, x, h, 0));
  }
  // 水面（可分段）
  const segs = waters || [{ x0: -w / 2 + t, x1: w / 2 - t, ...water }];
  for (const s of segs) {
    const ww = s.x1 - s.x0, dd = d - 2 * t;
    const m = new THREE.Mesh(new THREE.PlaneGeometry(ww, dd), waterMaterial({ ...s, size: [ww, dd] }));
    m.rotation.x = -Math.PI / 2; m.position.set((s.x0 + s.x1) / 2, h - waterInset, 0);
    g.add(m);
  }
  if (rail) {
    const o = 0.18;
    g.add(railing([[-w / 2 + o, -d / 2 + o], [w / 2 - o, -d / 2 + o], [w / 2 - o, d / 2 - o], [-w / 2 + o, d / 2 - o]], h + 0.06, { closed: true, h: 0.9 }));
  }
  return g;
}

// 圆形辐流沉淀池（带旋转刮泥桥）
export function clarifier(r, h, { water = {}, bridge = true } = {}) {
  const g = new THREE.Group();
  const t = 0.45;
  const outer = cyl(r + t, r + t, h, M.concrete, 96, true); outer.position.y = h / 2; g.add(outer);
  const inner = cyl(r, r, h, M.concreteIn, 96, true); inner.material = M.concreteIn.clone(); inner.material.side = THREE.BackSide; inner.position.y = h / 2; g.add(inner);
  const top = new THREE.Mesh(new THREE.RingGeometry(r - 0.02, r + t + 0.05, 96), M.concreteTop);
  top.rotation.x = -Math.PI / 2; top.position.y = h; top.receiveShadow = true; g.add(top);
  // 出水堰槽
  const weir = cyl(r - 0.9, r - 0.9, 0.7, M.concrete, 96, true); weir.material = M.concreteTop; weir.position.y = h - 0.55; g.add(weir);
  const wm = new THREE.Mesh(new THREE.CircleGeometry(r, 96), waterMaterial({ round: true, ...water }));
  wm.rotation.x = -Math.PI / 2; wm.position.y = h - 0.6; g.add(wm);
  // 中心筒
  const col = cyl(0.9, 0.9, 1.6, M.steel, 32); col.position.y = h - 0.3; g.add(col);
  const colTop = cyl(1.1, 1.1, 0.25, M.steelD, 32); colTop.position.y = h + 0.55; g.add(colTop);
  g.add(ringRailing(r + t * 0.6, h, { h: 0.9, posts: Math.round(r * 6) }));
  if (bridge) {
    const b = new THREE.Group(); b.position.y = h + 0.7;
    const L = r * 2 + 0.6;
    b.add(box(L, 0.3, 1.3, M.walk, 0, 0, 0));
    b.add(box(L, 0.08, 1.5, M.steel, 0, 0.18, 0));
    b.add(railing([[-L / 2, -0.7], [L / 2, -0.7]], 0.2, { h: 0.85, step: 1.2 }));
    b.add(railing([[-L / 2, 0.7], [L / 2, 0.7]], 0.2, { h: 0.85, step: 1.2 }));
    // 驱动小车
    b.add(box(1.4, 0.8, 1.6, M.pump, L / 2 - 0.7, 0.4, 0));
    b.add(box(1.4, 0.8, 1.6, M.steelD, -L / 2 + 0.7, 0.4, 0));
    // 刮臂
    for (const s of [-1, 1]) {
      const arm = box(r * 0.9, 0.12, 0.12, M.steelD, s * r * 0.48, -0.9, 0); b.add(arm);
    }
    shadow(b);
    b.userData.dynamic = true;
    b.userData.anim = { name: 'scraper_bridge', speed: 0.06 };   // rad/s，绕 Y；导出 GLB 动画用
    g.add(b);
    animated.push({ obj: b, update: (tm, dt) => { b.rotation.y += dt * 0.06; } });
  }
  return g;
}

// 立式罐（Lathe 轮廓）
export function vTank(r, h, { top = 'dome', bottom = 'flat', legs = 0, mat = M.steel, bands = true, rail = false, ladderSide = null, open = false, water = null } = {}) {
  const g = new THREE.Group();
  const lift = legs;
  const pts = [];
  if (bottom === 'cone') { pts.push(new THREE.Vector2(0.15, 0)); pts.push(new THREE.Vector2(r, r * 0.8)); }
  else { pts.push(new THREE.Vector2(0, 0)); pts.push(new THREE.Vector2(r, 0)); }
  const y0 = bottom === 'cone' ? r * 0.8 : 0;
  pts.push(new THREE.Vector2(r, y0 + h));
  if (!open) {
    if (top === 'dome') {
      for (let i = 1; i <= 10; i++) { const a = i / 10 * Math.PI / 2; pts.push(new THREE.Vector2(Math.cos(a) * r, y0 + h + Math.sin(a) * r * 0.32)); }
    } else if (top === 'cone') {
      pts.push(new THREE.Vector2(0.12, y0 + h + r * 0.55)); pts.push(new THREE.Vector2(0, y0 + h + r * 0.55));
    } else { pts.push(new THREE.Vector2(0, y0 + h)); }
  }
  const body = new THREE.Mesh(new THREE.LatheGeometry(pts, 64), mat);
  if (open) { body.material = mat.clone(); body.material.side = THREE.DoubleSide; }
  body.position.y = lift; body.castShadow = body.receiveShadow = true; g.add(body);
  if (open && water) {
    const wm = new THREE.Mesh(new THREE.CircleGeometry(r * 0.99, 64), waterMaterial({ round: true, ...water }));
    wm.rotation.x = -Math.PI / 2; wm.position.y = lift + y0 + h - 0.4; g.add(wm);
    const rim = new THREE.Mesh(new THREE.TorusGeometry(r, 0.08, 6, 64), M.steelD); rim.rotation.x = Math.PI / 2; rim.position.y = lift + y0 + h; g.add(rim);
  }
  if (bands) {
    const n = Math.max(1, Math.floor(h / 2.6));
    for (let i = 1; i <= n; i++) {
      const b = new THREE.Mesh(new THREE.TorusGeometry(r + 0.015, 0.035, 4, 64), M.steelD);
      b.rotation.x = Math.PI / 2; b.position.y = lift + y0 + i * h / (n + 1); g.add(b);
    }
  }
  if (legs) {
    for (let i = 0; i < 4; i++) {
      const a = i / 4 * Math.PI * 2 + Math.PI / 4;
      const L = box(0.18, lift + y0 * 0.9, 0.18, M.steelD, Math.cos(a) * r * 0.82, (lift + y0 * 0.9) / 2, Math.sin(a) * r * 0.82); g.add(L);
    }
  }
  if (rail) g.add(ringRailing(r * 0.92, lift + y0 + h + (top === 'dome' ? r * 0.2 : 0), { h: 0.8, posts: 28 }));
  if (ladderSide !== null) {
    const l = ladder(h + y0 + lift + 0.2); const a = ladderSide;
    l.position.set(Math.cos(a) * (r + 0.32), 0, Math.sin(a) * (r + 0.32)); l.rotation.y = -a + Math.PI / 2; g.add(l);
  }
  return shadow(g);
}

// 离心泵：默认出口朝 +x
export function pump({ scale = 1, color = M.pump } = {}) {
  const g = new THREE.Group();
  g.add(box(2.0, 0.22, 0.9, M.base, 0, 0.11, 0));
  const motor = cyl(0.36, 0.36, 1.05, color, 24); motor.rotation.z = Math.PI / 2; motor.position.set(-0.4, 0.62, 0); g.add(motor);
  const fan = cyl(0.3, 0.3, 0.2, M.pumpD, 24); fan.rotation.z = Math.PI / 2; fan.position.set(-1.0, 0.62, 0); g.add(fan);
  for (let i = 0; i < 6; i++) { const f = box(0.9, 0.05, 0.05, M.pumpD); f.position.set(-0.4, 0.62 + Math.sin(i) * 0.37, Math.cos(i) * 0.37); g.add(f); }
  const cup = cyl(0.16, 0.16, 0.35, M.steelD, 16); cup.rotation.z = Math.PI / 2; cup.position.set(0.25, 0.62, 0); g.add(cup);
  const vol = cyl(0.48, 0.48, 0.42, M.pumpD, 28); vol.rotation.z = Math.PI / 2; vol.position.set(0.62, 0.62, 0); g.add(vol);
  const inlet = cyl(0.16, 0.16, 0.4, M.pumpD, 16); inlet.rotation.z = Math.PI / 2; inlet.position.set(0.95, 0.62, 0); g.add(inlet);
  const out = cyl(0.14, 0.14, 0.55, M.pumpD, 16); out.position.set(0.62, 1.15, 0); g.add(out);
  const v = box(0.34, 0.1, 0.1, M.red, 0.62, 1.32, 0); g.add(v);
  const fl = cyl(0.22, 0.22, 0.06, M.steelD, 16); fl.position.set(0.62, 1.42, 0); g.add(fl);
  g.scale.setScalar(scale);
  return shadow(g);
}

// 搅拌机（立式），buoy 下方插入水中
export function mixer({ shaft = 3, color = M.pump } = {}) {
  const g = new THREE.Group();
  const m = cyl(0.42, 0.42, 1.0, color, 24); m.position.y = 1.15; g.add(m);
  const cap = cyl(0.3, 0.42, 0.25, M.pumpD, 24); cap.position.y = 1.78; g.add(cap);
  const gear = box(0.9, 0.5, 0.9, M.steelD, 0, 0.4, 0); g.add(gear);
  const s = cyl(0.07, 0.07, shaft, M.steelD, 8); s.position.y = -shaft / 2 + 0.2; g.add(s);
  const fanG = new THREE.Group(); fanG.position.y = 1.95;
  for (let i = 0; i < 3; i++) { const b = box(0.55, 0.04, 0.12, M.steel); b.position.x = 0.22; const p = new THREE.Group(); p.rotation.y = i * Math.PI * 2 / 3; p.add(b); fanG.add(p); }
  fanG.userData.dynamic = true;
  fanG.userData.anim = { name: 'mixer_blades', speed: 4 };
  g.add(fanG);
  animated.push({ obj: fanG, update: (t, dt) => { fanG.rotation.y += dt * 4; } });
  return shadow(g);
}

// ---------------------------------------------------------------- 管道
// 带圆角的折线管，pts: [[x,y,z],...]；kind 决定颜色；flow 开启流向动画
export function pipe(pts, { kind = 'water', r = 0.22, flow = true, speed = 1, bend = 0.7, dash = 3.2, seg = 3 } = {}) {
  const P = pts.map(p => new THREE.Vector3(...p));
  const path = new THREE.CurvePath();
  let cur = P[0].clone();
  for (let i = 1; i < P.length; i++) {
    const a = P[i - 1], b = P[i];
    if (i < P.length - 1) {
      const c = P[i + 1];
      const r1 = Math.min(bend, a.distanceTo(b) / 2, b.distanceTo(c) / 2);
      const p1 = b.clone().add(a.clone().sub(b).setLength(r1));
      const p2 = b.clone().add(c.clone().sub(b).setLength(r1));
      if (cur.distanceTo(p1) > 1e-4) path.add(new THREE.LineCurve3(cur.clone(), p1));
      path.add(new THREE.QuadraticBezierCurve3(p1, b.clone(), p2));
      cur = p2;
    } else {
      path.add(new THREE.LineCurve3(cur.clone(), b.clone()));
    }
  }
  const len = path.getLength();
  const segs = Math.max(16, Math.round(len * seg));
  const geo = new THREE.TubeGeometry(path, segs, r, 12, false);
  const g = new THREE.Group();
  Object.assign(g.userData, { pipe: true, curve: path, len, r, kind, pts });
  const m = new THREE.Mesh(geo, pipeMat(kind)); m.castShadow = true; m.receiveShadow = true; g.add(m);
  // 端头法兰
  for (const [p, q] of [[P[0], P[1]], [P[P.length - 1], P[P.length - 2]]]) {
    const f = new THREE.Mesh(new THREE.CylinderGeometry(r * 1.45, r * 1.45, 0.12, 16), M.steelD);
    f.position.copy(p); f.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), q.clone().sub(p).normalize()); g.add(f);
  }
  if (flow) {
    const tex = flowTex.clone(); tex.needsUpdate = true;
    tex.repeat.set(len / dash, 1);
    const fm = new THREE.MeshBasicMaterial({ map: tex, transparent: true, depthWrite: false, opacity: 0.85, color: '#ffffff', blending: THREE.AdditiveBlending });
    const fg = new THREE.Mesh(new THREE.TubeGeometry(path, segs, r * 1.06, 12, false), fm);
    fg.renderOrder = 2; g.add(fg);
    const entry = { tex, speed, base: speed, mat: fm, mesh: fg };
    flowTextures.push(entry);
    g.userData.flow = entry;
  }
  return g;
}

// 阀门（红手轮），放在管线上
export function valve(axis = 'x') {
  const g = new THREE.Group();
  const b = cyl(0.3, 0.3, 0.5, M.steelD, 16); if (axis === 'x') b.rotation.z = Math.PI / 2; else b.rotation.x = Math.PI / 2; g.add(b);
  const s = cyl(0.05, 0.05, 0.5, M.steelD, 6); s.position.y = 0.4; g.add(s);
  const w = new THREE.Mesh(new THREE.TorusGeometry(0.22, 0.05, 6, 16), M.red); w.rotation.x = Math.PI / 2; w.position.y = 0.65; g.add(w);
  return shadow(g);
}

// ---------------------------------------------------------------- 专用设备
export function blowerCabinet() {
  const g = new THREE.Group();
  const front = M.vent;
  const mats = [front, front, M.white, M.white, front, front];
  const b = new THREE.Mesh(new THREE.BoxGeometry(3.4, 4.2, 2.6), mats); b.position.y = 2.1; g.add(b);
  const b2 = new THREE.Mesh(new THREE.BoxGeometry(2.2, 3.4, 2.4), mats); b2.position.set(2.9, 1.7, 0); g.add(b2);
  g.add(box(3.6, 0.18, 2.8, M.steelD, 0, 4.25, 0));
  g.add(box(6.4, 0.2, 3.0, M.base, 1.3, 0.1, 0));
  const lamp = cyl(0.12, 0.12, 0.3, M.red, 12); lamp.position.set(1.2, 4.5, 0.8); g.add(lamp);
  const out = cyl(0.35, 0.35, 1.2, M.steel, 16); out.position.set(-0.8, 4.8, -0.4); g.add(out);
  return shadow(g);
}

export function boiler() {
  const g = new THREE.Group();
  const L = 9, r = 2.0;
  const body = cyl(r, r, L, M.white, 48); body.rotation.z = Math.PI / 2; body.position.set(0, r + 0.9, 0); g.add(body);
  for (const s of [-1, 1]) {
    const cap = new THREE.Mesh(new THREE.SphereGeometry(r, 48, 16, 0, Math.PI * 2, 0, Math.PI / 2), M.white);
    cap.scale.y = 0.35; cap.rotation.z = -s * Math.PI / 2; cap.position.set(s * L / 2, r + 0.9, 0); g.add(cap);
  }
  for (let i = -3; i <= 3; i += 2) { const b = new THREE.Mesh(new THREE.TorusGeometry(r + 0.02, 0.05, 4, 48), M.steelD); b.rotation.y = Math.PI / 2; b.position.set(i * 1.2, r + 0.9, 0); g.add(b); }
  g.add(box(1.4, 1.2, 3.6, M.steelD, -L / 2 + 1.5, 0.6, 0));
  g.add(box(1.4, 1.2, 3.6, M.steelD, L / 2 - 1.5, 0.6, 0));
  // 燃烧器
  const burner = box(1.6, 1.8, 1.6, M.pump, -L / 2 - 1.3, 2.6, 0); g.add(burner);
  const fl = new THREE.Mesh(new THREE.SphereGeometry(0.35, 12, 8), M.flame); fl.position.set(-L / 2 - 2.15, 2.6, 0); g.add(fl);
  // 烟囱
  const ch = cyl(0.45, 0.5, 6, M.steel, 20); ch.position.set(L / 2 - 1, 2 * r + 3.6, 0); g.add(ch);
  const ch2 = cyl(0.55, 0.55, 0.3, M.steelD, 20); ch2.position.set(L / 2 - 1, 2 * r + 6.6, 0); g.add(ch2);
  g.add(railing([[-L / 2 + 0.5, -1.2], [L / 2 - 0.5, -1.2]], 2 * r + 0.95, { h: 0.7 }));
  g.add(box(L - 1, 0.1, 1.4, M.walk, 0, 2 * r + 0.95, -0.6));
  return shadow(g);
}

export function flameArrester() {
  const g = new THREE.Group();
  g.add(box(1.8, 0.2, 1.4, M.base, 0, 0.1, 0));
  for (const x of [-0.7, 0.7]) g.add(box(0.12, 1.2, 0.12, M.steelD, x, 0.7, 0));
  const fin = new THREE.Mesh(new THREE.BoxGeometry(1.8, 1.6, 1.4), [M.steelD, M.steelD, M.white, M.white, M.fin, M.fin]);
  fin.position.y = 2.0; g.add(fin);
  for (const s of [-1, 1]) { const c = cyl(0.25, 0.25, 0.5, M.steelD, 16); c.rotation.z = Math.PI / 2; c.position.set(s * 1.1, 2.0, 0); g.add(c); }
  return shadow(g);
}

export function boosterSkid() {
  const g = new THREE.Group();
  g.add(box(6, 0.3, 3, M.base, 0, 0.15, 0));
  const p = pump({ scale: 1.25, color: M.pump }); p.position.set(-1.4, 0.3, 0.3); g.add(p);
  const t = cyl(0.7, 0.7, 2.6, M.white, 24); t.rotation.z = Math.PI / 2; t.position.set(1.6, 1.3, -0.3); g.add(t);
  for (const s of [-1, 1]) { const c = new THREE.Mesh(new THREE.SphereGeometry(0.7, 20, 10, 0, Math.PI * 2, 0, Math.PI / 2), M.white); c.scale.y = 0.4; c.rotation.z = -s * Math.PI / 2; c.position.set(1.6 + s * 1.3, 1.3, -0.3); g.add(c); }
  const fl = cyl(0.25, 0.25, 1.4, M.steelD, 12); fl.position.set(2.5, 1.0, 1.0); g.add(fl);
  const gauge = cyl(0.22, 0.22, 0.08, M.white, 16); gauge.rotation.x = Math.PI / 2; gauge.position.set(2.5, 1.9, 1.05); g.add(gauge);
  return shadow(g);
}

// 废气洗涤塔
export function scrubber(h = 9, r = 1.5) {
  const g = new THREE.Group();
  g.add(box(r * 2.4, 0.3, r * 2.4, M.base, 0, 0.15, 0));
  const b = cyl(r, r, h, M.green, 40); b.position.y = h / 2 + 0.3; g.add(b);
  const top = cyl(0.5, r, r * 0.6, M.green, 40); top.position.y = h + 0.3 + r * 0.3; g.add(top);
  for (let i = 1; i < 4; i++) { const t = new THREE.Mesh(new THREE.TorusGeometry(r + 0.02, 0.06, 4, 40), M.greenD); t.rotation.x = Math.PI / 2; t.position.y = 0.3 + i * h / 4; g.add(t); }
  const win = box(0.6, 0.8, 0.1, M.glass, 0, h * 0.35, r); g.add(win);
  return shadow(g);
}

export function centrifugalFan() {
  const g = new THREE.Group();
  g.add(box(3.2, 0.3, 1.8, M.base, 0, 0.15, 0));
  const vol = cyl(1.3, 1.3, 0.9, M.greenD, 36); vol.rotation.x = Math.PI / 2; vol.position.set(0.5, 1.6, 0); g.add(vol);
  const out = box(1.0, 1.4, 0.9, M.greenD, 1.2, 2.6, 0); g.add(out);
  const m = cyl(0.42, 0.42, 1.1, M.pump, 20); m.rotation.x = Math.PI / 2; m.position.set(-0.9, 0.85, 0); g.add(m);
  const hub = cyl(0.5, 0.5, 0.15, M.steelD, 20); hub.rotation.x = Math.PI / 2; hub.position.set(0.5, 1.6, 0.5); g.add(hub);
  return shadow(g);
}

// 管道入口箭头（地面上）
export function arrowHead(kind = 'water', r = 0.5) {
  const m = new THREE.Mesh(new THREE.ConeGeometry(r, r * 1.6, 16), pipeMat(kind));
  m.castShadow = true;
  return m;
}
