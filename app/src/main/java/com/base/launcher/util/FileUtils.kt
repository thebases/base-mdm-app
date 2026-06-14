package com.base.launcher.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object FileUtils {
    private const val TAG = "BaseFileUtil"

    @JvmStatic
    fun copyAssetsToSDCard(context: Context, assetDir: String, targetPath: String): Boolean {
        Log.d(TAG, "copyAssetsToSDCard: assetDir=$assetDir, targetPath=$targetPath")
        return try {
            val targetFile = File(targetPath)
            if (!targetFile.exists()) createFile(targetPath)

            val assetManager = context.assets
            val fileNames = assetManager.list(assetDir)
            if (fileNames != null) {
                for (fileName in fileNames) {
                    val inputStream = assetManager.open("$assetDir/$fileName")
                    val outputFile = File(targetPath, fileName)
                    Log.d(TAG, "outputFile: ${outputFile.absolutePath}")
                    if (!outputFile.exists()) {
                        createFile(outputFile.absolutePath)
                        val outputStream = FileOutputStream(outputFile)
                        try {
                            val buffer = ByteArray(4096)
                            var length: Int
                            while (inputStream.read(buffer).also { length = it } > 0) {
                                outputStream.write(buffer, 0, length)
                            }
                            outputStream.flush()
                        } finally {
                            try { inputStream.close() } catch (_: IOException) {}
                            try { outputStream.close() } catch (_: IOException) {}
                        }
                    } else {
                        try { inputStream.close() } catch (_: IOException) {}
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    fun createFile(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val target = File(path)
        return try {
            if (path.endsWith(File.separator)) {
                Log.d(TAG, "target.mkdirs()....")
                if (target.mkdirs()) target else null
            } else {
                if (!createParentDirs(target)) return null
                if (target.isDirectory || target.createNewFile()) target else null
            }
        } catch (_: IOException) {
            null
        }
    }

    @Throws(IOException::class)
    private fun createParentDirs(file: File): Boolean {
        val parent = file.parentFile
        return parent == null || parent.exists() || parent.mkdirs()
    }
}
