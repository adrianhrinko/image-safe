package com.safetica.datasafe.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.safetica.datasafe.R
import com.safetica.datasafe.interfaces.CryptoHandler
import com.safetica.datasafe.interfaces.FragmentManager
import com.safetica.datasafe.service.CryptoService
import com.safetica.datasafe.viewmodel.DataSafeViewModel
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.progress_layout.view.*
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * This class implements user interface for cryptographic operations
 */
class CryptoFragment : Fragment(), CoroutineScope {

    private val job = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    @Inject
    internal lateinit var viewModelFactory: ViewModelProvider.Factory

    private var rootView: View? = null

    private lateinit var viewModel: DataSafeViewModel

    private var fragmentManager: FragmentManager? = null
    private var cryptoHandler: CryptoHandler? = null
    private var intentFilter = IntentFilter(CryptoService.BROADCAST)
    private var encryptionMode = true
    private var photosNo = 0

    companion object {
        private const val MODE_KEY = "MODE"
        private const val PHOTOS_NO_KEY = "PHOTOS_NO"

        @JvmStatic
        fun instance(encryption: Boolean = true, photosNo: Int = 0) =
            CryptoFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(MODE_KEY, encryption)
                    putInt(PHOTOS_NO_KEY, photosNo)
                }
            }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val from = intent?.extras?.getInt(CryptoService.FROM) ?: 0
            val howMany = intent?.extras?.getInt(CryptoService.HOW_MANY) ?: 0
            updateProgress(from, howMany)
        }

    }

    /**
     * Updates progress view
     * @param from how many images in total
     * @param howMany images was encrypted/decrypted ...
     */
    private fun updateProgress(from: Int, howMany: Int) {
        if (from == 0) {
            if (encryptionMode) {

                rootView?.progress?.text = resources.getQuantityString(R.plurals.n_images_encrypted, howMany, howMany)
            } else {
                rootView?.progress?.text = resources.getQuantityString(R.plurals.n_images_decrypted, howMany, howMany)
            }
            rootView?.backBtn?.visibility = View.VISIBLE
            return
        }

        if (howMany == 0) {
            rootView?.progress?.text = "0/$from"
            return
        }

        rootView?.progress?.text = "$howMany/$from"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidSupportInjection.inject(this)
        initialiseViewModel()

        arguments?.let {
            encryptionMode = it.getBoolean(MODE_KEY, true)
            photosNo = it.getInt(PHOTOS_NO_KEY, 0)

        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(R.layout.progress_layout, container, false)
        init()
        return rootView
    }

    private fun initialiseViewModel() {
        viewModel = ViewModelProviders.of(requireActivity(), viewModelFactory).get(DataSafeViewModel::class.java)
    }

    private fun init() {
        requireActivity().registerReceiver(receiver, intentFilter)
        initView()

        if (encryptionMode) {
            encryptImages()
        } else {
            decryptImages()
        }
    }

    /**
     * Decrypts sent images
     */
    private fun decryptImages() = launch {
        val images = viewModel.getSentImages()
        if (images.isNotEmpty()) {
            cryptoHandler?.decrypt(*images.toTypedArray())
        }

    }

    private fun back() {
        requireActivity().onBackPressed()
    }

    private fun initView() {
        setTitle()
        initProgress()
        setListeners()
    }

    private fun setListeners() {
        rootView?.backBtn?.setOnClickListener {
            if (photosNo > 0) {
                back()
            } else {
                requireActivity().finish()
            }
        }
    }

    private fun setTitle() {
        if (encryptionMode) {
            rootView?.progressLayoutTitle?.text = resources.getString(R.string.encrypting)
        } else {
            rootView?.progressLayoutTitle?.text = resources.getString(R.string.decrypt)
        }
    }

    /**
     * Initialises progress view
     */
    private fun initProgress() {
        if (photosNo > 0) {
            rootView?.progress?.text = "0/$photosNo"
        } else {
            rootView?.progress?.text = "0/${viewModel?.sentUris?.size ?: 0}"
        }
    }

    /**
     * Encrypts images sent into the application
     */
    private fun encryptImages() {
        viewModel.sentUris?.let {
            cryptoHandler?.encrypt(*it.toTypedArray())
        }
    }

    override fun onDestroyView() {
        requireActivity().unregisterReceiver(receiver)
        super.onDestroyView()
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

}
