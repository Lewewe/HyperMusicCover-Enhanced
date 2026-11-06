// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// TypeScript port of ColorOsPill.kt. Its geometry, springs and timings are modelled on the
// behaviour and parameter values of ColorOS 16 (OPPO); no ColorOS code is included.

export class Box {
  constructor(public readonly x: number, public readonly y: number,
    public readonly w: number, public readonly h: number) {}
  cx() { return this.x + this.w * 0.5; }
  cy() { return this.y + this.h * 0.5; }
}

export const BRACKET_OUT_DP = 9;
export const BRACKET_STROKE_DP = 2.4;
export const BLUR_DP = 12;
const START_HEIGHT = 0.8;
const EAR_HALF_ANGLE = 0.47;
const BLEND_SHAPE = 0.45;
const BLEND_K = 0.14;
export const clamp = (x: number, lo = 0, hi = 1) => Math.min(hi, Math.max(lo, x));
export function smooth(t: number) {
  const x = clamp(t);
  return x * x * (3 - 2 * x);
}
function smoothstepF(e0: number, e1: number, x: number) {
  if (e0 === e1) return x < e0 ? 0 : 1;
  return smooth((x - e0) / (e1 - e0));
}
function gain(x: number, k: number) {
  const a = 0.5 * Math.pow(2 * (x < 0.5 ? x : 1 - x), k);
  return x < 0.5 ? a : 1 - a;
}
export const earWidth = (stroke: number) => 2.5 * stroke;
export function earEnd(rest: Box) {
  const start = rest.h * START_HEIGHT;
  return rest.w * start / (rest.w - rest.h + start);
}
export const roundAt = (rest: Box, stroke: number) =>
  clamp((earEnd(rest) - earWidth(stroke)) / rest.w, 0.02, 0.9);

export interface Frame {
  old: Box;
  oldBean: number;
  new: Box;
  newBean: number;
  reach: number;
  bracket: number;
  oldBracket: number;
}

export function frame(rest: Box, u: number, next: boolean, _gap: number,
  out: number, stroke: number): Frame {
  const w = rest.w, h = rest.h;
  const ear = earWidth(stroke);
  const start = h * START_HEIGHT;
  const d = clamp(u) * w;
  const cutMax = Math.max(w - ear - 1, 1);
  const cut = Math.min(d, cutMax);
  const oldW = w - cut;
  const oldH = h - (h - start) * cut / cutMax;
  const oldX = rest.x - out * Math.min(1, d / Math.max(w - out, 1));
  const newW = Math.min(d + ear, w);
  const newH = h - (h - start) * (w - newW) / w;
  const newR = rest.x + w + out * (1 - d / w);
  const box = (x: number, bw: number, bh: number) => new Box(x, rest.cy() - bh / 2, bw, bh);
  const o = box(oldX, oldW, oldH);
  const n = box(newR - newW, newW, newH);
  const end = earEnd(rest);
  const oldBean = oldW < end ? oldW / end : -1;
  const newBean = newW < end ? newW / end : -1;
  const earScale = Math.min(d, w - h) / Math.max(w - h, 1);
  const mirror = (b: Box) => next ? b : new Box(2 * rest.cx() - b.x - b.w, b.y, b.w, b.h);
  return {old: mirror(o), oldBean, new: mirror(n), newBean,
    reach: oldBean < 0 && newBean < 0 ? 1 : 0, bracket: earScale, oldBracket: 1 - earScale};
}

export const oldContent = (u: number): [number, number] =>
  [1 - smooth(u / 0.3), 1 - smooth((u - 0.35) / 0.25)];
export const newContent = (u: number, round: number): [number, number] =>
  [smooth((u - 0.7) / 0.3), smooth((u - round) / 0.25)];

export function capsulePath(b: Box) {
  const r = Math.min(b.w, b.h) / 2;
  return `M ${b.x + r} ${b.y} H ${b.x + b.w - r} A ${r} ${r} 0 0 1 ${b.x + b.w} ${b.y + r}
    V ${b.y + b.h - r} A ${r} ${r} 0 0 1 ${b.x + b.w - r} ${b.y + b.h}
    H ${b.x + r} A ${r} ${r} 0 0 1 ${b.x} ${b.y + b.h - r}
    V ${b.y + r} A ${r} ${r} 0 0 1 ${b.x + r} ${b.y} Z`;
}

