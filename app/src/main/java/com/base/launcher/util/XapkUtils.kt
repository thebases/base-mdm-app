package com.base.launcher.util

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.base.launcher.Const
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.LinkedList
import java.util.zip.ZipFile

object XapkUtils {
    @JvmStatic
    fun extract(context: Context, xapk: File): List<File>? {
        return try {
            val extractDir = xapk.name.dropLast(5)
            val extractDirFile = File(context.getExternalFilesDir(null), extractDir)
            when {
                extractDirFile.isDirectory -> FileUtils.deleteDirectory(extractDirFile)
                extractDirFile.exists() -> extractDirFile.delete()
            }
            extractDirFile.mkdirs()

            val result = LinkedList<File>()
            val zipFile = ZipFile(xapk)
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".apk")) {
                    val inputStream = zipFile.getInputStream(entry)
                    val resultFile = File(extractDirFile, entry.name)
                    val outputStream = FileOutputStream(resultFile)
                    IOUtils.copy(inputStream, outputStream)
                    inputStream.close()
                    outputStream.close()
                    result.add(resultFile)
                }
            }
            zipFile.close()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @JvmStatic
    fun install(context: Context, files: List<File>?, packageName: String?, errorHandler: InstallUtils.InstallErrorHandler?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        if (files == null) {
            RemoteLogger.log(context, Const.LOG_WARN, "Failed to unpack XAPK for $packageName - ignoring installation")
            errorHandler?.onInstallError(null)
            return
        }
        val totalSize = files.sumOf { it.length() }
        try {
            Log.i(Const.LOG_TAG, "Installing XAPK $packageName")
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            packageName?.let { params.setAppPackageName(it) }
            params.setSize(totalSize)
            val sessionId = packageInstaller.createSession(params)
            for (file in files) {
                addFileToSession(sessionId, file, packageInstaller)
            }
            val session = packageInstaller.openSession(sessionId)
            session.commit(InstallUtils.createIntentSender(context, sessionId, packageName))
            session.close()
            Log.i(Const.LOG_TAG, "Installation session committed")
        } catch (e: Exception) {
            errorHandler?.onInstallError(e.message)
        }
    }

    @Throws(IOException::class)
    private fun addFileToSession(sessionId: Int, file: File, packageInstaller: PackageInstaller) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        FileInputStream(file).use { input ->
            val session = packageInstaller.openSession(sessionId)
            session.openWrite(file.name, 0, file.length()).use { output ->
                val buffer = ByteArray(65536)
                var c: Int
                while (input.read(buffer).also { c = it } != -1) {
                    output.write(buffer, 0, c)
                }
                session.fsync(output)
            }
            session.close()
        }
    }
}
