// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// Its geometry, springs and timings are modelled on the
// behaviour and parameter values of ColorOS 16 (OPPO); no ColorOS code is included.

import {useCurrentFrame, useVideoConfig} from 'remotion';
import {Box, capsulePath, clamp} from './colorOsPill';
import {motion} from './demoMotion';
import {Content} from './IslandCards';
import {LockScreenGuide, type Guide} from './LockScreenGuide';

const D = 336 * 0.42;
const CY = 336 * 0.56;
const SIDE = 60, GAP = 66;
const REST = new Box(SIDE + D + GAP, CY - D / 2, 1140 - 2 * (SIDE + D + GAP), D);
function Finger({frame, fps, start, duration = 0.7, x, y = CY + 12, dx = 0, dy = 0}: {
  frame: number; fps: number; start: number; duration?: number; x: number; y?: number;
  dx?: number; dy?: number;
}) {
  const t = frame / fps - start;
  if (t <= 0 || t >= duration) return null;
  const p = clamp(motion(frame, fps, start + 0.1, duration - 0.22, 0.99));
  const alpha = clamp(motion(frame, fps, start, 0.1, 0.99)) *
    (1 - clamp(motion(frame, fps, start + duration - 0.12, 0.12, 0.99)));
  return <circle cx={x + dx * p} cy={y + dy * p} r={22 + 2 * (1 - alpha)}
    fill="white" fillOpacity={0.26} stroke="white" strokeOpacity={0.35} strokeWidth={1.5} opacity={alpha} />;
}

function HyperOs({frame, fps}: {frame: number; fps: number}) {
  const t = frame / fps, s = (start: number, duration = 0.7) => motion(frame, fps, start, duration);
  const appear = clamp(s(0.8) - s(4, 0.7));
  const share = s(0.8) - s(4, 0.7);
  const big = new Box(REST.x, REST.y, REST.w - D * 1.18 * share, D);
  const small = new Box(REST.x + REST.w - D, REST.y, D, D);
  const back = t >= 3.1 && t < 3.7;
  const progress = t >= 3.7 ? 0 : clamp(s(back ? 3.1 : 1.7, 0.6));
  const contents = (box: Box, compact: boolean) => {
    const content = (notification: boolean, id: string) => compact ?
      (notification ? <rect x={box.cx() - D * 0.28} y={box.cy() - D * 0.28}
        width={D * 0.56} height={D * 0.56} rx={22} fill="url(#artwork)" /> :
        <circle cx={box.cx()} cy={box.cy()} r={D * 0.28} fill="#7fb2ff" />) :
      <Content box={box} music={!notification} id={id} badge={notification}
        marquee={notification && t < 3.7 ? Math.max(0, t - 1.7) * 120 : 0} />;
    return <>
      <g opacity={1 - progress} style={{filter: `blur(${12 * progress}px)`}}
        transform={`translate(${box.cx()} ${box.cy()}) scale(${1 - 0.8 * progress}) translate(${-box.cx()} ${-box.cy()})`}>
        {content(back, compact ? 'hyper-small-old' : 'hyper-big-old')}
      </g>
      <g opacity={progress} style={{filter: `blur(${12 * (1 - progress)}px)`}}>
        {content(!back, compact ? 'hyper-small-new' : 'hyper-big-new')}
      </g>
    </>;
  };
  return <>
    <g id="hyper-big">
      <path d={capsulePath(big)} fill="url(#glass)" />
      {contents(big, false)}
    </g>
    {appear > 0 && <g id="hyper-small" opacity={appear * (0.25 + 0.75 * appear)}
      transform={`translate(${small.cx()} ${small.cy()}) scale(${0.6 + 0.4 * appear}) translate(${-small.cx()} ${-small.cy()})`}>
      <path d={capsulePath(small)} fill="url(#glass)" />
      <g style={{filter: `blur(${24 * (1 - appear)}px)`}}>{contents(small, true)}</g>
    </g>}
    {[1.7, 3.1].map(start => <Finger key={start} frame={frame} fps={fps} start={start} duration={0.6} x={small.cx()} dy={-5} />)}
  </>;
}

function Disc({cx, camera = false, cy = CY, diameter = D}: {
  cx: number; camera?: boolean; cy?: number; diameter?: number;
}) {
  return <g>
    <circle cx={cx} cy={cy} r={diameter / 2} fill="url(#glass)" />
    <g transform={`translate(${cx - diameter / 4} ${cy - diameter / 4}) scale(${diameter / 48})`}
      fill="none" stroke="white" strokeOpacity={0.9} strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round">
      {camera ? <>
        <path d="M 5 6.5 H 7.3 L 9 4 H 15 L 16.7 6.5 H 19 Q 21 6.5 21 8.5 V 18 Q 21 20 19 20 H 5 Q 3 20 3 18 V 8.5 Q 3 6.5 5 6.5 Z" />
        <path d="M 16 12.5 A 4 4 0 1 1 8 12.5 A 4 4 0 1 1 16 12.5 M 17.7 9 H 18" />
      </> : <>
        <path d="M 7 3 H 17 V 7 L 14.5 10.5 V 20 Q 14.5 21 13.5 21 H 10.5 Q 9.5 21 9.5 20 V 10.5 L 7 7 Z M 7.5 6.5 H 16.5" />
        <path d="M 12 13 V 16" />
      </>}
    </g>
  </g>;
}

export function IslandScene({frame, fps}: {frame: number; fps: number}) {
  return <svg xmlns="http://www.w3.org/2000/svg" width={1140} height={336} viewBox="0 0 1140 336"
    role="img" aria-label="HyperOS lock-screen islands: notifications, gestures and music">
    <defs>
      <linearGradient id="wallpaper" x1="0" x2="0" y1="0" y2="1">
        <stop stopColor="#2e3f5c" /><stop offset="1" stopColor="#18222f" />
      </linearGradient>
      <linearGradient id="glass" x1="0" x2="0" y1={REST.y} y2={REST.y + REST.h} gradientUnits="userSpaceOnUse">
        <stop stopColor="white" stopOpacity={0.3} /><stop offset="1" stopColor="white" stopOpacity={0.15} />
      </linearGradient>
      <linearGradient id="artwork" x1="0" y1="0" x2="1" y2="1">
        <stop stopColor="#ffb86b" /><stop offset="1" stopColor="#e2557a" />
      </linearGradient>
      <clipPath id="strip"><rect width={1140} height={336} rx={60} /></clipPath>
      <filter id="glow" x="-60%" y="-150%" width="220%" height="400%" colorInterpolationFilters="sRGB">
        <feGaussianBlur stdDeviation={62} />
      </filter>
    </defs>
    <g clipPath="url(#strip)">
      <rect width={1140} height={336} fill="url(#wallpaper)" />
      <g filter="url(#glow)" opacity={0.16}>
        <ellipse cx={350} cy={190} rx={230} ry={150} fill="#8979d3" />
        <ellipse cx={860} cy={650} rx={220} ry={160} fill="#3c8992" />
      </g>
      <Disc cx={SIDE + D / 2} /><Disc cx={1140 - SIDE - D / 2} camera />
      <HyperOs frame={frame} fps={fps} />
    </g>
  </svg>;
}

export function IslandDemo({guide}: {guide?: Guide}) {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  return guide ? <LockScreenGuide guide={guide} frame={frame} /> : <IslandScene frame={frame} fps={fps} />;
}
