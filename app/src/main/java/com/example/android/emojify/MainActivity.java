/*
* Copyright (C) 2017 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*  	http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.example.android.emojify;

import static android.support.v4.content.FileProvider.getUriForFile;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_STORAGE_PERMISSION = 1;

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String FILE_PROVIDER_AUTHORITY = "com.example.android.fileprovider";
    private static final String FILE_PATH_KEY = "file_path";

    private ImageView mImageView;

    private Button mEmojifyButton;
    private FloatingActionButton mShareButton;
    private FloatingActionButton mSaveButton;
    private FloatingActionButton mClearButton;

    private TextView mTitleTextView;

    private String mTempPhotoPath;
    private String mResultPhotoPath;

    private Bitmap mResultsBitmap;

    private Boolean mIsTempDeleted = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Views
        mImageView = (ImageView) findViewById(R.id.image_view);

        mEmojifyButton = (Button) findViewById(R.id.emojify_button);
        mSaveButton = (FloatingActionButton) findViewById(R.id.save_button);
        mShareButton = (FloatingActionButton) findViewById(R.id.share_button);
        mClearButton = (FloatingActionButton) findViewById(R.id.clear_button);

        mTitleTextView = (TextView) findViewById(R.id.title_text_view);

        // Restore image on configuration change
        if(savedInstanceState!=null){
            mTempPhotoPath = savedInstanceState.getString(FILE_PATH_KEY);
            if(mTempPhotoPath !=null) {
                processAndSetImage();
            }
        }
    }

    /**
     * OnClick method for "Emojify Me!" Button
     * @param view The emojify button
     */
    public void emojifyMe(View view) {
        // Check for the external storage permission
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // If you do not have permission, request it
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_STORAGE_PERMISSION);
        } else {
            // Launch the camera if the permission exists
            launchCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        // Called when you request permission to read and write to external storage
        switch (requestCode) {
            case REQUEST_STORAGE_PERMISSION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // If you get permission, launch the camera
                    launchCamera();
                } else {
                    // If you do not get permission, log it and show a Toast
                    Log.d(TAG, "onRequestPermissionsResult: PERMISSION DENIED");
                    Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    /**
     * Creates a temporary image file and captures a picture to store in it
     */
    private void launchCamera() {

        // Create the capture image intent
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createTempImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                ex.printStackTrace();
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {

                //Get the content URI for the image file
                Uri photoURI = getUriForFile(this,
                        FILE_PROVIDER_AUTHORITY,
                        photoFile);

                // Add the URI so the camera can store the image
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);

                // Launch the camera activity
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    /**
     * Helper method to create the temporary image file in the cache directory
     * @return The temp image file
     * @throws IOException Thrown if there is an error creating the file
     */
    private File createTempImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalCacheDir();

        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );



        // Save a file: path for use with ACTION_VIEW intents
        mTempPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // If the image capture activity was called and was successful
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // Process the image to detect faces and draw the appropriate emoji
            processAndSetImage();
        } else {

            // Otherwise, delete the temporary image file
            deleteTempImageFile();
        }
    }

    /**
     * Method for processing the captured image and setting it to the TextView
     */
    private void processAndSetImage(){

        // Toggle Visibility of the views
        mEmojifyButton.setVisibility(View.GONE);
        mTitleTextView.setVisibility(View.GONE);
        mSaveButton.setVisibility(View.VISIBLE);
        mShareButton.setVisibility(View.VISIBLE);
        mClearButton.setVisibility(View.VISIBLE);

        // Resample the saved image to fit the ImageView
        Bitmap picture = resamplePic();

        // Detect faces and draw appropriate emoji on top of the image.
        mResultsBitmap = Emojifier.detectAndDrawFaces(this, picture);

        // Set the new bitmap to the ImageView
        mImageView.setImageBitmap(mResultsBitmap);
    }

    /**
     * Helper method fir resampling the captured photo to fit the screen for better memeory usage
     * @return The resampled bitmap
     */
    private Bitmap resamplePic(){

        // Get device screen size information
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        int targetH = metrics.heightPixels;
        int targetW = metrics.widthPixels;

        // Get the dimensions of the original bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mTempPhotoPath, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine how much to scale down the image
        int scaleFactor = Math.min(photoW/targetW, photoH/targetH);

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;

        return BitmapFactory.decodeFile(mTempPhotoPath);
    }

    /**
     * OnClick method for the save button
     * @param view The save button
     */
    public void saveMe(View view) {
        // Save the image
        saveImage();
    }

    /**
     * OnClick method for the share button, saves and shares the new bitmap
     * @param view The share button
     */
    public void shareMe(View view) {
        // Save the new bitmap
        saveImage();

        // Create the share intent and start the share activity
        File imageFile = new File(mResultPhotoPath);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/*");
        Uri photoURI = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, imageFile);
        shareIntent.putExtra(Intent.EXTRA_STREAM, photoURI);
        startActivity(shareIntent);
    }

    /**
     * OnClick for the clear button, resets the app to original state
     * @param view The clear button
     */
    public void clearImage(View view) {
        // Clear the image and toggle the view visibility
        mImageView.setImageResource(0);
        mEmojifyButton.setVisibility(View.VISIBLE);
        mTitleTextView.setVisibility(View.VISIBLE);
        mShareButton.setVisibility(View.GONE);
        mSaveButton.setVisibility(View.GONE);
        mClearButton.setVisibility(View.GONE);

        // If the temporary file still exists, delete it
        if(!mIsTempDeleted) {
            deleteTempImageFile();
            mIsTempDeleted = true;
        }
    }

    /**
     * Helper method for saving the image
     */
    private void saveImage(){

        // Create the new file in the external storage
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + ".jpg";
        File storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/Emojify");
        boolean success = true;
        if(!storageDir.exists()) {
            success = storageDir.mkdirs();
        }

        // Save the new Bitmap
        if(success) {
            File imageFile = new File(storageDir, imageFileName);
            mResultPhotoPath = imageFile.getAbsolutePath();
            try {
                OutputStream fOut = new FileOutputStream(imageFile);
                mResultsBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
                fOut.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            // If the temporary file still exists, delete it
            if(!mIsTempDeleted) {
                deleteTempImageFile();
                mIsTempDeleted = true;
            }

            // Show a Toast with the save location
            String savedMessage = getString(R.string.saved_message, mResultPhotoPath);
            Toast.makeText(this, savedMessage, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Helper method for deleting the cached image file
     * @return Boolean indicating if the file was deleted.
     */
    private void deleteTempImageFile(){
        // Get the file
        File imageFile = new File(mTempPhotoPath);

        boolean deleted;

        if(!mIsTempDeleted) {
            deleted = imageFile.delete();

            // If there is an error deleting the file, show a Toast
            if (!deleted) {
                String errorMessage = getString(R.string.error);
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
            } else {

                mIsTempDeleted = true;
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if(mTempPhotoPath !=null){
            outState.putString(FILE_PATH_KEY, mTempPhotoPath);
        }
    }
}
