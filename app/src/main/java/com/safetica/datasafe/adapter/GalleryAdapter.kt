package com.safetica.datasafe.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.safetica.datasafe.interfaces.ClickListener
import com.safetica.datasafe.interfaces.ImageLoader
import com.safetica.datasafe.model.Image
import kotlinx.android.synthetic.main.gallery_cell.view.*
import com.davidecirillo.multichoicerecyclerview.MultiChoiceAdapter
import com.safetica.datasafe.R
import com.safetica.datasafe.extensions.getContentUri
import kotlin.collections.ArrayList

/**
 * Implements photo adapter for GalleryFragment
 */
class GalleryAdapter(val loader: ImageLoader): MultiChoiceAdapter<GalleryAdapter.ViewHolder>() {

    var galleryList = ArrayList<Image>()
    private var onClickListener: ClickListener? = null

    fun setContent(models: ArrayList<Image>?) {
        if (models == null) return
        this.galleryList = models
        notifyDataSetChanged()
    }


    fun setOnClickListener(listener: ClickListener) {
        onClickListener = listener
    }

    val selectedImages: Array<Image> get() = (selectedItemList.map { galleryList[it] }).toTypedArray()
    fun selectedUris(context: Context) = ArrayList(selectedItemList.map { galleryList[it].getContentUri(context) })

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): GalleryAdapter.ViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.gallery_cell, viewGroup, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: GalleryAdapter.ViewHolder, i: Int) {
        super.onBindViewHolder(viewHolder, i)
        viewHolder.bind(i)
    }

    override fun getItemCount(): Int {
        return galleryList.size
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val imageView: ImageView = view.img

        fun bind(position: Int) {
            val image = galleryList[position]
            loader.load(image, imageView)
        }
    }

    override fun defaultItemViewClickListener(holder: ViewHolder, position: Int): View.OnClickListener {
        return View.OnClickListener { onClickListener?.onImageClicked(position, galleryList[position]) }
    }


    override fun setActive(view: View, state: Boolean) {
        val imageView = view.img
        val choiceIdicator = view.choiceIndicator

        if (state) {
            imageView.scaleX = 0.9f
            imageView.scaleY = 0.9f
            choiceIdicator.visibility = View.VISIBLE
        } else {
            imageView.scaleX = 1f
            imageView.scaleY = 1f
            choiceIdicator.visibility = View.INVISIBLE
        }
    }
}