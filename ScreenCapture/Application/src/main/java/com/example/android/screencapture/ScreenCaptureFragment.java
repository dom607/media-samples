/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.screencapture;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.AudioPlaybackCaptureConfiguration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.example.android.common.logger.Log;
import com.example.android.screencapture.ScreenRecorder;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/**
 * Provides UI for the screen capture.
 */
public class ScreenCaptureFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "ScreenCaptureFragment";

    private static final String STATE_RESULT_CODE = "result_code";
    private static final String STATE_RESULT_DATA = "result_data";

    private static final int REQUEST_MEDIA_PROJECTION = 1;

    private int mScreenDensity;

    private int mResultCode;
    private Intent mResultData;

    private Surface mSurface;
    private MediaProjection mMediaProjection;
    private AudioRecord mAudioRecord;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionManager mMediaProjectionManager;
    private Button mButtonToggle;
    private SurfaceView mSurfaceView;

    // MediaRecorder variables
    private MediaRecorder mMediaRecorder;
    private File mOutputFile;
    private boolean isRecording = false;

    private int mScreenWidth;
    private int mScreenHeight;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mResultCode = savedInstanceState.getInt(STATE_RESULT_CODE);
            mResultData = savedInstanceState.getParcelable(STATE_RESULT_DATA);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_screen_capture, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mSurfaceView = (SurfaceView) view.findViewById(R.id.surface);
        mSurface = mSurfaceView.getHolder().getSurface();
        mButtonToggle = (Button) view.findViewById(R.id.toggle);
        mButtonToggle.setOnClickListener(this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Activity activity = getActivity();
        DisplayMetrics metrics = new DisplayMetrics();
        Display display = activity.getWindowManager().getDefaultDisplay();
        display.getMetrics(metrics);

        mScreenDensity = metrics.densityDpi;

        Point size = new Point();
        display.getSize(size);

        mScreenWidth = size.x;
        mScreenHeight = size.y;

//        mScreenWidth = 720;
//        mScreenHeight = 1280;

        mMediaProjectionManager = (MediaProjectionManager)
                activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mResultData != null) {
            outState.putInt(STATE_RESULT_CODE, mResultCode);
            outState.putParcelable(STATE_RESULT_DATA, mResultData);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.toggle:
                if (!isRecording) {
                    startScreenCapture();
                } else {
                    stopScreenCapture();
                }
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Log.i(TAG, "User cancelled");
                Toast.makeText(getActivity(), R.string.user_cancelled, Toast.LENGTH_SHORT).show();
                return;
            }
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            mResultCode = resultCode;
            mResultData = data;
            Intent service = new Intent(activity, ScreenRecorder.class);
            service.putExtra("code", resultCode);
            service.putExtras(data);
            service.putExtra("width", mScreenWidth);
            service.putExtra("height", mScreenHeight);
            service.putExtra("dpi", mScreenDensity);
            getContext().startForegroundService(service);

//            setUpMediaProjection();
//            setUpVirtualDisplay();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
//        stopScreenCapture();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tearDownMediaProjection();
    }

    private void setUpMediaProjection() {
        mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData);
    }

    private void tearDownMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    private void startScreenCapture() {
//        Activity activity = getActivity();
//        if (mSurface == null || activity == null) {
//            return;
//        }
//        if (mMediaProjection != null) {
//            setUpVirtualDisplay();
//        } else if (mResultCode != 0 && mResultData != null) {
//            setUpMediaProjection();
//            setUpVirtualDisplay();
//        } else {
//            Log.i(TAG, "Requesting confirmation");
//            // This initiates a prompt dialog for the user to confirm screen projection.
        Log.i(TAG, "Starting screen capture");

        isRecording = true;
        mButtonToggle.setText(R.string.stop);

        startActivityForResult(
                mMediaProjectionManager.createScreenCaptureIntent(),
                REQUEST_MEDIA_PROJECTION);
    }

    private void setUpVirtualDisplay() {
        if (!prepareVideoRecorder()) {
            Log.d(TAG, "Failed to prepare MediaRecorder");
            return;
        }

        Surface surface = mMediaRecorder.getSurface();
        if (!surface.isValid()) {
            Log.d(TAG, "MediaRecorder input surface is not valid.");
            return;
        }

//        Log.i(TAG, "Setting up a VirtualDisplay: " +
//                mSurfaceView.getWidth() + "x" + mSurfaceView.getHeight() +
//                " (" + mScreenDensity + ")");

        mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
                mScreenWidth, mScreenHeight, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, null);
        mButtonToggle.setText(R.string.stop);
        new MediaPrepareTask().execute(null, null, null);
    }

    private void stopScreenCapture() {
        Log.i(TAG, "Stop screen capture");

//        if (mVirtualDisplay == null) {
//            return;
//        }
//        mVirtualDisplay.release();
//        mVirtualDisplay = null;
//
//        // stop recording and release camera
//        try {
//            mMediaRecorder.stop();  // stop the recording
//        } catch (RuntimeException e) {
//            // RuntimeException is thrown when stop() is called immediately after start().
//            // In this case the output file is not properly constructed ans should be deleted.
//            android.util.Log.d(TAG, "RuntimeException: stop() is called immediately after start()");
//            //noinspection ResultOfMethodCallIgnored
//            mOutputFile.delete();
//        }
//        releaseMediaRecorder(); // release the MediaRecorder object

        Intent intent = new Intent(getActivity(), ScreenRecorder.class);
        getContext().stopService(intent);

        mButtonToggle.setText(R.string.start);
        isRecording = false;
    }

    private boolean prepareVideoRecorder() {
        // MediaRecorder profile
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        mMediaRecorder = new MediaRecorder();

        Display display = getActivity().getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

//        profile.videoFrameWidth = mScreenWidth;
//        profile.videoFrameHeight = mScreenHeight;

        profile.videoFrameWidth = mScreenWidth;
        profile.videoFrameHeight = mScreenHeight;

//        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.UNPROCESSED);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setProfile(profile);
        mMediaRecorder.setOutputFile(getOutputMediaFile().getPath());

        // Step 5: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            android.util.Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            android.util.Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }

//        AudioPlaybackCaptureConfiguration.Builder builder = new AudioPlaybackCaptureConfiguration.Builder(mMediaProjection);
//
//        try {
//            AudioPlaybackCaptureConfiguration audioCaptureConfig = builder.build();
//            mAudioRecord = new AudioRecord.Builder().setAudioPlaybackCaptureConfig(audioCaptureConfig).build();
//        } catch(Exception e) {
//            e.getMessage();
//        }
        return true;
    }


    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    public static File getOutputMediaFile(){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        if (!Environment.getExternalStorageState().equalsIgnoreCase(Environment.MEDIA_MOUNTED)) {
            return  null;
        }

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "CameraSample");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()) {
                android.util.Log.d("CameraSample", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp + ".mp4");

        return mediaFile;
    }

    class MediaPrepareTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... voids) {
            Log.i(TAG, "Start mediaRecorder");
            mMediaRecorder.start();
            isRecording = true;
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result) {
                getActivity().finish();
            }
            // inform the user that recording has started

            mButtonToggle.setText(R.string.stop);
        }
    }

}