export function earBox(rest: Box, right: boolean, out: number, stroke: number) {
  const ear = earWidth(stroke), h = rest.h * START_HEIGHT;
  return new Box(right ? rest.x + rest.w + out - ear : rest.x - out,
    rest.cy() - h / 2, ear, h);
}

export function earArc(s: number, h: number, stroke: number): [number, number] {
  const ear = earWidth(stroke);
  const turn = clamp(2 * ear / h, 0.05, 0.9);
  const p = clamp(s);
  if (p < turn) {
    const t = gain(smoothstepF(0, turn, p), 0.4);
    return [ear / 5 + (ear / 2.15 - ear / 5) * t, EAR_HALF_ANGLE];
  }
  const t = gain(smoothstepF(turn, 1, p), 0.4);
  return [ear / 2.15 + (h / 2 - ear / 2.15) * t, EAR_HALF_ANGLE * (1 - t)];
}

type Point = [number, number];
function arc(cx: number, cy: number, r: number, start: number, sweep: number): Point[] {
  return Array.from({length: 65}, (_, i) => {
    const a = start + sweep * i / 64;
    return [cx + r * Math.cos(a), cy + r * Math.sin(a)];
  });
}
const polygonPath = (p: Point[]) => p.length ? `M ${p.map(v => v.join(' ')).join(' L ')} Z` : '';
const cross = (a: Point, b: Point, p: Point) => (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]);

// shortcut: ear boolean intersections use subpixel sampled arcs; use an exact path engine if enlarged far beyond 1140px.
function intersect(subject: Point[], clip: Point[]) {
  let result = subject;
  for (let i = 0; i < clip.length && result.length; i++) {
    const a = clip[i], b = clip[(i + 1) % clip.length];
    if (Math.hypot(b[0] - a[0], b[1] - a[1]) < 1e-9) continue;
    const input = result;
    result = [];
    let prev = input[input.length - 1];
    let dp = cross(a, b, prev);
    for (const p of input) {
      const d = cross(a, b, p);
      if ((d >= 0) !== (dp >= 0)) {
        const t = dp / (dp - d);
        result.push([prev[0] + (p[0] - prev[0]) * t, prev[1] + (p[1] - prev[1]) * t]);
      }
      if (d >= 0) result.push(p);
      prev = p;
      dp = d;
    }
  }
  return result;
}

function earPoints(box: Box, outerLeft: boolean, rest: Box, stroke: number): Point[] {
  const end = earEnd(rest);
  const radius = rest.h / 2;
  const [rb, ang] = earArc(box.w / end, end, stroke);
  const outer = outerLeft ? box.x : box.x + box.w;
  const dir = outerLeft ? -1 : 1;
  const cx = outer - dir * (radius + rb), cy = box.cy();
  const theta = outerLeft ? Math.PI : 0;
  const first = theta - ang, last = theta + ang;
  let band: Point[];
  if (ang * 180 / Math.PI < 0.5) {
    band = arc(cx + dir * radius, cy, rb, 0, 2 * Math.PI);
  } else {
    band = [
      ...arc(cx, cy, radius + rb, first, 2 * ang),
      ...arc(cx + radius * Math.cos(last), cy + radius * Math.sin(last), rb, last, Math.PI),
      ...arc(cx, cy, radius - rb, last, -2 * ang),
      ...arc(cx + radius * Math.cos(first), cy + radius * Math.sin(first), rb, first + Math.PI, Math.PI),
    ];
  }
  const r = Math.min(box.w, box.h) / 2;
  const clip = [
    ...arc(box.x + box.w - r, box.y + r, r, -Math.PI / 2, Math.PI / 2),
    ...arc(box.x + box.w - r, box.y + box.h - r, r, 0, Math.PI / 2),
    ...arc(box.x + r, box.y + box.h - r, r, Math.PI / 2, Math.PI / 2),
    ...arc(box.x + r, box.y + r, r, Math.PI, Math.PI / 2),
  ];
  return intersect(band, clip);
}

export function earGlass(box: Box, outerLeft: boolean, rest: Box, stroke: number) {
  if (box.w < 0.5 || box.h < 0.5) return '';
  if (box.w >= earEnd(rest)) return capsulePath(box);
  return polygonPath(earPoints(box, outerLeft, rest, stroke));
}

