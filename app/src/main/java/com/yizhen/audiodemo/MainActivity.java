package com.yizhen.audiodemo;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;


public class MainActivity extends AppCompatActivity {


    @BindView(R.id.btn_file_mode)
    Button btnFileMode;
    @BindView(R.id.btn_stream_mode)
    Button btnStreamMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

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
