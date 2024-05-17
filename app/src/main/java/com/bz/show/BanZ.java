package com.bz.show;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

public class BanZ extends AppCompatActivity {

    public void run(Context context){
        runOnUiThread(new MyShow(context));
    }
}
