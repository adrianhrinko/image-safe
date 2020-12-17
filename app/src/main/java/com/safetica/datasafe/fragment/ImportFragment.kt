package com.safetica.datasafe.fragment


import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.safetica.datasafe.R
import com.safetica.datasafe.extensions.getUris
import com.safetica.datasafe.interfaces.CryptoHandler
import com.safetica.datasafe.interfaces.FragmentManager
import com.safetica.datasafe.interfaces.ILoginManager
import com.safetica.datasafe.utils.FileUtility
import com.safetica.datasafe.service.CryptoService
import com.safetica.datasafe.viewmodel.DataSafeViewModel
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.fragment_import.view.*
import kotlinx.android.synthetic.main.password_view.view.*
import kotlinx.android.synthetic.main.progress_layout.view.*
import kotlinx.coroutines.*
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * This class implements user interface for importing photos
 */
class ImportFragment : Fragment(), CoroutineScope {

    private val job = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job


    @Inject
    internal lateinit var viewModelFactory: ViewModelProvider.Factory

    private var rootView: View? = null

    private lateinit var viewModel: DataSafeViewModel

    @Inject
    internal lateinit var loginManager: ILoginManager

    private var intentFilter = IntentFilter(CryptoService.BROADCAST)

    private var fragmentManager: FragmentManager? = null
    private var cryptoHandler: CryptoHandler? = null

    private var fromApp: Boolean = false

    private var pickedUris: ArrayList<Uri>? = null

    private var passwordEntered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val from = intent?.extras?.getInt(CryptoService.FROM) ?: 0
            val howMany = intent?.extras?.getInt(CryptoService.HOW_MANY) ?: 0
            updateProgress(from, howMany)
        }

    }

    companion object {
        private const val MODE_KEY = "MODE2"
        private const val PICK_IMAGE = 32568
        private const val CHOSEN_URIS_KEY = "CHOSEN_URIS"
        private const val PASS_ENTERED_KEY = "PASS_ENTERED"

        @JvmStatic
        fun instance(fromApp: Boolean = false) =
            ImportFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(MODE_KEY, fromApp)
                }
            }
    }

    /**
     * Updates progress view
     * @param from how many images in total
     * @param howMany images was imported
     */
    private fun updateProgress(from: Int, howMany: Int) {
        if (from == 0) {
            rootView?.progress?.text = resources.getQuantityString(R.plurals.n_images_imported, howMany, howMany)
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
            fromApp = it.getBoolean(MODE_KEY, false)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        restoreState(savedInstanceState)
        rootView = inflater.inflate(R.layout.fragment_import, container, false)
        init()
        return rootView
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        pickedUris = savedInstanceState?.getParcelableArrayList(CHOSEN_URIS_KEY)
        passwordEntered = savedInstanceState?.getBoolean(PASS_ENTERED_KEY, false) ?: false
    }

    private fun init() {
        initView()
        requireActivity().registerReceiver(receiver, intentFilter)
        if (fromApp) {
            pickImages()
            enterPass()
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        pickedUris?.let {
            savedInstanceState.putParcelableArrayList(CHOSEN_URIS_KEY, pickedUris)
        }
        savedInstanceState.putBoolean(PASS_ENTERED_KEY, passwordEntered)
        super.onSaveInstanceState(savedInstanceState)
    }

    /**
     * Called after click on enter button
     */
    private fun enterPass() {
        if (passwordEntered) {
            showProgress()
        } else {
            showPassEntry()
        }
    }

    private fun showPassEntry() {
        rootView?.password_layout_import?.visibility = View.VISIBLE
        rootView?.progress_layout_import?.visibility = View.GONE
        rootView?.loader_layout_import?.visibility = View.GONE
    }

    private fun initView() {
        setText()
        setListeners()
    }

    private fun setText() {
        rootView?.progressLayoutTitle?.text = resources.getString(R.string.importing)
        rootView?.loginFragmentTitle?.text = resources.getText(R.string.enter_password)
        rootView?.loginFragmentDetail?.text = resources.getText(R.string.import_password_detail)
    }

    private fun setListeners() {
        rootView?.loginButton?.setOnClickListener {
            rootView?.passwordEntry?.text.let {
                val pass = it.toString()
                if (checkInput(pass)) {
                    tryImport(it.toString())
                    showLoader()
                }
            }
        }

        rootView?.backBtn?.setOnClickListener {
            if (fromApp) {
                back()
            } else {
                finish()
            }
        }
    }

    /**
     * Checks [input] if it is valid
     * @param input to be checked
     * @return true if valid, false otherwise
     */
    private fun checkInput(input: String): Boolean {
        if (input.isEmpty()) {
            return false
        }

        if (input.length > 256) {
            return false
        }

        return true
    }

    private fun finish() {
        requireActivity().finish()
    }

    private fun back() {
        requireActivity().onBackPressed()
    }

    /**
     * Opens gallery app to pick images
     */
    private fun pickImages() {
        if (pickedUris == null) {
            FileUtility.pickFile(
                "image/*", true,
                resources.getString(R.string.pick_images), this, PICK_IMAGE
            )
        }
    }

    private fun showLoader() {
        rootView?.password_layout_import?.visibility = View.GONE
        rootView?.progress_layout_import?.visibility = View.GONE
        rootView?.loader_layout_import?.visibility = View.VISIBLE
    }

    /**
     * Shows progress view
     */
    private fun showProgress() {
        rootView?.password_layout_import?.visibility = View.GONE
        rootView?.loader_layout_import?.visibility = View.GONE
        rootView?.progress_layout_import?.visibility = View.VISIBLE
    }

    private fun tryImport(password: String) = launch {
        val token = loginManager.generateToken(password)
        withContext(Dispatchers.Main) {
            passwordEntered = true
            showProgress()
            import(token)
        }
    }

    /**
     * Imports images
     * @param token that was used for encryption of images to be imported
     */
    private fun import(token: SecretKeySpec): Unit? {
        return if (fromApp) {
            pickedUris?.let {
                cryptoHandler?.import(token, *it.toTypedArray())
            }
        } else {
            viewModel.sentUris?.let {
                cryptoHandler?.import(token, *it.toTypedArray())
            }
        }
    }

    private fun initialiseViewModel() {
        viewModel = ViewModelProviders.of(requireActivity(), viewModelFactory).get(DataSafeViewModel::class.java)
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

    override fun onDestroyView() {
        requireActivity().unregisterReceiver(receiver)
        super.onDestroyView()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE) {
            if (resultCode == Activity.RESULT_OK) {
                importPicked(data)
                return
            }
            requireActivity().supportFragmentManager.popBackStack()
        }

    }

    /**
     * Imports picked images
     */
    private fun importPicked(data: Intent?) {
        pickedUris = data?.getUris()
    }

}
