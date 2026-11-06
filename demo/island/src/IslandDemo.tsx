// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// Its geometry, springs and timings are modelled on the
// behaviour and parameter values of ColorOS 16 (OPPO); no ColorOS code is included.

import {Easing, useCurrentFrame, useVideoConfig} from 'remotion';
import {Box, capsulePath, clamp, earGlass, frame as pillFrame, gooPath,
  neighbourPath, newContent, oldContent, roundAt} from './colorOsPill';
import {motion} from './demoMotion';
import {Content} from './IslandCards';

const D = 336 * 0.42;
const CY = 336 * 0.56;
const SIDE = 60, GAP = 66;
const REST = new Box(SIDE + D + GAP, CY - D / 2, 1140 - 2 * (SIDE + D + GAP), D);
const OUT = REST.h * 0.19, STROKE = REST.h * 0.05;
const SWIPE = Easing.bezier(0.3, 0, 0.15, 1);

function Finger({frame, fps, start, duration = 0.7, x, y = CY + 12, dx = 0, dy = 0, goo = false}: {
  frame: number; fps: number; start: number; duration?: number; x: number; y?: number;
  dx?: number; dy?: number; goo?: boolean;
}) {
  const t = frame / fps - start;
  if (t <= 0 || t >= duration) return null;
  const travel = clamp((t - 0.1) / (duration - 0.22));
  const p = goo ? SWIPE(travel) : clamp(motion(frame, fps, start + 0.1, duration - 0.22, 0.99));
  const alpha = clamp(motion(frame, fps, start, 0.1, 0.99)) *
    (1 - clamp(motion(frame, fps, start + duration - 0.12, 0.12, 0.99)));
  return <circle cx={x + dx * p} cy={y + dy * p} r={22 + 2 * (1 - alpha)}
    fill="white" fillOpacity={0.26} stroke="white" strokeOpacity={0.35} strokeWidth={1.5} opacity={alpha} />;
}

function Brackets({box = REST, alpha = 1}: {box?: Box; alpha?: number}) {
  return <path opacity={clamp(alpha)} d={neighbourPath(box, false, 1, OUT, STROKE) +
    neighbourPath(box, true, 1, OUT, STROKE)} fill="url(#glass)" />;
}

function Capsule({box, music, id, time, alpha = 1, scale = 1, blur = 0}: {
  box: Box; music: boolean; id: string; time: number; alpha?: number; scale?: number; blur?: number;
}) {
  return <g opacity={clamp(alpha)} transform={`translate(${box.cx()} ${box.cy()}) scale(${scale}) translate(${-box.cx()} ${-box.cy()})`}
    style={{filter: `blur(${Math.max(0, blur)}px)`}}>
    <path d={capsulePath(box)} fill="url(#glass)" />
    <Content box={box} music={music} id={id} badge={!music} marquee={music ? 0 : (time % 3) * 90} />
  </g>;
}

function Goo({u, right, music, time}: {u: number; right: boolean; music: boolean; time: number}) {
  // frame() mirrors its left-going geometry; right swipes use that mirror.
  const next = !right, f = pillFrame(REST, u, next, 0, OUT, STROKE);
  const a = f.oldBean < 0, b = f.newBean < 0;
  let path = a && b ? gooPath(f.new, f.old, f.reach) :
    (a ? gooPath(f.old) : earGlass(f.old, next, REST, STROKE)) +
    (b ? gooPath(f.new) : earGlass(f.new, !next, REST, STROKE));
  path += neighbourPath(REST, next, f.bracket, OUT, STROKE) +
    neighbourPath(REST, !next, f.oldBracket, OUT, STROKE);
  const content = (box: Box, isMusic: boolean, alpha: number, id: string) => {
    const k = clamp(box.w / REST.w, 0.2, 1);
    return <g opacity={alpha}>
      <defs><clipPath id={`${id}-goo`}><path d={capsulePath(box)} /></clipPath></defs>
      <g clipPath={`url(#${id}-goo)`}>
        <g transform={`translate(${box.x} ${box.y}) scale(${k} ${box.h / REST.h})`}>
          <Content box={new Box(0, 0, REST.w, REST.h)} music={isMusic} id={id}
            badge={!isMusic} marquee={isMusic ? 0 : (time % 3) * 90} />
        </g>
      </g>
    </g>;
  };
  return <>
    <path d={path} fill="url(#glass)" fillRule="nonzero" />
    {a && content(f.old, music, oldContent(u)[1], 'goo-old')}
    {b && content(f.new, !music, newContent(u, roundAt(REST, STROKE))[1], 'goo-new')}
  </>;
}

