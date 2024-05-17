package com.bz.show;

import android.app.Activity;

public class BanZ extends Activity {
    public void run(){
        runOnUiThread(new MyShow(this));
    }
}
