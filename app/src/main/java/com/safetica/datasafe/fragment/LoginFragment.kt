package com.safetica.datasafe.fragment

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_login.view.*
import kotlinx.android.synthetic.main.password_view.view.*

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.safetica.datasafe.BuildConfig

import com.safetica.datasafe.viewmodel.DataSafeViewModel
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.*
import javax.inject.Inject
import android.os.CountDownTimer
import com.safetica.datasafe.enums.PassStrength
import com.safetica.datasafe.extensions.copy
import com.safetica.datasafe.interfaces.CryptoHandler
import com.safetica.datasafe.interfaces.ILoginManager
import com.safetica.datasafe.manager.PreferenceManager
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.CoroutineContext
import com.safetica.datasafe.R
import android.view.inputmethod.EditorInfo
import android.view.*
import android.view.inputmethod.InputMethodManager


/**
 * This class implements user interface for password setup and login
 */
class LoginFragment : Fragment(), CoroutineScope {


    private val job = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job


    @Inject
    internal lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    internal lateinit var preferenceManager: PreferenceManager

    @Inject
    internal lateinit var loginManager: ILoginManager

    private lateinit var viewModel: DataSafeViewModel

    private var rootView: View? = null

    private var passwordSetupMode = false

    private var confirmation: String? = null

    private var passStrength = PassStrength.Weak

    private var tries = 3

    private var passwordChangeMode = false

    private var cryptoHandler: CryptoHandler? = null

    private var notAuthorized = true

    companion object {
        private const val MODE_KEY = "MODE3"
        private const val CONFIRMATION_KEY = "Confirmation"
        private const val AUTHORIZED_KEY = "authorized"
        private const val TIMER_SECONDS = 30L

        @JvmStatic
        fun instance(changePassword: Boolean = false) =
            LoginFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(MODE_KEY, changePassword)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidSupportInjection.inject(this)
        initialiseViewModel()

        arguments?.let {
            passwordChangeMode = it.getBoolean(MODE_KEY, false)
        }
    }

    private fun initialiseViewModel() {
        viewModel = ViewModelProviders.of(requireActivity(), viewModelFactory).get(DataSafeViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        restoreState(savedInstanceState)
        rootView = inflater.inflate(R.layout.fragment_login, container, false)
        init()
        return rootView
    }

    /**
     * Restores saved data
     */
    private fun restoreState(savedInstanceState: Bundle?) {
        confirmation = savedInstanceState?.getString(CONFIRMATION_KEY)
        notAuthorized = savedInstanceState?.getBoolean(AUTHORIZED_KEY, true) ?: true
    }

    private fun showLoader() {
        rootView?.passwordEntryLayout?.visibility = View.GONE
        rootView?.passwordLoaderLayout?.visibility = View.VISIBLE
    }

    private fun hideLoader() {
        rootView?.passwordLoaderLayout?.visibility = View.GONE
        rootView?.passwordEntryLayout?.visibility = View.VISIBLE
    }

    /**
     * Initialises view
     */
    private fun init() {
        runTimerIfNeeded()
        setTriesNoIfNeeded()
        setMode()
        setListeners()
        showKeyboard()
    }

    private fun showKeyboard() {
        requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        rootView?.passwordEntry?.requestFocus()
    }

    /**
     * Sets mode - password setup/login according to database state
     */
    private fun setMode() = launch {
        val isPassSet = loginManager.isPasswordSet()
        withContext(Dispatchers.Main) {
            if (isPassSet && notAuthorized) {
                setLoginMode()
            } else {
                setPasswordSetupMode()
            }
        }
    }


    private fun setListeners() {
        rootView?.loginButton?.setOnClickListener { processPassword() }
        rootView?.passwordEntry?.setOnEditorActionListener { _, actionId, _ ->
            var handled = false
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                processPassword()
                handled = true
            }
            handled
        }
    }

