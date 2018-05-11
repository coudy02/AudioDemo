package com.yizhen.audiodemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;


public class MainActivity extends AppCompatActivity {


    private static final String[] PERMISSIONS_STORAGE = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO};
    private static final int REQUEST_EXTERNAL_STORAGE = 0;
    @BindView(R.id.btn_file_mode)
    Button btnFileMode;
    @BindView(R.id.btn_stream_mode)
    Button btnStreamMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        /*
        6.0 以后的系统，
         */
       int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permission3 = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
       int permission2 =  ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
       if(permission != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
       }
        if( permission2 != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_EXTERNAL_STORAGE);
        }
        if( permission3 != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_EXTERNAL_STORAGE);
        }
    }

    @OnClick({R.id.btn_file_mode, R.id.btn_stream_mode})
    public void onViewClicked(View view) {

        Intent intent = null;

        switch (view.getId()) {
            case R.id.btn_file_mode:

                Toast.makeText(this, "文件模式", Toast.LENGTH_SHORT).show();

                intent = new Intent(this, AudioFileActivity.class);
                startActivity(intent);

                break;
            case R.id.btn_stream_mode:
                Toast.makeText(this, "字节流模式", Toast.LENGTH_SHORT).show();

                intent = new Intent(this, AudioStreamActivity.class);
                startActivity(intent);

                break;
        }
    }
}
