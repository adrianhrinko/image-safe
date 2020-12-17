package com.safetica.datasafe.fragment


import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.bumptech.glide.load.engine.DiskCacheStrategy

import com.safetica.datasafe.R
import com.safetica.datasafe.adapter.PhotoAdapter
import com.safetica.datasafe.glide.GlideApp
import com.safetica.datasafe.interfaces.ImageLoader
import com.safetica.datasafe.model.Image
import com.safetica.datasafe.viewmodel.DataSafeViewModel
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.fragment_detail.view.*
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import android.view.*
import com.safetica.datasafe.extensions.getContentUri
import com.safetica.datasafe.interfaces.CryptoHandler
import com.safetica.datasafe.interfaces.FragmentManager
import com.safetica.datasafe.utils.DialogUtility


/**
 * This class implements user interface for displaying detail of photo
 */
class DetailFragment : Fragment(), ImageLoader, CoroutineScope {



    private val job = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job


    @Inject
    internal lateinit var viewModelFactory: ViewModelProvider.Factory

    private var rootView: View? = null

    private lateinit var viewModel: DataSafeViewModel

    private lateinit var adapter: PhotoAdapter

    private var position: Int = 0

    private var fragmentManager: FragmentManager? = null
    private var cryptoHandler: CryptoHandler? = null

    private var dialog: Dialog? = null

    private var images = ArrayList<Image>()

    companion object {
        private const val POSITION_SET_KEY = "position_set"
        private const val POSITION_KEY = "POSITION"

        @JvmStatic
        fun instance(position: Int) =
            DetailFragment().apply {
                arguments = Bundle().apply {
                    putInt(POSITION_KEY, position)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidSupportInjection.inject(this)
        initialiseViewModel()
        setHasOptionsMenu(true)

        arguments?.let{
            position = it.getInt(POSITION_KEY, 0)
        }

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        restoreState(savedInstanceState)
        rootView = inflater.inflate(R.layout.fragment_detail, container, false)
        init()
        return rootView
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        position = savedInstanceState?.getInt(POSITION_SET_KEY) ?: position
    }

    private fun initialiseViewModel() {
        viewModel = ViewModelProviders.of(requireActivity(), viewModelFactory).get(DataSafeViewModel::class.java)
    }

    override fun onDestroy() {
        coroutineContext.cancelChildren()
        super.onDestroy()
    }

    private fun init() {
        setActionBar()
        setupPager()
        loadImages()
    }

    private fun loadImages() {
        if (viewModel.hasExtraUris) {
            loadFromUris()
        } else {
            loadFromDatabase()
        }
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.detail_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    private fun setActionBar() {
        val compatActivity = requireActivity() as AppCompatActivity
        compatActivity.setSupportActionBar(rootView?.detail_toolbar)
        compatActivity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        compatActivity.supportActionBar?.setDisplayShowHomeEnabled(true)
        compatActivity.supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    override fun onResume() {
        super.onResume()
        goFullscreen()
    }

    private fun goDefaultScreen() {
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }

    private fun goFullscreen() {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }

    override fun onPause() {
        goDefaultScreen()
        dialog?.dismiss()
        super.onPause()
    }
    private fun loadFromUris() = launch {
        getImages()
        withContext(Dispatchers.Main) {
            setImages()
        }
    }

    private suspend fun getImages() {
        images = viewModel.getSentImages()
    }

    private fun setImages() {
        if (images.isNotEmpty()) {
            adapter.setContent(images)
            setPosition()
        }
    }


    /**
     * Loads images from database
     */
    private fun loadFromDatabase() {
        viewModel.getImages(true, this, Observer {
            images = ArrayList(it)
            adapter.setContent(images)
            setPosition()
        })
    }

    /**
     * Sets position of image
     */
    private fun setPosition() {
        rootView?.photo_pager?.currentItem = position
    }

    /**
     * Returns position of current image
     */
    private fun getPosition(): Int?  {
        return rootView?.photo_pager?.currentItem
    }

    private fun currentImage(): Image? {
        val position = getPosition()

        if (positionInBounds(position)) {
            return images[position!!]
        }

        return null
    }

    private fun positionInBounds(position: Int?) = position != null && position < images.size && position >= 0

    private fun setupPager() {
        adapter = PhotoAdapter(this)
        rootView?.photo_pager?.adapter = adapter
    }


    /**
     * Loads [image] into the given view
     * @param image to be loaded
     * @param into which view to load
     */
    override fun load(image: Image, into: ImageView) {
        launch {

            val decryptedImage = viewModel.decryptImage(image)

            withContext(Dispatchers.Main) {
                decryptedImage?.let {
                    GlideApp.with(requireContext())
                        .load(decryptedImage)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(into)
                }
            }

        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is FragmentManager) {
            fragmentManager = context
        } else {
            throw RuntimeException("$context must implement FragmentManager")
        }

        if (context is CryptoHandler) {
            cryptoHandler = context
        } else {
            throw RuntimeException("$context must implement EM")
        }
    }

    override fun onDetach() {
        super.onDetach()
        fragmentManager = null
        cryptoHandler = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        position = rootView?.photo_pager?.currentItem ?: 0
        outState.putInt(POSITION_SET_KEY, position)
        super.onSaveInstanceState(outState)
    }


    /**
     * Decrypts current image on disc
     */
    private fun decrypt() {
        currentImage()?.let {
            cryptoHandler?.decrypt(it)
            requireActivity().onBackPressed()
        }

    }

    /**
     * Deletes current image
     */
    private fun delete() {
        currentImage()?.let {
            dialog = DialogUtility.showDialog(requireActivity(), R.string.attention, R.string.delete_detail) {
                viewModel.deleteImages(it)
                requireActivity().onBackPressed()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                requireActivity().onBackPressed()
                true
            }
            R.id.action_decrypt -> {
                decrypt()
                true
            }
            R.id.action_delete -> {
                delete()
                true
            }
            R.id.action_backup -> {
                share()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Shares current image
     */
    private fun share() {
        currentImage()?.let {
            val shareIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND_MULTIPLE
                putExtra(Intent.EXTRA_STREAM, it.getContentUri(requireContext()))
                type = "image/*"
            }
            startActivity(Intent.createChooser(shareIntent, resources.getString(R.string.send)))
        }
    }

}
