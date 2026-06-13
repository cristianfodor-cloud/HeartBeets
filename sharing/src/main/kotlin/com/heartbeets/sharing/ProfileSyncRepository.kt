package com.heartbeets.sharing

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Stores and retrieves shared profile definitions from Firestore.
 */
class ProfileSyncRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val collection = firestore.collection("profiles")

    /**
     * Upload or update a profile in Firestore.
     */
    suspend fun uploadProfile(profile: SharedProfile) {
        collection.document(profile.id).set(profile).await()
    }

    /**
     * Fetch a profile by ID. Returns null if not found.
     */
    suspend fun getProfile(profileId: String): SharedProfile? {
        val doc = collection.document(profileId).get().await()
        return doc.toObject(SharedProfile::class.java)
    }

    /**
     * Delete a profile from Firestore.
     */
    suspend fun deleteProfile(profileId: String) {
        collection.document(profileId).delete().await()
    }
}
