// 科技感特效：发光粒子水流、温度色带、地面扫描线、全息底座、报警光柱、全息描边。
// 发光部分输出 >1 的 HDR 颜色，交给引擎的辉光（Bloom）阈值挑出来。
import * as THREE from 'three';

// ---------------------------------------------------------------- 温度色带
const RAMP = [[10, '#3b82f6'], [35, '#22d3ff'], [45, '#3ff0b0'], [55, '#ffc23d'], [75, '#ff7a2f'], [105, '#ff2d55']].map(([t, c]) => [t, new THREE.Color(c)]);
export function tempColor(t, out = new THREE.Color()) {
  if (t <= RAMP[0][0]) return out.copy(RAMP[0][1]);
  for (let i = 1; i < RAMP.length; i++) {
    if (t <= RAMP[i][0]) { const [t0, c0] = RAMP[i - 1], [t1, c1] = RAMP[i]; return out.copy(c0).lerp(c1, (t - t0) / (t1 - t0)); }
  }
  return out.copy(RAMP[RAMP.length - 1][1]);
}
export const RAMP_CSS = 'linear-gradient(90deg,#22d3ff 0%,#3ff0b0 22%,#ffc23d 40%,#ff7a2f 62%,#ff2d55 100%)';

// ---------------------------------------------------------------- 发光粒子水流
// pipes: [{ group(userData.curve/len/r), entry(速度句柄), color }]
export class FlowParticles {
  constructor(pipes, { spacing = 0.55, trail = 3, speed = 1.3 } = {}) {
    this.pipes = pipes; this.speed = speed; this.trail = trail;
    this.samples = pipes.map(p => {
      const n = Math.max(8, Math.ceil(p.group.userData.len * 8));
      const pts = new Float32Array((n + 1) * 3);
      for (let i = 0; i <= n; i++) { const v = p.group.userData.curve.getPointAt(i / n); pts.set([v.x, v.y, v.z], i * 3); }
      return { n, pts };
    });
    const list = [];
    pipes.forEach((p, pi) => {
      const cnt = Math.max(2, Math.round(p.group.userData.len / spacing));
      for (let k = 0; k < cnt; k++) list.push({ pi, u: k / cnt + Math.random() * 0.3 / cnt });
    });
    this.parts = list;
    const N = list.length * trail;
    this.pos = new Float32Array(N * 3);
    this.col = new Float32Array(N * 3);
    const size = new Float32Array(N), fade = new Float32Array(N);
    list.forEach((q, i) => {
      const r = pipes[q.pi].group.userData.r;
      for (let k = 0; k < trail; k++) { size[i * trail + k] = r * (k ? 4.2 * (1 - k / trail) + 0.6 : 5.0); fade[i * trail + k] = 1 - k / trail; }
    });
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(this.pos, 3).setUsage(THREE.DynamicDrawUsage));
    geo.setAttribute('color', new THREE.BufferAttribute(this.col, 3).setUsage(THREE.DynamicDrawUsage));
    geo.setAttribute('aSize', new THREE.BufferAttribute(size, 1));
    geo.setAttribute('aFade', new THREE.BufferAttribute(fade, 1));
    geo.boundingSphere = new THREE.Sphere(new THREE.Vector3(), 1e4);
    this.mat = new THREE.ShaderMaterial({
      uniforms: { uScale: { value: 800 }, uReveal: { value: 1e4 }, uGain: { value: 2.6 }, uOpacity: { value: 1 } },
      vertexShader: /* glsl */`
        attribute float aSize; attribute float aFade; varying vec3 vCol; varying float vFade; varying float vX;
        uniform float uScale;
        void main(){
          vCol = color; vFade = aFade; vX = position.x;
          vec4 mv = modelViewMatrix * vec4(position, 1.0);
          gl_PointSize = aSize * uScale / -mv.z;
          gl_Position = projectionMatrix * mv;
        }`,
      fragmentShader: /* glsl */`
        varying vec3 vCol; varying float vFade; varying float vX; uniform float uReveal, uGain, uOpacity;
        void main(){
          if (vX > uReveal) discard;
          float d = length(gl_PointCoord - 0.5) * 2.0;
          float a = smoothstep(1.0, 0.0, d); a = a * a;
          float core = smoothstep(0.35, 0.0, d);
          gl_FragColor = vec4(max(vCol + core * 0.6, 0.0) * uGain * vFade * a, clamp(a * vFade * uOpacity, 0.0, 1.0));
        }`,
      transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, vertexColors: true,
    });
    this.points = new THREE.Points(geo, this.mat);
    this.points.renderOrder = 5;
    this.points.frustumCulled = false;
    this.colors = pipes.map(p => new THREE.Color(p.color || '#22d3ff'));
  }
  setColor(i, c) { this.colors[i].copy(c); }
  update(dt, renderer, camera) {
    this.mat.uniforms.uScale.value = renderer.getPixelRatio() * 1080 / (2 * Math.tan(THREE.MathUtils.degToRad(camera.fov) / 2));
    const { pos, col, trail } = this;
    let j = 0;
    for (const q of this.parts) {
      const p = this.pipes[q.pi], s = this.samples[q.pi], len = p.group.userData.len;
      const sp = p.entry ? p.entry.speed : 1;
      q.u = (q.u + dt * sp * this.speed / len) % 1;
      const c = this.colors[q.pi];
      const on = sp > 0.01 ? 1 : 0;
      for (let k = 0; k < trail; k++) {
        let u = q.u - k * 0.16 / len * Math.max(0.6, sp);
        u = ((u % 1) + 1) % 1;
        const f = u * s.n, i0 = Math.floor(f), t = f - i0, a = i0 * 3, b = Math.min(s.n, i0 + 1) * 3;
        pos[j * 3] = s.pts[a] + (s.pts[b] - s.pts[a]) * t;
        pos[j * 3 + 1] = s.pts[a + 1] + (s.pts[b + 1] - s.pts[a + 1]) * t;
        pos[j * 3 + 2] = s.pts[a + 2] + (s.pts[b + 2] - s.pts[a + 2]) * t;
        col[j * 3] = c.r * on; col[j * 3 + 1] = c.g * on; col[j * 3 + 2] = c.b * on;
        j++;
      }
    }
    this.points.geometry.attributes.position.needsUpdate = true;
    this.points.geometry.attributes.color.needsUpdate = true;
  }
}

