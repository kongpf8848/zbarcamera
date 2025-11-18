package com.zbar.util;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


class SmartSize {
    Size size;
    double longSize;
    double shortSize;

    public SmartSize(Integer width, Integer height) {
        size = new Size(width, height);
        longSize = max(size.getWidth(), size.getHeight());
        shortSize = min(size.getWidth(), size.getHeight());
    }

    @Override
    public String toString() {
        return String.format("SmartSize(%sx%s)", longSize, shortSize);
    }
}


public final class CameraUtil {

    private static final String TAG = "CameraUtil";

    private static final int PREVIEW_WIDTH = 1080;
    private static final int PREVIEW_HEIGHT = 1920;
    private String cameraId = "0";
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

    SmartSize SIZE_1080P = new SmartSize(1920, 1080);

    /**
     * Returns a [SmartSize] object for the given [Display]
     */
    SmartSize getDisplaySmartSize(Display display) {
        Point outPoint = new Point();
        display.getRealSize(outPoint);
        return new SmartSize(outPoint.x, outPoint.y);
    }


    Size getPreviewOutputSize(Display display, CameraCharacteristics characteristics, Integer format) {

        // Find which is smaller: screen or 1080p
        SmartSize screenSize = getDisplaySmartSize(display);
        boolean hdScreen = screenSize.longSize >= SIZE_1080P.longSize || screenSize.shortSize >= SIZE_1080P.shortSize;
        SmartSize maxSize;
        if (hdScreen) {
            maxSize = SIZE_1080P;
        } else {
            maxSize = screenSize;
        }

        StreamConfigurationMap config = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] allSizes = config.getOutputSizes(format);

        // Get available sizes and sort them by area from largest to smallest
        List<Size> sortedSizes = Arrays.asList(allSizes);
        List<SmartSize> validSizes = sortedSizes.stream().sorted(Comparator.comparing(s -> s.getHeight() * s.getWidth())).map(s -> new SmartSize(s.getWidth(), s.getHeight())).sorted(new Comparator<SmartSize>() {
            @Override
            public int compare(SmartSize o1, SmartSize o2) {
                return o2.size.getWidth() * o2.size.getHeight() - o1.size.getWidth() * o1.size.getHeight();
            }
        }).collect(Collectors.toList());

        // Then, get the largest output size that is smaller or equal than our max size
        return validSizes.stream().filter(s -> s.longSize <= maxSize.longSize && s.shortSize <= maxSize.shortSize).findFirst().get().size;
    }

    public CameraUtil(Context context, OnFrameCallback onFrameCallback) {
        this.context = context;
        this.onFrameCallback = onFrameCallback;
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        HandlerThread handlerThread = new HandlerThread("preview_thread");
        handlerThread.start();
        cameraHandler = new Handler(handlerThread.getLooper());
        cameraId = getCameraIdByFace(cameraManager, CameraCharacteristics.LENS_FACING_BACK);

        Size previewSize = new Size(0, 0);
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                previewSize = getPreviewOutputSize(context.getDisplay(), characteristics, ImageFormat.YUV_420_888);
            }
            Log.d(TAG, "CameraUtil() called with: previewSize = [" + previewSize + "]");
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }


        imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);
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

        Log.d(TAG, "CameraUtil() called with: cameraId = [" + cameraId + "]");
    }

    public String getCameraIdByFace(CameraManager cameraManager, int face) {
        try {
            String[] cameraIdList = cameraManager.getCameraIdList(); // may be empty
            for (String cameraId : cameraIdList) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (lensFacing != null && lensFacing == face) {
                    return cameraId;
                }
            }
        } catch (CameraAccessException e) {
        }
        return "0";
    }

    public void openCamera(SurfaceHolder holder) throws Exception {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        this.surfaceHolder = holder;
        cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
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
