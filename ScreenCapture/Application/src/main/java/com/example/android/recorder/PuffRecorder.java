package com.example.android.recorder;

import android.util.Log;
import android.view.Surface;

import java.io.IOException;

public class PuffRecorder {

    private static final String TAG = "PuffRecorder";
    public MediaVideoEncoder mMediaVideoEncoder;
    public enum RecordType {
        VideoOnly,
        AudioOnly,
        VideoAudio
    }

    // Recorder classes
    private MediaMuxerWrapper mMuxer;
    private RecordType mRecordType;

    // Video attributes
    private int mWidth;
    private int mHeight;

    // buffers
    private Surface mSurface;

    public PuffRecorder(int width, int height, String outputPath) {
        this.mWidth = width;
        this.mHeight = height;
        try{
            this.mMuxer = new MediaMuxerWrapper(outputPath);
        } catch (IOException e) {
            Log.e(TAG, "Failed to create muxer:", e);
        }
    }

    // 이렇게 하거나, listener 에다가 자동으로 hook 하도록 추가할 것.
    public void setVideoSurface(Surface surface) {
        this.mSurface = surface;
    }

    // Must be called after prepare called.
    public Surface getVideoSurface() {
        return this.mSurface;
    }

    public String getOutputPath() {
        return this.mMuxer.getOutputPath();
    }

    public void startRecord(RecordType recordType) {
        try{
            this.mRecordType = recordType;
            switch (mRecordType) {
                case AudioOnly:
                    new MediaAudioEncoder(mMuxer, mMediaEncoderListener);
                    break;
                case VideoOnly:
                    new MediaVideoEncoder(mMuxer, mMediaEncoderListener, mWidth, mHeight);
                    break;
                case VideoAudio:
                    new MediaAudioEncoder(mMuxer, mMediaEncoderListener);
                    new MediaVideoEncoder(mMuxer, mMediaEncoderListener, mWidth, mHeight);
                    break;
            }

            mMuxer.prepare(this);
            mMuxer.startRecording();
        } catch (final IOException e) {
            Log.e(TAG, "startCapture:", e);
        }
    }

    public void endRecord() {
        if (mMuxer != null) {
            mMuxer.stopRecording();
            if(mSurface.isValid()) {
                mSurface.release();
            }
            mSurface = null;
            mMuxer = null;
        }
    }

    public void frameAvailableSoon() {
        if(this.mMediaVideoEncoder != null) {
            this.mMediaVideoEncoder.frameAvailableSoon();
        }
    }

    // modify this depends on view usage
    // onPreapare에서 할당한 encoder를 반드시 onStopped에서 레퍼런스 해제 시킬 것.
    // 안 그럴경우 muxer 종료가 제대로 안 되면서 클립 생성이 안 될 경우가 있음.

    private final MediaEncoder.MediaEncoderListener mMediaEncoderListener = new MediaEncoder.MediaEncoderListener() {
        @Override
        public void onPrepared(final MediaEncoder encoder) {
            if (encoder instanceof MediaVideoEncoder)
                mMediaVideoEncoder = (MediaVideoEncoder) encoder;
        }

        @Override
        public void onStopped(final MediaEncoder encoder) {
            if (encoder instanceof MediaVideoEncoder)
                mMediaVideoEncoder = null;
        }
    };
}