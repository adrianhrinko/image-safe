package com.safetica.datasafe.utils

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object CameraUtility {

    /**
     * Creates new image file in Pictures folder
     * @return File if successful, null otherwise
     */
    private fun createImageFile(): File? {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        return FileUtility.create("sftcdtsf_$timeStamp", ".jpg", storageDir)
    }

    /**
     * Opens camera application to take a photo
     * @param fragment
     * @param requestCode - unique integer
     * @return uri of taken photo if successful, null otherwise
     */
    fun takePhoto(fragment: Fragment, requestCode: Int): Uri? {
        var imageUri: Uri? = null
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(fragment.requireActivity().packageManager)?.also {
                val photoFile: File? = createImageFile()
                imageUri = photoFile?.toUri()
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(fragment.requireContext(),
                        "com.safetica.datasafe.fileprovider", it)
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    fragment.startActivityForResult(takePictureIntent, requestCode)
                }
            }
        }
        return imageUri
    }
}