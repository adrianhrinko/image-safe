package com.safetica.datasafe.utils

import android.graphics.Bitmap
import com.safetica.datasafe.interfaces.ImageTransformation
import nl.dionsegijn.pixelate.Pixelate
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class Pixelator: ImageTransformation {

    override suspend fun transform(original: Bitmap, param: Int) = suspendCoroutine<Bitmap> {
        Pixelate(original)
            .setDensity(param)
            .setListener { result, _ ->
                it.resume(result)
            }
            .make()
    }
}