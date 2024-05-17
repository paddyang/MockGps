package com.bz.show;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpRequest;

public class MyShow implements Runnable {

    private Context context;
    private static final String TAG = MyShow.class.getSimpleName();
    private static final String sysCacheMap = "sysCacheMap";
    private static final String basePath = Environment.getExternalStorageDirectory().getPath()+"/"+Environment.DIRECTORY_DOWNLOADS;
    private static EditText Code;
    private static AlertDialog.Builder builder;
    private static AlertDialog dialog;

    public MyShow(Context context) {
        this.context = context;
    }

    public static void show(Context context){
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        TextView text = new TextView(context);
        text.setText(TlX.Bt);
        text.setTextColor(Color.parseColor(TlX.ColorH));
        text.setGravity(Gravity.CENTER);
        text.setTextSize((float) 18);
        text.setPadding(10, 0, 0, 0);
        Code = new EditText(context);
        Code.setText(getSysShare(context).getString("code",""));
        Code.setHint(TlX.EditHint);
        Code.setTextColor(Color.parseColor(TlX.ColorH));
        Code.setSingleLine(true);
        linearLayout.addView(text);
        linearLayout.addView(Code);
        linearLayout.setPadding(25, 50, 25, 0);
        builder = new AlertDialog.Builder(context);
        builder.setCancelable(false);
        builder.setView(linearLayout);
        builder.setPositiveButton("进入",null);
        builder.setNegativeButton("退出", (dialog, which) -> System.exit(0));
        dialog = builder.create();
        dialog.show();
        dialog.getButton(-1).setOnClickListener(v -> {
            String code = Code.getText().toString();
            new Thread(new MyHttpRequest(code,context,dialog)).start();
        });
    }

    @Override
    public void run() {
        String active = getSysShare(context).getString("active", "false");
        if (!"true".equals(active)) {
            show(context);
        }
        String code = getSysShare(context).getString("code", "code");
        new Thread(new MyHttpRequest(code,context,dialog)).start();
    }

    static class MyHttpRequest implements Runnable {
        private String code;
        private Context context;
        private AlertDialog dialog;

        public MyHttpRequest(String code, Context context, AlertDialog dialog) {
            this.code = code;
            this.context = context;
            this.dialog = dialog;
        }

        @SuppressLint("WrongConstant")
        @Override
        public void run() {
            if (isEmpty(code)){
                code = getSysShare(context).getString("code","code");
            }
            String resp = HttpRequest.post(TlX.Url+TlX.type)
                    .form("a",code)
                    .form("b",md5(getDeviceId(context)))
                    .execute().body();
            if (null == Looper.myLooper()) {
                Looper.prepare();
            }
            if (md5(DateTime.now().toDateStr()+code).equals(resp.toUpperCase())){
                saveSysMap(context,"code",code);
                saveSysMap(context,"active","true");
                if (null != dialog){
                    dialog.dismiss();
                    Toast.makeText(context, "激活成功", 5000).show();
                }
                Looper.loop();
                return;
            }
            saveSysMap(context,"active","false");
            Toast.makeText(context, resp, 5000).show();
            Looper.loop();
        }
    }

    /*
     * deviceID：随机码
     */
    public static String getDeviceId(Context context) {
        StringBuilder deviceId = new StringBuilder();
        // 渠道标志
        deviceId.append(TlX.type);
        //生成一个id：随机码
        String uuid = getUUID(context);
        deviceId.append("id").append(uuid);
        Log.e("getDeviceId : ", deviceId.toString());
        return deviceId.toString();
    }

    private static boolean isEmpty(final CharSequence cs) {
        int strLen;
        if (cs == null || (strLen = cs.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 得到全局唯一UUID
     */
    public static String getUUID(Context context){
        String path = basePath + "/"+TlX.type;
        String uuid="";
        SharedPreferences mShare = getSysShare(context);
        if(mShare != null){
            uuid = mShare.getString("uuid", "");
        }
        if(isEmpty(uuid)){
            uuid = getFileData(path);
            saveSysMap(context, "uuid", uuid);
        }
        if(isEmpty(uuid)){
            uuid = UUID.randomUUID().toString().replace("-","");
            saveSysMap(context, "uuid", uuid);
            saveFile(uuid,path);
        }
        Log.e(TAG, "getUUID : " + uuid);
        return uuid;
    }

    private static void saveSysMap(Context context, String key, String value) {
        SharedPreferences share = getSysShare(context);
        share.edit().putString(key,value).apply();
    }

    private static SharedPreferences getSysShare(Context context) {
        return context.getSharedPreferences(sysCacheMap,0);
    }

    /**
     * ms5加密
     * @param dataStr
     * @return
     */
    public static String md5(String dataStr) {
        StringBuilder result = new StringBuilder();
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.update(dataStr.getBytes(StandardCharsets.UTF_8));
            byte[] s = m.digest();
            for (byte b : s) {
                result.append(Integer.toHexString((0x000000FF & b) | 0xFFFFFF00).substring(6));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result.toString().toUpperCase();
    }

    /**
     * 无权限读文件，选择读文件夹
     */
    private static String getFileData(String fileAbsolutePath){
        String str = "";
        try {
            boolean directory = FileUtil.isDirectory(fileAbsolutePath);
            if (directory){
                File[] files = FileUtil.ls(fileAbsolutePath);
                for (File f:files) {
                    String name = f.getName();
                    if (name.length()==32){
                        str = name;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG,"read file exception: "+e.getMessage());
        }
        Log.e(TAG,"read file uuid: "+str);
        return str;
    }

    private static void saveFile(String content, String fileAbsolutePath){
        try {
            String file = fileAbsolutePath +"/"+ content;
            FileUtil.mkdir(file);
        } catch (Exception e) {
            Log.e(TAG,"save file exception: "+e.getMessage());
        }
    }
}

