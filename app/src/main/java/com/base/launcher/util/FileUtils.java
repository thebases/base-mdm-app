package com.base.launcher.util;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileUtils {

    private static final String TAG = "BaseFileUtil";

    public static boolean copyAssetsToSDCard(Context context, String assetDir, String targetPath) {
        Log.d(TAG, "copyAssetsToSDCard: assetDir=" + assetDir + ", targetPath=" + targetPath);
        try {
            File targetFile = new File(targetPath);
            if (!targetFile.exists()) {
                createFile(targetPath);
            }

            AssetManager assetManager = context.getAssets();
            String[] fileNames = assetManager.list(assetDir);
            if (fileNames != null) {
                for (String fileName : fileNames) {
                    InputStream inputStream = assetManager.open(assetDir + "/" + fileName);
                    File outputFile = new File(targetPath, fileName);
                    Log.d(TAG, "outputFile: " + outputFile.getAbsolutePath());

                    if (!outputFile.exists()) {
                        createFile(outputFile.getAbsolutePath());

                        FileOutputStream outputStream = new FileOutputStream(outputFile);
                        try {
                            byte[] buffer = new byte[4096];
                            int length;
                            while ((length = inputStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, length);
                            }
                            outputStream.flush();
                        } finally {
                            try {
                                inputStream.close();
                            } catch (IOException ignored) {
                            }
                            try {
                                outputStream.close();
                            } catch (IOException ignored) {
                            }
                        }
                    } else {
                        // If file already exists, still close the inputStream
                        try {
                            inputStream.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static File createFile(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        File target = new File(path);
        try {
            // Handle directory path (ends with separator)
            if (path.endsWith(File.separator)) {
                Log.d(TAG, "target.mkdirs()....");
                return target.mkdirs() ? target : null;
            }

            // Handle file path
            if (!createParentDirs(target)) {
                return null;
            }
            if (target.isDirectory() || target.createNewFile()) {
                return target;
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static boolean createParentDirs(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                return false;
            }
        }
        return true;
    }
}
