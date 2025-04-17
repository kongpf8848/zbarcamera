package com.zbar.client;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;


import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.zbar.camera.R;


public class MainActivity extends BaseActivity {

    Toolbar toolbar;
    ImageButton btn_home_scan;
    EditText et_mailno_search;


    private final int REQUEST_CODE_CAMERA = 1;

    @Override
    public int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    public void initData() {
        toolbar = findViewById(R.id.toolbar);
        btn_home_scan = findViewById(R.id.btn_home_scan);
        et_mailno_search = findViewById(R.id.et_mailno_search);

        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.app_name);

        btn_home_scan.setOnClickListener(v -> onScan());


        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA);

    }

    public void onScan() {
        et_mailno_search.setText("");
        Intent intent = new Intent(MainActivity.this, CaptureActivity.class);
        startActivityForResult(intent, 1);

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            Bundle bundle = data.getExtras();
            String scanResult = bundle.getString("result");
            et_mailno_search.setText(scanResult);
            et_mailno_search.setSelection(et_mailno_search.length());
        }
    }
}