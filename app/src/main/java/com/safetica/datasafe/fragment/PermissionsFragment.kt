package com.safetica.datasafe.fragment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_permissions.view.*
import com.safetica.datasafe.R
import com.safetica.datasafe.interfaces.PermissionsHandler
import com.safetica.datasafe.utils.DialogUtility

/**
 * This class implements user interface for permissions handling
 */
class PermissionsFragment : Fragment() {

    private var rootView: View? = null
    private var permissionsHandler: PermissionsHandler? = null

    companion object {
        private const val PERMISSION_REQUEST = 34765
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        rootView = inflater.inflate(R.layout.fragment_permissions, container, false)
        init()
        return rootView
    }

    private  fun init() {
        rootView?.grantPermissionsButton?.setOnClickListener {
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        requestPermissions(
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode  != PERMISSION_REQUEST)
            return

        var allGranted = true

        grantResults.forEach {
            if (it == PackageManager.PERMISSION_DENIED)
                allGranted = false
        }

        if (allGranted) {
            permissionsHandler?.onPermissionsGranted()
        } else {
            DialogUtility.showDialog(requireActivity(), R.string.attention, R.string.all_permissions_must_be_granted) {
                requestPermissions()
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is PermissionsHandler) {
            permissionsHandler = context
        } else {
            throw RuntimeException("$context must implement PermissionsHandler")
        }
    }

    override fun onDetach() {
        permissionsHandler = null
        super.onDetach()
    }

}
