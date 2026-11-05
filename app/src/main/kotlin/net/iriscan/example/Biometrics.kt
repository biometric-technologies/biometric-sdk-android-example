package net.iriscan.example

import android.content.Context
import android.graphics.Bitmap
import net.iriscan.sdk.BiometricSdkFactory
import net.iriscan.sdk.BiometricSdkOperations
import net.iriscan.sdk.face.FaceEncodeProperties
import net.iriscan.sdk.face.FaceExtractProperties
import net.iriscan.sdk.face.FaceMatchProperties
import net.iriscan.sdk.face.FaceNetModelConfiguration
import net.iriscan.sdk.iris.IrisEncodeProperties
import net.iriscan.sdk.iris.IrisExtractProperties
import net.iriscan.sdk.iris.IrisMatchProperties

enum class Modality(val label: String) { FACE("Face"), IRIS("Iris") }

/** The best candidate of a 1:N search and whether it passes the matching threshold. */
class Hit(val person: Person, val score: Double, val matches: Boolean) {
    /** Face scores are distances (lower is closer), iris scores similarities (higher is closer). */
    fun describe(): String = when (person.modality) {
        Modality.FACE -> "%s, distance %.2f".format(person.name, score)
        Modality.IRIS -> "%s, similarity %.2f".format(person.name, score)
    }
}

/**
 * The Biometric SDK set up for face and iris, and the two things the demo does with it: turn a
 * photo into a template, and search a template among the enrolled people.
 */
class Biometrics(context: Context) {

    private val sdk: BiometricSdkOperations

    init {
        BiometricSdkFactory.initialize(
            BiometricSdkFactory.configBuilder()
                // the SDK reads its files through a Context and keeps it, so the application one
                .withContext(context.applicationContext)
                .withIris(IrisExtractProperties(), IrisEncodeProperties(), IrisMatchProperties())
                .withFace(
                    extractor = FaceExtractProperties(),
                    // FaceNet in this app's assets: 160 x 160 input, 128-value embedding
                    encoder = FaceEncodeProperties(FaceNetModelConfiguration("assets:facenet.tflite", 160, 160, 128)),
                    // distance between L2-normalized embeddings, 0 to 2; 1.0 as in the SDK README
                    matcher = FaceMatchProperties(threshold = FACE_THRESHOLD),
                )
                .build()
        )
        sdk = BiometricSdkFactory.getInstance() ?: error("The SDK did not initialize")
    }

    /** The template of the face or iris in [photo], or null when the SDK finds none. */
    fun encode(modality: Modality, photo: Bitmap): ByteArray? = when (modality) {
        Modality.FACE -> sdk.face().encoder().extractAndEncode(photo)
        Modality.IRIS -> sdk.iris().encoder().extractAndEncode(sdk.io().convert(photo))
    }

    /** The closest enrolled person of the same modality, or null when nobody is enrolled. */
    fun search(modality: Modality, template: ByteArray, people: List<Person>): Hit? {
        val candidates = people.filter { it.modality == modality }
        if (candidates.isEmpty()) return null
        return when (modality) {
            Modality.FACE -> {
                val matcher = sdk.face().matcher()
                val scored = candidates.map { it to matcher.matchScore(template, it.template) }
                val (best, distance) = scored.minBy { it.second }
                Hit(best, distance, matcher.matches(template, best.template))
            }
            Modality.IRIS -> {
                val matcher = sdk.iris().matcher()
                val scored = candidates.map { it to matcher.score(template, it.template) }
                val (best, similarity) = scored.maxBy { it.second }
                Hit(best, similarity, matcher.matches(template, best.template))
            }
        }
    }

    companion object {
        const val FACE_THRESHOLD = 1.0
    }
}
