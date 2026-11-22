// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)

import {Easing} from 'remotion';
import {Box, capsulePath, clamp, frame as pillFrame, gooPath, earGlass,
  neighbourPath, newContent, oldContent, roundAt} from './colorOsPill';
import {mix} from './IslandCards';

export type Guide = 'capsule' | 'list' | 'stack';
const ease = Easing.bezier(0.29, 0, 0.17, 1);
const progress = (frame: number, start: number, end: number) => ease(clamp((frame - start) / (end - start)));
const glide = (t: number) => t * t * (3 - 2 * t);
const SCREEN = new Box(160, 12, 280, 626);
const CAPSULE = new Box(241, 575, 118, 34);
const STACK = new Box(187, 494, 226, 49);
const LIST = new Box(183, 200, 234, 49);

function Touch({frame, start, end, from, to}: {
  frame: number; start: number; end: number; from: [number, number]; to: [number, number];
}) {
  if (frame < start || frame > end + 8) return null;
  const p = progress(frame, start, end);
  const back = progress(frame - 6, start, end);
  const alpha = progress(frame, start, start + 4) * (1 - progress(frame, end, end + 8));
  const x = mix(from[0], to[0], p), y = mix(from[1], to[1], p);
  return <g opacity={alpha}>
    <path d={`M${mix(from[0], to[0], back)} ${mix(from[1], to[1], back)} L${x} ${y}`}
      stroke="#eeadcc" strokeWidth={19} strokeOpacity={0.52} strokeLinecap="round" />
    <circle cx={x} cy={y} r={9} fill="#fff5fa" stroke="#cd80a8" strokeWidth={4} />
  </g>;
}

function Notifications({frame, stacked}: {frame: number; stacked: boolean}) {
  const glideBetween = (start: number, end: number) => glide(clamp((frame - start) / (end - start)));
  const list = 1 - glideBetween(30, 62) + glideBetween(210, 243);
  const pile = glideBetween(30, 62) - glideBetween(85, 113) +
    glideBetween(150, 177) - glideBetween(210, 243);
  const rebound = (arrival: number) =>
    glideBetween(arrival - 6, arrival) - glideBetween(arrival, arrival + 12);
  const stackRebound = 6 * rebound(62) + 7 * rebound(113) -
    5 * rebound(177) - 8 * rebound(243);
  return <>
    {[2, 1, 0].map(index => {
      const listY = LIST.cy() + 59 * index;
      const stackY = STACK.cy() + 13 * index;
      const capsuleWidth = CAPSULE.w - 9 * index;
      const capsuleHeight = CAPSULE.h - 4 * index;
      const rise = glideBetween(95 + 1.5 * index, 125 + index);
      const drop = glideBetween(28, 54 - index * 5);
      const sizeInList = 1 - glideBetween(34 - index, 56 - 3 * index) +
        glideBetween(95 + 3 * index, 119 + index);
      const visible = stacked ? list + pile : sizeInList;
      const fill = `rgb(${Math.round(mix(194, 23, visible))},${Math.round(mix(226, 48, visible))},${Math.round(mix(230, 69, visible))})`;
      const width = mix(capsuleWidth, LIST.w, visible) -
        (stacked ? (LIST.w - STACK.w + 15 * index) * pile : 0);
      const height = mix(capsuleHeight, LIST.h, visible) - (stacked ? 2 * index * pile : 0);
      const hiddenY = CAPSULE.cy() - [0, 37, 91][index];
      const centerY = stacked
        ? CAPSULE.cy() + (stackY - CAPSULE.cy()) * pile + (listY - CAPSULE.cy()) * list + stackRebound
        : mix(mix(listY, hiddenY, drop), listY, rise) +
          (index === 0 ? 8 * rebound(54) : 0) - 8 * rebound(125 + index);
      const opacity = index === 0 ? 1 : stacked
        ? glide(clamp(visible / 0.45)) * mix(0.73 - index * 0.17, 1, list)
        : 1 - glideBetween(45 - 6 * index, 57 - 7 * index) +
          glideBetween(95 + 2 * index, 104 + 3 * index);
      return <rect key={index} id={`notification-${index}`}
        x={300 - width / 2} y={centerY - height / 2} width={width} height={height}
        rx={mix(capsuleHeight / 2, 14, visible)} fill={index === 0 ? fill : '#173045'}
        opacity={opacity} />;
    })}
    {stacked ? <>
      <Touch frame={frame} start={27} end={47} from={[300, 256]} to={[300, 412]} />
      <Touch frame={frame} start={82} end={104} from={[300, 454]} to={[300, 552]} />
      <Touch frame={frame} start={147} end={167} from={[300, 554]} to={[300, 499]} />
      <Touch frame={frame} start={207} end={237} from={[300, 491]} to={[300, 306]} />
    </> : <>
      <Touch frame={frame} start={25} end={53} from={[300, 266]} to={[300, 524]} />
      <Touch frame={frame} start={92} end={120} from={[300, 573]} to={[300, 333]} />
    </>}
  </>;
}

function ShortcutFace({box, ride, alpha}: {box: Box; ride: boolean; alpha: number}) {
  const r = Math.min(box.h * 0.38, box.w * 0.32), x = box.x + r + box.h * 0.12;
  return <g opacity={alpha}>
    <circle cx={x} cy={box.cy()} r={r} fill={ride ? '#655bc5' : '#96607f'} />
    <path d={ride ? 'M-6 2v-4l2-3h8l2 3v4zM-5 2v2m10-2v2M-6-2h12' :
      'M-6-5h12v10h-12zM-2-5v4h4v-4'}
      transform={`translate(${x} ${box.cy()}) scale(${r / 10})`}
      fill="none" stroke="white" strokeWidth="1.1" strokeLinecap="round" strokeLinejoin="round"/>
    <rect x={x + 1.5 * r} y={box.cy() - 0.25 * r} width={Math.max(0, box.w * .3)} height={r * .5}
      rx={r * .25} fill="#746d95" fillOpacity={0.6} />
  </g>;
}

