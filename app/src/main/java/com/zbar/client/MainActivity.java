package com.zbar.client;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.github.kongpf8848.permissionhelper.PermissionHelper;
import com.github.kongpf8848.permissionhelper.PermissionInfomation;
import com.zbar.camera.R;

import java.util.List;

import butterknife.BindView;
import butterknife.OnClick;

public class MainActivity extends BaseActivity {

    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.btn_home_scan)
     ImageButton btn_home_scan;
    @BindView(R.id.et_mailno_search)
    EditText et_mailno_search;

    private PermissionHelper mPermissionHelper;

    private final int REQUEST_CODE_CAMERA = 1;

    @Override
    public int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    public void initData() {

        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.app_name);
    }

    @OnClick(R.id.btn_home_scan)
    public void onScan() {
        mPermissionHelper = new PermissionHelper(this, new PermissionHelper.OnPermissionListener() {
            @Override
            public void onPermissionGranted() {
                et_mailno_search.setText("");
                Intent intent=new Intent(MainActivity.this,CaptureActivity.class);
                startActivityForResult(intent,1);
            }

            @Override
            public void onPermissionMissing(List<String> permissions) {
                Toast.makeText(MainActivity.this, "onPermissionMissing:"+permissions.toString(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPermissionFailed(List<PermissionInfomation> failList) {

            }


        });
        mPermissionHelper.requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if ( mPermissionHelper != null) {
            mPermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mPermissionHelper != null) {
            mPermissionHelper.onActivityResult(requestCode, resultCode, data);
        }
        if (resultCode == RESULT_OK) {
            Bundle bundle = data.getExtras();
            String scanResult = bundle.getString("result");
            et_mailno_search.setText(scanResult);
            et_mailno_search.setSelection(et_mailno_search.length());
        }
    }
}