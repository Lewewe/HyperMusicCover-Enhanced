// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// Its geometry, springs and timings are modelled on the
// behaviour and parameter values of ColorOS 16 (OPPO); no ColorOS code is included.

import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import ts from 'typescript';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';

const source = await readFile(new URL('./src/colorOsPill.ts', import.meta.url), 'utf8');
const {outputText} = ts.transpileModule(source, {compilerOptions: {
  target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext,
}});
const pill = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
const {Box, frame, gooPath, earGlass, earBox, neighbourPath, oldContent, newContent, roundAt} = pill;
const h = 336 * 0.42;
const rest = new Box(60 + h + 66, 336 * 0.56 - h / 2, 1140 - 2 * (60 + h + 66), h);
const out = h * 0.19, stroke = h * 0.05;
const near = (a, b) => assert.ok(Math.abs(a - b) < 1e-9, `${a} != ${b}`);
near(roundAt(rest, stroke), 0.16635827966416025);
assert.deepEqual(oldContent(0), [1, 1]);
assert.deepEqual(oldContent(1), [0, 0]);
assert.deepEqual(newContent(1, roundAt(rest, stroke)), [1, 1]);
assert.equal(neighbourPath(rest, true, 0, out, stroke), '');
for (let i = 0; i <= 200; i++) {
  const a = frame(rest, i / 200, true, 0, out, stroke);
  const b = frame(rest, i / 200, false, 0, out, stroke);
  for (const key of ['old', 'new']) {
    near(b[key].x, 2 * rest.cx() - a[key].x - a[key].w);
    near(a[key].cy(), rest.cy());
    assert.ok(a[key].w > 0 && a[key].h > 0);
  }
  for (const next of [true, false]) {
    const f = next ? a : b;
    const paths = [gooPath(f.old, f.new, f.reach),
      earGlass(f.old, next, rest, stroke), earGlass(f.new, !next, rest, stroke),
      neighbourPath(rest, next, f.bracket, out, stroke)];
    for (const [index, d] of paths.entries()) {
      if (index < 3) assert.ok(d.startsWith('M'), `Empty path ${index} at u=${i / 200}`);
      assert.ok(!/NaN|Infinity/.test(d), `Invalid path at u=${i / 200}`);
    }
  }
}
for (const right of [false, true]) {
  const coordinates = d => d.match(/-?\d+(?:\.\d+)?(?:e[+-]?\d+)?/g).map(Number);
  const ear = coordinates(earGlass(earBox(rest, right, out, stroke), !right, rest, stroke));
  const neighbour = coordinates(neighbourPath(rest, right, 1, out, stroke));
  assert.equal(ear.length, neighbour.length);
  ear.forEach((value, i) => near(value, neighbour[i]));
}
console.log('Geometry checks passed: endpoints, mirrors, ears and 201 transition samples.');

