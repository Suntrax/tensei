package com.blissless.tensei.api

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Audio fingerprinting service for detecting OP/ED timestamps.
 *
 * Uses a simplified approach that doesn't require external native libraries:
 * 1. Extract audio from video using MediaCodec
 * 2. Generate energy-based fingerprint (amplitude patterns)
 * 3. Use sliding window comparison to find matches
 */
class AudioFingerprinter(private val context: Context) {

    companion object {

        // Fingerprint parameters
        private const val SAMPLE_RATE = 22050
        private const val FINGERPRINT_DURATION_MS = 30000
        private const val HOP_SIZE_MS = 1000
        private const val FRAME_SIZE_MS = 500

        // Match threshold (lower = more strict)
        private const val MATCH_THRESHOLD = 0.65f

        // Search windows
        private const val OP_SEARCH_WINDOW_SECONDS = 300
        private const val ED_SEARCH_WINDOW_SECONDS = 600
    }

    data class MatchResult(
        val found: Boolean,
        val startTime: Float?,
        val endTime: Float?,
        val confidence: Float
    )

    data class AudioFingerprint(
        val duration: Float,
        val energyProfile: FloatArray,
        val peakPattern: IntArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as AudioFingerprint

            if (duration != other.duration) return false
            if (!energyProfile.contentEquals(other.energyProfile)) return false
            if (!peakPattern.contentEquals(other.peakPattern)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = duration.hashCode()
            result = 31 * result + energyProfile.contentHashCode()
            result = 31 * result + peakPattern.contentHashCode()
            return result
        }
    }

