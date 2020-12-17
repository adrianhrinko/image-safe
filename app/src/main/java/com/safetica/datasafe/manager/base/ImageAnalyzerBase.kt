package com.safetica.datasafe.manager.base

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler


open class ImageAnalyzerBase(val context: Context) {

    private val detector: FirebaseVisionImageLabeler by lazy { FirebaseVision.getInstance().onDeviceImageLabeler}

    fun analyze(path: Uri): Task<List<FirebaseVisionImageLabel>> {
        val image = FirebaseVisionImage.fromFilePath(context, path)
        return detector.processImage(image)
    }


}