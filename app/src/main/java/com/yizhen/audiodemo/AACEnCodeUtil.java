package com.yizhen.audiodemo;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Administrator on 2018/5/2.
 */

public class AACEnCodeUtil {

    private MediaCodec mMediaCodec;
    private MediaCodec.BufferInfo mBufferInfo;
    private final String mime = "audio/mp4a-latm";
    private int bitRate = 96000;
    private ByteBuffer[] inputBufferArray;
    private ByteBuffer[] outputBufferArray;
    private FileOutputStream fileOutputStream;

    public AACEnCodeUtil(){
        initMediaCodec();
    }

    private void initMediaCodec() {
        try {
            String root = Environment.getExternalStorageDirectory() + "/recordFile";
            File fileAAc = new File(root,"生成的aac.aac");
            if(!fileAAc.exists()){
                fileAAc.createNewFile();
            }
            if(!fileAAc.exists()){
                fileAAc.createNewFile();
            }
            fileOutputStream = new FileOutputStream(fileAAc.getAbsoluteFile());
            mMediaCodec = MediaCodec.createEncoderByType(mime);
            MediaFormat mediaFormat = new MediaFormat();
            mediaFormat.setString(MediaFormat.KEY_MIME, mime);
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000);
            mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
            mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024);
            mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
    /*
       第四个参数 编码的时候是MediaCodec.CONFIGURE_FLAG_ENCODE
                  解码的时候是0
     */
            mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            //start（）后进入执行状态，才能做后续的操作
            mMediaCodec.start();
            //获取输入缓存，输出缓存
            inputBufferArray = mMediaCodec.getInputBuffers();

            Log.e("zhen1", "inputBufferArray="+inputBufferArray.length);

            for(int i = 0; i < inputBufferArray.length; i++){
                Log.e("zhen1", "inputBufferArray*"+i+"="+inputBufferArray[i]);
            }

            outputBufferArray = mMediaCodec.getOutputBuffers();

            mBufferInfo = new MediaCodec.BufferInfo();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    byte[]  totalByte = new byte[0];

    public  byte[]  encodeData(byte[] data, int length){

        Log.e("zhen", "data="+ data.length);
        //dequeueInputBuffer（time）需要传入一个时间值，-1表示一直等待，0表示不等待有可能会丢帧，其他表示等待多少毫秒
        int inputIndex = mMediaCodec.dequeueInputBuffer(-1);//获取输入缓存的index
        Log.e("zhen", "inputIndex="+ inputIndex);
        if (inputIndex >= 0) {
            ByteBuffer inputByteBuf = inputBufferArray[inputIndex];
            Log.e("zhen", "inputByteBuf**"+inputIndex+"="+ inputByteBuf);
            inputByteBuf.clear();
            Log.e("zhen", "inputByteBuf*clear="+ inputByteBuf);
            inputByteBuf.put(data);//添加数据
            inputByteBuf.limit(length);//限制ByteBuffer的访问长度
            mMediaCodec.queueInputBuffer(inputIndex, 0, data.length, 0, 0);//把输入缓存塞回去给MediaCodec
        }
        Log.e("zhen", "mBufferInfo="+ mBufferInfo.size);
        int outputIndex;
        while(true){
            outputIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 0);//获取输出缓存的index
            Log.e("zhen", "outputIndex="+ outputIndex);
            if(outputIndex >= 0){
                break;
            }
        }
        while (outputIndex >= 0) {
            //获取缓存信息的长度sd
            int byteBufSize = mBufferInfo.size;
            //添加ADTS头部后的长度
            int bytePacketSize = byteBufSize + 7;

            ByteBuffer  outPutBuf = outputBufferArray[outputIndex];
            outPutBuf.position(mBufferInfo.offset);
            outPutBuf.limit(mBufferInfo.offset+mBufferInfo.size);

            byte[] targetByte = new byte[bytePacketSize];
            //添加ADTS头部
            addADTStoPacket(targetByte,bytePacketSize);
            /*
            get（byte[] dst,int offset,int length）:ByteBuffer从position位置开始读，读取length个byte，并写入dst下
            标从offset到offset + length的区域
             */
            outPutBuf.get(targetByte,7,byteBufSize);

            outPutBuf.position(mBufferInfo.offset);

            try {
                // 这个是封装的二进制的aac流  targetByte
                // 这个是封装的二进制的aac流  targetByte
                Log.e("zhen", "targetByte = "+targetByte.length);
                if(totalByte.length != 0){
                    totalByte = new byte[totalByte.length + targetByte.length];
                    System.arraycopy(targetByte, 0, totalByte, 0, targetByte.length);
                } else {
                    totalByte = targetByte;
                }
                Log.e("zhen", "totalByte="+totalByte.length);

                fileOutputStream.write(targetByte);

            } catch (IOException e) {
                e.printStackTrace();
            }
            //释放
            mMediaCodec.releaseOutputBuffer(outputIndex,false);
            outputIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo,0);
        }
        return totalByte;
    }
    /**
     * 给编码出的aac裸流添加adts头字段
     *
     * @param packet    要空出前7个字节，否则会搞乱数据
     * @param packetLen
     */
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        int freqIdx = 4;  //44.1KHz
        int chanCfg = 2;  //CPE
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

}
