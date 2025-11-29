package com.base.launcher.kozen;


import com.kozen.component.constant.KeyboardConstant;
import com.kozen.component.keyboard.IKeyboard;
import com.kozen.component.keyboard.InputCallback;
import com.kozen.component.secondaryScreen.IResultCallback;
import com.kozen.component.secondaryScreen.ISecondaryScreen;
import com.kozen.component.secondaryScreen.PicData;
import com.kozen.component_client.ComponentEngine;

import java.util.ArrayList;

/**
 * Thin wrapper around KOZEN Component SDK (Secondary screen + Keyboard).
 */
public class KozenComponentFacade {

    private static KozenComponentFacade instance;

    public static KozenComponentFacade get() {
        if (instance == null) {
            instance = new KozenComponentFacade();
        }
        return instance;
    }

    private final IKeyboard keyboard;
    private final ISecondaryScreen secondary;

    private KozenComponentFacade() {
        keyboard = ComponentEngine.INSTANCE.getKeyboardManager();
        secondary = ComponentEngine.INSTANCE.getSecondaryScreenManager();
    }

    // ---------------------------
    // 3.2 Keyboard module
    // ---------------------------
    public int startPhysicalKeyboard(InputCallback callback) {
        return keyboard.startPhysicalKeyboard(callback);
    }

    public int stopPhysicalKeyboard() {
        return keyboard.stopPhysicalKeyboard();
    }

    public int switchKeyButtonVoiceEnable(boolean enable) {
        return keyboard.switchKeyButtonVoiceEnable(enable);
    }

    public int isKeyButtonVoiceEnable() {
        return keyboard.isKeyButtonVoiceEnable();
    }

    // Example of how to implement callback usage
    public static abstract class SimpleInputCallback implements InputCallback {
        @Override
        public void onKey(KeyboardConstant.KeyCode keyCode,
                          KeyboardConstant.KeyAction action) {
            handleKey(keyCode, action);
        }

        public abstract void handleKey(KeyboardConstant.KeyCode keyCode,
                                       KeyboardConstant.KeyAction action);
    }

    // ---------------------------
    // 3.3 Secondary Screen module
    // ---------------------------
    public int showPic(String picPath) {
        return secondary.showPic(picPath);
    }

    public int showPic(ArrayList<String> picPathList, int intervalSeconds) {
        return secondary.showPic(picPathList, intervalSeconds);
    }

    public int showVideo(String videoPath) {
        return secondary.showVideo(videoPath);
    }

    public int power(boolean on) {
        return secondary.power(on);
    }

    public int setBrightness(int value) {
        return secondary.setBrightness(value);
    }

    public int setBootLogo(String filePath) {
        return secondary.setBootLogo(filePath);
    }

    public int[] getScreenResolution() {
        return secondary.getScreenResolution();
    }

    public void show(android.view.View view, IResultCallback cb) {
        secondary.show(view, cb);
    }

    public void show(android.view.View view,
                     ArrayList<PicData> picDataList,
                     IResultCallback cb) {
        secondary.show(view, picDataList, cb);
    }
}
