package com.safetica.datasafe.manager


import androidx.lifecycle.MutableLiveData
import com.safetica.datasafe.model.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject


class ImageDetector constructor(private val imageAnalyzer: ImageAnalyzer, subjectLabels: List<String>) {

    private val subjectLabels = subjectLabels.map { it.toLowerCase() }

    val proggress = MutableLiveData<Int>().apply { value = 0 }

    suspend fun findInsecureImages(images: List<File>): List<Image> {

        val insecureImages = mutableListOf<Image>()

        images.forEach {
            if(imageAnalyzer.containsLabels(it, subjectLabels))
                insecureImages

            withContext(Dispatchers.Main) {
                proggress.value = proggress.value?.plus(1)
            }

        }

        return insecureImages
    }
}