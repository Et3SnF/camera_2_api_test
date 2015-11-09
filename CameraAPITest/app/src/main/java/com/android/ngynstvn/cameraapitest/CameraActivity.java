package com.android.ngynstvn.cameraapitest;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = CameraActivity.class.getSimpleName() + ": ";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(TAG, "onCreate() called");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
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
        super.onPause();
    }

    // --------------------- //

}
