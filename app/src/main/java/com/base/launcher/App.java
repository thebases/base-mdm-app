
package com.base.launcher;

import android.app.Application;
import android.os.Environment;
import android.util.Log;

import com.base.launcher.kozen.KozenComponentFacade;
import com.base.launcher.util.FileUtils;
import com.jakewharton.picasso.OkHttp3Downloader;
import com.kozen.component.engine.InitListener;
import com.kozen.component_client.ComponentEngine;
import com.kozen.terminalmanager.InitCallBack;
import com.kozen.terminalmanager.TerminalManager;

import com.squareup.picasso.Picasso;

public class App extends Application {
    private static final String TAG = "BaseLauncherApplication";

    private static String RES_ROOT_PATH = Environment.getExternalStorageDirectory().getAbsolutePath()+"/Base/";

    @Override
    public void onCreate() {
        super.onCreate();
        FileUtils.copyAssetsToSDCard(this, "assets", RES_ROOT_PATH);
        Picasso.Builder builder = new Picasso.Builder(this);
        builder.downloader(new OkHttp3Downloader(this,Integer.MAX_VALUE));
        Picasso built = builder.build();
        built.setIndicatorsEnabled(true);
        built.setLoggingEnabled(true);
        Picasso.setSingletonInstance(built);
        initTerminalManager();
        initComponentSdk();


    }

    private void initTerminalManager() {
        TerminalManager.INSTANCE.init(this, new InitCallBack() {
            @Override
            public void onInitResult(int result, String errorMsg) {
                Log.d(TAG, "[terminal-manager] init result=" + result + ", error=" + errorMsg);
                if (result !=0) {
                    // TODO: add retry policy / notification to server if needed
                }
            }
        });
    }

    private void initComponentSdk() {
        ComponentEngine.INSTANCE.init(this, new InitListener() {
            @Override
            public void onResult(int result, String errorMsg) {
                Log.d(TAG, "[component-sdk] init result=" + result + ", error=" + errorMsg);
                // result == 0 success, -1 failure
                if (result ==0){
                    // Turn on customer display with your logo
                    Log.d(TAG, "======>>> Start set 2nd Screen");
                    Log.d(TAG, "======>>> Path:" + RES_ROOT_PATH + "momo_logo.png");

                    KozenComponentFacade comp = KozenComponentFacade.get();
                    comp.power(true);
                    comp.setBrightness(80);
                    comp.showPic(RES_ROOT_PATH + "momo_logo.png");
                }
            }
        });
    }

}
