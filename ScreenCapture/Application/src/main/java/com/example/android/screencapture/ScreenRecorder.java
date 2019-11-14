package com.example.android.screencapture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.view.Surface;

import com.example.android.common.logger.Log;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import com.example.android.recorder.PuffRecorder;

public class ScreenRecorder extends Service {
    private static final String TAG = "ScreenRecorderService";

    VirtualDisplay mVirtualDisplay;
    File mOutputFile;

    // Media classes muxer, muxer wapper and so on.
    MediaProjection mMediaProjection;
    MediaProjectionManager mMediaProjectionManager;
    PuffRecorder mPuffRecorder;

    // Service data
    int mResultCode;
    int mStartId;
    int mFlags;
    Intent mResultData;

    // Screen info
    int mWidth;
    int mHeight;
    int mDpi;

    // Thread
    CaptureThread mCaptureThread;

    @Override
    public void onCreate() {
        // Which context?
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Log.i(TAG, "onStartCommand");

        mResultCode = intent.getIntExtra("code", -1);
        mResultData = intent;
        mFlags = flags;
        mStartId = startId;
        mWidth = intent.getIntExtra("width", -1);
        mHeight = intent.getIntExtra("height", -1);
        mDpi = intent.getIntExtra("dpi", -1);

        Log.i(TAG, "Capture resolution : " + mWidth + "x" + mHeight);

        // Create MediaProjection
        mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, Objects.requireNonNull(mResultData));
        Log.i(TAG, "mMediaProjection created: " + mMediaProjection);

        // Prepare MediaRecorder or MediaMuxer
        if (!prepareVideoRecorder()) {
            Log.e(TAG, "Failed to create MediaRecorder");
        }
        Surface inputSurface = mPuffRecorder.getVideoSurface();
        if (!inputSurface.isValid()) {
            Log.e(TAG, "Non valid surface");
        }


        // Now we can create virtualDisplay
        mVirtualDisplay = mMediaProjection.createVirtualDisplay(mMediaProjection.toString(),
                mWidth, mHeight, mDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface, null, null);

        if (mVirtualDisplay == null) {
            Log.e(TAG, "Failed to create mVirtualDisplay");
        }

        // Finally launch thread
        mCaptureThread = new CaptureThread(new WeakReference<ScreenRecorder>(this));
        mCaptureThread.start();

        Log.i(TAG, "Start recording");
        mPuffRecorder.startRecord();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind service");
        return null;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");

        if (mVirtualDisplay == null) {
            Log.e(TAG, "Virtual display is null. Something wrong");
            return;
        }
        mVirtualDisplay.release();
        mVirtualDisplay = null;

        mCaptureThread.mShouldTerminate = true;

        // TODO : wait thread termination and assign null to mThread.
        mPuffRecorder.endRecord();
        releaseMediaRecorder();

        super.onDestroy();
    }

    private void createNotificationChannel() {
        // Notification builder required channel id from oreo ( Android 8.0 )
        Notification.Builder builder = new Notification.Builder(this.getApplicationContext());
        Intent nfIntent = new Intent(this, MainActivity.class);

        builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, 0))
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.drawable.ic_launcher))
                //.setContentTitle("SMI InstantView")
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentText("Capturing")
                .setWhen(System.currentTimeMillis());

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId("notification_id");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("notification_id", "notification_name", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = builder.build(); //
        notification.defaults = Notification.DEFAULT_SOUND; // deprecated
        startForeground(110, notification);
    }

    private boolean prepareVideoRecorder() {
        // MediaRecorder profile
        mOutputFile = getOutputMediaFile();
        Log.i(TAG, "Output path : " + mOutputFile.getPath());

        mPuffRecorder = new PuffRecorder(mWidth, mHeight, mOutputFile.getPath());
        mPuffRecorder.setMediaProjection(mMediaProjection);
        mPuffRecorder.prepare(PuffRecorder.RecordType.VideoAudio);

        if (mPuffRecorder == null) {
            return false;
        }

//        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
//        mMediaRecorder = new MediaRecorder();
//        profile.videoFrameWidth = mWidth;
//        profile.videoFrameHeight = mHeight;
//
//        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
//        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
//        mMediaRecorder.setProfile(profile);
//        mMediaRecorder.setOutputFile(mOutputFile.getPath());
//
//        // Step 5: Prepare configured MediaRecorder
//        try {
//            mMediaRecorder.prepare();
//        } catch (IllegalStateException e) {
//            android.util.Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
//            releaseMediaRecorder();
//            return false;
//        } catch (IOException e) {
//            android.util.Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
//            releaseMediaRecorder();
//            return false;
//        }

//        AudioPlaybackCaptureConfiguration.Builder builder = new AudioPlaybackCaptureConfiguration.Builder(mMediaProjection);
//        builder.addMatchingUsage(AudioAttributes.USAGE_GAME);
//        builder.addMatchingUsage(AudioAttributes.USAGE_MEDIA);
//
//        try {
//            AudioPlaybackCaptureConfiguration audioCaptureConfig = builder.build();
//
//            int minBufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
//
//            mAudioRecord = new AudioRecord.Builder()
//                .setAudioPlaybackCaptureConfig(audioCaptureConfig)
//                .setAudioFormat(new AudioFormat.Builder()
//                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
//                    .setSampleRate(44100)
//                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
//                    .build())
//                .setBufferSizeInBytes(minBufferSize)
//                .build();
//
//        } catch(Exception e) {
//            e.getMessage();
//        }

        return true;
    }

    private void releaseMediaRecorder() {
        mPuffRecorder = null;
    }

    public File getOutputMediaFile(){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        if (!Environment.getExternalStorageState().equalsIgnoreCase(Environment.MEDIA_MOUNTED)) {
            return  null;
        }

        // TODO : change save path.
        File rootPath = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "CameraSample");

        // Create the storage directory if it does not exist
        if (!rootPath.exists()){
            if (!rootPath.mkdirs()) {
                android.util.Log.d("CameraSample", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File mediaFile = new File(rootPath.getPath() + File.separator + "VID_" + timeStamp + ".mp4");

        return mediaFile;
    }

    // Capture thread
    private class CaptureThread extends Thread {
        private final long FRAME_INTERVAL = 32; // 32 for 30 fps, 0 for full speed
        private long mPreviousFrameTime = 0;
        private boolean mShouldTerminate = false;
        private WeakReference<ScreenRecorder> mScreenRecorderWeakReference;

        CaptureThread(final WeakReference<ScreenRecorder> screenRecorderWeakReference) {
            super();
            mScreenRecorderWeakReference = screenRecorderWeakReference;
        }

        @Override
        public void run() {

            while(!mShouldTerminate) {

                long currentTime = System.currentTimeMillis();
                if (currentTime - mPreviousFrameTime > FRAME_INTERVAL) {
                    mPreviousFrameTime = currentTime;
                    ScreenRecorder recorder = mScreenRecorderWeakReference.get();
                    recorder.mPuffRecorder.frameAvailableSoon();
                }
            }
        }
    }
}