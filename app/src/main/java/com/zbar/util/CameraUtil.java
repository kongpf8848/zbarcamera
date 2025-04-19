package com.zbar.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
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

    private static final String TAG = "CameraUnit";
    private final String CAMERA_ID = "0";
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
                int imageWidth = image.getWidth();
                int imageHeight = image.getHeight();
                Log.d(TAG, "imageWidth:" + imageWidth + ";imageHeight:" + imageHeight);

                byte[] data = ImageUtil.getBytesFromImageAsType(image, 2);

                if (onFrameCallback != null) {
                    onFrameCallback.onFrame(imageWidth, imageHeight, data);
                }
            } catch (Exception e) {
                e.printStackTrace();
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
                try {
                    startPreview(holder);
                } catch (Exception e) {
                    throw new RuntimeException(e);
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


    private void startPreview(SurfaceHolder holder) throws Exception {

        List<Surface> outputSurfaces = new ArrayList<>();
        outputSurfaces.add(holder.getSurface());
        outputSurfaces.add(imageReader.getSurface());

        cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                cameraCaptureSession = session;
                try {
                    CaptureRequest.Builder requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                    requestBuilder.addTarget(holder.getSurface());
                    requestBuilder.addTarget(imageReader.getSurface());
                    session.setRepeatingRequest(requestBuilder.build(), null, cameraHandler);
                } catch (CameraAccessException e) {
                    Log.d("", "CameraAccessException:" + e);
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

    public void resume() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
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