function ColorOs({frame, fps}: {frame: number; fps: number}) {
  const t = frame / fps, s = (start: number, duration = 0.7, damping = 0.75, response = 0.45) =>
    motion(frame, fps, start, duration, damping, response);
  const swipe = t >= 3 && t < 3.7 ? {start: 3, music: false, right: true} :
    t >= 4.3 && t < 5 ? {start: 4.3, music: true, right: false} :
    t >= 5.6 && t < 6.3 ? {start: 5.6, music: false, right: true} : null;
  const music = t < 1 || (t >= 3.7 && t < 4.3) || t >= 6.3;
  const update = t >= 1 && t < 2.2;
  const widthSpring = s(1, 1.2, 0.6, 0.9);
  // Opposing spring impulses dip the width before its overshooting recovery.
  const dip = 120 * (s(1, 0.6, 0.6, 0.9) - s(1.16, 1.04, 0.6, 0.9));
  const updated = new Box(REST.x + dip / 2, REST.y, REST.w - dip, REST.h);
  return <>
    {swipe ? <Goo u={SWIPE(clamp((t - swipe.start) / 0.7))} right={swipe.right} music={swipe.music} time={t} /> :
      update ? <>
        <Brackets box={updated} /><path d={capsulePath(updated)} fill="url(#glass)" />
        <g opacity={1 - clamp(s(1, 0.45, 0.99, 0.3))} style={{filter: `blur(${36 * clamp(widthSpring)}px)`}}
          transform={`translate(570 ${CY}) scale(${1 - 0.8 * clamp(s(1, 0.3, 0.99, 0.15))}) translate(-570 ${-CY})`}>
          <Content box={updated} music id="update-old" />
        </g>
        <g opacity={clamp(s(1.16, 0.6, 0.99))} style={{filter: `blur(${36 * (1 - clamp(s(1.16, 0.7, 0.99, 0.5)))}px)`}}>
          <Content box={updated} music={false} id="update-new" badge marquee={(t - 1) * 90} />
        </g>
      </> : <>
        <Brackets /><Capsule box={REST} music={music} id="color-rest" time={t} />
      </>}
    {[{start: 3, right: true}, {start: 4.3, right: false}, {start: 5.6, right: true}].map(({start, right}) =>
      <Finger key={start} frame={frame} fps={fps} start={start} x={REST.cx() + (right ? -130 : 130)} dx={right ? 260 : -260} goo />)}
  </>;
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

function Disc({cx, camera = false}: {cx: number; camera?: boolean}) {
  return <g>
    <circle cx={cx} cy={CY} r={D / 2} fill="url(#glass)" />
    <g transform={`translate(${cx - D / 4} ${CY - D / 4}) scale(${D / 48})`}
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

export function IslandScene({colorOs, frame, fps}: {colorOs: boolean; frame: number; fps: number}) {
  return <svg xmlns="http://www.w3.org/2000/svg" width={1140} height={336} viewBox="0 0 1140 336"
    role="img" aria-label={`${colorOs ? 'ColorOS' : 'HyperOS'} lock-screen islands: notifications, gestures and music`}>
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
      {colorOs ? <ColorOs frame={frame} fps={fps} /> : <HyperOs frame={frame} fps={fps} />}
    </g>
  </svg>;
}

export function IslandDemo({colorOs}: {colorOs: boolean}) {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  return <IslandScene colorOs={colorOs} frame={frame} fps={fps} />;
}
