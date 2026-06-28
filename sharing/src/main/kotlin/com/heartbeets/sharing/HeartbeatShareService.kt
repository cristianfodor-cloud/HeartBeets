package com.heartbeets.sharing

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles uploading/downloading heartbeat configs and voice recordings to/from Firebase.
 *
 * - Config stored in Firestore: `/heartbeats/{code}`
 * - Voice files stored in Firebase Storage: `voices/{code}/{index}.m4a`
 */
class HeartbeatShareService(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    /**
     * Upload a heartbeat config + voice recordings.
     * @param code The 10-char sharing code.
     * @param shared The heartbeat DTO (without voice URLs — they'll be set after upload).
     * @param localVoicePaths Local file paths of voice recordings to upload.
     */
    suspend fun upload(code: String, shared: SharedHeartbeat, localVoicePaths: List<String>) {
        // Upload voice files first
        val voiceUrls = localVoicePaths.mapIndexedNotNull { index, path ->
            uploadVoiceFile(code, index, path)
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
     * @return The shared heartbeat DTO, or null if not found.
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
     * Download voice recordings to local cache.
     * @param code The sharing code.
     * @param urls The Firebase Storage URLs/paths from the config.
     * @return List of local file paths where recordings were saved.
     */
    suspend fun downloadVoiceFiles(code: String, urls: List<String>): List<String> =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "received/$code")
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
     * Delete a shared heartbeat from Firebase (config + voice files).
     */
    suspend fun delete(code: String) {
        // Delete voice files
        try {
            val storageRef = storage.reference.child("voices/$code")
            val items = storageRef.listAll().await()
            items.items.forEach { it.delete().await() }
        } catch (_: Exception) { /* may not exist */ }

        // Delete Firestore doc
        firestore.collection("heartbeats")
            .document(code)
            .delete()
            .await()
    }

    private suspend fun uploadVoiceFile(code: String, index: Int, localPath: String): String? {
        return try {
            val file = File(localPath)
            if (!file.exists()) return null
            val ref = storage.reference.child("voices/$code/$index.m4a")
            ref.putFile(Uri.fromFile(file)).await()
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e("HeartbeatShareService", "Failed to upload voice $index", e)
            null
        }
    }
}
