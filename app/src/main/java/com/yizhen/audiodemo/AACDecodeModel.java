package com.yizhen.audiodemo;


import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Created by Administrator on 2018/5/7.
 */

public class AACDecodeModel {

    //用于分离出音频轨道
    private MediaExtractor mMediaExtractor;
    private MediaCodec mMediaDecode;
    private File targetFile;
    //类型
    private String mime = "audio/mp4a-latm";
    //输入缓存组
    private ByteBuffer[] inputBuffers;
    //输出缓存组
    private ByteBuffer[] outputBuffers;
    private MediaCodec.BufferInfo bufferInfo;
    private File pcmFile;
    private FileOutputStream fileOutputStream;
    private int totalSize = 0;

    public AACDecodeModel(){
        prepare();
    }

    public void prepare(){
        File root = new File(Environment.getExternalStorageDirectory(), "/recordFile");
        targetFile = new File(root, "生成的aac.aac");
        pcmFile = new File(root, "解码的pcm.pcm");
        if (!pcmFile.exists()) {
            try {
                pcmFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            fileOutputStream = new FileOutputStream(pcmFile.getAbsoluteFile());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        mMediaExtractor = new MediaExtractor();
        try {
            //设置资源
//            InputStream in = new InputStream;
            mMediaExtractor.setDataSource(targetFile.getAbsolutePath());
            //获取含有音频的MediaFormat
            MediaFormat mediaFormat = createMediaFormat();
            mMediaDecode = MediaCodec.createDecoderByType(mime);
            mMediaDecode.configure(mediaFormat, null, null, 0);//当解压的时候最后一个参数为0
            mMediaDecode.start();//开始，进入runnable状态
            //只有MediaCodec进入到Runnable状态后，才能过去缓存组
            inputBuffers = mMediaDecode.getInputBuffers();
            outputBuffers = mMediaDecode.getOutputBuffers();
            bufferInfo = new MediaCodec.BufferInfo();
        } catch (IOException e) {
            Log.e("tag_ioException",e.getMessage()+"");
            e.printStackTrace();
        }
    }

    private MediaFormat createMediaFormat() {
        //获取文件的轨道数，做循环得到含有音频的mediaFormat
        for (int i = 0; i < mMediaExtractor.getTrackCount(); i++) {
            MediaFormat mediaFormat = mMediaExtractor.getTrackFormat(i);
//            int bit_rate = mediaFormat.getInteger(MediaFormat.KEY_BIT_RATE);
//            int sample_rate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
//            Log.e("zhen2", "bit_rate="+bit_rate+" sample_rate="+sample_rate);
            //MediaFormat键值对应
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mime.contains("audio/")) {
                mMediaExtractor.selectTrack(i);
                return mediaFormat;
            }
        }
        return null;
    }

    private int BUFFER_SIZE = 1024*2;

    private AudioTrack mAudioTrack;
    // 初始化 AudioTrack
    private void init(){
        int streamType = AudioManager.STREAM_MUSIC;
        int sampleRateInHz = 44100; //  要和录制时的采样率一样
        int channelConfig = AudioFormat.CHANNEL_OUT_STEREO; // 要和录制时采样通道一致
        int audioForMat = AudioFormat.ENCODING_PCM_16BIT; // 要和录制时格式一样
        int bufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, audioForMat);

        int mode = AudioTrack.MODE_STREAM;
        mAudioTrack = new AudioTrack(streamType, sampleRateInHz, channelConfig, audioForMat, Math.max(bufferSizeInBytes, BUFFER_SIZE), mode);
    }


    public void decode() {
        Log.e("zhen", "开始解码");
        boolean inputSawEos = false;
        boolean outputSawEos = false;
        long kTimes = 5000;//循环时间
        while (!outputSawEos) {
            if (!inputSawEos) {
                //每5000毫秒查询一次
                int inputBufferIndex = mMediaDecode.dequeueInputBuffer(kTimes);
                //输入缓存index可用
                if (inputBufferIndex >= 0) {
                    //获取可用的输入缓存
                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                    //从MediaExtractor读取数据到输入缓存中，返回读取长度
                    int bufferSize = mMediaExtractor.readSampleData(inputBuffer, 0);
                    if (bufferSize <= 0) {//已经读取完
                        //标志输入完毕
                        inputSawEos = true;
                        //做标识
                        mMediaDecode.queueInputBuffer(inputBufferIndex, 0, 0, kTimes, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    } else {
                        long time = mMediaExtractor.getSampleTime();
                        //将输入缓存放入MediaCodec中
                        mMediaDecode.queueInputBuffer(inputBufferIndex, 0, bufferSize, time, 0);
                        //指向下一帧
                        mMediaExtractor.advance();
                    }
                }
            }
            //获取输出缓存，需要传入MediaCodec.BufferInfo 用于存储ByteBuffer信息
            int outputBufferIndex = mMediaDecode.dequeueOutputBuffer(bufferInfo, kTimes);
            if (outputBufferIndex >= 0) {
                int id = outputBufferIndex;
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    mMediaDecode.releaseOutputBuffer(id, false);
                    continue;
                }
                //有输出数据
                if (bufferInfo.size > 0) {
                    //获取输出缓存
                    ByteBuffer outputBuffer = outputBuffers[id];
                    //设置ByteBuffer的position位置
                    outputBuffer.position(bufferInfo.offset);
                    //设置ByteBuffer访问的结点
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                    byte[] targetData = new byte[bufferInfo.size];
                    //将数据填充到数组中
                    outputBuffer.get(targetData);
                    try {
                        fileOutputStream.write(targetData);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                //释放输出缓存
                mMediaDecode.releaseOutputBuffer(id, false);
                //判断缓存是否完结
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputSawEos = true;
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = mMediaDecode.getOutputBuffers();

            }else if(outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                MediaFormat  mediaFormat = mMediaDecode.getOutputFormat();
            }
        }
        //释放资源
        try {
            fileOutputStream.flush();
            fileOutputStream.close();
            mMediaDecode.stop();
            mMediaDecode.release();
            mMediaExtractor.release();
            Log.e("zhen", "解码完成");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
