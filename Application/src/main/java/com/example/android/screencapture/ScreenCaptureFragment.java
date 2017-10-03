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

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.icu.text.SimpleDateFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Toast;
import android.widget.VideoView;

import com.example.android.common.logger.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Date;

/**
 * Provides UI for the screen capture.
 */
public class ScreenCaptureFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "ScreenCaptureFragment";

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private static final String STATE_RESULT_CODE = "result_code";
    private static final String STATE_RESULT_DATA = "result_data";

    private static final int REQUEST_MEDIA_PROJECTION = 1;

    private int mScreenDensity;

    private int mResultCode;
    private Intent mResultData;

    private Surface mSurface;
    private Surface previewSurface;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionManager mMediaProjectionManager;
    private Button mButtonToggle;
    private SurfaceView mSurfaceView;
    private VideoView videoView;

    private MediaCodec encoder;
    private FileOutputStream fileOutputStream = null;

    private DatagramSocket sock;
    private InetAddress group;
    private DatagramPacket currentPacket;
    private boolean configSent = false;

    private final int PORT_OUT = 1900;
//    private final int PORT_OUT = 1900;
//    private final int streamWidth = 1080;
//    private final int streamHeight = 1794;
//    private final int streamWidth = 720;
//    private final int streamHeight = 1280;
    private int streamWidth = 360;
    private int streamHeight = 640;

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
//        mSurfaceView = (SurfaceView) view.findViewById(R.id.surface);
//        previewSurface = mSurfaceView.getHolder().getSurface();
        videoView = (VideoView)view.findViewById(R.id.vid);
        String path = "android.resource://" + getActivity().getPackageName() + "/" + R.raw.sw;
        videoView.setVideoURI(Uri.parse(path));
        videoView.start();
        mButtonToggle = (Button) view.findViewById(R.id.toggle);
        mButtonToggle.setOnClickListener(this);

        startBroadcast();
        Toast started = Toast.makeText(getActivity(), "Started broadcast", Toast.LENGTH_SHORT);
        started.show();
    }

    private void startBroadcast(){

        try {
            sock = new DatagramSocket(4445);
            // Connect to the transmitting device IP.
            // PIXEL HOST DEVICE
//            group = InetAddress.getByName("224.0.113.0"); // For multicast
//            group = InetAddress.getByName("192.168.43.110"); // Samsung Galaxy S7
//            group = InetAddress.getByName("192.168.43.37"); // Jk iPhone
//            group = InetAddress.getByName("192.168.43.137"); // Moto E4
//            group = InetAddress.getByName("192.168.43.81"); // Lab iPhone

            // E4 HOST DEVICE
//            group = InetAddress.getByName("192.168.43.7"); // Pixel
            group = InetAddress.getByName("192.168.43.37"); // Jk iPhone

        } catch (SocketException | UnknownHostException e) {
            e.printStackTrace();
        }

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;

        ActivityCompat.requestPermissions(
                getActivity(),
                PERMISSIONS_STORAGE,
                REQUEST_EXTERNAL_STORAGE
        );
        // Set up file writing for debugging.
        SimpleDateFormat s = new SimpleDateFormat("ddMMyyyyhhmmss");
        File fileOut = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                s.format(new Date()) + "testFrameOutput.h264");
        try {
            fileOutputStream = new FileOutputStream(fileOut, true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
//            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
//                    width, height);
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                    streamWidth, streamHeight);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 220000);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 20);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
            format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                format.setInteger(MediaFormat.KEY_LATENCY, 0);
            }
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
            // Set the encoder priority to realtime.
;            format.setInteger(MediaFormat.KEY_PRIORITY, 0x00);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mSurface = MediaCodec.createPersistentInputSurface();
            encoder.setInputSurface(mSurface);
            encoder.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {

                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                    // Wait for SPS and PPS frames to be sent first.
                    if(!configSent){
                        codec.releaseOutputBuffer(index, false);
                        return;
                    }

                    ByteBuffer outputBuffer = codec.getOutputBuffer(index);
//                    try {
                        int val;
                        ByteBuffer buf;

                        if (outputBuffer != null) {
                            buf = ByteBuffer.allocate(outputBuffer.limit());
//                            while(outputBuffer.position() < outputBuffer.limit()){
//                                byte cur = outputBuffer.get();
//                                fileOutputStream.write(cur);
//                                buf.put(cur);
//                            }
                            buf.put(outputBuffer);
                            buf.flip();
                            Log.d(TAG, "Wrote " + outputBuffer.limit() + " bytes.");

                            BroadcastTask broadcastTask = new BroadcastTask(new DatagramPacket(buf.array(), outputBuffer.limit(), group, PORT_OUT));
                            broadcastTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        }
                        else{
                            return;
                        }

                        codec.releaseOutputBuffer(index, false);
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }

                }

                @Override
                public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {

                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
                    Log.d(TAG, "Updated output format! New height:"
                            + format.getInteger(MediaFormat.KEY_HEIGHT) + " new width: " +
                              format.getInteger(MediaFormat.KEY_WIDTH));

                    ByteBuffer sps = format.getByteBuffer("csd-0");
                    ByteBuffer pps = format.getByteBuffer("csd-1");
                    BroadcastTask spsTask = new BroadcastTask(new DatagramPacket(sps.array(), sps.limit(), group, PORT_OUT));
                    BroadcastTask ppsTask = new BroadcastTask(new DatagramPacket(pps.array(), pps.limit(), group, PORT_OUT));
                    spsTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
                    ppsTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
                    configSent = true;
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
        if(sock != null) {
            sock.close();
        }
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
        if(!videoView.isPlaying()) {
            videoView.start();
        }
    }

    private void setUpVirtualDisplay() {
//        mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
//                mSurfaceView.getWidth(), mSurfaceView.getHeight(), mScreenDensity,
//                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
//                mSurface, null, null);
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
                streamWidth, streamHeight, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mSurface, null, null);

        mButtonToggle.setText(R.string.stop);
    }

    private void stopScreenCapture() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        mVirtualDisplay = null;
        mButtonToggle.setText(R.string.start);
        if(videoView.isPlaying()){
            videoView.pause();
        }
    }

    private class BroadcastTask extends AsyncTask<String, String, String> {
        DatagramPacket packetOut;

        BroadcastTask(DatagramPacket packet){
            packetOut = packet;
        }

        @Override
        protected String doInBackground(String... strings) {
            try {
                sock.send(packetOut);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result){}
    }

}
