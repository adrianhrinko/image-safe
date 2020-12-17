package com.safetica.datasafe.fragment

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.widget.ImageView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.safetica.datasafe.R
import com.safetica.datasafe.viewmodel.DataSafeViewModel
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.safetica.datasafe.adapter.GalleryAdapter
import com.safetica.datasafe.glide.GlideApp
import com.safetica.datasafe.interfaces.ClickListener
import com.safetica.datasafe.interfaces.FragmentManager
import com.safetica.datasafe.interfaces.ImageLoader
import com.safetica.datasafe.model.Image
import kotlinx.android.synthetic.main.fragment_gallery.view.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import android.content.Intent
import android.net.Uri
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.davidecirillo.multichoicerecyclerview.MultiChoiceAdapter
import com.davidecirillo.multichoicerecyclerview.MultiChoiceToolbar
import com.google.android.material.navigation.NavigationView
import com.safetica.datasafe.extensions.getUris
import com.safetica.datasafe.interfaces.CryptoHandler
import com.safetica.datasafe.utils.FileUtility
import com.safetica.datasafe.utils.CameraUtility
import com.safetica.datasafe.utils.DialogUtility
import kotlin.collections.ArrayList


/**
 * This class implements user interface of gallery
 */
class GalleryFragment : Fragment(), ImageLoader, ClickListener, CoroutineScope, NavigationView.OnNavigationItemSelectedListener, MultiChoiceAdapter.Listener {


    private val job = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    companion object {
        private const val PICK_IMAGE = 42564
        private const val TAKE_PHOTO = 42565
    }

    @Inject
    internal lateinit var viewModelFactory: ViewModelProvider.Factory

    private var rootView: View? = null

    private lateinit var viewModel: DataSafeViewModel

    private lateinit var adapter: GalleryAdapter

    private var fragmentManager: FragmentManager? = null
    private var cryptoHandler: CryptoHandler? = null

    private var takenPhotoUri: Uri? = null

