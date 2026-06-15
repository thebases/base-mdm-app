package com.base.launcher.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.base.launcher.BuildConfig
import com.base.launcher.Const
import com.base.launcher.db.DatabaseHelper
import com.base.launcher.db.RemoteFileTable
import com.base.launcher.helper.CryptoHelper
import com.base.launcher.json.Application
import com.base.launcher.json.RemoteFile
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.net.HttpURLConnection
import java.net.URL

object InstallUtils {
    fun interface DownloadProgress {
        fun onDownloadProgress(progress: Int, total: Long, current: Long)
    }

    interface InstallErrorHandler {
        fun onInstallError(msg: String?)
    }

    private val DO_NOT_VERIFY = HostnameVerifier { _: String, _: SSLSession -> true }

    @JvmStatic
    fun generateApplicationsForInstallList(
        context: Context, applications: List<Application>,
        applicationsForInstall: MutableList<Application>,
        pendingInstallations: Map<String, File>
    ) {
        val packageManager = context.packageManager

        for (a in applications) {
            if ((a.type == null || a.type == Application.TYPE_APP) && a.isRemove && !isInList(applicationsForInstall, a)) {
                Log.d(Const.LOG_TAG, "checkAndUpdateApplications(): marking app ${a.pkg} to remove")
                applicationsForInstall.add(a)
            }
        }
        for (a in applications) {
            if ((a.type == null || a.type == Application.TYPE_APP) && !a.isRemove &&
                !pendingInstallations.containsKey(a.pkg) && !isInList(applicationsForInstall, a)) {
                Log.d(Const.LOG_TAG, "checkAndUpdateApplications(): marking app ${a.pkg} to install")
                applicationsForInstall.add(a)
            }
        }

        val it = applicationsForInstall.iterator()
        while (it.hasNext()) {
            val application = it.next()
            if ((application.url.isNullOrBlank()) && !application.isRemove) {
                Log.d(Const.LOG_TAG, "checkAndUpdateApplications(): app ${application.pkg} is system, skipping")
                it.remove()
                continue
            }
            try {
                val pkg = application.pkg ?: continue
                val packageInfo = packageManager.getPackageInfo(pkg, 0)

                if (application.isRemove && application.version != "0" &&
                    !areVersionsEqual(packageInfo.versionName, packageInfo.versionCode, application.version, application.code)) {
                    Log.d(Const.LOG_TAG, "checkAndUpdateApplications(): app ${application.pkg} version not match: ${application.version} ${packageInfo.versionName}, skipping")
                    it.remove()
                    continue
                }

                if (!application.isRemove && !upgradingBaseFreeToFull(context, application, packageInfo) &&
                    (application.isSkipVersion || application.version == "0" ||
                        areVersionsEqual(packageInfo.versionName, packageInfo.versionCode, application.version, application.code))) {
                    Log.d(Const.LOG_TAG, "checkAndUpdateApplications(): app $pkg versions match: ${application.version} ${packageInfo.versionName}, skipping")
                    it.remove()
                    continue
                }

                if (!application.isRemove &&
                    compareVersions(packageInfo.versionName, packageInfo.versionCode, application.version, application.code) > 0) {
                    RemoteLogger.log(context, Const.LOG_DEBUG, "Downgrade requested for $pkg: installed version ${packageInfo.versionName}, required version ${application.version}")
                    var canDowngrade = false
                    for (a in applications) {
                        if (a.pkg.equals(pkg, ignoreCase = true) && a.isRemove &&
                            areVersionsEqual(packageInfo.versionName, packageInfo.versionCode, a.version, a.code)) {
                            canDowngrade = true
                            break
                        }
                    }
                    if (canDowngrade) {
                        RemoteLogger.log(context, Const.LOG_DEBUG, "Current version of ${application.pkg} will be removed, downgrade allowed")
                    } else {
                        RemoteLogger.log(context, Const.LOG_DEBUG, "Ignoring downgrade request for ${application.pkg}: remove current version first!")
                        it.remove()
                        continue
                    }
                }
            } catch (e: PackageManager.NameNotFoundException) {
                if (application.isRemove) {
                    Log.d(Const.LOG_TAG, "checkAndUpdateApplications(): app ${application.pkg} not found, nothing to remove")
                    it.remove()
                }
            }
        }
    }

    private fun isInList(applicationsForInstall: List<Application>, a: Application): Boolean {
        return applicationsForInstall.any {
            a.pkg.equals(it.pkg, ignoreCase = true) &&
            a.version.equals(it.version, ignoreCase = true) &&
            a.isRemove == it.isRemove
        }
    }