    private fun hideKeyboard() {
        val view = activity?.currentFocus
        view?.let { v ->
            val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }

    private fun setTriesNoIfNeeded() {
        val savedTries = savedTries()
        if (savedTries < tries) {
            tries = savedTries
            showInvalidPasswordMessage()
        }
    }

    private fun runTimerIfNeeded() {
        val timerSeconds = preferenceManager.loginTimerSecondsRemain

        if (timerSeconds > 0) {
            showTimer(timerSeconds)
        }
    }

    /**
     * Processes password after click on enter button
     */
    private fun processPassword() {
        val password = rootView?.passwordEntry?.text.toString()

        val correct = checkInput(password)
        if (correct) {
            rootView?.passwordEntry?.text?.clear()
            if (passwordSetupMode) {
                setPassword(password)
            } else {
                validatePassword(password)
            }
        }

    }

    /**
     * Tries to set password
     */
    private fun setPassword(password: String) {
        if (confirmation != null) {
            showLoader()
            launch {
                val token = loginManager.setPassword(password, confirmation!!)

                withContext(Dispatchers.Main) {
                    passwordSet(token)
                }
            }

        } else {
            confirmation = password
            rootView?.loginFragmentDetail?.text = resources.getString(R.string.confirm_password)
        }
    }

    /**
     * Calls if password setup is successful
     */
    private fun passwordSet(token: SecretKeySpec?) {
        if (token != null) {
            proceed(token)
        } else {
            rollback()
            hideLoader()
        }
    }

    /**
     * Calls if password setup is not successful
     */
    private fun rollback() {
        rootView?.loginFragmentDetail?.text = resources.getString(R.string.password_setup_detail)
        rootView?.passwordDetail?.text = resources.getString(R.string.passwords_not_same)
        rootView?.passwordDetail?.visibility = View.VISIBLE
        confirmation = null
    }

    /**
     * Sets the token variable in DataSafeViewmodel
     * @param token to be set
     */
    private fun proceed(token: SecretKey) {
        resetTriesAndTimer()
        confirmation = null
        rewriteKeys(token)
        viewModel.setToken(token)
    }

    /**
     * Rewrite keys in files which contain images, sets new token
     * @param token to be set
     */
    private fun rewriteKeys(token: SecretKey) {
        if (passwordChangeMode && passwordSetupMode) {
            viewModel.tokenValue?.let {
                cryptoHandler?.rewriteKeys(it.copy(), token)
            }
        }
    }

    /**
     * Validates [password], shows another fragment if correct or error message if incorrect
     * @param password
     */
    private fun validatePassword(password: String) {
        showLoader()
        GlobalScope.launch {
            val token = loginManager.validatePassword(password)


            withContext(Dispatchers.Main) {
                if (token != null) {
                    onCorrectPassword(token)
                } else {
                    onInvalidPassword()
                }
            }
        }
    }

    /**
     * Calls if entered password is correct
     * @param token to be set
     */
    private fun onCorrectPassword(token: SecretKey) {
        if (passwordChangeMode) {
            changePassword()
        } else {
            proceed(token)
        }
    }

    /**
     * Switches to password change mode
     */
    private fun changePassword() {
        hideLoader()
        notAuthorized = false
        setPasswordSetupMode()
    }

    /**
     * Called when given password is incorrect
     */
    private fun onInvalidPassword() {
        hideLoader()
        tries--
        preferenceManager.loginPasswordTriesRemain = tries
        if (tries <= 0) {
            showTimer(TIMER_SECONDS)
        } else {
            showInvalidPasswordMessage()
        }

    }

    /**
     * Shows timer in case that incorrect password has been submitted for 3. time
     * @param duration in seconds
     */
    private fun showTimer(duration: Long) {
        rootView?.passwordDetail?.visibility = View.GONE
        rootView?.countDownView?.setTimerDigits(TIMER_SECONDS)
        rootView?.passwordEntryLayout?.visibility = View.GONE
        rootView?.countDownView?.visibility = View.VISIBLE

        object : CountDownTimer(duration * 1000L, 1000) {

            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                preferenceManager.loginTimerSecondsRemain = seconds
                rootView?.countDownView?.setTimerDigits(seconds)
            }

            override fun onFinish() {
                resetTriesAndTimer()
                hideTimer()
            }
        }.start()

    }

