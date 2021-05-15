/*
 * Copyright (C) 2014-2021 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.amaze.filemanager.ui.fragments

import android.app.Activity.RESULT_OK
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES.LOLLIPOP
import android.os.Build.VERSION_CODES.M
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.provider.Settings
import android.text.InputType
import android.text.Spanned
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT
import androidx.fragment.app.Fragment
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.getActionButton
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.files.folderChooser
import com.afollestad.materialdialogs.input.input
import com.amaze.filemanager.R
import com.amaze.filemanager.application.AppConfig
import com.amaze.filemanager.asynchronous.services.ftp.FtpService
import com.amaze.filemanager.asynchronous.services.ftp.FtpService.Companion.getLocalInetAddress
import com.amaze.filemanager.asynchronous.services.ftp.FtpService.Companion.isConnectedToLocalNetwork
import com.amaze.filemanager.asynchronous.services.ftp.FtpService.Companion.isConnectedToWifi
import com.amaze.filemanager.asynchronous.services.ftp.FtpService.Companion.isEnabledWifiHotspot
import com.amaze.filemanager.asynchronous.services.ftp.FtpService.Companion.isRunning
import com.amaze.filemanager.asynchronous.services.ftp.FtpService.FtpReceiverActions
import com.amaze.filemanager.databinding.DialogFtpLoginBinding
import com.amaze.filemanager.databinding.FragmentFtpBinding
import com.amaze.filemanager.filesystem.files.CryptUtil
import com.amaze.filemanager.ui.activities.MainActivity
import com.amaze.filemanager.ui.notifications.FtpNotification
import com.amaze.filemanager.ui.theme.AppTheme
import com.amaze.filemanager.utils.OneCharacterCharSequence
import com.amaze.filemanager.utils.Utils
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.*

/**
 * Created by yashwanthreddyg on 10-06-2016. Edited by Luca D'Amico (Luca91) on 25 Jul 2017 (Fixed
 * FTP Server while usi
 */
@Suppress("TooManyFunctions")
class FtpServerFragment : Fragment(R.layout.fragment_ftp) {

    private val statusText: TextView get() = binding.textViewFtpStatus
    private val url: TextView get() = binding.textViewFtpUrl
    private val username: TextView get() = binding.textViewFtpUsername
    private val password: TextView get() = binding.textViewFtpPassword
    private val port: TextView get() = binding.textViewFtpPort
    private val sharedPath: TextView get() = binding.textViewFtpPath
    private val ftpBtn: Button get() = binding.startStopButton
    private val ftpPasswordVisibleButton: ImageButton get() = binding.ftpPasswordVisible
    private var accentColor = 0
    private var spannedStatusNoConnection: Spanned? = null
    private var spannedStatusConnected: Spanned? = null
    private var spannedStatusUrl: Spanned? = null
    private var spannedStatusSecure: Spanned? = null
    private var spannedStatusNotRunning: Spanned? = null
    private var snackbar: Snackbar? = null

    private var _binding: FragmentFtpBinding? = null
    private val binding get() = _binding!!

    private val mainActivity: MainActivity get() = requireActivity() as MainActivity

