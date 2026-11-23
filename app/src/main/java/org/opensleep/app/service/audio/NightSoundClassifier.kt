package org.opensleep.app.service.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.opensleep.app.data.local.db.SoundCategory
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

data class SoundClassificationResult(
    val category: SoundCategory,
    val rawLabel: String,
    val confidence: Float
)

@Singleton
class NightSoundClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var interpreter: Interpreter? = null
    private var classLabels: List<String> = emptyList()

    companion object {
        const val REQUIRED_SAMPLES_16KHZ = 15_600 // 0.975s @ 16kHz
        private const val NUM_CLASSES = 521
        private const val MIN_CONFIDENCE = 0.25f
    }

    @Synchronized
    fun initialize() {
        if (interpreter != null) return
        runCatching {
            val modelBuffer = loadModelFile("yamnet.tflite")
            val options = Interpreter.Options().apply { setNumThreads(2) }
            interpreter = Interpreter(modelBuffer, options)
            classLabels = loadClassMap("yamnet_class_map.csv")
        }
    }

    @Synchronized
    fun classifyWindow(pcmFloat15600: FloatArray): SoundClassificationResult? {
        val interp = interpreter ?: return null
        if (pcmFloat15600.size < REQUIRED_SAMPLES_16KHZ) return null

        val input = if (pcmFloat15600.size == REQUIRED_SAMPLES_16KHZ) {
            pcmFloat15600
        } else {
            pcmFloat15600.copyOf(REQUIRED_SAMPLES_16KHZ)
        }

        val outputScores = Array(1) { FloatArray(NUM_CLASSES) }
        interp.run(input, outputScores)

        val scores = outputScores[0]
        var bestIdx = -1
        var bestScore = 0f
        for (i in scores.indices) {
            if (scores[i] > bestScore) {
                bestScore = scores[i]
                bestIdx = i
            }
        }

        if (bestIdx == -1 || bestScore < MIN_CONFIDENCE) return null
        val label = classLabels.getOrElse(bestIdx) { "Unknown" }
        return SoundClassificationResult(
            category = mapAudioSetLabelToCategory(label),
            rawLabel = label,
            confidence = bestScore
        )
    }

    @Synchronized
    fun close() {
        interpreter?.close()
        interpreter = null
    }

    private fun mapAudioSetLabelToCategory(label: String): SoundCategory {
        val l = label.lowercase()
        return when {
            "snoring" in l || "snort" in l -> SoundCategory.SNORING
            "speech" in l || "whisper" in l || "groan" in l || "talk" in l -> SoundCategory.VOICE
            "cough" in l || "sneeze" in l || "throat" in l || "gasp" in l -> SoundCategory.BODY
            "rustle" in l || "crumpl" in l || "tap" in l || "thump" in l -> SoundCategory.MOVEMENT
            "dog" in l || "cat" in l || "bark" in l || "meow" in l -> SoundCategory.ANIMAL
            "vehicle" in l || "car" in l || "traffic" in l || "siren" in l -> SoundCategory.VEHICLE
            "rain" in l || "thunder" in l || "wind" in l -> SoundCategory.WEATHER
            "music" in l || "noise" in l || "hum" in l -> SoundCategory.NOISE
            else -> SoundCategory.UNKNOWN
        }
    }

    private fun loadModelFile(assetName: String): MappedByteBuffer {
        val fd = context.assets.openFd(assetName)
        FileInputStream(fd.fileDescriptor).use { fis ->
            return fis.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

    private fun loadClassMap(assetName: String): List<String> {
        return runCatching {
            context.assets.open(assetName).bufferedReader().useLines { lines ->
                lines.drop(1)
                    .mapNotNull { line ->
                        line.split(",", limit = 3).getOrNull(2)?.trim()?.removeSurrounding("\"")
                    }
                    .toList()
            }
        }.getOrDefault(emptyList())
    }
}