package com.yizhen.audiodemo;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class AudioStreamActivity extends AppCompatActivity {

    @BindView(R.id.btn_stream_record)
    Button btnStreamRecord;
    @BindView(R.id.tv_stream_record)
    TextView tvStreamRecord;

    @BindView(R.id.btn_stream_play)
    Button btnStreamPlay;

    // 创建 AudioRecorder
    AudioRecord mAudioRecord;
    // 是否正在录制的标识, 一定要用 volatile
    private volatile boolean mIsRecording = false;
    private volatile boolean mIsPlaying = false;
    private ExecutorService mExecutorService;
    private Handler mMainThreadHandler;
    private long mStartTime;
    private long mEndTime;
    private File audioFile;
    private FileOutputStream mFileOutputStream;
    private int BUFFER_SIZE = 1024 * 2;
    private byte[] mBuffer;
    private FileInputStream fis = null;
    private AudioTrack mAudioTrack;
    // 录制最短时长
    private final int DURATION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_stream);
        ButterKnife.bind(this);
        mExecutorService = Executors.newSingleThreadExecutor();
        mMainThreadHandler = new Handler(Looper.getMainLooper());
        mBuffer = new byte[BUFFER_SIZE];
    }

    @OnClick({R.id.btn_stream_record, R.id.btn_stream_play})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.btn_stream_record: {
                if (!mIsRecording) {
                    // 如果在录制，更新UI
                    btnStreamRecord.setText("停止");
                    // 更新录制标识
                    mIsRecording = true;
                    // 后台执行，录制任务
                    mExecutorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            if (!startRecord()) { // 将录音封装到函数里，如果不成功，提示用户
                                recordFail();
                            }
                        }
                    });
                } else {
                    // 停止录制，更新UI
                    btnStreamRecord.setText("录制");
                    // 更新录制标识
                    mIsRecording = false;
                }
                break;
            }
            case R.id.btn_stream_play: {
                // 检查播放状态，防止重复播放
                if(audioFile != null & !mIsPlaying){
                    mIsRecording = true;
                    // 播放逻辑，后台执行
                    mExecutorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            // 播放，如果播放失败，提示用户
                            if (!doPlay()) {
                                playFail();
                            }
                        }
                    });
                } else if(audioFile == null){
                    Toast.makeText(AudioStreamActivity.this, "播放文件不存在", Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 销毁之前，释放资源，避免内存泄漏
        mExecutorService.shutdownNow();
    }

    /**
     * 播放 pcm 文件
     * @return
     */
    private boolean doPlay() {
        // 实例化播放器  AudioTrack
        //AudioTrack(int streamType, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes, int mode)
        int streamType = AudioManager.STREAM_MUSIC;
        int sampleRateInHz = 44100; //  要和录制时的采样率一样
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO; // 要和录制时采样通道一致
        int audioForMat = AudioFormat.ENCODING_PCM_16BIT; // 要和录制时格式一样
        int bufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, audioForMat);
                        /*
                         AudioTrack.MODE_STREAM 按照一定的规定不断的传递给接收方，理论上适用于任何音频播放的场景。
                         实际应用于这三个场景：1. 音频比较大；2. 音频属性要求比较高，采样率高、深度大的数据【深度如何理解,指的是8bit和16bit】；
                                              3. 音频数据实时产生，也只能有流模式
                          */
        // AudioTrack.MODE_STATIC 适用于音频文件比较小的，如铃声，系统提醒暂用内存比较小的操作
        int mode = AudioTrack.MODE_STREAM;
        mAudioTrack = new AudioTrack(streamType, sampleRateInHz, channelConfig, audioForMat, Math.max(bufferSizeInBytes, BUFFER_SIZE), mode);
        try {
            mMainThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    btnStreamPlay.setText("播放中...");
                }
            });

            // 读入 文件
            fis = new FileInputStream(audioFile);
            int read;
            mAudioTrack.play();
            while((read = fis.read(mBuffer)) >  0){
                mAudioTrack.write(mBuffer, 0, read);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            // 播放失败，提示用户
            playFail();
            return false;
        } finally {
            mIsRecording = false;
            mMainThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    btnStreamPlay.setText("播放");
                }
            });


            // 关闭输入流
            if (fis != null){ // 因为 关闭 文件输入流会产生异常，写在这里，代码臃肿，另起一个方法
                closeQuietly();
            }
            releaseAudioTrack();
        }
    }

    /**
     * 开始录音
     *
     * @return
     */
    private boolean startRecord() {
        try {
            // 创建录音文件
            audioFile = createFile();
            // 创建文件输出流
            mFileOutputStream = new FileOutputStream(audioFile);
            //(int audioSource, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes)
            int audioSource = MediaRecorder.AudioSource.MIC;
            int sampleRateInHz = 44100;
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;//单声道
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT; // 录制的格式
            // 计算 AudioRecord 里内部里 buffer 的 最小大小 --------有啥用呢？？？？
            int minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
            // buffer 不能小于最低要求，也不能小于我们每次读取的大小
            mAudioRecord = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, Math.max(minBufferSize, BUFFER_SIZE));
            // 开始录音
            mAudioRecord.startRecording();
            // 记录开始时间
            mStartTime = System.currentTimeMillis();
            // 循环读取数据，写到输出流中
            while (mIsRecording) {
                // 只要还在录音转态，就一直读
                int read = mAudioRecord.read(mBuffer, 0, BUFFER_SIZE);
                if (read > 0) { // 读取成功
                    mFileOutputStream.write(mBuffer, 0, read);
                } else {  // 读取失败, 提示用户
                    recordFail();
                    return false;
                }
            }
            // 退出循环，停止录音，释放录音器
            return stopRecord();
        } catch (IOException e) {
            e.printStackTrace();
            // 捕获异常，避免闪退，提示用户
            recordFail();
            return false;
        } finally {
            // 释放资源
            if (mAudioRecord != null) {
                mAudioRecord.release();
            }
        }
    }

    /**
     * 结束录音逻辑
     */
    private boolean stopRecord() {
        try {
            // 停止录音，关闭文件输出流
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
            mFileOutputStream.close();
            // 记录时间
            // 记录停止的时间
            mEndTime = System.currentTimeMillis();
            // 只接受超过3秒的录音，在UI上显示
            final int duration = (int) ((mEndTime - mStartTime) / 1000);
            if (duration > DURATION) {
                // 线程更新UI
                mMainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        tvStreamRecord.setText("录音 " + duration + " 秒");
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 录音失败，提示用户
     */
    private void recordFail() {

        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(AudioStreamActivity.this, "录音失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 播放失败提示用户
     */
    private void playFail() {
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(AudioStreamActivity.this, "播放失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 释放AudioTrack 播放器
     */
    private void releaseAudioTrack() {
        try{
            //释放播放器
            mAudioTrack.stop();
            mAudioTrack.release();
        }catch (RuntimeException e){
            e.printStackTrace();
        }
    }

    /**
     * 静默关闭文件输入流
     */
    private void closeQuietly() {
        try {
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建文件
     *
     * @return
     */
    private File createFile() {
        String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        File dirFile = new File(dirPath, "recordFile");
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }
        File file = new File(dirFile, "aaa" + ".pcm");
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }


}
