package com.bz.show;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

public class BanZ{

    private static final Handler handler = new Handler(Looper.getMainLooper());

    public static void runOnUiThread(Runnable runnable) {
        handler.post(runnable);
    }
    public static void run(Context context){
        runOnUiThread(new MyShow(context));
    }
}
