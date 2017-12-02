package com.xjh.gin.im;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Gin on 2017/11/28.
 */

public class StreamActivity extends AppCompatActivity implements View.OnClickListener {
    private Button btn_start;
    private TextView mTvLog;

    private volatile boolean mIsRecording;//volatile保证多线程内存同步
    private ExecutorService mExecutorService;
    private Handler mMainThreadHandler;

    private byte[] mBuffer;//不能太大
    private static final int BUFFER_SIZE = 2048;
    private File mAudioFile;
    private long mStartRecordTime, mStopRecordTime;

    private FileOutputStream mFileOutputStream;
    private AudioRecord mAudioRecord;


    private Button mBtn;
    private volatile boolean mIsPlaying;//主线程和后台播放线程的数据同步

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);
        initView();
        initEvent();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //activity销毁时，停止后台任务，避免内存泄露
        mExecutorService.shutdownNow();
    }

    private void initEvent() {
        btn_start.setOnClickListener(this);
        mBtn.setOnClickListener(this);
    }

    private void initView() {
        btn_start = this.findViewById(R.id.mBtnStart);
        mBtn = this.findViewById(R.id.mBtnPlay);
        mTvLog = this.findViewById(R.id.mTvLogs);
        //录音的JNI函数不具备线程安全性，所以要用单线程
        mExecutorService = Executors.newSingleThreadExecutor();
        //主线程的Handler
        mMainThreadHandler = new Handler(Looper.getMainLooper());
        mBuffer = new byte[BUFFER_SIZE];
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.mBtnStart:
                if (mIsRecording) {
                    mIsRecording = false;
                    btn_start.setText("开始");
                } else {
                    mIsRecording = true;
                    btn_start.setText("停止");

                    //提交后台任务，执行录音逻辑
                    mExecutorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            if (!startRecord()) {
                                recordFail();
                            }
                        }
                    });
                }
            break;
            case R.id.mBtnPlay:
                //Toast.makeText(StreamActivity.this,mAudioFile.toString(),Toast.LENGTH_SHORT).show();
                if(mAudioFile != null&&!mIsPlaying){
                    //Toast.makeText(StreamActivity.this,"s1",Toast.LENGTH_SHORT).show();
                    //设置当前的播放状态
                    mIsPlaying=true;

                    //提交后台任务，播放
                    mExecutorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            doPlay(mAudioFile);
                        }
                    });
                }
                //Toast.makeText(StreamActivity.this,"s2  ",Toast.LENGTH_SHORT).show();
            break;
        }
    }

    private boolean startRecord() {
        try {
            //创建录音文件
            mAudioFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/sound/" + System.currentTimeMillis() + ".pcm");//获取绝对路径
            mAudioFile.getParentFile().mkdirs();//保证路径是存在的
            mAudioFile.createNewFile();

            //创建文件输入流
            mFileOutputStream = new FileOutputStream(mAudioFile);

            //配置 AudioRecord
            int audioSource = MediaRecorder.AudioSource.MIC;//从麦克风采集
            int sampleRate = 44100;//采样频率（越高效果越好，但是文件相应也越大，44100是所有安卓系统都支持的采样频率）
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;//单声道输入
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;//PCM 16 是所有安卓系统都支持的
            int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);//计算 AudioRecord 内部 buffer 最小的大小

            mAudioRecord = new AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, Math.max(minBufferSize, BUFFER_SIZE));            //buffer 不能小于最低要求，也不能小于我们每次读取的大小

            //开始录音
            mAudioRecord.startRecording();

            //记录开始时间
            mStartRecordTime = System.currentTimeMillis();

            //循环读取数据，写入输出流中
            while (mIsRecording) {
                int read = mAudioRecord.read(mBuffer, 0, BUFFER_SIZE);//返回长度
                if (read > 0) {
                    //读取成功，写入文件
                    mFileOutputStream.write(mBuffer, 0, read);
                } else {
                    //读取失败，提示用户
                    return false;
                }
            }

            //退出循环，停止录音，释放资源
            return stopRecord();
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            return false;
        } finally {
            //释放资源
            if (mAudioRecord != null) {
                mAudioRecord.release();
                mAudioRecord = null;
            }
        }
    }

    /**
     * 结束录音
     **/
    private boolean stopRecord() {
        try {
            //停止录音，关闭文件输出流
            mAudioRecord.stop();
            mAudioRecord.release();

            mFileOutputStream.close();

            //记录结束时间
            mStopRecordTime = System.currentTimeMillis();

            //大于3秒才成功，在主线程改变UI
            final int times = (int) ((mStopRecordTime - mStartRecordTime) / 1000);
            if (times > 3) {
                //在主线程改变UI，显示出来
                mMainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTvLog.setText(mTvLog.getText() + "\n录音成功 " + times + "秒");
                    }
                });
                //停止成功
                return true;
            }
            //停止失败
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void recordFail() {
        //Toast必须要在主线程才会显示，所有不能直接在这里写
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(StreamActivity.this, "录音失败", Toast.LENGTH_SHORT).show();

                //重置录音状态，以及UI状态
                mIsRecording = false;
                btn_start.setText("开始");
            }
        });
    }

    //播放逻辑
    private void doPlay(File mAudioFile) {
        Log.e("TAGSS",""+mAudioFile);
        //配置播放器 MediaPlayer
        int streamType = AudioManager.STREAM_MUSIC;//音乐类型，扬声器播放
        int sampleRate = 44100;//采样频率，要与录制时一样
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO;//声道设置，要与录制时一样
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;//要与录制时一样
        int mode = AudioTrack.MODE_STREAM;//流模式

        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate,channelConfig,audioFormat);//计算最小 buffer 的大小

        Log.e("TAGSS",""+minBufferSize);
        //创建 AudioTrack
        AudioTrack audioTrack = new AudioTrack(streamType,sampleRate,channelConfig,audioFormat,Math.max(minBufferSize,BUFFER_SIZE),mode);
        audioTrack.play();//启动AudioTrack

        //从文件流中读取数据
        FileInputStream inputStream = null;
        try{
            inputStream = new FileInputStream(mAudioFile);

            //循环读数据，写到播放器中去
            int read;
            while((read=inputStream.read(mBuffer))>0){
                Log.e("TAGSS",""+read);
                int ret = audioTrack.write(mBuffer,0,read);
                //检查返回值
                switch (ret){
                    case AudioTrack.ERROR_INVALID_OPERATION:
                    case AudioTrack.ERROR_BAD_VALUE:
                    case AudioManager.ERROR_DEAD_OBJECT:
                        playFail();
                        return;
                    default:
                        break;
                }
            }
        }catch (RuntimeException | IOException e){
            //异常处理，防止闪退
            e.printStackTrace();
            playFail();
        }finally {
            mIsPlaying = false;
            //关闭文件输入流
            if(inputStream != null){
                colseQuietly(inputStream);
            }
            //播放器释放
            resetQuietly(audioTrack);
        }
    }

    //提醒用户播放失败
    private void playFail() {
        //在主线程Toast提示
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(StreamActivity.this,"播放失败",Toast.LENGTH_SHORT).show();
            }
        });
    }

    //关闭文件输入流
    private void colseQuietly(FileInputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //播放器释放
    private void resetQuietly(AudioTrack audioTrack) {
        try {
            audioTrack.stop();
            audioTrack.release();
        }catch (RuntimeException e){
            e.printStackTrace();
        }
    }
}