    private fun hideTimer() {
        rootView?.countDownView?.visibility = View.GONE
        rootView?.passwordEntryLayout?.visibility = View.VISIBLE
        rootView?.passwordDetail?.visibility = View.INVISIBLE

    }

    /**
     * Resets timer and tries
     */
    private fun resetTriesAndTimer() {
        preferenceManager.loginTimerSecondsRemain = 0
        preferenceManager.loginPasswordTriesRemain = 3
        tries = 3
    }

    private fun savedTries(): Int {
        return preferenceManager.loginPasswordTriesRemain
    }

    /**
     * Sets the ui for password setup
     */
    private fun setPasswordSetupMode() {
        passwordSetupMode = true
        rootView?.loginFragmentTitle?.text = resources.getString(R.string.password_setup)
        rootView?.loginFragmentDetail?.text = resources.getString(R.string.password_setup_detail)
        rootView?.passwordDetail?.setTextColor(resources.getColor(R.color.colorCritical))
        setPasswordStrengthChecker()
    }

    /**
     * Registers password strength checker on password edit text component
     */
    private fun setPasswordStrengthChecker() {
        rootView?.passwordEntry?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrEmpty()) {
                    updatePasswordStrength(s)
                }
            }
        })
    }

    /**
     * Updates password strength in ui according [pass] provided
     * @param pass to be assessed
     *
     */
    private fun updatePasswordStrength(pass: CharSequence) {
        passStrength = loginManager.getPassphraseStrength(pass.toString())
        showPasswordStrength()
    }

    /**
     * Displays password strength
     */
    private fun showPasswordStrength() {
        when (passStrength) {
            PassStrength.Weak -> {
                rootView?.passwordDetail?.text = resources.getString(R.string.weak)
                rootView?.passwordDetail?.setTextColor(resources.getColor(R.color.colorCritical))
            }
            PassStrength.Medium -> {
                rootView?.passwordDetail?.text = resources.getString(R.string.sufficient)
                rootView?.passwordDetail?.setTextColor(resources.getColor(R.color.colorWarning))
            }
            PassStrength.Strong -> {
                rootView?.passwordDetail?.text = resources.getString(R.string.safe)
                rootView?.passwordDetail?.setTextColor(resources.getColor(R.color.colorSafe))
            }
        }
        rootView?.passwordDetail?.visibility = View.VISIBLE
    }

    /**
     * Switches ui into login mode
     */
    private fun setLoginMode() {
        rootView?.loginFragmentTitle?.text = resources.getString(R.string.login)
        rootView?.loginFragmentDetail?.text = resources.getString(R.string.login_detail)
        passwordSetupMode = false
    }

    private fun showInvalidPasswordMessage() {
        rootView?.passwordDetail?.text = resources.getQuantityString(R.plurals.incorrect_password, tries, tries)
        rootView?.passwordDetail?.setTextColor(resources.getColor(R.color.colorCritical))
        rootView?.passwordDetail?.visibility = View.VISIBLE
    }

    /**
     * Checks input if it is valid
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

        if (!BuildConfig.DEBUG) {
            if (passwordSetupMode && passStrength == PassStrength.Weak) {
                return false
            }
        }
        return true
    }


    override fun onDestroy() {
        coroutineContext.cancelChildren()
        super.onDestroy()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (context is CryptoHandler) {
            cryptoHandler = context
        } else {
            throw RuntimeException("$context must implement EM")
        }
    }

    override fun onDetach() {
        super.onDetach()
        cryptoHandler = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(CONFIRMATION_KEY, confirmation)
        outState.putBoolean(AUTHORIZED_KEY, notAuthorized)
        super.onSaveInstanceState(outState)
    }

}
