package net.iriscan.example

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.Executors

/**
 * One screen: choose face or iris, take or choose a photo, then enroll the person or search for
 * them among the people enrolled on this device. Enrollment first searches too, so a second
 * enrollment of the same person is flagged, which is what de-duplication means.
 */
class MainActivity : Activity() {

    private val worker = Executors.newSingleThreadExecutor()
    private var biometrics: Biometrics? = null
    private lateinit var gallery: Gallery

    private var modality = Modality.FACE
    private var photo: Bitmap? = null

    private lateinit var faceTab: Button
    private lateinit var irisTab: Button
    private lateinit var faceSources: LinearLayout
    private lateinit var irisSources: LinearLayout
    private lateinit var preview: ImageView
    private lateinit var enroll: Button
    private lateinit var search: Button
    private lateinit var status: TextView
    private lateinit var enrolled: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gallery = Gallery(this)
        setContentView(buildLayout())
        showModality(Modality.FACE)
        say("Starting the SDK…")
        worker.execute {
            val result = runCatching { Biometrics(this) }
            runOnUiThread {
                result.onSuccess { biometrics = it; say("Ready. Take or choose a photo.") }
                    .onFailure { say("The SDK did not start: ${it.message}") }
                updateButtons()
            }
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    // ---- layout -----------------------------------------------------------------------------

    private fun buildLayout(): View {
        val pad = dp(16)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        column.addView(TextView(this).apply {
            text = "Iriscan Biometric SDK demo"
            textSize = 22f
        })
        column.addView(TextView(this).apply {
            text = "Templates are computed and compared on this device. Nothing is sent anywhere."
            setPadding(0, dp(4), 0, dp(12))
        })

        faceTab = button("Face") { showModality(Modality.FACE) }
        irisTab = button("Iris") { showModality(Modality.IRIS) }
        column.addView(row(faceTab, irisTab))

        faceSources = row(button("Take photo") { takePhoto() }, button("Choose photo") { choosePhoto() })
        irisSources = row(button("Scan with Biomatiques") { scanIris() }, button("Choose image") { choosePhoto() })
        column.addView(faceSources)
        column.addView(irisSources)

        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFFEEEEEE.toInt())
            minimumHeight = dp(200)
        }
        column.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(280)).apply {
            topMargin = dp(8); bottomMargin = dp(8)
        })

        enroll = button("Enroll") { enrollPerson() }
        search = button("Search") { searchPerson() }
        column.addView(row(enroll, search))

        status = TextView(this).apply { textSize = 16f; setPadding(0, dp(12), 0, dp(12)) }
        column.addView(status)

        enrolled = TextView(this)
        column.addView(enrolled)
        column.addView(button("Delete all enrolled") { confirmClear() }.apply { gravity = Gravity.CENTER })
        updateEnrolled()
        return ScrollView(this).apply { addView(column) }
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach { addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun say(text: String) {
        status.text = text
    }

    private fun showModality(m: Modality) {
        modality = m
        faceTab.isEnabled = m != Modality.FACE
        irisTab.isEnabled = m != Modality.IRIS
        faceSources.visibility = if (m == Modality.FACE) View.VISIBLE else View.GONE
        irisSources.visibility = if (m == Modality.IRIS) View.VISIBLE else View.GONE
        setPhoto(null)
        if (biometrics != null) say(
            if (m == Modality.FACE) "Take a photo of a face, or choose one."
            else "Scan an eye with a Biomatiques EPI-1000, or choose a near-infrared eye image."
        )
    }

    private fun setPhoto(bitmap: Bitmap?) {
        photo = bitmap
        preview.setImageBitmap(bitmap)
        updateButtons()
    }

    private fun updateButtons() {
        val ready = biometrics != null && photo != null
        enroll.isEnabled = ready
        search.isEnabled = ready
    }

    private fun updateEnrolled() {
        val people = gallery.all()
        val faces = people.count { it.modality == Modality.FACE }
        enrolled.text = "Enrolled on this device: $faces face, ${people.size - faces} iris"
    }

    // ---- photo sources ----------------------------------------------------------------------

    private val photoFile by lazy { File(cacheDir, "photos/photo.jpg").also { it.parentFile?.mkdirs() } }

    private fun takePhoto() {
        // the camera app writes the full-size photo to our file; without EXTRA_OUTPUT it returns
        // only a thumbnail, too small for face detection
        val uri = FileProvider.getUriForFile(this, "$packageName.photos", photoFile)
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        start(intent, REQUEST_CAMERA, "No camera app on this device.")
    }

    private fun choosePhoto() {
        start(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), REQUEST_PICK, "No app to choose images.")
    }

    /**
     * Biomatiques ships its scanner driver as a separate app, "Biomatiques Iris Capture"
     * (package com.biomatiques.irisSdk). It takes the picture and returns the encoded image in
     * the extra IrisCaptureImage.
     */
    private fun scanIris() {
        start(Intent(BIOMATIQUES_CAPTURE), REQUEST_BIOMATIQUES,
            "Install the Biomatiques Iris Capture app from the scanner maker and connect the scanner with an OTG cable.")
    }

    private fun start(intent: Intent, request: Int, missing: String) {
        try {
            startActivityForResult(intent, request)
        } catch (e: ActivityNotFoundException) {
            say(missing)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        worker.execute {
            val bitmap = runCatching {
                when (requestCode) {
                    REQUEST_CAMERA -> decode(photoFile.readBytes(), exifRotation(photoFile))
                    REQUEST_PICK -> data?.data?.let { uri -> decode(read(uri), exifRotation(uri)) }
                    REQUEST_BIOMATIQUES -> data?.getByteArrayExtra("IrisCaptureImage")?.takeIf { it.isNotEmpty() }
                        ?.let { decode(it, 0) }
                    else -> null
                }
            }.getOrNull()
            runOnUiThread {
                if (bitmap == null) say("No image came back.") else {
                    setPhoto(bitmap)
                    say("Image ${bitmap.width} × ${bitmap.height}. Enroll or search.")
                }
            }
        }
    }

    private fun read(uri: Uri): ByteArray = contentResolver.openInputStream(uri)!!.use { it.readBytes() }

    /** Decodes, scales to at most 1600 pixels on the long side, and turns upright. */
    private fun decode(bytes: ByteArray, rotation: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        if (rotation == 0) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(rotation.toFloat()) }, true)
    }

    private fun exifRotation(file: File): Int = runCatching { degrees(ExifInterface(file.path)) }.getOrDefault(0)

    private fun exifRotation(uri: Uri): Int {
        if (Build.VERSION.SDK_INT < 24) return 0 // ExifInterface reads streams from Android 7
        return runCatching { contentResolver.openInputStream(uri)!!.use { degrees(ExifInterface(it)) } }.getOrDefault(0)
    }

    private fun degrees(exif: ExifInterface) = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    // ---- enroll and search ------------------------------------------------------------------

    /** Encodes the shown photo in the background and hands the template to [then] on the UI thread. */
    private fun withTemplate(then: (ByteArray) -> Unit) {
        val sdk = biometrics ?: return
        val image = photo ?: return
        val m = modality
        say("Working…")
        worker.execute {
            val result = runCatching { sdk.encode(m, image) }
            runOnUiThread {
                result.onFailure { say("The SDK failed: ${it.message}") }
                    .onSuccess { template ->
                        if (template == null) say(if (m == Modality.FACE) "No face found in the photo." else "No iris found in the image.")
                        else then(template)
                    }
            }
        }
    }

    private fun searchPerson() = withTemplate { template ->
        val hit = biometrics!!.search(modality, template, gallery.all())
        say(when {
            hit == null -> "Nobody is enrolled for ${modality.label.lowercase()} yet."
            hit.matches -> "Found: ${hit.describe()}."
            else -> "No match above the threshold. Closest: ${hit.describe()}."
        })
    }

    private fun enrollPerson() = withTemplate { template ->
        val hit = biometrics!!.search(modality, template, gallery.all())
        if (hit != null && hit.matches) {
            AlertDialog.Builder(this)
                .setTitle("Possibly enrolled already")
                .setMessage("This looks like ${hit.describe()}. Enroll as a new person anyway?")
                .setPositiveButton("Enroll anyway") { _, _ -> askName(template) }
                .setNegativeButton("Cancel", null)
                .show()
            say("Possible duplicate: ${hit.describe()}.")
        } else askName(template)
    }

    private fun askName(template: ByteArray) {
        val input = EditText(this).apply { hint = "Name" }
        AlertDialog.Builder(this)
            .setTitle("Enroll")
            .setView(input)
            .setPositiveButton("Enroll") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Person ${gallery.all().size + 1}" }
                gallery.add(name, modality, template)
                updateEnrolled()
                say("Enrolled $name (${modality.label.lowercase()}).")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setMessage("Delete every enrolled template on this device?")
            .setPositiveButton("Delete") { _, _ -> gallery.clear(); updateEnrolled(); say("Deleted.") }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        private const val REQUEST_CAMERA = 1
        private const val REQUEST_PICK = 2
        private const val REQUEST_BIOMATIQUES = 3
        /** Intent action of the Biomatiques Iris Capture app (from its Android SDK sample) */
        private const val BIOMATIQUES_CAPTURE = "in.biomatiquesSDK.Android.IrisCapture"
    }
}
