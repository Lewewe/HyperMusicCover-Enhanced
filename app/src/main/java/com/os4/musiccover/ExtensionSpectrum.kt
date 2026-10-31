package com.os4.musiccover

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Parcel

/**
 * The visualizer's five levels for an extension (HyperImmersive's StandBy).
 *
 * The extension sends `op extspectrum` with `do start` and a Binder of its own under `sink`,
 * and repeats it while it wants levels; this takes a visual lease on the shared capture (the
 * same one the island and player bars use, Bluetooth delay included), runs it through
 * [IslandSpectrum] and pushes each frame to the sink as one oneway transaction - code
 * [TX_LEVELS]: five floats, then the bars' top and bottom colour as the Bar color setting
 * draws them (artwork gradient, white, dark gray or custom) and a flags int, bit 0 set while
 * Audio visualization is on. `do stop`, a dead sink or [EXPIRE_MS] without a repeat ends it.
 *
 * The lock screen's backdrop goes along too, as code [TX_BACKDROP]: a track key and a Bitmap,
 * composed by [CoverCompose.cardBackground] - the cropped cover blurred under its own colour
 * regions, the very backdrop the lock screen shows - at a landscape size, sent once per track.
 *
 * The current lyric goes along as code [TX_LYRICS] whenever [LockLyrics] settles a new set or
 * its look settings change, so the extension draws it the way the user set HMC-E's lyrics up:
 * size sp, weight, alignment, depth blur %, alive effects mode; then the line count and per
 * line the text, start and end ms, whether it is the duet's other singer, the word timing (three
 * int arrays, null for a line-timed file), and the romanisation and translation rows exactly as
 * the lock screen shows them (null when switched off or absent). An empty set means none.
 *
 * Only the extension signed like this module is served.
 */
object ExtensionSpectrum {
    const val PKG = "com.yzc26623.HyperImmersive"
    private const val TX_LEVELS = IBinder.FIRST_CALL_TRANSACTION
    private const val TX_BACKDROP = IBinder.FIRST_CALL_TRANSACTION + 1
    private const val TX_LYRICS = IBinder.FIRST_CALL_TRANSACTION + 2
    /** Landscape backdrop size; it is a blur of colour, so a quarter of the screen is plenty. */
    private const val BACKDROP_W = 664
    private const val BACKDROP_H = 305
    private const val EXPIRE_MS = 3_000L
    private const val TAG = "[MCProbe] ext spectrum: "

    private val main = Handler(Looper.getMainLooper())
    private val worker by lazy {
        Handler(HandlerThread("mc-ext-spectrum").apply { start() }.looper)
    }
    private val spectrum = IslandSpectrum()
    private val levels = FloatArray(5)
    @Volatile private var sink: IBinder? = null
    private var lease: AudioSpectrumCapture.Lease? = null
    private var verified: Boolean? = null
    private var frames = 0L
    private val expire = Runnable { stop("no repeat") }

    @JvmStatic
    fun handle(context: Context, intent: Intent) {
        if (verified == null) {
            verified = runCatching {
                context.packageManager.checkSignatures(BuildConfig.APPLICATION_ID, PKG) ==
                    PackageManager.SIGNATURE_MATCH
            }.getOrDefault(false)
        }
        if (verified != true) {
            Xp.log(TAG + "refused: $PKG is not signed like this module")
            return
        }
        when (intent.getStringExtra("do")) {
            "start" -> {
                val b = intent.extras?.getBinder("sink") ?: return
                main.post { begin(b) }
            }
            "stop" -> main.post { stop("asked") }
        }
    }

    /** Read on the main thread with each repeat (it walks the island's views), used per frame. */
    @Volatile private var top = 0xFFFFFFFF.toInt()
    @Volatile private var bottom = 0xFFFFFFFF.toInt()
    private val recolor = object : Runnable {
        override fun run() {
            runCatching { AudioWaveDrawable.currentColors() }.getOrNull()?.let {
                top = it.first; bottom = it.second
            }
            if (sink != null) {
                artFromSession()
                pushBackdrop()
                pushLyrics()
                main.postDelayed(this, 300L)
            }
        }
    }

    private fun begin(b: IBinder) {
        // A new sink (the extension restarted) has seen no backdrop yet.
        if (sink !== b) { sentKey = ""; sentLyrics = "" }
        if (sink == null) { sink = b; main.removeCallbacks(recolor); recolor.run() }
        main.removeCallbacks(expire)
        main.postDelayed(expire, EXPIRE_MS)
        sink = b
        // Audio visualization off in HMC-E: no capture for the extension either. Told once per
        // repeat, so it hides its bars; turned back on, the next repeat starts the capture.
        if (!MediaCardRuntime.visualizerOn()) {
            java.util.Arrays.fill(levels, 0f)
            push()
            if (lease != null) worker.post { lease?.release(); lease = null; spectrum.reset() }
            return
        }
        if (lease != null) return
        Xp.log(TAG + "start")
        worker.post {
            if (sink == null || lease != null) return@post
            runCatching {
                lease = AudioSpectrumCapture.acquire(true) { _, fft, rate, power ->
                    spectrum.sample(fft, rate, power)
                    spectrum.copyTo(levels)
                    push()
                }
            }.onFailure { Xp.w(TAG + "no capture: $it") }
        }
    }