export function neighbourPath(rest: Box, right: boolean, grow: number, out: number, stroke: number) {
  const g = clamp(grow);
  if (g <= 0.01) return '';
  const box = earBox(rest, right, out, stroke);
  return polygonPath(earPoints(box, !right, rest, stroke).map(([x, y]) =>
    [box.cx() + (x - box.cx()) * g, box.cy() + (y - box.cy()) * g]));
}

function rbox(px: number, py: number, bx: number, by: number, rad: number) {
  const qx = Math.abs(px) - bx + rad, qy = Math.abs(py) - by + rad;
  return Math.min(Math.max(qx, qy), 0) + Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) - rad;
}

class Field {
  readonly cy: number;
  readonly xc: number;
  readonly shapeChange: number;
  readonly k: number;
  readonly band: number;
  private readonly h: number;
  private readonly blend: number;
  private readonly space: number;
  constructor(private readonly l: Box, private readonly r: Box) {
    this.cy = l.cy();
    const h = this.h = Math.min(l.h, r.h);
    const gap = r.x - (l.x + l.w);
    this.xc = (l.x + l.w + r.x) / 2;
    const narrow = l.w < r.w ? l : r;
    const blend = this.blend = h * BLEND_SHAPE * smooth((narrow.w - narrow.h) / (0.4 * narrow.h));
    const space = this.space = Math.max(gap, 0);
    this.shapeChange = blend <= 0.5 ? 0 :
      smoothstepF(2 * blend + 0.055 * h, 2 * blend, space) *
      smoothstepF(Math.min(l.w, r.w) / 2, -2 * blend, -gap);
    const diff = -gap + this.shapeChange * 2 * blend;
    this.k = h * BLEND_K * gain(smoothstepF(0, 1, diff), 0.3);
    this.band = 2 * blend * 2 + space * 0.5 + this.k;
  }
  private pull(x: number, y: number) {
    if (this.shapeChange <= 0) return 0;
    const rd = smoothstepF(this.blend * 2 + this.space * 0.5, 0, Math.abs(x - this.xc)) *
      smoothstepF(this.h / 2 * 1.2, 0, Math.abs(y));
    return this.blend * rd * this.shapeChange;
  }
  sdf(x: number, y: number) {
    const s = this.pull(x, y), l = this.l, r = this.r;
    const d1 = rbox(x - s - l.cx(), y, l.w / 2, l.h / 2, Math.min(l.w, l.h) / 2);
    const d2 = rbox(x + s - r.cx(), y, r.w / 2, r.h / 2, Math.min(r.w, r.h) / 2);
    if (this.k <= 0.001) return Math.min(d1, d2);
    const hh = clamp(0.5 + 0.5 * (d1 - d2) / this.k);
    return d1 + (d2 - d1) * hh - this.k * hh * (1 - hh);
  }
  halfHeight(x: number) {
    if (this.sdf(x, 0) > 0) return 0;
    let lo = 0, hi = this.h;
    for (let i = 0; i < 18; i++) {
      const m = (lo + hi) / 2;
      if (this.sdf(x, m) <= 0) lo = m; else hi = m;
    }
    return lo;
  }
}

// Clockwise subpaths with nonzero fill form the same union without dark overlap seams.
export function gooPath(a: Box, b: Box | null = null, reach = 0) {
  let path = capsulePath(a);
  if (b === null || b.w < 1 || b.h < 1) return path;
  path += capsulePath(b);
  if (reach <= 0) return path;
  const left = a.cx() <= b.cx() ? a : b, right = left === a ? b : a;
  const f = new Field(left, right);
  if (f.shapeChange <= 0 && f.k <= 0) return path;
  const x0 = f.xc - f.band, x1 = f.xc + f.band, n = 48;
  const xs = Array.from({length: n + 1}, (_, i) => x0 + (x1 - x0) * i / n);
  const hs = xs.map(x => f.halfHeight(x));
  let start = -1;
  for (let i = 0; i <= n; i++) {
    const inside = hs[i] > 0.5;
    if (inside && start < 0) start = i;
    if ((!inside || i === n) && start >= 0) {
      const end = inside ? i : i - 1;
      if (end > start) {
        const run: Point[] = [];
        for (let q = start; q <= end; q++) run.push([xs[q], f.cy - hs[q]]);
        for (let q = end; q >= start; q--) run.push([xs[q], f.cy + hs[q]]);
        path += polygonPath(run);
      }
      start = -1;
    }
  }
  return path;
}
