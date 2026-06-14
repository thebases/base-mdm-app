package com.base.launcher.ui

import android.app.Dialog
import android.os.Handler
import android.view.LayoutInflater
import android.view.Window
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.base.launcher.R
import com.base.launcher.databinding.ActivityMainBinding
import com.base.launcher.databinding.DialogFileDownloadingFailedBinding
import com.base.launcher.helper.ConfigUpdater
import com.base.launcher.json.Application
import com.base.launcher.json.RemoteFile

class AppInstallDelegate(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val handler: Handler,
    private val configUpdater: ConfigUpdater,
    private val isContentShown: () -> Boolean,
    private val onAllInstallComplete: () -> Unit
) : ConfigUpdater.UINotifier {

    private var fileNotDownloadedDialog: Dialog? = null
    private var dialogFileDownloadingFailedBinding: DialogFileDownloadingFailedBinding? = null

    // This flag tracks whether we're downloading a file (true) vs app (false) when showing the error dialog
    var downloadingFile = false

    fun dismissFileNotDownloadedDialog() {
        dismissDialog(fileNotDownloadedDialog)
    }

    private fun dismissDialog(dialog: Dialog?) {
        if (dialog != null) {
            try { dialog.dismiss() } catch (e: Exception) { /* ignored */ }
        }
    }

    fun createAndShowFileNotDownloadedDialog(fileName: String) {
        dismissDialog(fileNotDownloadedDialog)
        fileNotDownloadedDialog = Dialog(activity)
        dialogFileDownloadingFailedBinding = DataBindingUtil.inflate(
            LayoutInflater.from(activity),
            R.layout.dialog_file_downloading_failed,
            null,
            false
        )
        val errorTextResource = if (downloadingFile) R.string.main_file_downloading_error else R.string.main_app_downloading_error
        dialogFileDownloadingFailedBinding!!.title.text =
            activity.getString(errorTextResource) + " " + fileName
        fileNotDownloadedDialog!!.setCancelable(false)
        fileNotDownloadedDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        fileNotDownloadedDialog!!.setContentView(dialogFileDownloadingFailedBinding!!.root)
        try {
            fileNotDownloadedDialog!!.show()
        } catch (e: Exception) {
            // BadTokenException ignored
        }
    }

    fun repeatDownload() {
        dismissDialog(fileNotDownloadedDialog)
        if (downloadingFile) configUpdater.repeatDownloadFiles() else configUpdater.repeatDownloadApps()
    }

    fun confirmDownloadFailure() {
        dismissDialog(fileNotDownloadedDialog)
        if (downloadingFile) configUpdater.skipDownloadFiles() else configUpdater.skipDownloadApps()
    }

    // ---- ConfigUpdater.UINotifier implementation (install/file subset) ----

    override fun onConfigUpdateStart() {
        binding.setMessage(activity.getString(R.string.main_activity_update_config))
    }

    override fun onConfigUpdateServerError(errorText: String) {
        // Handled by MainActivity (uses BaseActivity dialog helpers)
    }

    override fun onConfigUpdateNetworkError(errorText: String) {
        // Handled by MainActivity (uses BaseActivity dialog helpers)
    }

    override fun onConfigLoaded() {
        // Handled by MainActivity
    }

    override fun onPoliciesUpdated() {
        // Handled by MainActivity
    }

    override fun onFileDownloading(remoteFile: RemoteFile) {
        handler.post {
            binding.setMessage(activity.getString(R.string.main_file_downloading) + " " + remoteFile.getPath())
            binding.setDownloading(true)
        }
    }

    override fun onDownloadProgress(progress: Int, total: Long, current: Long) {
        handler.post {
            binding.progress.max = 100
            binding.progress.progress = progress
            binding.setFileLength(total)
            binding.setDownloadedLength(current)
        }
    }

    override fun onFileDownloadError(remoteFile: RemoteFile) {
        if (!isContentShown()) {
            downloadingFile = true
            createAndShowFileNotDownloadedDialog(remoteFile.getUrl())
            binding.setDownloading(false)
        } else {
            configUpdater.skipDownloadFiles()
        }
    }

    override fun onFileInstallError(remoteFile: RemoteFile) {
        if (!isContentShown()) {
            try {
                AlertDialog.Builder(activity)
                    .setMessage(activity.getString(R.string.file_create_error) + " " + remoteFile.getPath())
                    .setPositiveButton(R.string.dialog_administrator_mode_continue) { _, _ ->
                        configUpdater.skipDownloadFiles()
                    }
                    .create()
                    .show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            configUpdater.skipDownloadFiles()
        }
    }

    override fun onAppUpdateStart() {
        binding.setMessage(activity.getString(R.string.main_activity_applications_update))
    }

    override fun onAppRemoving(application: Application) {
        handler.post {
            binding.setMessage(activity.getString(R.string.main_app_removing) + " " + application.getName())
            binding.setDownloading(false)
        }
    }

    override fun onAppDownloading(application: Application) {
        handler.post {
            binding.setMessage(activity.getString(R.string.main_app_downloading) + " " + application.getName())
            binding.setDownloading(true)
        }
    }

    override fun onAppInstalling(application: Application) {
        handler.post {
            binding.setMessage(activity.getString(R.string.main_app_installing) + " " + application.getName())
            binding.setDownloading(false)
        }
    }

    override fun onAppDownloadError(application: Application) {
        if (!isContentShown()) {
            downloadingFile = false
            createAndShowFileNotDownloadedDialog(application.getName())
            binding.setDownloading(false)
        } else {
            configUpdater.skipDownloadApps()
        }
    }

    override fun onAppInstallError(packageName: String) {
        handler.post {
            if (!isContentShown()) {
                try {
                    AlertDialog.Builder(activity)
                        .setMessage(activity.getString(R.string.install_error) + " " + packageName)
                        .setPositiveButton(R.string.dialog_administrator_mode_continue) { _, _ ->
                            configUpdater.repeatDownloadApps()
                        }
                        .create()
                        .show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                configUpdater.repeatDownloadApps()
            }
        }
    }

    override fun onAppInstallComplete(packageName: String) {
        // no-op
    }

    override fun onConfigUpdateComplete() {
        // Handled by MainActivity
    }

    override fun onAllAppInstallComplete() {
        onAllInstallComplete()
    }
}