    private fun push() {
        val s = sink ?: return
        val p = Parcel.obtain()
        try {
            val on = MediaCardRuntime.visualizerOn()
            for (v in levels) p.writeFloat(if (on) v else 0f)
            p.writeInt(top)
            p.writeInt(bottom)
            p.writeInt(if (on) 1 else 0)
            s.transact(TX_LEVELS, p, null, IBinder.FLAG_ONEWAY)
            frames++
        } catch (t: Throwable) {
            main.post { stop("sink gone: $t") }
        } finally {
            p.recycle()
        }
    }

    private fun stop(why: String) {
        main.removeCallbacks(expire)
        if (sink == null && lease == null) return
        sink = null
        Xp.log(TAG + "stop ($why) after $frames frames")
        frames = 0
        worker.post {
            lease?.release()
            lease = null
            spectrum.reset()
        }
    }

    @Volatile private var art: android.graphics.Bitmap? = null
    @Volatile private var artKey = ""
    private var sentKey = ""

    /** Called from the cover push with each track's source artwork. */
    @JvmStatic
    fun noteArtwork(source: android.graphics.Bitmap, key: String?) {
        if (key == null || key == artKey) return
        val copy = runCatching {
            val k = 256f / maxOf(source.width, source.height)
            android.graphics.Bitmap.createScaledBitmap(source,
                maxOf(1, (source.width * k).toInt()), maxOf(1, (source.height * k).toInt()), true)
        }.getOrNull() ?: return
        art = copy
        artKey = key
        artTitle = sessionTitle()
    }

    /** Which track [art] belongs to, by title - the push's key and the session's differ. */
    @Volatile private var artTitle: String? = null

    private fun sessionTitle(): String? = runCatching {
        Main.miniPlayerSession()?.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
    }.getOrNull()

    /**
     * No cover push since SystemUI started (StandBy hides the lock screen, so none comes):
     * the session's own picture stands in until one does.
     */
    private fun artFromSession() {
        val c = runCatching { Main.miniPlayerSession() }.getOrNull() ?: return
        val md = c.metadata ?: return
        val title = md.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: return
        if (title == artTitle) return
        val bmp = md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART) ?: return
        noteArtwork(bmp, "session|" + c.packageName + "|" + title)
    }

    private fun pushBackdrop() {
        val s = sink ?: return
        val src = art ?: return
        val key = artKey
        if (key == sentKey) return
        sentKey = key
        worker.post {
            val bmp = runCatching { CoverCompose.cardBackground(src, BACKDROP_W, BACKDROP_H) }
                .getOrNull() ?: return@post
            val p = Parcel.obtain()
            try {
                p.writeString(key)
                bmp.writeToParcel(p, 0)
                s.transact(TX_BACKDROP, p, null, IBinder.FLAG_ONEWAY)
            } catch (t: Throwable) {
                Xp.w(TAG + "backdrop not sent: $t")
                sentKey = ""
            } finally {
                p.recycle()
                bmp.recycle()
            }
        }
    }

    private var sentLyrics = ""

    private fun pushLyrics() {
        val s = sink ?: return
        val version = LockLyrics.version()
        val style = LockLyrics.sStyle
        val below = LockLyrics.below()
        val look = "$version|${style.sizeSp}|${style.weight}|${LockLyrics.sAlign}|" +
            "${LockLyrics.sDepthBlur}|${LockLyrics.sAliveFx}|$below|${LockLyrics.sOnlineTranslateMode}"
        if (look == sentLyrics) return
        sentLyrics = look
        val lines = LockLyrics.lines()
        val p = Parcel.obtain()
        try {
            p.writeInt(version)
            p.writeFloat(style.sizeSp)
            p.writeInt(style.weight)
            p.writeInt(LockLyrics.sAlign)
            p.writeInt(LockLyrics.sDepthBlur)
            p.writeInt(LockLyrics.sAliveFx)
            p.writeInt(lines.size)
            for (l in lines) {
                p.writeString(l.text)
                p.writeInt(l.start)
                p.writeInt(l.end)
                p.writeInt(if (l.opposite) 1 else 0)
                p.writeIntArray(l.sylStart)
                p.writeIntArray(l.sylEnd)
                p.writeIntArray(l.charEnd)
                p.writeString(if (below and LockLyrics.BELOW_ROMA != 0) l.renderedRoma() else null)
                p.writeString(if (below and LockLyrics.BELOW_TRANS != 0) LyricView.translationFor(l) else null)
            }
            s.transact(TX_LYRICS, p, null, IBinder.FLAG_ONEWAY)
        } catch (t: Throwable) {
            Xp.w(TAG + "lyrics not sent: $t")
            sentLyrics = ""
        } finally {
            p.recycle()
        }
    }

    @JvmStatic
    fun describe(): String = "extSpectrum sink=${sink != null} lease=${lease != null} frames=$frames"
}
