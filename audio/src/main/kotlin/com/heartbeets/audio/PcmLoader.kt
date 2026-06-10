package com.heartbeets.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.annotation.RawRes
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes an audio resource (WAV, OGG, MP3) into a raw PCM [ShortArray]
 * suitable for writing directly into an [android.media.AudioTrack].
 *
 * Output is always 16-bit mono at [SAMPLE_RATE] Hz. Stereo sources are
 * downmixed. Resampling is left to the platform codec.
 */
internal object PcmLoader {

    const val SAMPLE_RATE = 44_100

    /**
     * Loads and decodes a raw resource into PCM 16-bit samples.
     */
    fun load(context: Context, @RawRes resId: Int): ShortArray {
        val fd = context.resources.openRawResourceFd(resId)
            ?: throw IllegalArgumentException("Cannot open raw resource $resId")

        val extractor = MediaExtractor()
        extractor.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        fd.close()

        require(extractor.trackCount > 0) { "No tracks found in resource $resId" }
        extractor.selectTrack(0)

        val format = extractor.getTrackFormat(0)
        val mime = format.getString(MediaFormat.KEY_MIME)
            ?: throw IllegalStateException("No MIME type in track format")
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val outputChunks = mutableListOf<ShortArray>()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false

        while (true) {
            // Feed input
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inputIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(
                            inputIndex, 0, sampleSize,
                            extractor.sampleTime, 0
                        )
                        extractor.advance()
                    }
                }
            }

            // Drain output
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outputIndex >= 0) {
                if (bufferInfo.size > 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                    val chunk = extractSamples(outputBuffer, bufferInfo.size, channelCount)
                    outputChunks.add(chunk)
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                break
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        return merge(outputChunks)
    }

    private fun extractSamples(
        buffer: ByteBuffer,
        size: Int,
        channelCount: Int
    ): ShortArray {
        buffer.position(bufferInfo_offset(buffer))
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val shortBuf = buffer.asShortBuffer()
        val totalSamples = size / 2  // 16-bit = 2 bytes per sample
        val raw = ShortArray(totalSamples)
        shortBuf.get(raw)

        return if (channelCount == 1) {
            raw
        } else {
            // Downmix to mono: average channels
            val monoCount = totalSamples / channelCount
            ShortArray(monoCount) { i ->
                var sum = 0
                for (ch in 0 until channelCount) {
                    sum += raw[i * channelCount + ch].toInt()
                }
                (sum / channelCount).toShort()
            }
        }
    }

    private fun bufferInfo_offset(buffer: ByteBuffer): Int = buffer.position()

    private fun merge(chunks: List<ShortArray>): ShortArray {
        val totalSize = chunks.sumOf { it.size }
        val result = ShortArray(totalSize)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }
}
