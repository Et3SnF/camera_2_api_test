package com.android.ngynstvn.cameraapitest;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;

import java.util.Arrays;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CameraActivity extends AppCompatActivity {

    /**
     * THE FOLLOWING HAS TO BE CREATED. Each have their own category
     *
     * Source: http://jylee-world.blogspot.com/2014/12/a-tutorial-of-androidhardwarecamera2.html
     *
     * CameraManager
     *      Select Camera
     *      Create CameraDevice
     * CameraDevice
     *      Create CaptureRequest
     *      Create CameraCaptureSession
     * CaptureRequest & CaptureRequest.Builder
     *      Link Surface for Viewing
     *      Create CaptureRequest
     * CameraCaptureSession
     *      Capture camera image and but the result on the Surface registered in CaptureRequest
     *
     * NOTES - Main Thread should not be blocked by Camera Capture --> need a background thread
     *
     */

    private static final String TAG = CameraActivity.class.getSimpleName() + ": ";

    /**
     *
     * TextureView Portion
     *
     */

    private TextureView textureView = null;
    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @TargetApi(Build.VERSION_CODES.M)
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.v(TAG, "onSurfaceTextureAvailable() called");

            /**
             *
             * CameraManager Portion
             *
             */

            CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

            // Get necessary camera id, characteristics, char maps, and output sizes
            // After that, activate the camera

            try {
                String cameraId = cameraManager.getCameraIdList()[0];
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                StreamConfigurationMap streamConfigurationMap = cameraCharacteristics
                        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                previewSize = streamConfigurationMap.getOutputSizes(SurfaceTexture.class)[0];
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    public void requestPermissions(@NonNull String[] permissions, int requestCode)
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for Activity#requestPermissions for more details.
                    return;
                }
                cameraManager.openCamera(cameraId, stateCallback, null);
            }
            catch (CameraAccessException e) {
                Log.e(TAG, "ISSUE FOUND ON LINE: " + e.getStackTrace()[0].getLineNumber());
                e.printStackTrace();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            Log.v(TAG, "onSurfaceTextureSizeChanged() called");
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            Log.v(TAG, "onSurfaceTextureDestroyed() called");
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            Log.v(TAG, "onSurfaceTextureUpdated() called");
        }
    };

    private Size previewSize = null;

    /**
     *
     * CameraDevice Portion - Creating the request
     *
     */

    private CameraDevice cameraDevice;
    private CaptureRequest.Builder previewBuilder;
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Log.v(TAG, "onOpened() called");
            cameraDevice = camera;

            /**
             *
             * CaptureRequest.Builder Portion - BRANCH 1 of CameraDevice
             *
             */

            // Instantiate the SurfaceTexture (needs TextureView)

            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();

            if(surfaceTexture == null) {
                Log.v(TAG, "SurfaceTexture is null");
                return;
            }

            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            // Instantiate Surface (needs SurfaceTexture)
            // This is called after CaptureRequest.Builder() is called. Needs to be declared first

            Surface surface = new Surface(surfaceTexture);

            try {
                previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            }
            catch (CameraAccessException e) {
                e.printStackTrace();
            }

            previewBuilder.addTarget(surface);

            try {
                cameraDevice.createCaptureSession(Arrays.asList(surface), previewSessionStateCallback, null);
            }
            catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            Log.v(TAG, "onDisconnected() called");
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.v(TAG, "onError() called");
        }
    };

    /**
     *
     * CameraCaptureSession.StateCallBack - CameraDevice Branch 2
     *
     */

    private CameraCaptureSession previewSession;

    private CameraCaptureSession.StateCallback previewSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            Log.v(TAG, "onConfigured() called");
            // Get the CameraCaptureSession variable assigned to be used soon
            previewSession = session;
            previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            /**
             *
             * HandlerThread & Handler - CameraCaptureSession.StateCallback Branch 1
             * Keep sessions in the background in case we need to call them again
             *
             */

            HandlerThread handlerThread = new HandlerThread("PreviewSession");
            handlerThread.start();
            Handler handler = new Handler(handlerThread.getLooper());

            /**
             *
             * CameraCaptureSession - CameraCaptureSession.StateCallback Branch  2
             * WORKS SIDE BY SIDE WITH BRANCH 1
             *
             */

            try {
                previewSession.setRepeatingRequest(previewBuilder.build(), null, handler);
            }
            catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            Log.v(TAG, "onConfigureFailed() called");
        }
    };

    private CoordinatorLayout coordinatorLayout;
    private Button captureButton;

    // ----- Lifecycle Methods ----- //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(TAG, "onCreate() called");
        super.onCreate(savedInstanceState);
        // called first before adding content
        setContentView(R.layout.activity_camera);

        // Basically get this whole cycle working...it all starts with TextureView

        textureView = (TextureView) findViewById(R.id.txv_camera_activity);
        textureView.setSurfaceTextureListener(surfaceTextureListener);

        coordinatorLayout = (CoordinatorLayout) findViewById(R.id.sb_activity_camera);

        captureButton = (Button) findViewById(R.id.btn_pic_capture);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Snackbar.make(coordinatorLayout, "Capture Button pressed", Snackbar.LENGTH_SHORT).show();

                /**
                 *
                 * Code for the undo button
                 *
                 * Snackbar.make(coordinatorLayout, "Capture Button Pressed", SnackBar.LENGTH_SHORT)
                 * .setAction("Undo", new OnClickListner() {...}.show();
                 *
                 */
            }
        });
    }

    @Override
    protected void onResume() {
        Log.e(TAG, "onResume() called");
        super.onResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.e(TAG, "onActivityResult() called");
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause() called");
        super.onPause();

        // Stop any camera sessions

        if(cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    // --------------------- //


}
