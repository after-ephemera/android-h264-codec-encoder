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
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.example.android.common.logger.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

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
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionManager mMediaProjectionManager;
    private Button mButtonToggle;
    private SurfaceView mSurfaceView;

    private MediaCodec encoder;
    FileOutputStream fileOutputStream = null;

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
//        mSurface = mSurfaceView.getHolder().getSurface();
        mButtonToggle = (Button) view.findViewById(R.id.toggle);
        mButtonToggle.setOnClickListener(this);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;


        // Set up file writing for debugging.
        File fileOut = new File(getActivity().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "testFrameOutput.h264");
        try {
            fileOutputStream = new FileOutputStream(fileOut, true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                    width, height);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mSurface = MediaCodec.createPersistentInputSurface();
            encoder.setInputSurface(mSurface);
            encoder.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {

                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                    try {
                        int val;
                        while(outputBuffer.position() < outputBuffer.limit()){
                            fileOutputStream.write(outputBuffer.get());
                        }
                        Log.d(TAG, "Wrote " + outputBuffer.limit() + " bytes.");
                        codec.releaseOutputBuffer(index, true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }

                @Override
                public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {

                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
                    Log.d(TAG, "Updated output format!");
                }
            });
            encoder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Activity activity = getActivity();
        DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
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
                if (mVirtualDisplay == null) {
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
            Log.i(TAG, "Starting screen capture");
            mResultCode = resultCode;
            mResultData = data;
            setUpMediaProjection();
            setUpVirtualDisplay();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopScreenCapture();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tearDownMediaProjection();
        encoder.release();
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
        Activity activity = getActivity();
        if (mSurface == null || activity == null) {
            return;
        }
        if (mMediaProjection != null) {
            setUpVirtualDisplay();
        } else if (mResultCode != 0 && mResultData != null) {
            setUpMediaProjection();
            setUpVirtualDisplay();
        } else {
            Log.i(TAG, "Requesting confirmation");
            // This initiates a prompt dialog for the user to confirm screen projection.
            startActivityForResult(
                    mMediaProjectionManager.createScreenCaptureIntent(),
                    REQUEST_MEDIA_PROJECTION);
        }
    }

    private void setUpVirtualDisplay() {
        Log.i(TAG, "Setting up a VirtualDisplay: " +
                mSurfaceView.getWidth() + "x" + mSurfaceView.getHeight() +
                " (" + mScreenDensity + ")");
        // Preview Display
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
                mSurfaceView.getWidth(), mSurfaceView.getHeight(), mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mSurface, null, null);

        // Output Display
//        mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
//                mSurfaceView.getWidth(), mSurfaceView.getHeight(), mScreenDensity,
//                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
//                mSurface, null, null);
        mButtonToggle.setText(R.string.stop);
    }

    private void stopScreenCapture() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        mVirtualDisplay = null;
        mButtonToggle.setText(R.string.start);
    }

}