    /**
     * Extract fingerprint from an OP/ED audio/video URL
     */
    suspend fun extractFingerprintFromUrl(url: String): AudioFingerprint? = withContext(Dispatchers.IO) {
        try {
            val audioFile = downloadAudioSegment(url) ?: return@withContext null
            val fingerprint = generateFingerprint(audioFile)
            audioFile.delete()
            fingerprint
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Find OP timestamp in episode by matching fingerprint
     */
    suspend fun findOpTimestamp(
        episodePath: String,
        opFingerprint: AudioFingerprint,
        episodeDuration: Float
    ): MatchResult? = findTimestamp(episodePath, opFingerprint, OP_SEARCH_WINDOW_SECONDS.coerceAtMost(episodeDuration.toInt()), episodeDuration)

    /**
     * Find ED timestamp in episode by matching fingerprint
     */
    suspend fun findEdTimestamp(
        episodePath: String,
        edFingerprint: AudioFingerprint,
        episodeDuration: Float
    ): MatchResult? {
        val searchStart = (episodeDuration - ED_SEARCH_WINDOW_SECONDS).coerceAtLeast(0f)
        return findTimestamp(episodePath, edFingerprint, ED_SEARCH_WINDOW_SECONDS, episodeDuration,
            searchStart)
    }

    private suspend fun findTimestamp(
        episodePath: String,
        fingerprint: AudioFingerprint,
        searchWindowSeconds: Int,
        episodeDuration: Float,
        searchStartOffset: Float = 0f
    ): MatchResult? = withContext(Dispatchers.IO) {
        try {
            val episodeFile = File(episodePath)
            if (!episodeFile.exists()) return@withContext null

            val episodeFingerprint = extractEpisodeSegmentFingerprint(episodeFile, searchStartOffset, searchWindowSeconds) ?: return@withContext null
            val matchPosition = findBestMatchPosition(episodeFingerprint, fingerprint) ?: return@withContext null

            val startTime = searchStartOffset + matchPosition.first
            val endTime = startTime + fingerprint.duration

            MatchResult(true, startTime, endTime.coerceAtMost(episodeDuration), matchPosition.second)
        } catch (_: Exception) { null }
    }

    private fun downloadAudioSegment(url: String): File? {
        return try {
            val tempFile = File(context.cacheDir, "audio_${System.currentTimeMillis()}.webm")
            val connection = java.net.URL(url).openConnection() as javax.net.ssl.HttpsURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            if (connection.responseCode != 200) return null

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var totalRead = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1 && totalRead < 5 * 1024 * 1024) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                    }
                }
            }
            connection.disconnect()
            if (tempFile.length() > 0) tempFile else { tempFile.delete(); null }
        } catch (_: Exception) { null }
    }

    private fun generateFingerprint(audioFile: File): AudioFingerprint? {
        return try {
            val extractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                extractor.release()
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            val samples = extractAudioSamples(extractor, audioFormat)
            extractor.release()

            if (samples.isEmpty()) return null

            val energyProfile = computeEnergyProfile(samples)
            val peakPattern = computePeakPattern(energyProfile)
            AudioFingerprint(samples.size.toFloat() / SAMPLE_RATE, energyProfile, peakPattern)
        } catch (_: Exception) { null }
    }

    private fun extractAudioSamples(extractor: MediaExtractor, format: MediaFormat): FloatArray {
        val samples = mutableListOf<Float>()
        val sampleBuffer = mutableListOf<Byte>()
        val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var isEOS = false
        val maxSamples = SAMPLE_RATE * 60

        while (samples.size < maxSamples) {
            if (!isEOS) {
                val inputIndex = decoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                    val sampleSize = extractor.readSampleData(inputBuffer!!, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputIndex >= 0) {
                val outputBuffer = decoder.getOutputBuffer(outputIndex)
                val chunk = ByteArray(bufferInfo.size)
                outputBuffer?.get(chunk)
                sampleBuffer.addAll(chunk.toList())
                decoder.releaseOutputBuffer(outputIndex, false)

                if (sampleBuffer.size >= 4096) {
                    samples.addAll(processPcmData(sampleBuffer.toByteArray()).toList())
                    sampleBuffer.clear()
                }
            }
            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
        }
        decoder.stop()
        decoder.release()
        return samples.toFloatArray()
    }

    private fun processPcmData(data: ByteArray): FloatArray {
        val samples = FloatArray(data.size / 2)
        val byteBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        for (i in samples.indices) samples[i] = byteBuffer.short.toFloat() / Short.MAX_VALUE
        return downsample(samples, 44100, SAMPLE_RATE)
    }

    private fun downsample(samples: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate) return samples
        val ratio = fromRate.toFloat() / toRate
        val newLength = (samples.size / ratio).toInt()
        val result = FloatArray(newLength)
        for (i in 0 until newLength) {
            val srcIndex = (i * ratio).toInt()
            if (srcIndex < samples.size) result[i] = samples[srcIndex]
        }
        return result
    }

    private fun computeEnergyProfile(samples: FloatArray): FloatArray {
        val frameSize = (SAMPLE_RATE * FRAME_SIZE_MS / 1000)
        val numFrames = samples.size / frameSize
        val energyProfile = FloatArray(numFrames)
        for (i in 0 until numFrames) {
            val start = i * frameSize
            val end = (start + frameSize).coerceAtMost(samples.size)
            var sumSquares = 0.0
            for (j in start until end) sumSquares += samples[j] * samples[j]
            energyProfile[i] = sqrt(sumSquares / (end - start)).toFloat()
        }
        val maxEnergy = energyProfile.maxOrNull() ?: 1f
        if (maxEnergy > 0) for (i in energyProfile.indices) energyProfile[i] /= maxEnergy
        return energyProfile
    }

    private fun computePeakPattern(energyProfile: FloatArray): IntArray {
        if (energyProfile.size < 3) return IntArray(0)
        val pattern = IntArray(energyProfile.size)
        val threshold = 0.1f
        for (i in 1 until energyProfile.size - 1) {
            val prev = energyProfile[i - 1]; val curr = energyProfile[i]; val next = energyProfile[i + 1]
            if (curr > prev && curr > next && curr - prev > threshold) pattern[i] = 1
            else if (curr < prev && curr < next && prev - curr > threshold) pattern[i] = -1
        }
        return pattern
    }

    private fun extractEpisodeSegmentFingerprint(episodeFile: File, startOffset: Float, durationSeconds: Int): AudioFingerprint? {
        return try {
            val extractor = MediaExtractor().apply { setDataSource(episodeFile.absolutePath) }
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrackIndex = i; audioFormat = format; break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) { extractor.release(); return null }
            extractor.selectTrack(audioTrackIndex)
            extractor.seekTo((startOffset * 1000000).toLong(), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val samples = extractAudioSamplesForDuration(extractor, audioFormat, durationSeconds)
            extractor.release()
            if (samples.isEmpty()) return null
            val energyProfile = computeEnergyProfile(samples)
            val peakPattern = computePeakPattern(energyProfile)
            AudioFingerprint(samples.size.toFloat() / SAMPLE_RATE, energyProfile, peakPattern)
        } catch (_: Exception) { null }
    }

    private fun extractAudioSamplesForDuration(extractor: MediaExtractor, format: MediaFormat, maxDurationSeconds: Int): FloatArray {
        val samples = mutableListOf<Float>()
        val sampleBuffer = mutableListOf<Byte>()
        val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        decoder.configure(format, null, null, 0)
        decoder.start()
        val bufferInfo = MediaCodec.BufferInfo()
        var isEOS = false
        val maxSamples = SAMPLE_RATE * maxDurationSeconds
        while (samples.size < maxSamples) {
            if (!isEOS) {
                val inputIndex = decoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                    val sampleSize = extractor.readSampleData(inputBuffer!!, 0)
                    if (sampleSize < 0) { decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); isEOS = true }
                    else { decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0); extractor.advance() }
                }
            }
            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputIndex >= 0) {
                val outputBuffer = decoder.getOutputBuffer(outputIndex)
                val chunk = ByteArray(bufferInfo.size); outputBuffer?.get(chunk); sampleBuffer.addAll(chunk.toList())
                decoder.releaseOutputBuffer(outputIndex, false)
                if (sampleBuffer.size >= 4096) { samples.addAll(processPcmData(sampleBuffer.toByteArray()).toList()); sampleBuffer.clear() }
            }
            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
        }
        decoder.stop(); decoder.release(); return samples.toFloatArray()
    }

    private fun findBestMatchPosition(episodeFingerprint: AudioFingerprint, targetFingerprint: AudioFingerprint): Pair<Float, Float>? {
        val episodeProfile = episodeFingerprint.energyProfile; val targetProfile = targetFingerprint.energyProfile
        if (episodeProfile.size < targetProfile.size) return null
        val windowSize = targetProfile.size; val numWindows = episodeProfile.size - windowSize
        var bestPosition = 0; var bestSimilarity = 0f
        for (offset in 0..numWindows step (HOP_SIZE_MS / FRAME_SIZE_MS)) {
            val similarity = computeSimilarity(episodeProfile, offset, windowSize, targetProfile)
            if (similarity > bestSimilarity) { bestSimilarity = similarity; bestPosition = offset }
        }
        val peakSimilarity = comparePeakPatterns(episodeFingerprint.peakPattern, bestPosition, targetFingerprint.peakPattern)
        val combinedScore = (bestSimilarity * 0.7f + peakSimilarity * 0.3f)
        return if (combinedScore >= MATCH_THRESHOLD) Pair(bestPosition * FRAME_SIZE_MS / 1000f, combinedScore) else null
    }

    private fun computeSimilarity(profile: FloatArray, offset: Int, windowSize: Int, target: FloatArray): Float {
        var sumProduct = 0.0; var sumProfileSq = 0.0; var sumTargetSq = 0.0
        for (i in 0 until windowSize) {
            val p = profile[offset + i]; val t = target[i]
            sumProduct += p * t; sumProfileSq += p * p; sumTargetSq += t * t
        }
        val denom = sqrt(sumProfileSq * sumTargetSq)
        return if (denom < 0.0001) 0f else (sumProduct / denom).toFloat()
    }

    private fun comparePeakPatterns(episodePattern: IntArray, offset: Int, targetPattern: IntArray): Float {
        var matches = 0; var total = 0
        for (i in targetPattern.indices) {
            val ep = episodePattern[offset + i]; val tp = targetPattern[i]
            if (tp != 0) { total++; if (ep == tp) matches++ }
        }
        return if (total > 0) matches.toFloat() / total else 0f
    }
}


