// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// Its geometry, springs and timings are modelled on the
// behaviour and parameter values of ColorOS 16 (OPPO); no ColorOS code is included.

import {spring} from 'remotion';

// Android uses damping ratio and response; convert those into Remotion's mass-one spring.
export function motion(frame: number, fps: number, start: number, duration = 0.7,
  damping = 0.75, response = 0.45) {
  if (frame <= start * fps) return 0;
  if (frame >= (start + duration) * fps) return 1;
  const frequency = 2 * Math.PI / response;
  return spring({frame: frame - start * fps, fps, durationInFrames: duration * fps,
    config: {mass: 1, stiffness: frequency ** 2, damping: 2 * damping * frequency}});
}

