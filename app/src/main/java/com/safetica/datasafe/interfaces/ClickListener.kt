package com.safetica.datasafe.interfaces

import com.safetica.datasafe.model.Image


interface ClickListener {
    fun onImageClicked(position: Int, image: Image)
}