function Carousel({frame}: {frame: number}) {
  const left = frame < 175;
  const p = left ? progress(frame, 82, 116) : progress(frame, 186, 208);
  const out = 5, stroke = 2;
  const part = pillFrame(CAPSULE, p, left, 0, out, stroke);
  const settled = p === 0 || p === 1;
  const leaving = part.oldBean < 0, arriving = part.newBean < 0;
  const glass = leaving && arriving ? gooPath(part.new, part.old, part.reach) :
    (leaving ? gooPath(part.old) : earGlass(part.old, left, CAPSULE, stroke)) +
    (arriving ? gooPath(part.new) : earGlass(part.new, !left, CAPSULE, stroke));
  const impact = left ? 108 : 200;
  const squeeze = progress(frame, impact - 14, impact) - progress(frame, impact, impact + 15);
  const stretch = 1 - 0.075 * squeeze;
  return <>
    <g transform={`translate(300 592) scale(${stretch} ${1 - (stretch - 1) * 0.22}) translate(-300 -592)`}>
      {settled ? <>
      <path d={capsulePath(CAPSULE) + neighbourPath(CAPSULE, true, 1, out, stroke) +
        neighbourPath(CAPSULE, false, 1, out, stroke)} fill="#ece5f6" />
      <ShortcutFace box={CAPSULE} ride={left === (p === 0)} alpha={1} />
    </> : <>
      <path d={glass + neighbourPath(CAPSULE, left, part.bracket, out, stroke) +
        neighbourPath(CAPSULE, !left, part.oldBracket, out, stroke)} fill="#ece5f6" />
      {leaving && <ShortcutFace box={part.old} ride={left} alpha={oldContent(p)[1]} />}
      {arriving && <ShortcutFace box={part.new} ride={!left}
        alpha={newContent(p, roundAt(CAPSULE, stroke))[1]} />}
      </>}
    </g>
    <Touch frame={frame} start={81} end={113} from={[343, 592]} to={[274, 592]} />
    <Touch frame={frame} start={175} end={207} from={[283, 592]} to={[341, 592]} />
  </>;
}

export function LockScreenGuide({guide, frame}: {guide: Guide; frame: number}) {
  const zoom = guide === 'capsule' ? progress(frame, 26, 61) - progress(frame, 280, 319) : 0;
  return <svg xmlns="http://www.w3.org/2000/svg" width={600} height={650} viewBox="0 0 600 650"
    role="img" aria-label={guide}>
    <defs>
      <linearGradient id="guide-wallpaper" x1="0" y1="0" x2="0.85" y2="1">
        <stop stopColor="#567ca7" /><stop offset="0.46" stopColor="#4e88a9" /><stop offset="1" stopColor="#438e8e" />
      </linearGradient>
      <linearGradient id="guide-highlight" x2="1" y2="1">
        <stop stopColor="#d1f5ff" stopOpacity={0.16} /><stop offset="1" stopColor="#e1fff0" stopOpacity={0} />
      </linearGradient>
      <clipPath id="guide-screen">
        <rect x={SCREEN.x} y={SCREEN.y} width={SCREEN.w} height={SCREEN.h} rx={40} />
      </clipPath>
    </defs>
    <g transform={`translate(300 640) scale(${1 + 0.94 * zoom}) translate(-300 -640)`}>
      <rect x={SCREEN.x - 4} y={SCREEN.y - 4} width={SCREEN.w + 8} height={SCREEN.h + 8}
        rx={44} fill="#51606f" stroke="#93a5b1" strokeOpacity={0.65} strokeWidth={1.5} />
      <g clipPath="url(#guide-screen)">
        <rect x={SCREEN.x} y={SCREEN.y} width={SCREEN.w} height={SCREEN.h} fill="url(#guide-wallpaper)" />
        <path d="M160 478C235 393 309 427 440 306V638H160Z" fill="url(#guide-highlight)" />
        <g fill="#f3f5ff" fontFamily="Arial, sans-serif" textAnchor="middle">
          <text x={300} y={85} fontSize={15} opacity={0.92}>Nov 21, Sat</text>
          <g fontSize={88} fontWeight={700} letterSpacing={-2} opacity={0.92} aria-label="23:52">
            <text x={289} y={157} textAnchor="end" textLength={103} lengthAdjust="spacingAndGlyphs">23</text>
            <circle cx={300} cy={113} r={6.3} /><circle cx={300} cy={140} r={6.3} />
            <text x={311} y={157} textAnchor="start" textLength={103} lengthAdjust="spacingAndGlyphs">52</text>
          </g>
        </g>
        <g fill="#d9f3f5" fillOpacity={0.58}>
          <circle cx={210} cy={592} r={17} /><circle cx={390} cy={592} r={17} />
        </g>
        <g fill="none" stroke="#66627f" strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round">
          <path d="M205 584h10v4l-3 3v8h-4v-8l-3-3zM205 588h10" />
          <path d="M382 588h4l2-2h4l2 2h4v10h-16zM393 593a3 3 0 1 1-6 0a3 3 0 1 1 6 0z" />
        </g>
        {guide === 'capsule' ? <Carousel frame={frame} /> : <Notifications frame={frame} stacked={guide === 'stack'} />}
      </g>
    </g>
  </svg>;
}