// ---------------------------------------------------------------- 地面扫描线（沿 z 从后往前扫）
// soft：不画硬亮线，只留一条柔和的光带，并在扫到前后两端时淡出（避免前沿闪一道刺眼的线）
export function scanFloor(w, d, { color = '#2ee6ff', period = 5, soft = false } = {}) {
  const mat = new THREE.ShaderMaterial({
    uniforms: { uTime: { value: 0 }, uColor: { value: new THREE.Color(color) }, uSize: { value: new THREE.Vector2(w, d) }, uPeriod: { value: period }, uOpacity: { value: 1 }, uSoft: { value: soft ? 1 : 0 } },
    vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.); }',
    fragmentShader: /* glsl */`
      varying vec2 vUv; uniform float uTime, uPeriod, uOpacity, uSoft; uniform vec3 uColor; uniform vec2 uSize;
      void main(){
        float p = fract(uTime / uPeriod);
        float z = 1.0 - vUv.y;
        float dz = (p * 1.3 - 0.15) - z;
        float band = step(0.0, dz) * smoothstep(0.0, 0.004, dz) * exp(-max(dz, 0.0) * 14.0);
        float line = smoothstep(0.012, 0.0, abs(dz));
        vec2 g = vUv * uSize;
        float grid = max(smoothstep(0.035, 0.0, abs(fract(g.x) - 0.5) - 0.465), smoothstep(0.035, 0.0, abs(fract(g.y) - 0.5) - 0.465));
        float edge = smoothstep(0.0, 0.03, vUv.x) * smoothstep(1.0, 0.97, vUv.x);
        float a = (band * (0.18 + grid * 0.8) + line * 0.9) * edge * uOpacity;
        if (uSoft > 0.5) {
          float wide = step(0.0, dz) * smoothstep(0.0, 0.03, dz) * exp(-max(dz, 0.0) * 7.0);
          float ends = smoothstep(0.0, 0.12, z) * smoothstep(1.0, 0.72, z);
          a = wide * (0.08 + grid * 0.42) * edge * ends * uOpacity;
        }
        gl_FragColor = vec4(uColor * a * 1.8, a);
      }`,
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
  });
  const m = new THREE.Mesh(new THREE.PlaneGeometry(w, d), mat);
  m.rotation.x = -Math.PI / 2; m.renderOrder = 3;
  m.userData.dynamic = true;
  return m;
}

