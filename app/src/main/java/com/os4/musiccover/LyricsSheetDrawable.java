// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// The lyrics button's icon, drawn for this project: the sheet, its lines and the cut round the
// note are this project's own. The note is Material Design Icons' "music_note" (Round),
// Copyright Google LLC, Apache-2.0. If you reuse this file, keep this notice and the AGPL-3.0
// terms (publish your source).
package com.os4.musiccover;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import androidx.core.graphics.PathParser;

/**
 * The lyrics button's icon: a sheet of text lines (this project's own drawing) with Material
 * Design's rounded music note at its lower right, the sheet cut back round the note.
 * Outlined while the lyrics are off, filled (the lines cut out of the sheet) while they are on.
 * Built on a 24-unit grid; both states share one frame, so switching does not move the icon.
 */
final class LyricsSheetDrawable extends Drawable {
    private static final float L = 2.8f, T = 1.8f, R = 17.8f, B = 21.2f, CORNER = 2.6f, RING = 1.3f;
    private static final float LINE_H = 1.5f;
    /** Each text line: left, right, centre y. */
    private static final float[][] LINES = {
            {6.25f, 14.35f, 6f}, {6.25f, 14.35f, 9.67f}, {6.25f, 12.6f, 13.33f}, {6.25f, 10.4f, 17f}};
    /**
     * Material Icons "music_note", Round style (Apache-2.0, Google), on its own 24-unit grid.
     */
    private static final String NOTE = "M12 5v8.55c-.94-.54-2.1-.75-3.33-.32-1.34.48-2.37 1.67-2.61 "
            + "3.07-.46 2.74 1.86 5.08 4.59 4.65 1.96-.31 3.35-2.11 3.35-4.1V7h2c1.1 0 2-.9 2-2s-.9-2-2-2h-2"
            + "c-1.1 0-2 .9-2 2z";
    private static final float NOTE_SCALE = 0.58f, NOTE_X = 10.1f, NOTE_Y = 9.02f;
    /** The clear band between the note and the sheet: wider round the outline's thin ring. */
    private static final float GAP = 1.0f, OUTLINE_GAP = 1.4f;
    /** Of the bounds' shorter side the icon's longer one takes. */
    private static final float FILL = 0.92f;

    private final Path outline = new Path();
    private final Path filled = new Path();
    private final RectF frame = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean isFilled;

    LyricsSheetDrawable() {
        paint.setColor(0xFFFFFFFF);
        Path note = PathParser.createPathFromPathData(NOTE);
        Matrix m = new Matrix();
        m.setScale(NOTE_SCALE, NOTE_SCALE);
        m.postTranslate(NOTE_X, NOTE_Y);
        note.transform(m);

        Path sheet = new Path();
        sheet.addRoundRect(L, T, R, B, CORNER, CORNER, Path.Direction.CW);
        Path inner = new Path();
        float r = CORNER - RING;
        inner.addRoundRect(L + RING, T + RING, R - RING, B - RING, r, r, Path.Direction.CW);
        Path lines = new Path();
        for (float[] l : LINES) {
            Path one = new Path();
            one.addRoundRect(l[0], l[2] - LINE_H / 2f, l[1], l[2] + LINE_H / 2f,
                    LINE_H / 2f, LINE_H / 2f, Path.Direction.CW);
            lines.op(one, Path.Op.UNION);
        }

        outline.set(sheet);
        outline.op(inner, Path.Op.DIFFERENCE);
        outline.op(lines, Path.Op.UNION);
        outline.op(cut(note, OUTLINE_GAP), Path.Op.DIFFERENCE);
        outline.op(note, Path.Op.UNION);

        filled.set(sheet);
        filled.op(lines, Path.Op.DIFFERENCE);
        filled.op(cut(note, GAP), Path.Op.DIFFERENCE);
        filled.op(note, Path.Op.UNION);

        RectF other = new RectF();
        outline.computeBounds(frame, true);
        filled.computeBounds(other, true);
        frame.union(other);
    }

    /** The note grown by [gap]. */
    private static Path cut(Path note, float gap) {
        Paint stroke = new Paint();
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f * gap);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        Path cut = new Path();
        stroke.getFillPath(note, cut);
        cut.op(note, Path.Op.UNION);
        return cut;
    }

    void setFilled(boolean value) {
        if (isFilled == value) return;
        isFilled = value;
        invalidateSelf();
    }

    @Override public void draw(Canvas canvas) {
        float side = Math.min(getBounds().width(), getBounds().height());
        if (side <= 0f || frame.isEmpty()) return;
        float k = side * FILL / Math.max(frame.width(), frame.height());
        int save = canvas.save();
        canvas.translate(getBounds().exactCenterX(), getBounds().exactCenterY());
        canvas.scale(k, k);
        canvas.translate(-frame.centerX(), -frame.centerY());
        canvas.drawPath(isFilled ? filled : outline, paint);
        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) {
        paint.setColorFilter(filter); invalidateSelf();
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