    private fun upgradingBaseFreeToFull(context: Context, application: Application, packageInfo: android.content.pm.PackageInfo): Boolean {
        if (application.pkg != context.packageName) return false
        return Utils.getLauncherVariant() == "opensource" && application.url?.endsWith("master.apk") == true
    }

    private fun areVersionsEqual(v1: String?, c1: Int, v2: String?, c2: Int?): Boolean {
        if (c2 != null && c2 != 0) return c1 == c2
        if (v1 == null || v2 == null) return v1 == v2
        return v1.replace("[^\\d.]".toRegex(), "") == v2.replace("[^\\d.]".toRegex(), "")
    }

    @JvmStatic
    fun compareVersions(v1: String?, c1: Int, v2: String?, c2: Int?): Int {
        if (c2 != null && c2 != 0) return c1.compareTo(c2)
        if (v1 == null && v2 == null) return 0
        if (v1 == null) return -1
        if (v2 == null) return 1

        val v1n = v1.replace("[^\\d.]".toRegex(), "").split(".")
        val v2n = v2.replace("[^\\d.]".toRegex(), "").split(".")
        val count = minOf(v1n.size, v2n.size)

        for (n in 0 until count) {
            try {
                val n1 = v1n[n].toInt()
                val n2 = v2n[n].toInt()
                if (n1 < n2) return -1
                if (n1 > n2) return 1
            } catch (_: Exception) {
                return 0
            }
        }
        return v1n.size.compareTo(v2n.size)
    }

    @JvmStatic
    fun getFileByPath(path: String): File {
        return if (path.startsWith("//")) File(path.substring(1))
        else File(Environment.getExternalStorageDirectory(), path)
    }

    @JvmStatic
    fun generateFilesForInstallList(context: Context, files: List<RemoteFile>, filesForInstall: MutableList<RemoteFile>) {
        for (remoteFile in files) {
            if (remoteFile.path == null) continue
            val file = getFileByPath(remoteFile.path!!)
            if (remoteFile.isRemove) {
                if (file.exists()) filesForInstall.add(remoteFile)
            } else {
                if (!file.exists()) {
                    filesForInstall.add(remoteFile)
                } else {
                    val remoteFileDb = RemoteFileTable.selectByPath(
                        DatabaseHelper.instance(context).readableDatabase, remoteFile.path)
                    if (remoteFileDb == null || remoteFileDb.lastUpdate < remoteFile.lastUpdate) {
                        filesForInstall.add(remoteFile)
                    }
                }
            }
        }
    }

    @JvmStatic
    fun getAppTempPath(context: Context, strUrl: String?): String {
        return File(context.getExternalFilesDir(null), getFileName(strUrl ?: "")).absolutePath
    }

