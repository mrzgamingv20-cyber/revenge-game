package com.mrzgaming.revenge.dev

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Render cutscene frame-by-frame lalu encode H.264 MP4 (MediaCodec + MediaMuxer).
 * Hasil ke folder Download HP.
 */
object Mp4Exporter {
    data class Progress(val frame: Int, val total: Int, val message: String)

    fun export(
        ctx: Context,
        project: CutsceneProject,
        width: Int = 720,
        height: Int = 1280,
        fps: Int = 24,
        onProgress: ((Progress) -> Unit)? = null
    ): File {
        val w = width - width % 2
        val h = height - height % 2
        val total = project.shots.sumOf { (it.durationSec * fps).toInt().coerceAtLeast(1) }

        val charKey = DevConfig.getAssetKey(ctx, DevConfig.KEY_STORY_CHAR, "char_doodle")
        val charBmp = AssetCatalog.resolveBitmap(ctx, charKey, com.mrzgaming.revenge.R.drawable.char_doodle)

        val mime = "video/avc"
        val format = MediaFormat.createVideoFormat(mime, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val tmp = File(ctx.cacheDir, "cutscene_${System.currentTimeMillis()}.mp4")
        val muxer = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()
        var inputIndex = 0
        val frameDurationUs = 1_000_000L / fps
        var frameNo = 0

        fun drain(endOfStream: Boolean) {
            if (endOfStream) {
                try {
                    codec.signalEndOfInputStream()
                } catch (_: Exception) {
                }
            }
            while (true) {
                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) break else continue
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val encoded = codec.getOutputBuffer(outIndex) ?: continue
                        if (bufferInfo.size > 0 && muxerStarted) {
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(track, encoded, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        if (!endOfStream) break
                    }
                }
            }
        }

        try {
            for ((shotIndex, shot) in project.shots.withIndex()) {
                val n = (shot.durationSec * fps).toInt().coerceAtLeast(1)
                for (f in 0 until n) {
                    val t = f.toFloat() / n
                    val bmp = renderFrame(w, h, shot, charBmp, t, project.title, shotIndex)
                    val yuv = argbToNv12(bmp, w, h)
                    bmp.recycle()

                    var submitted = false
                    while (!submitted) {
                        val inIndex = codec.dequeueInputBuffer(50_000)
                        if (inIndex >= 0) {
                            val inBuf = codec.getInputBuffer(inIndex)!!
                            inBuf.clear()
                            inBuf.put(yuv)
                            codec.queueInputBuffer(inIndex, 0, yuv.size, inputIndex * frameDurationUs, 0)
                            inputIndex++
                            submitted = true
                        } else {
                            drain(false)
                        }
                    }
                    drain(false)
                    frameNo++
                    onProgress?.invoke(Progress(frameNo, total, "Encode ${frameNo}/$total"))
                }
            }
            // EOS via empty input buffer with flag (more compatible than signalEndOfInputStream alone)
            var eos = false
            while (!eos) {
                val inIndex = codec.dequeueInputBuffer(50_000)
                if (inIndex >= 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, inputIndex * frameDurationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    eos = true
                } else {
                    drain(false)
                }
            }
            drain(true)
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            codec.release()
            if (muxerStarted) {
                try {
                    muxer.stop()
                } catch (_: Exception) {
                }
            }
            muxer.release()
            if (!charBmp.isRecycled) charBmp.recycle()
        }

        return publishToDownloads(ctx, tmp, project.title)
    }

    private fun renderFrame(
        w: Int,
        h: Int,
        shot: CutsceneShot,
        charBmp: Bitmap,
        t: Float,
        projectTitle: String,
        shotIndex: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(shot.bgColor)

        val fade = when {
            t < 0.12f -> t / 0.12f
            t > 0.88f -> (1f - t) / 0.12f
            else -> 1f
        }.coerceIn(0f, 1f)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((180 * fade).toInt(), 224, 145, 184)
            textSize = w * 0.045f
            typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText(projectTitle.uppercase(), w * 0.08f, h * 0.08f, titlePaint)

        if (shot.showChar) {
            val size = w * 0.38f
            val bob = kotlin.math.sin(t * Math.PI * 2).toFloat() * (h * 0.01f)
            val left = (w - size) / 2f
            val top = h * 0.18f + bob
            val alphaPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (255 * fade).toInt()
            }
            c.drawBitmap(charBmp, null, RectF(left, top, left + size, top + size), alphaPaint)
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((255 * fade).toInt(), 240, 240, 240)
            textSize = w * 0.048f
        }
        drawMultiline(c, shot.text, w * 0.08f, h * 0.58f, w * 0.84f, textPaint)

        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((120 * fade).toInt(), 200, 200, 200)
            textSize = w * 0.032f
        }
        c.drawText("Shot ${shotIndex + 1}", w * 0.08f, h * 0.94f, footer)
        return bmp
    }

    private fun drawMultiline(c: Canvas, text: String, x: Float, y: Float, maxW: Float, paint: Paint) {
        val words = text.split(Regex("\\s+"))
        var line = ""
        var cy = y
        val lineH = paint.textSize * 1.35f
        for (word in words) {
            val trial = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(trial) > maxW && line.isNotEmpty()) {
                c.drawText(line, x, cy, paint)
                cy += lineH
                line = word
            } else {
                line = trial
            }
        }
        if (line.isNotEmpty()) c.drawText(line, x, cy, paint)
    }

    private fun argbToNv12(bmp: Bitmap, w: Int, h: Int): ByteArray {
        val argb = IntArray(w * h)
        bmp.getPixels(argb, 0, w, 0, 0, w, h)
        val ySize = w * h
        val out = ByteArray(ySize + ySize / 2)
        var yIndex = 0
        var uvIndex = ySize
        var index = 0
        for (j in 0 until h) {
            for (i in 0 until w) {
                val col = argb[index++]
                val r = (col shr 16) and 0xFF
                val g = (col shr 8) and 0xFF
                val b = col and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                out[yIndex++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    out[uvIndex++] = u.coerceIn(0, 255).toByte()
                    out[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return out
    }

    private fun publishToDownloads(ctx: Context, tmp: File, title: String): File {
        val safe = title.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "cutscene" }
        val name = "${safe}_${System.currentTimeMillis()}.mp4"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Gagal buat MediaStore entry")
            resolver.openOutputStream(uri)?.use { out ->
                tmp.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            tmp.delete()
            return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name)
        } else {
            @Suppress("DEPRECATION")
            val dest = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name)
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
            return dest
        }
    }
}
