package com.safetica.datasafe.interfaces

import android.widget.ImageView
import com.safetica.datasafe.model.Image

interface ImageLoader {
    fun load(image: Image, into: ImageView)
}