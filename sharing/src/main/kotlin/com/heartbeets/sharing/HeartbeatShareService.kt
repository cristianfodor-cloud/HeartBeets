package com.heartbeets.sharing

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles uploading/downloading heartbeat configs and voice recordings to/from Firebase.
 * Config in Firestore: /heartbeats/{code}
 * Voice files in Firebase Storage: voices/{code}/{index}.m4a
 */
class HeartbeatShareService(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    /**
     * Upload a heartbeat config + voice recordings.
     */
    suspend fun upload(code: String, shared: SharedHeartbeat, localVoicePaths: List<String>) =
        withContext(Dispatchers.IO) {
            // Upload voice files to Storage
            val voiceUrls = localVoicePaths.mapIndexedNotNull { index, path ->
                try {
                    val file = File(path)
                    if (!file.exists()) return@mapIndexedNotNull null
                    val ref = storage.reference.child("voices/$code/$index.m4a")
                    ref.putBytes(file.readBytes()).await()
                    ref.downloadUrl.await().toString()
                } catch (e: Exception) {
                    Log.e("HeartbeatShareService", "Failed to upload voice $index: ${e.message}", e)
                    null
                }
            }

            // Store config with voice URLs in Firestore
            val withUrls = shared.copy(voiceRecordingUrls = voiceUrls)
            firestore.collection("heartbeats")
                .document(code)
                .set(withUrls)
                .await()
        }

    /**
     * Download a heartbeat config by code.
     */
    suspend fun downloadConfig(code: String): SharedHeartbeat? {
        val doc = firestore.collection("heartbeats")
            .document(code)
            .get()
            .await()
        if (!doc.exists()) return null
        return doc.toObject(SharedHeartbeat::class.java)
    }

    /**
     * Download voice recordings from Storage to local files.
     */
    suspend fun downloadVoiceFiles(code: String, urls: List<String>): List<String> =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "received_voices/$code")
            dir.mkdirs()
            urls.mapIndexedNotNull { index, url ->
                try {
                    val localFile = File(dir, "$index.m4a")
                    storage.getReferenceFromUrl(url)
                        .getFile(localFile)
                        .await()
                    localFile.absolutePath
                } catch (e: Exception) {
                    Log.e("HeartbeatShareService", "Failed to download voice $index", e)
                    null
                }
            }
        }

    /**
     * Delete a shared heartbeat from Firebase.
     */
    suspend fun delete(code: String) {
        try {
            val storageRef = storage.reference.child("voices/$code")
            val items = storageRef.listAll().await()
            items.items.forEach { it.delete().await() }
        } catch (_: Exception) { }

        firestore.collection("heartbeats")
            .document(code)
            .delete()
            .await()
    }
}