// ---------------------------------------------------------------- 全息底座（圆角矩形描边 + 四角括号 + 呼吸）
const holoMat = new THREE.ShaderMaterial({
  uniforms: { uTime: { value: 0 }, uColor: { value: new THREE.Color('#2ee6ff') }, uOpacity: { value: 1 } },
  vertexShader: 'attribute vec2 aSize; varying vec2 vUv; varying vec2 vSize; void main(){ vUv = uv; vSize = aSize; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.); }',
  fragmentShader: /* glsl */`
    varying vec2 vUv; varying vec2 vSize; uniform float uTime, uOpacity; uniform vec3 uColor;
    float rbox(vec2 p, vec2 b, float r){ vec2 q = abs(p) - b + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
    void main(){
      vec2 p = (vUv - 0.5) * vSize;
      float d = rbox(p, vSize * 0.5 - 0.12, 0.18);
      float ring = smoothstep(0.05, 0.0, abs(d));
      float fill = smoothstep(0.0, -0.6, d) * 0.12 * (1.0 - smoothstep(-0.6, -2.5, d));
      vec2 q = abs(p) - (vSize * 0.5 - 0.02);
      float corner = step(-0.5, q.x) * step(-0.5, q.y) * smoothstep(0.06, 0.0, min(abs(q.x), abs(q.y)));
      float breathe = 0.65 + 0.35 * sin(uTime * 2.2);
      float a = (ring * 0.7 * breathe + fill + corner * 1.2) * uOpacity;
      gl_FragColor = vec4(uColor * a * 1.6, a);
    }`,
  transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
});
export const holoUniforms = holoMat.uniforms;
export function holoBase(w, d) {
  const geo = new THREE.PlaneGeometry(w, d);
  const s = new Float32Array(geo.attributes.position.count * 2);
  for (let i = 0; i < s.length; i += 2) { s[i] = w; s[i + 1] = d; }
  geo.setAttribute('aSize', new THREE.BufferAttribute(s, 2));
  const m = new THREE.Mesh(geo, holoMat);
  m.rotation.x = -Math.PI / 2; m.renderOrder = 3;
  return m;
}

// ---------------------------------------------------------------- 报警光柱 + 地面脉冲环
export function beacon(color = '#ff3b4a', h = 7) {
  const g = new THREE.Group(); g.userData.dynamic = true;
  const c = new THREE.Color(color);
  const colMat = new THREE.ShaderMaterial({
    uniforms: { uTime: { value: 0 }, uColor: { value: c } },
    vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.); }',
    fragmentShader: /* glsl */`
      varying vec2 vUv; uniform float uTime; uniform vec3 uColor;
      void main(){
        float a = pow(clamp(1.0 - vUv.y, 0.0, 1.0), 1.6) * (0.55 + 0.45 * sin(uTime * 6.0));
        float stripes = 0.75 + 0.25 * sin((vUv.y * 20.0 - uTime * 4.0));
        gl_FragColor = vec4(uColor * 2.4 * a * stripes, a * 0.8);
      }`,
    transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide,
  });
  const col = new THREE.Mesh(new THREE.CylinderGeometry(0.55, 0.85, h, 32, 1, true), colMat);
  col.position.y = h / 2; g.add(col);
  const rings = [];
  for (let i = 0; i < 3; i++) {
    const rm = new THREE.MeshBasicMaterial({ color: c.clone().multiplyScalar(2.2), transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide });
    const r = new THREE.Mesh(new THREE.RingGeometry(0.9, 1.05, 48), rm);
    r.rotation.x = -Math.PI / 2; r.position.y = 0.03; g.add(r); rings.push(r);
  }
  g.userData.update = t => {
    colMat.uniforms.uTime.value = t;
    rings.forEach((r, i) => {
      const k = (t * 0.6 + i / 3) % 1;
      r.scale.setScalar(0.6 + k * 3.2);
      r.material.opacity = (1 - k) * 0.9;
    });
  };
  return g;
}

// ---------------------------------------------------------------- 全息描边（给已合并的设备加边线，挂在网格下随设备移动）
export function edgeOverlay(root, { color = '#2ee6ff', opacity = 0.3, angle = 32 } = {}) {
  const mat = new THREE.LineBasicMaterial({ color: new THREE.Color(color).multiplyScalar(1.4), transparent: true, opacity, depthWrite: false, blending: THREE.AdditiveBlending });
  const lines = [];
  const meshes = [];
  root.traverse(m => { if (m.isMesh && !m.material.transparent && !m.material.isShaderMaterial && !Array.isArray(m.material)) meshes.push(m); });
  for (const m of meshes) {
    if (m.geometry.attributes.position.count > 80000) continue;
    const ls = new THREE.LineSegments(new THREE.EdgesGeometry(m.geometry, angle), mat);
    ls.raycast = () => {};
    m.add(ls); lines.push(ls);
  }
  return { mat, lines, set visible(v) { lines.forEach(l => (l.visible = v)); } };
}
