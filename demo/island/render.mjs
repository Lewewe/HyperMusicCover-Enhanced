// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// Its geometry, springs and timings are modelled on the
// behaviour and parameter values of ColorOS 16 (OPPO); no ColorOS code is included.

import {mkdir, stat} from 'node:fs/promises';
import {spawn} from 'node:child_process';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const project = path.dirname(fileURLToPath(import.meta.url));
process.chdir(project);
const out = path.join(project, 'out');
const temporary = path.join(out, '.tmp');
await mkdir(temporary, {recursive: true});
// Remotion and Chromium also keep temporary files inside this project.
process.env.TMP = process.env.TEMP = process.env.TMPDIR = temporary;
const {bundle} = await import('@remotion/bundler');
const {renderFrames, selectComposition} = await import('@remotion/renderer');

function runFfmpeg(args) {
  return new Promise((resolve, reject) => {
    const child = spawn('ffmpeg', args, {cwd: project, stdio: 'inherit', windowsHide: true});
    child.once('error', reject);
    child.once('exit', (code, signal) => code === 0 ? resolve() :
      reject(new Error(`ffmpeg failed (${signal ?? code})`)));
  });
}

const serveUrl = await bundle({entryPoint: path.join(project, 'src/index.ts'),
  outDir: path.join(out, 'bundle')});

for (const [id, file, frames] of [
  ['IslandColorOS', 'island_demo_coloros.webp', 420],
  ['IslandHyperOS', 'island_demo_hyperos.webp', 324],
]) {
  const composition = await selectComposition({serveUrl, id, inputProps: {}});
  if (composition.durationInFrames !== frames || composition.fps !== 60 ||
      composition.width !== 1140 || composition.height !== 336) {
    throw new Error(`Unexpected composition settings: ${id}`);
  }
  const outputDir = path.join(out, id);
  await mkdir(outputDir, {recursive: true});
  await renderFrames({serveUrl, composition, inputProps: {}, outputDir, imageFormat: 'png',
    // Both compositions have three-digit frame numbers; the literal zero makes four.
    imageSequencePattern: 'frame-0[frame].[ext]',
    onStart: () => console.log(`Rendering ${id} (${frames} frames)`),
    onFrameUpdate: () => {},
  });
  const output = path.join(out, file);
  await runFfmpeg(['-y', '-framerate', '60', '-i', path.join(outputDir, 'frame-%04d.png'),
    '-c:v', 'libwebp_anim', '-lossless', '0', '-quality', '80', '-loop', '0',
    '-pix_fmt', 'yuva420p', output]);
  const {size} = await stat(output);
  console.log(`${output}: ${size.toLocaleString()} bytes (${(size / 1024).toFixed(1)} KiB)`);
}
