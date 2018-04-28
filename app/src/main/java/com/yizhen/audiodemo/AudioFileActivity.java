package com.yizhen.audiodemo;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class AudioFileActivity extends AppCompatActivity {

    @BindView(R.id.tv_file_record)
    TextView tvFileRecord;
    @BindView(R.id.btn_file_record)
    Button btnFileRecord;
    @BindView(R.id.btn_file_stop)
    Button btnFileStop;

    private MediaRecorder mMediaRecorder;
    private File audioFile;
    private long startTime = 0;
    private long endTime = 0;
    private ExecutorService mExecutorService;
    private Handler mMainThreadHandler;
    // 主线程和后台播放线程同步 volatile
    /*
    对于volatile类型的变量，系统每次用到他的时候都是直接从对应的内存当中提取，而不会利用cache当中的原有数值，
    以适应它的未知何时会发生的变化，系统对这种变量的处理不会做优化——显然也是因为它的数值随时都可能变化的情况。
     */
    private volatile boolean mIsPlaying = false;
    private MediaPlayer mMediaPlayer;
    // 录制最短时长
    private final int DURATION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_file);
        ButterKnife.bind(this);
        // 录音 JNI 函数，不具备线程安全性，所以要用单线程 --- why
        mExecutorService = Executors.newSingleThreadExecutor();
        mMainThreadHandler = new Handler(Looper.getMainLooper()); // 获取主线程的handler
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @OnClick({R.id.btn_file_record, R.id.btn_file_stop, R.id.btn_file_play})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.btn_file_record:
//                Toast.makeText(this, "开始录制", Toast.LENGTH_SHORT).show();
                // 开始录制
                startRecord();
                break;
            case R.id.btn_file_stop:
                if (audioFile != null){
                    // 停止录音
                    stopRecorder();
                } else {
                    Toast.makeText(this, "请点击录制", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.btn_file_play:{
                // 检查当前状态，防止重复播放，同时检查录音文件是否存在 即 audioFile
                if(audioFile != null && !mIsPlaying){
                    // 开启线程服务，执行播放逻辑
                    mExecutorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            doPlay(audioFile);
                        }
                    });
                } else if(audioFile == null){
                    Toast.makeText(this, "播放文件不存在，请点击录制", Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Activity 销毁时，停止后台任务，防止内存泄漏
        mExecutorService.shutdownNow();
        releaseRecorder();
        // 销毁时，为什么也要停止播放  ？？？？？
        stopPlaying();
    }

    /**
     * 播放逻辑
     * @param audioFile
     */
    private void doPlay(File audioFile) {
        // 初始化播放器 MediaPlayer
        mMediaPlayer = new MediaPlayer();
        // 配置参数
        try{
            // 播放源
            mMediaPlayer.setDataSource(audioFile.getAbsolutePath());
            // 播放完成监听
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    // 播放完成释放播放器
                    stopPlaying();
                }
            });
            // 播放错误监听
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    // 提示用户
                    playFail();
                    // 停止播放
                    stopPlaying();
                    // true 表示处理播放错误
                    return true;
                }
            });
            // 配置音量
            mMediaPlayer.setVolume(1, 1);// 1 通用的声音
            // 配置是否循环
            mMediaPlayer.setLooping(false);
            // 准备 开始播放
            mMediaPlayer.prepare();
            mMediaPlayer.start();
        }catch (IOException e){
            e.printStackTrace();
            // 异常 停止播放释放播放器, 提示用户
            // 提示用户
            playFail();
            // 停止播放
            stopPlaying();
        }
    }

    /**
     * 播放完成，释放播放器
     */
    private void stopPlaying() {
        // 重置播放状态
        mIsPlaying = false;
        // 判断 MediaPlayer 是否为null
        // 释放播放器
        if(mMediaPlayer != null){
            // 重置监听器，防止内存泄漏
            mMediaPlayer.setOnErrorListener(null);
            mMediaPlayer.setOnCompletionListener(null);
            mMediaPlayer.stop();
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    /**
     * 提示用户播放失败
     */
    private void playFail() {
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(AudioFileActivity.this, "播放失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 开始录音
     */
    private void startRecord() {
        // 改变UI状态
        btnFileRecord.setText("录制中...");

        // 执行后台任务，执行录音逻辑
        mExecutorService.submit(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void run() {
                // 录音之前，先释放原来的录音（有可能没释放掉，大多数时候，会在停止录音时释放）
                releaseRecorder();
                // 录音，有可能失败,提示用户
                if(!doStart()){
                    recordFail();
                }
            }
        });
    }
    /**
     * 停止录音
     */
    private void stopRecorder() {
        // 改变UI状态
        btnFileRecord.setText("点击录制");

        // 执行后台任务，执行录音逻辑
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                // 执行停止录音逻辑，有可能失败，提示用户
                if(!doStop()){
                    recordFail();
                }
                // 释放 MediaRecorder
                releaseRecorder();
            }
        });
    }

    /**
     * 开始录音逻辑
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private boolean doStart() {
            try{
                // 创建 MediaRecorder
                mMediaRecorder = new MediaRecorder();
                // 创建文件，用来存放录制的音频文件
                audioFile = createFile();
                // 配置 MediaRecorder
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);// 录制输出文件格式
                mMediaRecorder.setAudioSamplingRate(44100);
                mMediaRecorder.setAudioEncodingBitRate(96000); // 音质比较好哒
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);//编码格式，aac是通用的
                mMediaRecorder.setOutputFile(audioFile.getAbsolutePath());// 录制好的文件保存到哪里, 为什么要getAbsolutePath(), 只写文件不行
                // 开始录音
                mMediaRecorder.prepare();
                mMediaRecorder.start();
                // 记录开始时间
                startTime = System.currentTimeMillis();
            }catch (Exception e){
                e.printStackTrace(); // 捕获异常，避免闪退，return false 提醒用户
                return false;
            }
            // 录音成功
            return true;
    }

    /**
     * 停止录音逻辑
     * @return
     */
    private boolean doStop() {
        try{
            // 停止录音
            mMediaRecorder.stop();
            // 记录停止的时间
            endTime = System.currentTimeMillis();
            // 只接受超过3秒的录音，在UI上显示
            final int duration = (int) ((endTime - startTime) / 1000);
            if(duration > DURATION){
                // 线程更新UI
                mMainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        tvFileRecord.setText("录音 " + duration + " 秒");
                    }
                });
            }
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
        // 停止成功
        return true;
    }

    /**
     * 释放录音器
     */
    private void releaseRecorder() {

        // 检查 MediaRecorder
        if (mMediaRecorder != null){
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    /**
     * 录音失败
     */
    private void recordFail() {
        mMediaRecorder = null;
        // 吐司提示用户，要在主线程提示用户，因为这本身是在Executor的线程里执行
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(AudioFileActivity.this, "录制失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 创建文件
     * @return
     */
    private File createFile() {
        String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        File dirFile = new File(dirPath, "recordFile");
        if(!dirFile.exists()){
            dirFile.mkdirs();
        }
        File file = new File(dirFile, "abc"+".aac");
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }
}
