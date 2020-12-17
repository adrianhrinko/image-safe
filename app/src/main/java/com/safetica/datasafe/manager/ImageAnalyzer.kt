package com.safetica.datasafe.manager

import android.app.Application
import android.content.Context
import android.net.Uri
import com.safetica.datasafe.extensions.await
import com.safetica.datasafe.manager.base.ImageAnalyzerBase
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageAnalyzer @Inject constructor(context: Application): ImageAnalyzerBase(context) {

    suspend fun containsLabels(file: File, labels: List<String>): Boolean {
        return containsLabels(Uri.fromFile(file), labels)
    }

    suspend fun containsLabels(path: Uri, labels: List<String>): Boolean {

        val subjectLabels = labels.map { it.toLowerCase() }
        var resultLabels = listOf<String>()

        try {
            resultLabels = analyze(path).await().map { it.text.toLowerCase() }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        labels.forEach {
            if (resultLabels.contains(it)) return true
        }

        return false
    }
}