    @Suppress("LabeledExpression")
    private val activityResultHandler = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK && Build.VERSION.SDK_INT >= LOLLIPOP) {
            val directoryUri = it.data?.data ?: return@registerForActivityResult
            requireContext().contentResolver.takePersistableUriPermission(
                directoryUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            changeFTPServerPath(directoryUri.toString())
            updatePathText()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        retainInstance = true
        mainActivity.appbar.setTitle(R.string.ftp)
        mainActivity.fab.hide()
        mainActivity.appbar.bottomBar.setVisibility(View.GONE)
        mainActivity.invalidateOptionsMenu()
        val skin_color = mainActivity.currentColorPreference.primaryFirstTab
        val skinTwoColor = mainActivity.currentColorPreference.primarySecondTab
        mainActivity.updateViews(
            ColorDrawable(
                if (MainActivity.currentTab == 1) {
                    skinTwoColor
                } else {
                    skin_color
                }
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFtpBinding.inflate(inflater)
        val startDividerView = binding.dividerFtpStart
        val statusDividerView = binding.dividerFtpStatus
        accentColor = mainActivity.accent
        updateSpans()
        updateStatus()
        when (mainActivity.appTheme.simpleTheme) {
            AppTheme.LIGHT -> {
                startDividerView.setBackgroundColor(Utils.getColor(context, R.color.divider))
                statusDividerView.setBackgroundColor(Utils.getColor(context, R.color.divider))
            }
            AppTheme.DARK, AppTheme.BLACK -> {
                startDividerView.setBackgroundColor(
                    Utils.getColor(context, R.color.divider_dark_card)
                )
                statusDividerView.setBackgroundColor(
                    Utils.getColor(context, R.color.divider_dark_card)
                )
            }
            else -> {}
        }
        ftpBtn.setOnClickListener {
            if (!isRunning()) {
                if (isConnectedToWifi(requireContext()) ||
                    isConnectedToLocalNetwork(requireContext()) ||
                    isEnabledWifiHotspot(requireContext())
                ) {
                    startServer()
                } else {
                    // no Wi-Fi and no eth, we shouldn't be here in the first place,
                    // because of broadcast receiver, but just to be sure
                    statusText.text = spannedStatusNoConnection
                }
            } else {
                stopServer()
            }
        }
        return binding.root
    }

    // Pending upgrading material-dialogs to simplify the logic here.
    @Suppress("ComplexMethod", "LongMethod")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.choose_ftp_port -> {
                val currentFtpPort = defaultPortFromPreferences
                MaterialDialog(requireContext()).show {
                    input(
                        hintRes = R.string.ftp_port_edit_menu_title,
                        prefill = currentFtpPort.toString(),
                        inputType = InputType.TYPE_CLASS_NUMBER,
                        waitForPositiveButton = true,
                        callback = { _, input ->
                            val name = input.toString()
                            val portNumber = name.toInt()
                            if (portNumber < 1024) {
                                Toast.makeText(
                                    activity,
                                    R.string.ftp_port_change_error_invalid,
                                    Toast.LENGTH_SHORT
                                )
                                    .show()
                            } else {
                                changeFTPServerPort(portNumber)
                                Toast.makeText(
                                    activity, R.string.ftp_port_change_success, Toast.LENGTH_SHORT
                                )
                                    .show()
                            }
                        }
                    )
                    positiveButton(text = getString(R.string.change).toUpperCase())
                    negativeButton(res = R.string.cancel)
                    getActionButton(WhichButton.POSITIVE).setTextColor(accentColor)
                    getActionButton(WhichButton.NEGATIVE).setTextColor(accentColor)
                    // TextWatcher for port number was deliberately removed. It didn't work anyway, so
                    // no reason to keep here. Pending reimplementation when material-dialogs lib is
                    // upgraded.
                }
                return true
            }
            R.id.ftp_path -> {
                if (Build.VERSION.SDK_INT >= M) {
                    activityResultHandler.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                } else {
                    MaterialDialog(requireActivity()).show {
                        folderChooser(
                            context = requireContext(),
                            initialDirectory = File(defaultPathFromPreferences),
                            selection = { _, folder ->
                                if (folder.exists() && folder.isDirectory()) {
                                    changeFTPServerPath(folder.getPath())
                                    Toast.makeText(
                                        requireContext(),
                                        R.string.ftp_path_change_success,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    // try to get parent
                                    folder.parentFile?.let { parentFolder ->
                                        if (parentFolder.exists() && parentFolder.isDirectory) {
                                            changeFTPServerPath(parentFolder.path)
                                            Toast.makeText(
                                                requireContext(),
                                                R.string.ftp_path_change_success,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            // don't have access, print error
                                            Toast.makeText(
                                                requireContext(),
                                                R.string.ftp_path_change_error_invalid,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        )
                        positiveButton(res = R.string.choose_folder)
                        negativeButton(res = R.string.cancel)
                        getActionButton(WhichButton.POSITIVE).setTextColor(accentColor)
                        getActionButton(WhichButton.NEGATIVE).setTextColor(accentColor)
                    }
                }

                return true
            }
            R.id.ftp_login -> {
                MaterialDialog(requireContext()).show {
                    val loginDialogView =
                        DialogFtpLoginBinding.inflate(LayoutInflater.from(requireContext())).also {
                            initLoginDialogViews(it)
                        }
                    customView(view = loginDialogView.root, dialogWrapContent = true)
                    positiveButton(
                        text = getString(R.string.set).toUpperCase(),
                        click = {
                            if (loginDialogView.checkboxFtpAnonymous.isChecked) {
                                // remove preferences
                                setFTPUsername("")
                                setFTPPassword("")
                            } else {
                                // password and username field not empty, let's set them to preferences
                                setFTPUsername(
                                    loginDialogView.editTextDialogFtpUsername.text.toString()
                                )
                                setFTPPassword(
                                    loginDialogView.editTextDialogFtpPassword.text.toString()
                                )
                            }
                        }
                    )
                    negativeButton(R.string.cancel)
                    title(R.string.ftp_login)
                    getActionButton(WhichButton.POSITIVE).setTextColor(accentColor)
                    getActionButton(WhichButton.NEGATIVE).setTextColor(accentColor)
                }
                return true
            }
            R.id.checkbox_ftp_readonly -> {
                val shouldReadonly = !item.isChecked
                item.isChecked = shouldReadonly
                readonlyPreference = shouldReadonly
                updatePathText()
                promptUserToRestartServer()
                return true
            }
            R.id.checkbox_ftp_secure -> {
                val shouldSecure = !item.isChecked
                item.isChecked = shouldSecure
                securePreference = shouldSecure
                promptUserToRestartServer()
                return true
            }
            R.id.ftp_timeout -> {
                MaterialDialog(requireActivity()).show {
                    title(
                        text =
                            resources.getString(R.string.ftp_timeout) +
                                " (" +
                                resources.getString(R.string.ftp_seconds) +
                                ")"
                    )
                    input(
                        hint = FtpService.DEFAULT_TIMEOUT.toString() +
                            " " +
                            resources.getString(R.string.ftp_seconds),
                        prefill = ftpTimeout.toString(),
                        inputType = InputType.TYPE_CLASS_NUMBER,
                        allowEmpty = true,
                        callback = { _, input ->
                            val isInputInteger: Boolean = try {
                                // try parsing for integer check
                                input.toString().toInt()
                                true
                            } catch (e: NumberFormatException) {
                                false
                            }
                            ftpTimeout = if (input.isEmpty() || !isInputInteger) {
                                FtpService.DEFAULT_TIMEOUT
                            } else {
                                Integer.valueOf(input.toString())
                            }
                        }
                    )
                    positiveButton(text = resources.getString(R.string.set).toUpperCase())
                    negativeButton(res = R.string.cancel)
                }
                return true
            }
        }
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        mainActivity.menuInflater.inflate(R.menu.ftp_server_menu, menu)
        menu.findItem(R.id.checkbox_ftp_readonly).isChecked = readonlyPreference
        menu.findItem(R.id.checkbox_ftp_secure).isChecked = securePreference
    }

    private val mWifiReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // connected to Wi-Fi or eth
            if (isConnectedToLocalNetwork(context)) {
                ftpBtn.isEnabled = true
                dismissSnackbar()
            } else {
                // Wi-Fi or eth connection lost
                stopServer()
                statusText.text = spannedStatusNoConnection
                ftpBtn.isEnabled = false
                ftpBtn.text = resources.getString(R.string.start_ftp).toUpperCase()
                promptUserToEnableWireless()
            }
        }
    }

    /**
     * Handles messages sent from [EventBus].
     *
     * @param signal as [FtpReceiverActions]
     */
    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    @Suppress("StringLiteralDuplication")
    fun onFtpReceiveActions(signal: FtpReceiverActions) {
        updateSpans()
        when (signal) {
            FtpReceiverActions.STARTED, FtpReceiverActions.STARTED_FROM_TILE -> {
                statusText.text = if (securePreference) spannedStatusSecure
                else spannedStatusConnected

                url.text = spannedStatusUrl
                ftpBtn.text = resources.getString(R.string.stop_ftp).toUpperCase()
                FtpNotification.updateNotification(
                    context,
                    FtpReceiverActions.STARTED_FROM_TILE == signal
                )
            }
            FtpReceiverActions.FAILED_TO_START -> {
                statusText.text = spannedStatusNotRunning
                Toast.makeText(context, R.string.unknown_error, Toast.LENGTH_LONG).show()
                ftpBtn.text = resources.getString(R.string.start_ftp).toUpperCase()
                url.text = "URL: "
            }
            FtpReceiverActions.STOPPED -> {
                statusText.text = spannedStatusNotRunning
                url.text = "URL: "
                ftpBtn.text = resources.getString(R.string.start_ftp).toUpperCase()
            }
        }
    }

    /** Sends a broadcast to start ftp server  */
    private fun startServer() {
        requireContext().sendBroadcast(
            Intent(FtpService.ACTION_START_FTPSERVER)
                .setPackage(requireContext().packageName)
        )
    }

    /** Sends a broadcast to stop ftp server  */
    private fun stopServer() {
        requireContext().sendBroadcast(
            Intent(FtpService.ACTION_STOP_FTPSERVER)
                .setPackage(requireContext().packageName)
        )
    }

    override fun onResume() {
        super.onResume()
        val wifiFilter = IntentFilter()
        wifiFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        requireContext().registerReceiver(mWifiReceiver, wifiFilter)
        EventBus.getDefault().register(this)
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(mWifiReceiver)
        EventBus.getDefault().unregister(this)
        dismissSnackbar()
    }

    /** Update UI widgets after change in shared preferences  */
    private fun updateStatus() {
        if (!isRunning()) {
            if (!isConnectedToWifi(requireContext()) &&
                !isConnectedToLocalNetwork(requireContext()) &&
                !isEnabledWifiHotspot(requireContext())
            ) {
                statusText.text = spannedStatusNoConnection
                ftpBtn.isEnabled = false
            } else {
                statusText.text = spannedStatusNotRunning
                ftpBtn.isEnabled = true
            }
            url.text = "URL: "
            ftpBtn.text = resources.getString(R.string.start_ftp).toUpperCase()
        } else {
            accentColor = mainActivity.accent
            url.text = spannedStatusUrl
            statusText.text = spannedStatusConnected
            ftpBtn.isEnabled = true
            ftpBtn.text = resources.getString(R.string.stop_ftp).toUpperCase()
        }
        val passwordDecrypted = passwordFromPreferences
        val passwordBulleted: CharSequence = OneCharacterCharSequence(
            '\u25CF',
            passwordDecrypted!!.length
        )
        username.text = "${resources.getString(R.string.username)}: $usernameFromPreferences"
        password.text = "${resources.getString(R.string.password)}: $passwordBulleted"
        ftpPasswordVisibleButton.setImageDrawable(
            resources.getDrawable(R.drawable.ic_eye_grey600_24dp)
        )
        ftpPasswordVisibleButton.visibility = if (passwordDecrypted.isEmpty()) {
            View.GONE
        } else {
            View.VISIBLE
        }
        ftpPasswordVisibleButton.setOnClickListener { v: View? ->
            if (password.text.toString().contains("\u25CF")) {
                // password was not visible, let's make it visible
                password.text = resources.getString(R.string.password) + ": " + passwordDecrypted
                ftpPasswordVisibleButton.setImageDrawable(
                    resources.getDrawable(R.drawable.ic_eye_off_grey600_24dp)
                )
            } else {
                // password was visible, let's hide it
                password.text = resources.getString(R.string.password) + ": " + passwordBulleted
                ftpPasswordVisibleButton.setImageDrawable(
                    resources.getDrawable(R.drawable.ic_eye_grey600_24dp)
                )
            }
        }
        port.text = "${resources.getString(R.string.ftp_port)}: $defaultPortFromPreferences"
        updatePathText()
    }

    private fun updatePathText() {
        val sb = StringBuilder(resources.getString(R.string.ftp_path))
            .append(": ")
            .append(defaultPathFromPreferences)
        if (readonlyPreference) sb.append(" \uD83D\uDD12")
        sharedPath.text = sb.toString()
    }

    /** Updates the status spans  */
    private fun updateSpans() {
        var ftpAddress = ftpAddressString
        if (ftpAddress == null) {
            ftpAddress = ""
            Toast.makeText(
                context,
                resources.getString(R.string.local_inet_addr_error),
                Toast.LENGTH_SHORT
            )
                .show()
        }
        val statusHead = "${resources.getString(R.string.ftp_status_title)}: "
        spannedStatusConnected = HtmlCompat.fromHtml(
            "$statusHead<b>&nbsp;&nbsp;<font color='$accentColor'>" +
                "${resources.getString(R.string.ftp_status_running)}</font></b>",
            FROM_HTML_MODE_COMPACT
        )
        spannedStatusUrl = HtmlCompat.fromHtml(
            "URL:&nbsp;$ftpAddress",
            FROM_HTML_MODE_COMPACT
        )
        spannedStatusNoConnection = HtmlCompat.fromHtml(
            "$statusHead<b>&nbsp;&nbsp;&nbsp;&nbsp;" +
                "<font color='${Utils.getColor(context, android.R.color.holo_red_light)}'>" +
                "${resources.getString(R.string.ftp_status_no_connection)}</font></b>",
            FROM_HTML_MODE_COMPACT
        )
        spannedStatusNotRunning = HtmlCompat.fromHtml(
            "$statusHead<b>&nbsp;&nbsp;&nbsp;&nbsp;" +
                "${resources.getString(R.string.ftp_status_not_running)}</b>",
            FROM_HTML_MODE_COMPACT
        )
        spannedStatusSecure = HtmlCompat.fromHtml(
            "$statusHead<b>&nbsp;&nbsp;&nbsp;&nbsp;<font color='${Utils.getColor(
                context,
                android.R.color.holo_green_light
            )}'>" +
                "${resources.getString(R.string.ftp_status_secure_connection)}</font></b>",
            FROM_HTML_MODE_COMPACT
        )
        spannedStatusUrl = HtmlCompat.fromHtml(
            "URL:&nbsp;$ftpAddress",
            FROM_HTML_MODE_COMPACT
        )
    }

    private fun initLoginDialogViews(loginDialogView: DialogFtpLoginBinding) {
        val usernameEditText = loginDialogView.editTextDialogFtpUsername
        val passwordEditText = loginDialogView.editTextDialogFtpPassword
        val anonymousCheckBox = loginDialogView.checkboxFtpAnonymous
        anonymousCheckBox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            usernameEditText.isEnabled = !isChecked
            passwordEditText.isEnabled = !isChecked
        }

        // init dialog views as per preferences
        if (usernameFromPreferences == FtpService.DEFAULT_USERNAME) {
            anonymousCheckBox.isChecked = true
        } else {
            usernameEditText.setText(usernameFromPreferences)
            passwordEditText.setText(passwordFromPreferences)
        }
    }

    // return address the FTP server is running
    private val ftpAddressString: String?
        get() {
            val ia = getLocalInetAddress(requireContext()) ?: return null
            return (
                (
                    if (securePreference) {
                        FtpService.INITIALS_HOST_SFTP
                    } else {
                        FtpService.INITIALS_HOST_FTP
                    }
                    ) +
                    ia.hostAddress +
                    ":" +
                    defaultPortFromPreferences
                )
        }

    private val defaultPortFromPreferences: Int
        get() = mainActivity.prefs
            .getInt(FtpService.PORT_PREFERENCE_KEY, FtpService.DEFAULT_PORT)
    private val usernameFromPreferences: String
        get() = mainActivity.prefs
            .getString(FtpService.KEY_PREFERENCE_USERNAME, FtpService.DEFAULT_USERNAME)!!

    // can't decrypt the password saved in preferences, remove the preference altogether
    private val passwordFromPreferences: String?
        get() = runCatching {
            val encryptedPassword = mainActivity.prefs.getString(
                FtpService.KEY_PREFERENCE_PASSWORD, ""
            )
            if (encryptedPassword == "") {
                ""
            } else {
                CryptUtil.decryptPassword(requireContext(), encryptedPassword)
            }
        }.onFailure {
            it.printStackTrace()
            Toast.makeText(requireContext(), R.string.error, Toast.LENGTH_SHORT).show()
            mainActivity.prefs.edit().putString(FtpService.KEY_PREFERENCE_PASSWORD, "").apply()
        }.getOrNull()

    private val defaultPathFromPreferences: String
        get() {
            return pathToDisplayString(
                PreferenceManager.getDefaultSharedPreferences(activity)
                    .getString(FtpService.KEY_PREFERENCE_PATH, FtpService.DEFAULT_PATH)!!
            )
        }

    private fun pathToDisplayString(path: String): String {
        return when {
            path == FtpService.DEFAULT_PATH -> {
                Environment.getExternalStorageDirectory().absolutePath
            }
            path.startsWith("file:///") -> {
                path.substringAfter("file://")
            }
            path.startsWith("content://") -> {
                return Uri.parse(path).let {
                    "/storage${it.path?.replace(':', '/')}"
                }
            }
            else -> {
                path
            }
        }
    }

    private fun changeFTPServerPort(port: Int) {
        mainActivity.prefs.edit().putInt(FtpService.PORT_PREFERENCE_KEY, port).apply()

        // first update spans which will point to an updated status
        updateSpans()
        updateStatus()
    }

    /**
     * Update FTP server shared path in [android.content.SharedPreferences].
     *
     * @param path new shared path. Can be either absolute path (pre 4.4) or URI, which can be
     * <code>file:///</code> or <code>content://</code> as prefix
     */
    fun changeFTPServerPath(path: String) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        preferences.edit().putString(FtpService.KEY_PREFERENCE_PATH, path).apply()
        updateStatus()
    }

    private fun setFTPUsername(username: String) {
        mainActivity
            .prefs
            .edit()
            .putString(FtpService.KEY_PREFERENCE_USERNAME, username)
            .apply()
        updateStatus()
    }

    private fun setFTPPassword(password: String) {
        try {
            mainActivity
                .prefs
                .edit()
                .putString(
                    FtpService.KEY_PREFERENCE_PASSWORD, CryptUtil.encryptPassword(context, password)
                )
                .apply()
        } catch (e: GeneralSecurityException) {
            e.printStackTrace()
            Toast.makeText(context, resources.getString(R.string.error), Toast.LENGTH_LONG)
                .show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(context, resources.getString(R.string.error), Toast.LENGTH_LONG)
                .show()
        }
        updateStatus()
    }

    // Returns timeout from preferences, in seconds
    private var ftpTimeout: Int
        get() = mainActivity
            .prefs
            .getInt(FtpService.KEY_PREFERENCE_TIMEOUT, FtpService.DEFAULT_TIMEOUT)
        private set(seconds) {
            mainActivity.prefs.edit().putInt(FtpService.KEY_PREFERENCE_TIMEOUT, seconds).apply()
        }

    private var securePreference: Boolean
        get() = mainActivity
            .prefs
            .getBoolean(FtpService.KEY_PREFERENCE_SECURE, FtpService.DEFAULT_SECURE)
        private set(isSecureEnabled) {
            mainActivity
                .prefs
                .edit()
                .putBoolean(FtpService.KEY_PREFERENCE_SECURE, isSecureEnabled)
                .apply()
        }

    private var readonlyPreference: Boolean
        get() = mainActivity.prefs.getBoolean(FtpService.KEY_PREFERENCE_READONLY, false)
        private set(isReadonly) {
            mainActivity
                .prefs
                .edit()
                .putBoolean(FtpService.KEY_PREFERENCE_READONLY, isReadonly)
                .apply()
        }

    private fun promptUserToRestartServer() {
        if (isRunning()) AppConfig.toast(context, R.string.ftp_prompt_restart_server)
    }

    private fun promptUserToEnableWireless() {
        // No wifi, no data, no connection at all
        snackbar = Utils.showThemedSnackbar(
            activity as MainActivity?,
            getString(R.string.ftp_server_prompt_connect_to_network),
            BaseTransientBottomBar.LENGTH_INDEFINITE,
            R.string.ftp_server_open_settings
        ) { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        snackbar!!.show()
    }

    private fun dismissSnackbar() = snackbar?.dismiss()

    companion object {
        const val TAG = "FtpServerFragment"
        const val REQUEST_CODE_SAF_FTP = 225
    }
}