// Inspect React element trees without invoking a renderer or starting Chromium.
const require = createRequire(import.meta.url);
const modules = new Map();
async function loadSource(name) {
  if (modules.has(name)) return modules.get(name);
  const source = await readFile(new URL(`./src/${name}`, import.meta.url), 'utf8');
  let {outputText} = ts.transpileModule(source, {compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext, jsx: ts.JsxEmit.ReactJSX,
  }});
  const imports = [...outputText.matchAll(/from ['"]([^'"]+)['"]/g)];
  for (const [, specifier] of imports) {
    const url = specifier.startsWith('.') ?
      await loadSource(`${specifier.slice(2)}${['./IslandCards', './IslandDemo'].includes(specifier) ? '.tsx' : '.ts'}`) :
      pathToFileURL(require.resolve(specifier)).href;
    outputText = outputText.replaceAll(`"${specifier}"`, JSON.stringify(url))
      .replaceAll(`'${specifier}'`, JSON.stringify(url));
  }
  const url = `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`;
  modules.set(name, url);
  return url;
}
const {Root} = await import(await loadSource('Root.tsx'));
assert.deepEqual(Root().props.children.map(({props: {id, width, height, fps, durationInFrames}}) =>
  [id, width, height, fps, durationInFrames]), [
  ['IslandColorOS', 1140, 336, 60, 420], ['IslandHyperOS', 1140, 336, 60, 324],
]);
const {IslandScene} = await import(await loadSource('IslandDemo.tsx'));
const {motion} = await import(await loadSource('demoMotion.ts'));
assert.equal(motion(60, 60, 1), 0);
assert.equal(motion(132, 60, 1, 1.2), 1);
function inspect(element) {
  if (element == null || typeof element === 'boolean') return null;
  if (Array.isArray(element)) return element.map(inspect);
  if (typeof element !== 'object') return element;
  if (typeof element.type === 'function') return inspect(element.type(element.props));
  const {children, ...props} = element.props;
  for (const [key, value] of Object.entries(props)) {
    if (typeof value === 'number') assert.ok(Number.isFinite(value), `Invalid ${key}`);
    if (typeof value === 'number' && ['width', 'height', 'r', 'rx'].includes(key)) assert.ok(value >= 0, `Negative ${key}`);
    if (typeof value === 'string') assert.ok(!/NaN|Infinity/.test(value), `Invalid ${key}`);
  }
  return {type: typeof element.type === 'symbol' ? 'fragment' : element.type, props, children: inspect(children)};
}
for (const [colorOs, frames] of [[true, 420], [false, 324]]) {
  const scene = frame => inspect(IslandScene({colorOs, frame, fps: 60}));
  const opening = scene(0);
  assert.equal(opening.props.width, 1140);
  assert.equal(opening.props.height, 336);
  const nodes = tree => tree == null || typeof tree !== 'object' ? [] :
    Array.isArray(tree) ? tree.flatMap(nodes) : [tree, ...nodes(tree.children)];
  const circles = nodes(opening).filter(node => node.type === 'circle' && node.props.fill === 'url(#glass)');
  assert.equal(circles.length, 2);
  for (const circle of circles) {
    near(circle.props.cy, 336 * 0.56);
    near(circle.props.r, h / 2);
  }
  near(circles[0].props.cx - h / 2, 60);
  near(1140 - circles[1].props.cx - h / 2, 60);
  assert.ok(nodes(opening).some(node => node.type === 'rect' && node.props.rx === 60 && node.props.height === 336));
  assert.ok(nodes(scene(150)).some(node => node.type === 'text' && node.children === '3+'));
  assert.equal(opening.props.viewBox, '0 0 1140 336');
  assert.deepEqual(scene(0), scene(frames - 1), 'Loop endpoints differ');
  if (!colorOs) {
    const islands = frame => nodes(scene(frame)).filter(node =>
      ['hyper-big', 'hyper-small'].includes(node.props.id));
    const resting = islands(102);
    for (const start of [102, 186]) {
      for (let frame = start; frame <= start + 36; frame++) {
        islands(frame).forEach((island, i) => {
          assert.deepEqual(island.props, resting[i].props, 'Island shell moved during tap');
          assert.deepEqual(island.children[0], resting[i].children[0], 'Island shell resized during tap');
        });
      }
      const big = islands(start + 6)[0];
      const [outgoing, incoming] = big.children[1].children;
      assert.ok(outgoing.props.opacity > 0 && outgoing.props.opacity < 1);
      assert.ok(outgoing.props.transform.includes('scale('));
      assert.ok(parseFloat(outgoing.props.style.filter.slice(5)) > 0);
      assert.ok(parseFloat(incoming.props.style.filter.slice(5)) > 0);
      const finished = islands(start + 35)[0].children[1].children;
      assert.ok(finished[0].props.opacity < 0.01);
      assert.ok(finished[1].props.opacity > 0.99);
    }
  }
  for (let frame = 0; frame < frames; frame++) scene(frame);
}
console.log('Scene checks passed: 744 frame trees, finite geometry and identical loop endpoints; no rendering.');