    @JvmStatic
    @Throws(Exception::class)
    fun downloadFile(context: Context, strUrl: String, progressHandler: DownloadProgress): File {
        var tempFile = File(context.getExternalFilesDir(null), getFileName(strUrl))
        if (tempFile.exists()) tempFile.delete()

        try {
            try {
                tempFile.createNewFile()
            } catch (e: Exception) {
                e.printStackTrace()
                tempFile = File.createTempFile(getFileName(strUrl), "temp")
            }

            val url = URL(strUrl)
            val connection: HttpURLConnection =
                if (BuildConfig.TRUST_ANY_CERTIFICATE && url.protocol.lowercase() == "https") {
                    (url.openConnection() as HttpsURLConnection).also {
                        it.hostnameVerifier = DO_NOT_VERIFY
                    }
                } else {
                    url.openConnection() as HttpURLConnection
                }

            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.connectTimeout = Const.CONNECTION_TIMEOUT.toInt()
            connection.readTimeout = Const.CONNECTION_TIMEOUT.toInt()
            val signature = getRequestSignature(strUrl)
            if (signature != null) connection.setRequestProperty("X-Request-Signature", signature)
            connection.connect()

            if (connection.responseCode != 200) {
                throw Exception("Bad server response for $strUrl: ${connection.responseCode}")
            }

            val lengthOfFile = connection.contentLength.toLong()
            progressHandler.onDownloadProgress(0, lengthOfFile, 0)

            val dis = DataInputStream(connection.inputStream)
            val buffer = ByteArray(1024)
            var length: Int
            var total = 0L

            FileOutputStream(tempFile).use { fos ->
                while (dis.read(buffer).also { length = it } > 0) {
                    total += length
                    progressHandler.onDownloadProgress(
                        ((total * 100.0f) / lengthOfFile).toInt(), lengthOfFile, total)
                    fos.write(buffer, 0, length)
                }
                fos.flush()
            }
            dis.close()
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
        return tempFile
    }

    @JvmStatic
    fun getRequestSignature(strUrl: String): String? {
        val index = strUrl.indexOf("/files/", 0)
        if (index == -1) return null
        val filepath = strUrl.substring(index + "/files/".length)
        return try {
            CryptoHelper.getSHA1String(BuildConfig.REQUEST_SIGNATURE + filepath)
        } catch (_: Exception) {
            null
        }
    }

    private fun getFileName(strUrl: String?): String {
        if (strUrl == null) return ""
        val slashIndex = strUrl.lastIndexOf("/")
        return if (slashIndex >= 0) strUrl.substring(slashIndex) else strUrl
    }

    @JvmStatic
    fun silentInstallApplication(context: Context, file: File, packageName: String, errorHandler: InstallErrorHandler) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return

        if (file.name.endsWith(".xapk")) {
            val files = XapkUtils.extract(context, file)
            XapkUtils.install(context, files, packageName, errorHandler)
            return
        }

        try {
            Log.i(Const.LOG_TAG, "Installing $packageName")
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(packageName)
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)
            FileInputStream(file).use { input ->
                session.openWrite("COSU", 0, -1).use { output ->
                    val buffer = ByteArray(65536)
                    var c: Int
                    while (input.read(buffer).also { c = it } != -1) output.write(buffer, 0, c)
                    session.fsync(output)
                }
            }
            session.commit(createIntentSender(context, sessionId, packageName))
            Log.i(Const.LOG_TAG, "Installation session committed")
        } catch (e: Exception) {
            Log.w(Const.LOG_TAG, "PackageInstaller error: ${e.message}")
            e.printStackTrace()
            errorHandler.onInstallError(e.message)
        }
    }

    @JvmStatic
    fun createIntentSender(context: Context, sessionId: Int, packageName: String?): IntentSender {
        val intent = Intent(Const.ACTION_INSTALL_COMPLETE).apply {
            if (packageName != null) putExtra(Const.PACKAGE_NAME, packageName)
        }
        return PendingIntent.getBroadcast(
            context, sessionId, intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
        ).intentSender
    }

    @JvmStatic
    fun silentUninstallApplication(context: Context, packageName: String) {
        try {
            val pi = context.packageManager.packageInstaller
            val sr = createIntentSender(context, 0, packageName)
            pi.uninstall(packageName.toString(), sr)
        } catch (_: Exception) {}
    }

    @JvmStatic
    fun requestInstallApplication(context: Context, file: File, errorHandler: InstallErrorHandler) {
        if (file.name.endsWith(".xapk")) {
            XapkUtils.install(context, XapkUtils.extract(context, file), null, errorHandler)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val uri = FileProvider.getUriForFile(context,
                    context.applicationContext.packageName + ".provider", file)
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try { context.startActivity(intent) } catch (e: Exception) { e.printStackTrace() }
        } else {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    @JvmStatic
    fun requestUninstallApplication(context: Context, packageName: String) {
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$packageName")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try { context.startActivity(intent) } catch (e: Exception) { e.printStackTrace() }
    }

    @JvmStatic
    fun getPackageInstallerStatusMessage(status: Int): String {
        return when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> "PENDING_USER_ACTION"
            PackageInstaller.STATUS_SUCCESS             -> "SUCCESS"
            PackageInstaller.STATUS_FAILURE             -> "FAILURE_UNKNOWN"
            PackageInstaller.STATUS_FAILURE_BLOCKED     -> "BLOCKED"
            PackageInstaller.STATUS_FAILURE_ABORTED     -> "ABORTED"
            PackageInstaller.STATUS_FAILURE_INVALID     -> "INVALID"
            PackageInstaller.STATUS_FAILURE_CONFLICT    -> "CONFLICT"
            PackageInstaller.STATUS_FAILURE_STORAGE     -> "STORAGE"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "INCOMPATIBLE"
            else -> "UNKNOWN"
        }
    }

    @JvmStatic
    fun initUnsafeTrustManager() {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            @Throws(CertificateException::class)
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            @Throws(CertificateException::class)
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        })
        try {
            val sc = SSLContext.getInstance("TLS")
            sc.init(null, trustAllCerts, java.security.SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun deleteTempApk(file: File) {
        try {
            if (file.name.endsWith(".xapk")) {
                val path = file.absolutePath
                val directory = File(path.dropLast(5))
                if (directory.exists()) deleteRecursive(directory)
            }
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
    }

    private fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.forEach { deleteRecursive(it) }
        }
        fileOrDirectory.delete()
    }

    @JvmStatic
    fun clearTempFiles(context: Context) {
        try {
            val filesDir = context.getExternalFilesDir(null) ?: return
            filesDir.listFiles()?.forEach { child ->
                if (child.name == "MqttConnection" || child.name == "init.json") return@forEach
                if (child.isDirectory) deleteRecursive(child) else child.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
