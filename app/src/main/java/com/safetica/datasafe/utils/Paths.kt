package com.safetica.datasafe.utils

import android.content.Context
import android.provider.MediaStore
import java.io.File

object Paths {

    fun getActiveVolumes(context: Context): List<String> {
        return context.getExternalFilesDirs("").map { getRootOfExternalStorage(context.packageName, it) }
    }

    private fun getRootOfExternalStorage(packageName: String, file: File): String {
        return file.absolutePath.replace("/Android/data/$packageName/files".toRegex(), "")
    }

}