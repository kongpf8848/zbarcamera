package com.zbar.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

public final class CameraUtil {

    private static final String TAG = "CameraUtil";
    private final String CAMERA_ID = "" + CameraCharacteristics.LENS_FACING_FRONT;
    private static final int PREVIEW_WIDTH = 1080;
    private static final int PREVIEW_HEIGHT = 1920;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private Handler cameraHandler;
    private ImageReader imageReader;
    private Context context;
    private OnFrameCallback onFrameCallback;
    private SurfaceHolder surfaceHolder;


    public interface OnFrameCallback {
        void onFrame(int width, int height, byte[] data);
    }


    public CameraUtil(Context context, OnFrameCallback onFrameCallback) {

        this.context = context;
        this.onFrameCallback = onFrameCallback;
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        HandlerThread handlerThread = new HandlerThread("preview_thread");
        handlerThread.start();
        cameraHandler = new Handler(handlerThread.getLooper());
        imageReader = ImageReader.newInstance(PREVIEW_WIDTH, PREVIEW_HEIGHT, ImageFormat.YUV_420_888, 2);
        imageReader.setOnImageAvailableListener(reader -> {
            try (Image image = reader.acquireLatestImage()) {
                if (image != null) {
                    int imageWidth = image.getWidth();
                    int imageHeight = image.getHeight();
                    byte[] data = ImageUtil.getBytesFromImageAsType(image, 2);
                    Log.d(TAG, "imageWidth:" + imageWidth + ";imageHeight:" + imageHeight + "," + data.length);
                    if (onFrameCallback != null) {
                        onFrameCallback.onFrame(imageWidth, imageHeight, data);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "CameraUtil() called with: context = [" + context + "], onFrameCallback = [" + onFrameCallback + "]");
            }

        }, cameraHandler);
    }

    public void openCamera(SurfaceHolder holder) throws Exception {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        this.surfaceHolder = holder;
        cameraManager.openCamera(CAMERA_ID, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                cameraDevice = camera;
                Log.d(TAG, "onOpened() called with: camera = [" + camera + "]");
                Log.d(TAG, "startPreview");
                try {
                    startPreview();
                } catch (Exception e) {
                    Toast.makeText(context, "startPreview Failed:" + e, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                cameraDevice = null;
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                Toast.makeText(context, "openCamera Failed:" + error, Toast.LENGTH_SHORT).show();
            }
        }, cameraHandler);

    }


    private void startPreview() throws Exception {
        Log.d(TAG, "startPreview() called with: holder = [" + this.surfaceHolder + "]");
        List<Surface> outputSurfaces = new ArrayList<>();
        outputSurfaces.add(this.surfaceHolder.getSurface());
        outputSurfaces.add(imageReader.getSurface());


        cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                cameraCaptureSession = session;
                try {
                    CaptureRequest.Builder previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                    previewRequestBuilder.addTarget(surfaceHolder.getSurface());
                    previewRequestBuilder.addTarget(imageReader.getSurface());
                    session.setRepeatingRequest(previewRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onReadoutStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                            super.onReadoutStarted(session, request, timestamp, frameNumber);
                            Log.d(TAG, "onReadoutStarted() called with: session = [" + session + "], request = [" + request + "], timestamp = [" + timestamp + "], frameNumber = [" + frameNumber + "]");
                        }

                        @Override
                        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                            super.onCaptureStarted(session, request, timestamp, frameNumber);
                            Log.d(TAG, "onCaptureStarted() called with: session = [" + session + "], request = [" + request + "], timestamp = [" + timestamp + "], frameNumber = [" + frameNumber + "]");
                        }

                        @Override
                        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                            super.onCaptureProgressed(session, request, partialResult);
                            Log.d(TAG, "onCaptureProgressed() called with: session = [" + session + "], request = [" + request + "], partialResult = [" + partialResult + "]");
                        }

                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                            super.onCaptureCompleted(session, request, result);
                            Log.d(TAG, "onCaptureCompleted() called with: session = [" + session + "], request = [" + request + "], result = [" + result + "]");
                        }

                        @Override
                        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                            super.onCaptureFailed(session, request, failure);
                            Log.d(TAG, "onCaptureFailed() called with: session = [" + session + "], request = [" + request + "], failure = [" + failure + "]");
                        }

                        @Override
                        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
                            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
                            Log.d(TAG, "onCaptureSequenceCompleted() called with: session = [" + session + "], sequenceId = [" + sequenceId + "], frameNumber = [" + frameNumber + "]");
                        }

                        @Override
                        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
                            super.onCaptureSequenceAborted(session, sequenceId);
                            Log.d(TAG, "onCaptureSequenceAborted() called with: session = [" + session + "], sequenceId = [" + sequenceId + "]");
                        }

                        @Override
                        public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
                            super.onCaptureBufferLost(session, request, target, frameNumber);
                            Log.d(TAG, "onCaptureBufferLost() called with: session = [" + session + "], request = [" + request + "], target = [" + target + "], frameNumber = [" + frameNumber + "]");
                        }
                    }, cameraHandler);
                } catch (CameraAccessException e) {
                    Log.d(TAG, "CameraAccessException:" + e);
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                Toast.makeText(context, "onConfigureFailed:" + session, Toast.LENGTH_SHORT).show();
            }
        }, cameraHandler);
    }

    public void pause() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    public void destroy() {
        this.surfaceHolder = null;
    }

    public void enableFlashlight() {
        if (cameraDevice == null) {
            return;
        }
        try {
            CaptureRequest.Builder requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            requestBuilder.addTarget(surfaceHolder.getSurface());
            requestBuilder.addTarget(imageReader.getSurface());
            requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
            cameraCaptureSession.setRepeatingRequest(requestBuilder.build(), null, cameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void disableFlashlight() {
        if (cameraDevice == null) {
            return;
        }
        try {
            CaptureRequest.Builder requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            requestBuilder.addTarget(surfaceHolder.getSurface());
            requestBuilder.addTarget(imageReader.getSurface());
            requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            cameraCaptureSession.setRepeatingRequest(requestBuilder.build(), null, cameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


}
