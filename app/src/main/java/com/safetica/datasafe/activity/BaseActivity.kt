package com.safetica.datasafe.activity

import android.Manifest
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.Parcelable
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.safetica.datasafe.R
import com.safetica.datasafe.fragment.*
import com.safetica.datasafe.interfaces.CryptoHandler
import com.safetica.datasafe.interfaces.FragmentManager
import com.safetica.datasafe.interfaces.PermissionsHandler
import com.safetica.datasafe.model.Image
import com.safetica.datasafe.service.CryptoService
import com.safetica.datasafe.viewmodel.DataSafeViewModel
import dagger.android.AndroidInjection
import javax.inject.Inject
import com.safetica.datasafe.utils.HomeWatcher.OnHomePressedListener
import com.safetica.datasafe.utils.HomeWatcher
import kotlinx.coroutines.*
import javax.crypto.SecretKey
import kotlin.coroutines.CoroutineContext
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.safetica.datasafe.BuildConfig

/**
 * Abstract class that implements navigation within app (fragmets) and delegates operations to fragments
 */
abstract class BaseActivity: AppCompatActivity(), FragmentManager, OnHomePressedListener, ServiceConnection, CryptoHandler, CoroutineScope, PermissionsHandler {

    private val job = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    companion object {
        const val LOGGED_IN_KEY = "LOGGED_IN"
    }

    protected var loggedIn = false

    @Inject
    internal lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    internal lateinit var homeWatcher: HomeWatcher


    protected lateinit var viewModel: DataSafeViewModel

    private val screenOffFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)

    /**
     * Reacts on SCREEN_OFF, when the screen goes off. User is logged out of the app.
     */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            logout()
        }
    }

    private val loginFragment = LoginFragment()
    protected val galleryFragment = GalleryFragment()
    protected val encryptionFragment = CryptoFragment.instance(true)
    protected val decryptionFragment = CryptoFragment.instance(false)
    protected val detailFragment = DetailFragment()
    protected val importFragment = ImportFragment()
    private val permissionsFragment = PermissionsFragment()

    private var cryptoService: CryptoService? = null

    private var savedInstance: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_safe)

        if (!BuildConfig.DEBUG) {
            setSecure()
        }

        AndroidInjection.inject(this)

        restoreState(savedInstanceState)
        setListeners()
        initViewModel()
        handleIncomingIntent()

        viewModel.tokenSetEvent(this, Observer {
            if (it == null) {
                showFragment(loginFragment, false)
            } else {
                proceed(savedInstanceState)
            }
        })

        bindService()

    }

    /**
     * Forbids taking screenshots above app
     */
    private fun setSecure() {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /**
     * Restores saved state
     */
    private fun restoreState(savedInstanceState: Bundle?) {
        savedInstance = savedInstanceState
        loggedIn = savedInstanceState?.getBoolean(LOGGED_IN_KEY) ?: false
    }

    /**
     * Connects activity to CryptoService
     */
    private fun bindService() {
        val intent = Intent(this, CryptoService::class.java)
        bindService(intent, this, Context.BIND_AUTO_CREATE)
    }

    private fun setListeners() {
        homeWatcher.setOnHomePressedListener(this)
        registerReceiver(screenOffReceiver, screenOffFilter)
    }

    /**
     * Called after successful authentication
     */
    private fun proceed(savedInstanceState: Bundle?) {
        if (allPermissionsGranted()) {
            onLoggedIn(savedInstanceState)
            loggedIn = true
        } else {
            showFragment(permissionsFragment, false)
        }
    }

    /**
     * @return true if all needed permissions are granted, false otherwise
     */
    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }


    /**
     * Called when all permissions are granted
     */
    override fun onPermissionsGranted() {
        onLoggedIn(savedInstance)
        loggedIn = true
    }

    /**
     * Handles intent message incoming from other apps
     */
    private fun handleIncomingIntent() {
        when (intent.action) {
            Intent.ACTION_SEND -> handleActionSend()
            Intent.ACTION_SEND_MULTIPLE -> handleActionSendMultiple()
        }
    }

    /**
     * Handles action send multiple incoming from other apps (gets image uris from intent message)
     */
    private fun handleActionSendMultiple() {
        if (intent.type?.startsWith("image/") == true) {
            intent?.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.let { list ->
                viewModel.processImageUris(*list.toTypedArray())
            }
        }
    }

    /**
     * Handles action send incoming from other apps (gets image uri from intent message)
     */
    private fun handleActionSend() {
        if (intent.type?.startsWith("image/") == true) {
            intent?.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM)?.let {
                viewModel.processImageUris(it)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        homeWatcher.startWatch()
    }

    override fun onStop() {
        homeWatcher.stopWatch()
        super.onStop()
    }

    private fun initViewModel() {
        viewModel = ViewModelProviders.of(this, viewModelFactory).get(DataSafeViewModel::class.java)
    }

    /**
     * Shows given [fragment] and adds it into [backstack] if true
     * @param fragment to show
     * @param backstack - fragment is added to the backstack if true
     */
    override fun showFragment(fragment: Fragment, backstack: Boolean) {
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.fragmentContainer, fragment)
        fragmentTransaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)

        if (backstack) {
            fragmentTransaction.addToBackStack(null)
        }

        fragmentTransaction.commit()
    }


    override fun onHomePressed() {
        logout()
    }

    override fun onHomeLongPressed() {
        logout()
    }


    private fun logout() {
        loggedIn = false
        viewModel.unsetToken()
    }


    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putBoolean(LOGGED_IN_KEY, loggedIn)
    }

    override fun onServiceConnected(componentName: ComponentName?, binder: IBinder?) {
        val musicServiceBinder = binder as CryptoService.EncryptionServiceBinder
        cryptoService = musicServiceBinder.service
    }

    override fun onServiceDisconnected(p0: ComponentName?) {
        cryptoService = null
    }

    override fun onDestroy() {
        unbindService(this)
        unregisterReceiver(screenOffReceiver)
        super.onDestroy()
    }

    /**
     * Calls method encryptImages(...) from CryptoService with given parameters
     * Encrypts images with given [uris]
     * @param uris of images to be encrypted
     */
    override fun encrypt(vararg uris: Uri) {
        viewModel.tokenValue?.let {
            cryptoService?.encryptImages(it, *uris)
        }
    }

    /**
     * Calls method decryptImages(...) from CryptoService with given parameters
     * Decrypts given [images]
     * @param images to be decrypted
     */
    override fun decrypt(vararg images: Image) {
        viewModel.tokenValue?.let {
            cryptoService?.decryptImages(it, *images)
        }
    }

    /**
     * Calls method importImages(...) from CryptoService with given parameters
     * Imports images with given [uris]
     * @param token which has been used for encryption of images
     * @param uris of images to be imported
     */
    override fun import(token: SecretKey, vararg uris: Uri) {
        viewModel.tokenValue?.let {
            cryptoService?.importImages(token, it, *uris)
        }
    }

    /**
     * Calls method rewriteKeys() from CryptoService with given parameters
     * Rewrites keys of all protected images
     * @param oldToken that has been used for encryption
     * @param newToken that is going to be used from now
     */
    override fun rewriteKeys(oldToken: SecretKey, newToken: SecretKey) {
        loggedIn = false
        launch {
            val images = viewModel.getImages(true)
            withContext(Dispatchers.Main) {
                cryptoService?.rewriteKeys(oldToken, newToken, *images.toTypedArray())
            }
        }
    }


    /**
     * Every children should implement this function
     * It is called after successful authentication and should show some other fragment
     */
    abstract fun onLoggedIn(savedInstanceState: Bundle?)
}