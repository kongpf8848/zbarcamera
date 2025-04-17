package com.zbar.client;

import android.content.Intent;
import android.os.Bundle;


import androidx.appcompat.app.AppCompatActivity;


/**
 * Created by pengf on 2017/3/29.
 */

public abstract class BaseActivity extends AppCompatActivity {

    public abstract int getLayoutId();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutId());

        initData();
    }


    protected void initData() {

    }

    public void startActivity(Class<?> cls) {
        startActivity(cls, false);
    }

    public void startActivity(Class<?> cls, boolean bFinish) {
        Intent intent = new Intent(this, cls);
        startActivity(intent);
        if (bFinish) {
            this.finish();
        }
    }


}
