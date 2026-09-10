package com.lirui.charchat.data.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import kotlin.concurrent.thread

/**
 * 极简 PCM WAV 录音器：16kHz / 单声道 / 16bit，直接落成 `.wav`。
 *
 * 为什么不用 MediaRecorder：多数云端 ASR（含小米 MiMo）只接受 wav/mp3，
 * 而 MediaRecorder 只能直接产出 AAC/m4a，需要额外解码转码。
 * AudioRecord 直接拿 PCM，自己补 44 字节 WAV 头即可，采样率也完全可控。
 *
 * 体积估算：16k mono 16bit ≈ 32KB/s → 1 分钟约 1.9MB，
 * Base64 后仍远低于 MiMo 的 10MB 单次上限。
 */
class WavRecorder(private val file: File) {

    private var record: AudioRecord? = null
    private var worker: Thread? = null

    @Volatile
    private var running = false

    /** 已写入的 PCM 字节数（不含 WAV 头）。 */
    @Volatile
    private var pcmBytes = 0L

    /**
     * 开始录音。
     * @return true 表示已成功启动；false 表示麦克风被占用或参数不被支持。
     */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) return false
        // 至少 ~200ms 的缓冲，避免读循环被写盘抖动拖出 underrun
        val bufSize = maxOf(minBuf, SAMPLE_RATE / 5 * 2 * 2)

        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                bufSize
            )
        } catch (e: Throwable) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { rec.release() }
            return false
        }

        file.parentFile?.mkdirs()
        record = rec
        pcmBytes = 0L
        running = true
        runCatching { rec.startRecording() }

        worker = thread(name = "wav-recorder") {
            val out = try {
                RandomAccessFile(file, "rw")
            } catch (e: Throwable) {
                running = false
                return@thread
            }
            try {
                out.setLength(0)
                out.write(ByteArray(HEADER_SIZE)) // 先占位，收尾时回填真实长度
                val buf = ByteArray(bufSize)
                while (running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) {
                        out.write(buf, 0, n)
                        pcmBytes += n
                    }
                }
            } catch (_: Throwable) {
                // 停止时的正常中断，忽略
            } finally {
                runCatching {
                    out.seek(0)
                    out.write(wavHeader(pcmBytes))
                }
                runCatching { out.close() }
            }
        }
        return true
    }

    /**
     * 停止录音并回填 WAV 头。
     * @return 实际录音时长（毫秒）；0 表示没有有效音频。
     */
    fun stop(): Long {
        val rec = record ?: return 0
        record = null
        running = false
        runCatching { rec.stop() }          // 让阻塞中的 read 立即返回
        runCatching { worker?.join(1500) }  // 等写盘线程收尾（含回填头）
        worker = null
        runCatching { rec.release() }
        val bytes = pcmBytes
        pcmBytes = 0L
        return bytes * 1000L / (SAMPLE_RATE * 2L)
    }

    /** 放弃本次录音：停止并删除文件。 */
    fun cancel() {
        stop()
        runCatching { file.delete() }
    }

    /** 标准 44 字节 RIFF/WAVE 头（小端）。 */
    private fun wavHeader(dataLen: Long): ByteArray {
        val header = ByteArray(HEADER_SIZE)
        var p = 0
        fun ascii(s: String) {
            for (c in s) header[p++] = c.code.toByte()
        }
        fun le32(v: Long) {
            for (i in 0 until 4) header[p++] = ((v shr (8 * i)) and 0xFF).toByte()
        }
        fun le16(v: Int) {
            for (i in 0 until 2) header[p++] = ((v shr (8 * i)) and 0xFF).toByte()
        }
        val byteRate = SAMPLE_RATE * 2 // 单声道 × 16bit

        ascii("RIFF"); le32(36 + dataLen); ascii("WAVE")
        ascii("fmt "); le32(16); le16(1); le16(1)
        le32(SAMPLE_RATE.toLong()); le32(byteRate.toLong()); le16(2); le16(16)
        ascii("data"); le32(dataLen)
        return header
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val HEADER_SIZE = 44
    }
}
