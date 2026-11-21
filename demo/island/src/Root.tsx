// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// Its geometry, springs and timings are modelled on the
// behaviour and parameter values of ColorOS 16 (OPPO); no ColorOS code is included.

import {Composition} from 'remotion';
import {IslandDemo} from './IslandDemo';

export const Root = () => <>
  <Composition id="IslandColorOS" component={IslandDemo} width={600} height={650}
    fps={60} durationInFrames={358} defaultProps={{guide: 'capsule'}} />
  <Composition id="IslandHyperOS" component={IslandDemo} width={1140} height={336}
    fps={60} durationInFrames={324} defaultProps={{}} />
  <Composition id="IslandColorOSList" component={IslandDemo} width={600} height={650}
    fps={60} durationInFrames={171} defaultProps={{guide: 'list'}} />
  <Composition id="IslandColorOSStack" component={IslandDemo} width={600} height={650}
    fps={60} durationInFrames={265} defaultProps={{guide: 'stack'}} />
</>;
