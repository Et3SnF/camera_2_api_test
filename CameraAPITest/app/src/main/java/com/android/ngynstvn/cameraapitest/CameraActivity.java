package com.android.ngynstvn.cameraapitest;

import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
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
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "(" + CameraActivity.class.getSimpleName() + "): ";

    // Camera State static variables

    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAKE_LOCK = 1;

    private RelativeLayout topIconsHolder;
    private Button exitCameraBtn;

    private RelativeLayout bottomIconsHolder;
    private View captureButton;
    private Button cameraSwitchBtn;
    private Button flashModeBtn;

    private RelativeLayout cancelCapBtnHolder;
    private View cancelCaptureBtn;
    private RelativeLayout approveCapBtnHolder;
    private View approveCaptureBtn;

    DisplayMetrics displayMetrics = new DisplayMetrics();
    private long transDuration = 200L;
    private long fadeDuration = 600L;

    /**
     *
     * Camera Variables
     *
     * Sequence of Events :
     * 1. Initialize TextureView by inflating the view
     * 2. Activate TextureView adding the instance of a SurfaceTextureListener anonymous class
     * 3. When SurfaceTexture is available, begin setting up the camera
     * 4. Set up the camera by calling CameraManager, get the CameraCharacteristics, cameraId, and
     * preview sizes (using StreamConfigurationMap with SCALER_STREAM_CONFIGURATION_MAP and a method
     * to get the appropriate resolution)
     * 5. Create an anonymous class implementation of CameraDevice.StateCallback to define the state of upcoming
     * camera object
     * 6. Start camera by opening it with CameraManager using the cameraId, CameraDevice.StateCallback
     * and Handler to allow it to run in the background
     * 7. When the camera is officially opened, get the assign CameraDevice object from the StateCallback (onOpened())
     * 8. Once the cameraDevice object is assigned and initialized, create the preview session (Look
     * at procedure above createCaptureSession() method
     *
     */

    private Size previewSize;
    private String cameraId;
    private TextureView textureView;
    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {

            // When this is available, get the width the height of the Texture for the preview
            // NOTE: At start, I want to access the camera. Then remove any resources of the camera
            // whenver I move to another application.
            // onResume(), set up the camera
            setupCamera(width, height);

            // Now open the camera after setup

            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    // Setting up the camera device
    private CameraDevice cameraDevice;
    private CameraDevice.StateCallback cameraDeviceStateCallback = new CameraDevice.StateCallback() {

        // Provides that different states of what the cameraDevice will be in - opened, disconnected, and error

        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreviewSession();
            Log.v(TAG, "Camera is now opened");
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            Log.v(TAG, "Camera is now disconnected");
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.e(TAG, "There was an error opening teh camera. Closing camera resources.");
            camera.close();
            cameraDevice = null;
        }
    };

    // Preview Session variables

    private CaptureRequest previewCaptureRequest;
    private CaptureRequest.Builder previewCaptureRequestBuilder; // this is a helper to the CaptureRequest
    private CameraCaptureSession cameraCaptureSession;
    private CameraCaptureSession.CaptureCallback cameraCaptureSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            process(result);
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Log.e(TAG, "Unable to capture photo");
            Toast.makeText(getApplicationContext(), "Unable to capture photo.", Toast.LENGTH_SHORT).show();
        }

        private void process(CaptureResult captureResult) {
            switch (currentState) {
                case STATE_PREVIEW:
                    // Just continue...
                    break;
                case STATE_WAKE_LOCK:
                    // If the mode is waking the focus lock, get the auto focus state and perform
                    // the following:
                    // if auto focus state is locked

                    Integer autoFocusState = captureResult.get(CaptureResult.CONTROL_AF_STATE);

                    if(autoFocusState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED) {
                        unlockFocus();
                        Log.v(TAG, "Focus locked");
                        Toast.makeText(getApplicationContext(), "Focus Locked", Toast.LENGTH_SHORT).show();

                        // Capture image

                        captureStillImage();
                        Log.v(TAG, "Image Captured");
                    }

                    break;
            }
        }
    };

    // Set up background threads

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    // Lock Focus Variables

    private int currentState;

    // Image saving variables

    private static File imageFile;
    private static class ImageSaver implements Runnable {

        private final Image image;

        private ImageSaver(Image image) {
            this.image = image;
        }

        @Override
        public void run() {
            // Create a byte buffer to contain the byte data returned from Camera2 Surface
            ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();

            // Create array that contains the number of content data from the byteBuffer
            byte[] bytes = new byte[byteBuffer.remaining()];

            // Copy data from byteBuffer and populate it into the array
            byteBuffer.get(bytes);

            // Store that data

            FileOutputStream fileOutputStream = null;

            try {
                fileOutputStream = new FileOutputStream(imageFile);
                fileOutputStream.write(bytes);
            }
            catch (IOException e) {
                e.printStackTrace();
            }

            image.close();
            if(fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // Once everything is written via the stream, close any resources. Now use it with ImageReader

    }
    private ImageReader imageReader;
    private ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            // Once that image is available, pass that image to the image saver runnable and save it as a file

            backgroundHandler.post(new ImageSaver(reader.acquireNextImage()));
        }
    };

    // Capturing Still Image Variables

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(TAG, "onCreate() called");
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Basically get this whole cycle working...it all starts with TextureView

        textureView = (TextureView) findViewById(R.id.txv_camera_activity);
        topIconsHolder = (RelativeLayout) findViewById(R.id.rl_top_icons);
        exitCameraBtn = (Button) findViewById(R.id.btn_exit_camera);
        bottomIconsHolder = (RelativeLayout) findViewById(R.id.rl_bottom_icons);
        captureButton = findViewById(R.id.v_pic_capture);
        cameraSwitchBtn = (Button) findViewById(R.id.btn_camera_switch);
        flashModeBtn = (Button) findViewById(R.id.btn_flash_mode);
        cancelCapBtnHolder = (RelativeLayout) findViewById(R.id.rl_cancel_picture);
        approveCapBtnHolder = (RelativeLayout) findViewById(R.id.rl_approve_pic);
        cancelCaptureBtn = findViewById(R.id.v_pic_cancel);
        approveCaptureBtn = findViewById(R.id.v_pic_approve);

        exitCameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });

        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                takePhoto();

                int upDownTransHeight = bottomIconsHolder.getMeasuredHeight();

                moveVerticalAnimation(bottomIconsHolder, 0, upDownTransHeight, transDuration);
                moveVerticalAnimation(topIconsHolder, 0, (-1 * upDownTransHeight), transDuration);

                topIconsHolder.setEnabled(false);
                bottomIconsHolder.setEnabled(false);
                topIconsHolder.setVisibility(View.GONE);
                bottomIconsHolder.setVisibility(View.GONE);

                cancelCapBtnHolder.setEnabled(true);
                approveCapBtnHolder.setEnabled(true);

                int leftRightTransWidth = cancelCapBtnHolder.getMeasuredWidth();

                moveFadeAnimation(cancelCapBtnHolder, (-1 * leftRightTransWidth),
                        displayMetrics.widthPixels, 0, 0, 0.00f, 1.00f, transDuration, fadeDuration);

                moveFadeAnimation(approveCapBtnHolder, leftRightTransWidth, displayMetrics.widthPixels
                        , 0, 0, 0.0f, 1.0f, transDuration, fadeDuration);

                cancelCapBtnHolder.setVisibility(View.VISIBLE);
                approveCapBtnHolder.setVisibility(View.VISIBLE);

                textureView.setEnabled(false);
            }
        });

        cameraSwitchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        flashModeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        cancelCaptureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                int leftRightTransWidth = cancelCapBtnHolder.getMeasuredWidth();

                moveFadeAnimation(cancelCapBtnHolder, displayMetrics.widthPixels
                        , (-1 * leftRightTransWidth), 0, 0, 1.00f, 0.00F, transDuration, fadeDuration);

                moveFadeAnimation(approveCapBtnHolder, displayMetrics.widthPixels
                        , leftRightTransWidth, 0, 0, 1.00F, 0.00F, transDuration, fadeDuration);

                cancelCapBtnHolder.setVisibility(View.GONE);
                approveCapBtnHolder.setVisibility(View.GONE);
                cancelCapBtnHolder.setEnabled(false);
                approveCapBtnHolder.setEnabled(false);

                bottomIconsHolder.setEnabled(true);
                topIconsHolder.setEnabled(true);

                int upDownTransHeight = bottomIconsHolder.getMeasuredHeight();

                moveVerticalAnimation(bottomIconsHolder, upDownTransHeight, 0, transDuration);
                moveVerticalAnimation(topIconsHolder, (-1 * upDownTransHeight), 0, transDuration);

                bottomIconsHolder.setVisibility(View.VISIBLE);
                topIconsHolder.setVisibility(View.VISIBLE);

                // Can case something seriously happened...

                if(!textureView.isEnabled()) {
                    textureView.setEnabled(true);
                }
            }
        });

        approveCaptureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        /**
         *
         * Camera Code below
         *
         */

        textureView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(TAG, "TextureView touched.");
            }
        });
    }

    @Override
    protected void onStart() {
        Log.e(TAG, "onStart() called");
        super.onStart();
    }

    @Override
    protected void onResume() {
        Log.e(TAG, "onResume() called");
        super.onResume();

        openBackgroundThread();

        if(textureView.isAvailable()) {
            // If TextureView is still available, just set up the camera and then open the camera
            setupCamera(previewSize.getWidth(), previewSize.getHeight());
            openCamera();
        }
        else {
            // If it's not available, make sure this is setup so things start working or else
            // you will see a gray screen
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause() called");
        super.onPause();

        // Clean up camera resources here so that I don't cause other camera applications to crash..
        closeCamera();
        closeBackgroundThread();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.e(TAG, "onSaveInstanceState() called");
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onStop() {
        Log.e(TAG, "onStop() called");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy() called");
        super.onDestroy();
    }

    /**
     *
     * Animation Methods
     *
     */

    private void moveFadeAnimation(ViewGroup viewGroup, float fromX, float toX, float fromY, float toY,
                                   float fromAlpha, float toAlpha, long time1, long time2) {
        AnimationSet animationSet = new AnimationSet(true);

        TranslateAnimation translateAnimation = new TranslateAnimation(fromX, toX, fromY, toY);
        translateAnimation.setDuration(time1);

        AlphaAnimation fadeAnimation = new AlphaAnimation(fromAlpha, toAlpha);
        fadeAnimation.setDuration(time2);

        animationSet.addAnimation(translateAnimation);
        animationSet.addAnimation(fadeAnimation);

        viewGroup.startAnimation(animationSet);
    }

    private void moveVerticalAnimation(ViewGroup viewGroup, int fromY, int toY, long time) {

        TranslateAnimation translateAnimation = new TranslateAnimation(0, 0, fromY, toY);
        translateAnimation.setDuration(time);
        viewGroup.startAnimation(translateAnimation);

    }

    /**
     *
     * Camera Methods
     *
     */

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setupCamera(int width, int height) {
        // To get the camera resources, we first need a CameraManager

        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        // Now time to call the resources and surround it with try/catch in case something goes wrong

        try {
            // With that cameraManager, we can find any information about our current camera
            // First get the available cameras in the phone
            // Then get the information of each camera (using CameraCharacteristics)

            for(String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);

                // For now were are just using the rear facing camera

                if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                // Now get the StreamConfigurationMap which will give us the sizes of the preview output sizes
                // based on the TextureView dimensions
                // Then we will pick the preview size that is preferred
                // Store the cameraId so we know which camera is being used at the moment and if we
                // need to change it later, we can update this variable
                StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                previewSize = getPreferredPreviewSize(streamConfigurationMap.getOutputSizes(SurfaceTexture.class), width, height);
                this.cameraId = cameraId;

                // Below is for image dimensions for output

                Size largestImageSize = Collections.max(Arrays.asList(streamConfigurationMap
                        .getOutputSizes(ImageFormat.JPEG)), new Comparator<Size>() {
                    @Override
                    public int compare(Size lhs, Size rhs) {
                        return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                    }
                });

                // Set up ImageReader to initialize the save image mechanism for each camera

                imageReader = ImageReader.newInstance(largestImageSize.getWidth(),
                        largestImageSize.getHeight(), ImageFormat.JPEG, 1);

                // Set up callback listener related to ImageReader to know what state it's in

                imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);

                return;
            }
        }
        catch (CameraAccessException e) {
            Log.e(TAG, "There was an issue setting up the camera");
            e.printStackTrace();
        }
        catch(NullPointerException e) {
            Log.e(TAG, "There is no camera on the phone");
            e.printStackTrace();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private Size getPreferredPreviewSize(Size[] mapSizes, int width, int height) {

        // This method will help return the correct sizes when taking a picture.
        // Without this, one can get a portrait photo that is meant for landscape.
        // Orientation is corrected here with the following iteration

        List<Size> collectorSizes  = new ArrayList<Size>();

        for(Size option : mapSizes) {
            // Landscape image
            if(width > height) {

                // We are performing a check to see if whatever Size we get is larger
                // than what the TextureView gives us (starting with the bare minimum preview size)
                // We are basically ensuring we are getting correctly returned dimensions and orientations
                // If the conditions are met, add them to the list of sizes

                // TextureView: (980 x 640)
                // if(1920 > 980 and 1080 > 640) --> add to list
                // Added Size: (1920 x 1080)

                if (option.getWidth() > width && option.getHeight() > height) {
                    collectorSizes.add(option);
                }
            }
            // Portrait Mode (width < height)
            else {

                // TextureView : (640 x 980)
                // if (1080 > 980 and 1920 > 640) --> add to list
                // Added size: (1080 x 1920)

                if(option.getWidth() > height && option.getHeight() > width) {
                    collectorSizes.add(option);
                }
            }
        }

        // Now pick up the size that is closes to the TextureView dimensions

        if(collectorSizes.size() > 0) {
            return Collections.min(collectorSizes, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }

        // Worst case scenario
        return mapSizes[0];
    }

    private void openCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        // get the camera functionality and protect it
        // Now, to open the camera, you need to have the cameraId and the stateCallback created
        // earlier. If not, how would the manager know which camera is opened and what state it is in?
        // It won't.

        try {
            cameraManager.openCamera(cameraId, cameraDeviceStateCallback, backgroundHandler);
            // NOTE: null --> for now we're just going to use the UI thread to open this
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
        catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if(cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }

        if(cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }

        // Clean up image reader resource

        if(imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    /**
     *
     * To create the CameraPreviewSession, it will require the following:
     *
     * SurfaceTexture, Surface, CaptureRequestBuilder, CaptureRequest, CameraCaptureSession,
     * CameraCaptureSession.StateCallback (to begin the capture session),
     * CameraCaptureSession.CaptureCallback (to get camera session states and get ready)
     *
     * Procedure:
     * 1. Create SurfaceTexture object using the textureView created at the beginning
     * 2. Set the DefaultBufferSize of the surfaceTexture to the previewSize created from the beginning
     * (get the width and height using the invocation of the Size object of previewSize)
     * 3. Create the Surface object, which requires the SurfaceTexture as parameter
     * 4. Create the CaptureRequestBuilder object using the cameraDevice. Use TEMPLATE_PREVIEW
     * as parameter
     * 5. Add the Surface as target to the CaptureRequestBuilder (this will allow the image to show image on view)
     * 6. Create capture session using cameraDevice. It has to take in a list of Surface objects (1 in this case),
     * the StateCallback of the CameraCaptureSession to know the states, and Handler
     * 7. Inside onConfigured, create the CaptureRequest by simply building the CaptureRequestBuilder
     * 8. Then set the CameraCaptureSession variable
     * 9. Initialize the CameraCaptureSession using the setRepeatingRequest that takes the
     * CaptureRequest, a CameraCaptureSession.CaptureCallback, and a Handler as parameters
     *
     * */

    private void createCameraPreviewSession() {
        try {

            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);

            previewCaptureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewCaptureRequestBuilder.addTarget(previewSurface);

            // The list should contain surfaces for a particular purpose: preview, capture, and video

            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {

                            if(cameraDevice == null) {
                                return;
                            }

                            try {
                                previewCaptureRequest = previewCaptureRequestBuilder.build();
                                cameraCaptureSession = session;
                                cameraCaptureSession.setRepeatingRequest(previewCaptureRequest,
                                        cameraCaptureSessionCaptureCallback, backgroundHandler);
                            }
                            catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e(TAG, "Unable to create camera session.");
                        }
                    }, null);

        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * We are filling anything that was originally running on the UI thread (any capture
     * session / open camera) by opening background threads and then decongestng the UI
     * thread using a backgroundHandler, which needs HandlerThread's Looper
     *
     */

    private void openBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackgroundThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void closeBackgroundThread() {

        backgroundThread.quitSafely();

        try {
            // I want to block the UI thread until the desired thread to be closed is properly released
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * Focus Methods - Method for focusing before picture is taken
     *
     */

    private void lockFocus() {

        // To lock the focus, ensure that the current camera state is on Focus Lock.
        // Create a CaptureRequestBuilder and then create a request to trigger the autofocus
        // This is a key/value pair that needs to match otherwise this will not work.
        // Build the CaptureRequestBuilder and create CameraCaptureSession by capturing.
        // It takes in a CaptureRequest, CameraCaptureSession.CaptureCallBack and a Handler as parameters

        try {
            currentState = STATE_WAKE_LOCK;
            previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureResult.CONTROL_AF_TRIGGER_START);
            cameraCaptureSession.capture(previewCaptureRequestBuilder.build(), cameraCaptureSessionCaptureCallback, backgroundHandler);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void unlockFocus() {

        // After a focus, it must be unlocked otherwise the camera will just keep focusing.
        // Once it's unlocked it will be back to preview state but focused in at a certain area

        // Create CaptureRequestBuilder to cancel the auto focus trigger
        // Then create CameraCaptureSession by capturing
        // It takes in a CaptureRequest, CameraCaptureSession.CaptureCallBack and a Handler as parameters

        try {
            currentState = STATE_PREVIEW;
            previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureResult.CONTROL_AF_TRIGGER_CANCEL);
            cameraCaptureSession.capture(previewCaptureRequestBuilder.build(), cameraCaptureSessionCaptureCallback, backgroundHandler);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * At this point, if we want to capture an image or video record, a Surface is created for each one
     * Then each capture method will require the same proceeding objects - CaptureRequest and then a CameraCaptureSession
     *
     */

    private void takePhoto() {

        imageFile = createImageFile();

        if(imageFile == null) {
            Log.e(TAG, "Unable to save image file");
            Toast.makeText(getApplicationContext(), "Unable to save image file.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Next create a Runnable to that saves the image in the background

        lockFocus();
    }

    private void captureStillImage() {

        try {
            CaptureRequest.Builder captureStillBuilder = cameraDevice
                    .createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureStillBuilder.addTarget(imageReader.getSurface());

            // Fix alignment of preview and the actual image itself

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureStillBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            // CameraCaptureSession CaptureCallback

            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    // When it is complete, unlock the focus and prepare to take another picture
                    unlockFocus();
                }
            };

            cameraCaptureSession.capture(captureStillBuilder.build(), captureCallback, null);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    /**
     *
     * File Related Methods
     *
     */

    private File createImageFile() {
        String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String imageFileName = "IMAGE_BP_" + timeStamp + "_";

        File storageDirectory = new File(Environment.getExternalStorageDirectory() + "/Blocparty/");

        if(!storageDirectory.exists()) {
            storageDirectory.mkdir();
        }

        try {
            return File.createTempFile(imageFileName, ".jpg", storageDirectory);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}
