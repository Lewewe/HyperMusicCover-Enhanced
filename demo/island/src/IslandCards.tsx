// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)

import {Box} from './colorOsPill';

export const mix = (a: number, b: number, p: number) => a + (b - a) * p;
export const blendBox = (a: Box, b: Box, p: number) =>
  new Box(mix(a.x, b.x, p), mix(a.y, b.y, p), mix(a.w, b.w, p), mix(a.h, b.h, p));

export function Content({box: b, music, id, marquee = 0, badge = false}: {
  box: Box; music: boolean; id: string; marquee?: number; badge?: boolean;
}) {
  const pad = b.h * 0.18, icon = b.h - pad * 2;
  const tx = b.x + pad * 1.8 + icon, bar = b.h * 0.11;
  const end = b.x + b.w - pad - (music || badge ? icon : 0);
  const width = Math.max(0, end - tx);
  return <g>
    <defs><clipPath id={id}><rect x={tx} y={b.y} width={width} height={b.h} /></clipPath></defs>
    {music ? <rect x={b.x + pad} y={b.y + pad} width={icon} height={icon} rx={icon * 0.22} fill="url(#artwork)" /> :
      <circle cx={b.x + pad + icon / 2} cy={b.cy()} r={icon / 2} fill="#6fd39a" />}
    <g clipPath={`url(#${id})`} fill="white">
      <rect x={tx - marquee} y={b.cy() - bar * 1.6} width={width * (badge ? 1.9 : 0.75)} height={bar} rx={bar / 2} opacity={0.9} />
      <rect x={tx} y={b.cy() + bar * 0.6} width={width * 0.5} height={bar} rx={bar / 2} opacity={0.55} />
      {badge && <>
        <rect x={tx + width * 0.65 - marquee} y={b.cy() - bar * 1.6} width={12} height={bar} fill="#899aaa" />
        <rect x={tx + width * 1.25 - marquee} y={b.cy() - bar * 1.6} width={12} height={bar} fill="#899aaa" />
      </>}
    </g>
    {music && <g fill="white" opacity={0.9}>
      <rect x={b.x + b.w - pad - icon / 2 - 11} y={b.cy() - 14} width={7} height={28} rx={3} />
      <rect x={b.x + b.w - pad - icon / 2 + 4} y={b.cy() - 14} width={7} height={28} rx={3} />
    </g>}
    {badge && <text x={b.x + b.w - pad - icon / 2} y={b.cy() + 13} textAnchor="middle" fill="white" fontFamily="sans-serif" fontSize={39}>3+</text>}
  </g>;
}
