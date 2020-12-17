package com.safetica.datasafe.manager

import android.app.Application
import android.content.Context
import com.safetica.imagesafe.manager.base.FileScanner
import com.safetica.datasafe.utils.Paths
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageScanner @Inject constructor(val context: Application): FileScanner() {

    fun getRecentlyModifiedImages(threshold: Long): MutableList<File> {
        val volumes = Paths.getActiveVolumes(context)

        val filesToAnalyze = mutableListOf<File>()

        volumes.forEach {
            val foundFiles = getFiles(it) {
                file -> FileScanner.Filters.isImage(file) &&
                    FileScanner.Filters.wasModifiedAfter(threshold, file)
            }

            filesToAnalyze.addAll(foundFiles)
        }

        return filesToAnalyze
    }
}