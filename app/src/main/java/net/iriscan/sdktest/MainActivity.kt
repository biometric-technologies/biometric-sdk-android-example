package net.iriscan.sdktest

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import net.iriscan.sdk.BiometricSdkConfigBuilder
import net.iriscan.sdk.BiometricSdkFactory
import net.iriscan.sdk.face.FaceEncodeProperties
import net.iriscan.sdk.face.FaceExtractProperties
import net.iriscan.sdk.face.FaceMatchProperties
import net.iriscan.sdk.face.FaceNetModelConfiguration
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.random.nextInt

class MainActivity : AppCompatActivity() {

    val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    val db = mutableMapOf<String, List<ByteArray>>()
    var image: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val statusView = findViewById<TextView>(R.id.statusText)
        statusView.text = "Initializing SDK..."
        /* DEFAULT CONFIG*/
        BiometricSdkFactory.configure(
            BiometricSdkConfigBuilder(this)
                .withFace(
                    FaceExtractProperties(),
                    FaceEncodeProperties(
                        faceNetModel =  FaceNetModelConfiguration(
                            "assets://facenet.tflite",
                            160,
                            160,
                            128
                        )
                    ),
                    FaceMatchProperties(threshold = 10.0)
                )
                .build()
        )
        /* CUSTOM MODEL
        BiometricSdkFactory.configure(
            BiometricSdkConfigBuilder(this)
                .withFace(
                    FaceExtractProperties(),
                    FaceEncodeProperties(
                        FaceNetModelConfiguration(
                            "https://raw.githubusercontent.com/mosip/mosip-mobileid-sdk/InjiFaceSdk/android/src/main/assets/facenet.tflite",
                            160,
                            160,
                            512
                        )
                    ),
                    FaceMatchProperties(
                        10.0
                    )
                )
                .build()
        )*/
        loadDb()
    }

    private fun loadDb() {
        val statusView = findViewById<TextView>(R.id.statusText)
        statusView.text = "Loading DB..."
        executor.execute {
            db["Howard"] = loadFolder("howard")
            db["Sheldon"] = loadFolder("sheldon")
            runOnUiThread {
                statusView.text = ""
                Toast.makeText(this, "Db initialized", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadFolder(path: String): List<ByteArray> {
        val sdk = BiometricSdkFactory.getInstance()
        return assets.list(path)!!
            .map {
                val bytes = assets.open("$path/$it").readBytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val template = sdk.face()
                    .encoder()
                    .encode(sdk.io().convert(bitmap))
                template
            }
    }

    fun loadRandom(view: View) {
        val imgView = findViewById<ImageView>(R.id.src)
        val randIdx = Random.nextInt(1..4)
        val imgBytes = assets.open("img$randIdx.jpg").readBytes()
        image?.recycle()
        image = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size)
        imgView.setImageBitmap(image)
    }

    fun detect(view: View) {
        if (image == null) return
        val sdk = BiometricSdkFactory.getInstance()
        val statusView = findViewById<TextView>(R.id.statusText)
        statusView.text = "Searching..."
        executor.execute {
            val template = sdk.face()
                .encoder()
                .extractAndEncode(sdk.io().convert(image!!))
            val foundPerson = db.entries
                .firstOrNull {
                    it.value
                        .any { nameTemplate -> sdk.face().matcher().matches(template, nameTemplate) }
                }?.key ?: "Unknown"
            runOnUiThread {
                statusView.text = ""
                AlertDialog.Builder(this)
                    .setTitle("Result")
                    .setMessage("Found person: $foundPerson")
                    .show()
            }
        }
    }

}