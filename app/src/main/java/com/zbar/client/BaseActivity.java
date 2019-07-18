package com.zbar.client;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import butterknife.ButterKnife;

/**
 * Created by pengf on 2017/3/29.
 */

public abstract class BaseActivity extends AppCompatActivity {

    public abstract int getLayoutId();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initBefore();
        setContentView(getLayoutId());

        ButterKnife.bind(this);
        initData();
    }


    protected void initBefore() {

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
