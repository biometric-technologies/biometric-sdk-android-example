package net.iriscan.example

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** An enrolled person: one template of one modality. */
class Person(val id: String, val name: String, val modality: Modality, val template: ByteArray, val enrolledAt: Long)

/**
 * The people enrolled on this device, kept in a JSON file in the app's private storage. Only
 * templates are stored, not photos. Uninstalling the app or "Delete all" removes them.
 */
class Gallery(context: Context) {

    private val file = File(context.filesDir, "gallery.json")
    private val people = ArrayList<Person>()

    init {
        if (file.exists()) {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                people += Person(
                    o.getString("id"), o.getString("name"), Modality.valueOf(o.getString("modality")),
                    Base64.decode(o.getString("template"), Base64.NO_WRAP), o.getLong("enrolledAt"),
                )
            }
        }
    }

    fun all(): List<Person> = synchronized(people) { people.toList() }

    fun add(name: String, modality: Modality, template: ByteArray): Person = synchronized(people) {
        val person = Person(UUID.randomUUID().toString(), name, modality, template, System.currentTimeMillis())
        people += person
        save()
        person
    }

    fun clear() = synchronized(people) {
        people.clear()
        save()
    }

    private fun save() {
        val array = JSONArray()
        people.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id).put("name", it.name).put("modality", it.modality.name)
                    .put("template", Base64.encodeToString(it.template, Base64.NO_WRAP))
                    .put("enrolledAt", it.enrolledAt)
            )
        }
        // write a new file and rename it, so an interrupted write cannot leave half a gallery
        val tmp = File(file.parentFile, "gallery.json.tmp")
        tmp.writeText(array.toString())
        tmp.renameTo(file)
    }
}
