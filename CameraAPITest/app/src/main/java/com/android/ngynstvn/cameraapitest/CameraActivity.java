package com.android.ngynstvn.cameraapitest;

import android.annotation.TargetApi;
import android.content.Intent;
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
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;

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
             * activateCamera() involves has CameraManager instance with other info (like below..)
             *
             *      Get necessary camera id, characteristics, char maps, and output sizes
             *      After that, activate the camera
             *
             */

            activateCamera();
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

    private CameraDevice cameraDevice = null;
    private CaptureRequest.Builder previewBuilder = null;
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

    private CameraCaptureSession previewSession = null;

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

    private RelativeLayout bottomIconsHolder;
    private Button captureButton;
    private Button cameraSwitchBtn;
    private Button exitCameraBtn;

    private RelativeLayout middleIconsHolder;
    private Button cancelCaptureBtn;
    private Button approveCaptureBtn;

    // ----- Lifecycle Methods ----- //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(TAG, "onCreate() called");
        // called first before adding content
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Basically get this whole cycle working...it all starts with TextureView

        textureView = (TextureView) findViewById(R.id.txv_camera_activity);
        coordinatorLayout = (CoordinatorLayout) findViewById(R.id.sb_activity_camera);
        bottomIconsHolder = (RelativeLayout) findViewById(R.id.rl_bottom_icons);
        captureButton = (Button) findViewById(R.id.btn_pic_capture);
        cameraSwitchBtn = (Button) findViewById(R.id.btn_camera_switch);
        exitCameraBtn = (Button) findViewById(R.id.btn_exit_camera_mode);
        middleIconsHolder = (RelativeLayout) findViewById(R.id.rl_middle_icons);
        cancelCaptureBtn = (Button) findViewById(R.id.btn_pic_cancel);
        approveCaptureBtn = (Button) findViewById(R.id.btn_pic_approve);

        textureView.setSurfaceTextureListener(surfaceTextureListener);

        if(middleIconsHolder.isEnabled()) {
            middleIconsHolder.setEnabled(false);
        }

        textureView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Snackbar.make(coordinatorLayout, "TextureView Surface Pressed", Snackbar.LENGTH_SHORT).show();
            }
        });

        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Snackbar.make(coordinatorLayout, "Capture Image Button Pressed", Snackbar.LENGTH_SHORT).show();

                /**
                 *
                 * Code for the undo button
                 *
                 * Snackbar.make(coordinatorLayout, "Capture Button Pressed", SnackBar.LENGTH_SHORT)
                 * .setAction("Undo", new OnClickListner() {...}.show();
                 *
                 */

                bottomIconsHolder.setVisibility(View.GONE);
                bottomIconsHolder.setEnabled(false);

                middleIconsHolder.setVisibility(View.VISIBLE);
                middleIconsHolder.setEnabled(true);

                textureView.setEnabled(false);
            }
        });

        cameraSwitchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Snackbar.make(coordinatorLayout, "Camera Mode Button Pressed", Snackbar.LENGTH_SHORT).show();
            }
        });

        exitCameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Snackbar.make(coordinatorLayout, "Exit Camera Button Pressed", Snackbar.LENGTH_SHORT).show();
            }
        });

        cancelCaptureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Snackbar.make(coordinatorLayout, "Cancel Capture Pressed", Snackbar.LENGTH_SHORT).show();

                middleIconsHolder.setVisibility(View.GONE);
                middleIconsHolder.setEnabled(false);

                bottomIconsHolder.setVisibility(View.VISIBLE);
                bottomIconsHolder.setEnabled(true);

                // Safety measure??

                if(!textureView.isEnabled()) {
                    textureView.setEnabled(true);
                }
            }
        });

        approveCaptureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Snackbar.make(coordinatorLayout, "Approve Capture Pressed", Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        Log.e(TAG, "onResume() called");
        super.onResume();

        textureView.setSurfaceTextureListener(surfaceTextureListener);

        if(textureView.isAvailable()) {
            activateCamera();
        }
        else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
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
        closeCamera();
    }

    // --------------------- //

    private void activateCamera() {

        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        // Get necessary camera id, characteristics, char maps, and output sizes
        // After that, activate the camera

        try {
            String cameraId = cameraManager.getCameraIdList()[0];
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap streamConfigurationMap = cameraCharacteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            previewSize = streamConfigurationMap.getOutputSizes(SurfaceTexture.class)[0];
            cameraManager.openCamera(cameraId, stateCallback, null);
        }
        catch (CameraAccessException e) {
            Log.e(TAG, "ISSUE FOUND ON LINE: " + e.getStackTrace()[0].getLineNumber());
            e.printStackTrace();
        }
        catch (SecurityException e) {
            e.printStackTrace();
        }

    }

    private void closeCamera() {
        if(cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }
}
