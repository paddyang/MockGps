package com.bz.show;

import android.app.ProgressDialog;
import android.content.Context;

public class Loading {
    private static ProgressDialog Loading;

    public Loading() {
    }

    public static void hide() {
        if (Loading != null) {
            Loading.dismiss();
        }
    }

    public static void show(Context context) {
        Loading = new ProgressDialog(context, 5);
        Loading.setMessage("通信中........");
        Loading.setCancelable(false);
        Loading.show();
    }
}
