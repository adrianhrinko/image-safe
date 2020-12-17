package com.safetica.imagesafe.manager.base

import android.net.Uri
import java.io.File

open class FileScanner {

    fun getFiles(uri: Uri, filter: (file: File) -> Boolean): MutableList<File> {
        return getFiles(File(uri.path), filter)
    }

    fun getFiles(path: String, filter: (file: File) -> Boolean): MutableList<File> {
        return getFiles(File(path), filter)
    }

    fun getFiles(file: File, filter: (file: File) -> Boolean): MutableList<File> {
        val result = mutableListOf<File>()
        if (file.exists()) {
            val files = file.listFiles()
            files?.let {
                for (i in files.indices) {
                    val file = files[i]
                    if (file.isDirectory) {
                        result.addAll(getFiles(file, filter))
                    } else {
                        if (filter.invoke(file)) result.add(file)
                    }
                }
            }
        }
        return result
    }

    object Filters {

        private val imageFormats = listOf(
                "jpg",
                "png"
        )

        fun isImage(file: File): Boolean {
            val name = file.name
            val format = name.substring(name.lastIndexOf('.') + 1).toLowerCase()

            imageFormats.forEach {
                if (it == format) {
                    return true
                }
            }

            return false
        }

        fun wasModifiedAfter(milis: Long, file: File): Boolean {
            return file.lastModified() >= milis
        }
    }
}