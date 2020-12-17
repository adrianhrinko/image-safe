package com.safetica.datasafe.interfaces

import android.graphics.Bitmap

interface ImageTransformation {
    suspend fun transform(original: Bitmap, param: Int): Bitmap
}