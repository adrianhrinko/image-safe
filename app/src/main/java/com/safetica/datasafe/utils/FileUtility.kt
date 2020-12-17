package com.safetica.datasafe.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.safetica.datasafe.model.Image
import timber.log.Timber
import java.io.File
import java.io.IOException

object FileUtility {

    /**
     * Clears metadata of the file from the system
     * @param path to the file
     * @param context of application
     */
    fun clearMetada(path: String, context: Context) {
        context.contentResolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Images.ImageColumns.DATA + "=?" , arrayOf(path) )
    }

    /**
     * Refreshes metadata of the file from the system
     * @param path to the file
     * @param context of application
     */
    fun refreshMetadata(path: String, context: Context) {
        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also {
            it.data = Uri.fromFile(File(path))
            context.sendBroadcast(it)
        }
    }

    /**
     * Deletes file
     * @param path to the file
     * @param context of application
     * @return true if successful, false otherwise
     */
    private fun delete(path: String, context: Context): Boolean {
        val file = File(path)
        if (file.exists()) {
            file.delete()
            clearMetada(path, context)
            return true
        }
        return false
    }

    /**
     * Deletes images
     * @param images to be deleted
     * @param context of application
     */
    fun delete(context: Context, vararg images: Image) {
        images.forEach {
            delete(it.path, context)
        }
    }

    /**
     * Creates new file
     * @param fileName
     * @param suffix
     * @param directory in which the new file will be created
     * @return created File if successful, null otherwise
     */
    fun create(fileName: String, suffix: String, directory: File): File? {
        val file = File(directory, "$fileName$suffix")
        return try {
            file.createNewFile()
            file
        } catch (e: IOException) {
            Timber.e("Failed to create file ${directory.path}/$fileName$suffix:")
            Timber.e(e)
            null
        }
    }

    /**
     * Opens gallery to pick images
     */
    fun pickFile(type: String, multiple: Boolean, title: String, fragment: Fragment, requestCode: Int) {
        val intent = Intent()
        intent.type = type
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
        intent.action = Intent.ACTION_GET_CONTENT
        fragment.startActivityForResult(Intent.createChooser(intent, title), requestCode)
    }

    fun getContentUriForPath(path: String, context: Context): Uri {
        return getContentUriForFile(File(path), context)
    }

    fun getContentUriForFile(file: File, context: Context): Uri {
        return FileProvider.getUriForFile(context, "com.safetica.datasafe.fileprovider", file)
    }
}