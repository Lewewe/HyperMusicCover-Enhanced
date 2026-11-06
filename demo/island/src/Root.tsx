// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// Its geometry, springs and timings are modelled on the
// behaviour and parameter values of ColorOS 16 (OPPO); no ColorOS code is included.

import {Composition} from 'remotion';
import {IslandDemo} from './IslandDemo';

export const Root = () => <>
  <Composition id="IslandColorOS" component={IslandDemo} width={1140} height={336}
    fps={60} durationInFrames={420} defaultProps={{colorOs: true}} />
  <Composition id="IslandHyperOS" component={IslandDemo} width={1140} height={336}
    fps={60} durationInFrames={324} defaultProps={{colorOs: false}} />
</>;
