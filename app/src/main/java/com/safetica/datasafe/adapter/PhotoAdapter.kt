package com.safetica.datasafe.adapter

import android.view.View
import androidx.viewpager.widget.PagerAdapter
import com.safetica.datasafe.model.Image
import androidx.viewpager.widget.ViewPager
import android.view.LayoutInflater
import android.view.ViewGroup
import com.safetica.datasafe.R
import com.safetica.datasafe.interfaces.ImageLoader
import kotlinx.android.synthetic.main.image_detail_layout.view.*

import android.widget.LinearLayout

/**
 * Implements photo adapter for DetailFragment
 */
class PhotoAdapter (private val imageLoader: ImageLoader): PagerAdapter() {

    private var photoList = ArrayList<Image>()

    fun setContent(models: ArrayList<Image>?) {
        if (models == null) return
        this.photoList = models
        notifyDataSetChanged()
    }


    override fun getCount(): Int {
        return photoList.size
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object` as LinearLayout
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val view = LayoutInflater.from(container.context)
            .inflate(R.layout.image_detail_layout, container, false)

        imageLoader.load(photoList[position], view.image_detail)

        (container as ViewPager).addView(view)

        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        (container as ViewPager).removeView(`object` as LinearLayout)
    }
}