    private var dialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidSupportInjection.inject(this)
        setHasOptionsMenu(true)
        initialiseViewModel()

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(R.layout.fragment_gallery, container, false)
        init()
        return rootView
    }

    /**
     * Implements menu navigation
     * @param item that has been tapped
     */
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.drawer_import -> {
                fragmentManager?.showFragment(ImportFragment.instance(true), true)
                true
            }
            R.id.drawer_change_pass -> {
                dialog = DialogUtility.showDialog(requireActivity(), R.string.attention, R.string.password_change_warning) {
                    fragmentManager?.showFragment(LoginFragment.instance(true), false)
                }

                true
            }
            else -> false
        }
    }

    private fun initialiseViewModel() {
        viewModel = ViewModelProviders.of(requireActivity(), viewModelFactory).get(DataSafeViewModel::class.java)
    }



    private fun init() {
        setActionBar()
        setupGalleryGrid()
        setListeners()
        loadImages()
    }

    private fun loadImages() {
        if (viewModel.hasExtraUris) {
            loadFromUris()
        } else {
            loadFromDatabase()
        }
    }

    private fun setListeners() {
        rootView?.nav_view?.setNavigationItemSelectedListener(this)

        rootView?.fab_file?.setOnClickListener {
            FileUtility.pickFile("image/*", true,
                resources.getString(R.string.pick_images), this, PICK_IMAGE)
        }

        rootView?.fab_camera?.setOnClickListener {
            takenPhotoUri = CameraUtility.takePhoto(this, TAKE_PHOTO)
        }
    }

    /**
     * Loads images from uris that has been sent to the app
     */
    private fun loadFromUris() = launch {
        val images = viewModel.getSentImages()
        withContext(Dispatchers.Main) {
            if (images.isNotEmpty()) {
                    adapter.setContent(images)
            }
        }
    }


    /**
     * Loads images from the database
     */
    private fun loadFromDatabase() {
        viewModel.getImages(true, this, Observer {
            showEmptyMessageIfNeeded(it)
            adapter.setContent(ArrayList(it))
        })
    }

    private fun showEmptyMessageIfNeeded(it: List<Image>) {
        if (it.isEmpty()) {
            showEmptyMessage()
        } else {
            hideEmptyMessage()
        }
    }

    private fun showEmptyMessage() {
        rootView?.empty_msg?.visibility = View.VISIBLE
    }

    private fun hideEmptyMessage() {
        rootView?.empty_msg?.visibility = View.GONE
    }


    private fun setupGalleryGrid() {
        val recyclerView = rootView?.galleryGrid
        val layoutManager = GridLayoutManager(requireContext(), 3)
        recyclerView?.layoutManager = layoutManager
        adapter = GalleryAdapter( this)
        adapter.setOnClickListener(this)
        adapter.setMultiChoiceSelectionListener(this)
        adapter.setMultiChoiceToolbar(getToolbar())
        adapter.selectAll()
        recyclerView?.adapter = adapter
    }

    private fun getToolbar(): MultiChoiceToolbar? {
        return MultiChoiceToolbar.Builder(requireActivity() as AppCompatActivity, rootView?.gallery_toolbar)
            .setDefaultIcon(R.drawable.drawer_icon) {
                rootView?.drawer_layout?.openDrawer(GravityCompat.START)
            }
            .build()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.clear()
        if (adapter.selectedItemCount > 0) {
            requireActivity().menuInflater.inflate(R.menu.detail_menu, menu)
        } else {
            requireActivity().menuInflater.inflate(R.menu.gallery_menu, menu)
        }
        super.onPrepareOptionsMenu(menu)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {rootView?.drawer_layout?.openDrawer(GravityCompat.START); true }
            R.id.action_select_all -> {adapter.selectAll(); true}
            R.id.action_delete -> {deleteSelected(); true}
            R.id.action_decrypt -> {decryptSelected(); true}
            R.id.action_backup -> {sendSelectedItems(); true}
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() {
        super.onPause()
        dialog?.dismiss()
    }

    /**
     * Decrypts selected photos
     */
    private fun decryptSelected() {
        cryptoHandler?.decrypt(*adapter.selectedImages)
        fragmentManager?.showFragment(CryptoFragment.instance(false, adapter.selectedItemCount), true)
        adapter.deselectAll()
    }

    /**
     * Deletes selected photos
     */
    private fun deleteSelected() {
        FileUtility.delete(requireActivity(), *adapter.selectedImages)
        dialog = DialogUtility.showDialog(requireActivity(), R.string.attention, R.string.delete_detail) {
            viewModel.deleteImages(*adapter.selectedImages)
            adapter.deselectAll()
        }
    }

    /**
     * Shares selected photos
     */
    private fun sendSelectedItems() {
        val shareIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, adapter.selectedUris(requireContext()))
            type = "image/*"
        }
        startActivity(Intent.createChooser(shareIntent, resources.getString(R.string.send)))
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
       inflater.inflate(R.menu.gallery_menu, menu)
       super.onCreateOptionsMenu(menu, inflater)
   }

   private fun setActionBar() {
       val compatActivity = requireActivity() as AppCompatActivity
       compatActivity.setSupportActionBar(rootView?.gallery_toolbar)
       compatActivity.supportActionBar?.setHomeAsUpIndicator(R.drawable.drawer_icon)
       compatActivity.supportActionBar?.setHomeButtonEnabled(true)
       compatActivity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
       compatActivity.supportActionBar?.setDisplayShowTitleEnabled(false)
   }

   override fun onSaveInstanceState(savedInstanceState: Bundle) {
       (rootView?.galleryGrid?.adapter as MultiChoiceAdapter).onSaveInstanceState(savedInstanceState)
       super.onSaveInstanceState(savedInstanceState)
   }

   override fun onViewStateRestored(savedInstanceState: Bundle?) {
       (rootView?.galleryGrid?.adapter as MultiChoiceAdapter).onRestoreInstanceState(savedInstanceState)
       super.onViewStateRestored(savedInstanceState)
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
                   GlideApp.with(this@GalleryFragment)
                       .load(decryptedImage)
                       .diskCacheStrategy(DiskCacheStrategy.NONE)
                       .into(into)
               }
           }
       }
   }


    override fun onImageClicked(position: Int, image: Image) {
       fragmentManager?.showFragment(DetailFragment.instance(position), true)
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

   override fun onDestroy() {
       coroutineContext.cancelChildren()
       super.onDestroy()
   }


   override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
       super.onActivityResult(requestCode, resultCode, data)

       if (requestCode == PICK_IMAGE) {
           encryptPicked(data)
       }

       if (requestCode == TAKE_PHOTO ) {
           encryptTaken()
       }

   }

    /**
     * Encrypt photo taken by camera
     */
   private fun encryptTaken() {
       takenPhotoUri?.let {
           cryptoHandler?.encrypt(it)
       }
   }

    /**
     * Encrypt images that has been picked
     * @param data which contains uris to images to be encrypted
     */
    private fun encryptPicked(data: Intent?) {
       val uris = data?.getUris()
       uris?.let {
           cryptoHandler?.encrypt(*uris.toTypedArray())
           fragmentManager?.showFragment(CryptoFragment.instance(true, it.size), true)
       }
   }

    override fun OnDeselectAll(itemSelectedCount: Int, allItemCount: Int) {
        requireActivity().invalidateOptionsMenu()
    }

    override fun OnSelectAll(itemSelectedCount: Int, allItemCount: Int) {
        requireActivity().invalidateOptionsMenu()
    }

    override fun OnItemSelected(selectedPosition: Int, itemSelectedCount: Int, allItemCount: Int) {
        requireActivity().invalidateOptionsMenu()
    }

    override fun OnItemDeselected(deselectedPosition: Int, itemSelectedCount: Int, allItemCount: Int) {
        requireActivity().invalidateOptionsMenu()
    }


}
