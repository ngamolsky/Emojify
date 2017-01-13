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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.SparseArray;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import timber.log.Timber;

/**
 * Identifies and adds correct emoji to face
 */
class Emojifier {

    // Enum for all possible Emojis
    // TODO Colt doesn't like enums: https://www.youtube.com/watch?v=Hzs6OBcvNQE
    private enum Emoji {
        SMILE,
        SAD,
        NEUTRAL,
        LEFT_WINK,
        RIGHT_WINK,
        CLOSED_SMILE,
        CLOSED_FROWN
    }

    /**
     * Detects faces in an image and draws the most similar emoji
     *
     * @param context Application context
     * @param picture The Bitmap to scan for the faces
     * @return New bitmap including emojis
     */
    static Bitmap detectAndDrawFaces(Context context, Bitmap picture) {

        // Create the face detector, disable tracking and enable classifications
        FaceDetector detector = new FaceDetector.Builder(context)
                .setTrackingEnabled(false)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        // Build the frame
        Frame frame = new Frame.Builder().setBitmap(picture).build();

        // Detect the faces
        SparseArray<Face> faces = detector.detect(frame);

        // Log the number of faces
        Timber.d("numFaces: " + faces.size());

        // Initialize result bitmap to original picture
        Bitmap resultBitmap = picture;

        // Iterate through the faces
        for (int i = 0; i < faces.size(); ++i) {
            Face face = faces.valueAt(i);

            // Determine most appropriate emoji and and set to emojiBitmap
            Bitmap emojiBitmap;
            switch (whichEmoji(face)) {
                case SMILE:
                    emojiBitmap = BitmapFactory.decodeResource(context.getResources(),
                            R.drawable.smile);
                    break;
                case SAD:
                    emojiBitmap = BitmapFactory.decodeResource(context.getResources(),
                            R.drawable.frown);
                    break;
                case NEUTRAL:
                    emojiBitmap = BitmapFactory.decodeResource(context.getResources(),
                            R.drawable.neutral);
                    break;
                case LEFT_WINK:
                    emojiBitmap = BitmapFactory.decodeResource(context.getResources(),
                            R.drawable.leftwink);
                    break;
                case RIGHT_WINK:
                    emojiBitmap = BitmapFactory.decodeResource(context.getResources(),
                            R.drawable.rightwink);
                    break;
                case CLOSED_SMILE:
                    emojiBitmap = BitmapFactory.decodeResource(context.getResources(),
                            R.drawable.closed_smile);
                    break;
                case CLOSED_FROWN:
                    emojiBitmap = BitmapFactory.decodeResource(context.getResources(),
                            R.drawable.closed_frown);
                    break;
                default:
                    emojiBitmap = null;
                    Toast.makeText(context, R.string.no_emoji, Toast.LENGTH_SHORT).show();
            }

            // Add the emojiBitmap to the proper position in the original image
            resultBitmap = addBitmapToFace(resultBitmap, emojiBitmap, face);
        }

        detector.release();
        return resultBitmap;
    }

    /**
     * Determines the closest emoji to the expression on the face, based on the
     * odds that the person is smiling and has each eye open
     *
     * @param face The face on which to draw the emoji
     * @return The most appropriate emoji enum
     */
    private static Emoji whichEmoji(Face face) {

        // Log all the detected probabilities
        Timber.d("smilingProb: " + face.getIsSmilingProbability());
        Timber.d("rightEyeOpenProb: " + face.getIsRightEyeOpenProbability());
        Timber.d("leftEyeOpenProb: " + face.getIsLeftEyeOpenProbability());

        // Determine the smiling and frowning thresholds
        boolean smiling = face.getIsSmilingProbability() > .15; // TODO consider making these constant values that can be tweaked above
        boolean frowning = face.getIsSmilingProbability() < .01;

        // Determine the eyes closed thresholds
        boolean leftEyeWink = (face.getIsLeftEyeOpenProbability() < .5) &&
                (face.getIsRightEyeOpenProbability() > .5);
        boolean rightEyeWink = (face.getIsRightEyeOpenProbability() < .5) &&
                (face.getIsLeftEyeOpenProbability() > .5);
        boolean bothEyesClosed = (face.getIsLeftEyeOpenProbability() < .5) &&
                (face.getIsRightEyeOpenProbability() < .5);

        // Determine and return the appropriate emoji
        Emoji emoji;
        if (leftEyeWink) {
            emoji = Emoji.LEFT_WINK;
        } else if (rightEyeWink) {
            emoji = Emoji.RIGHT_WINK;
        } else if (bothEyesClosed) {
            if (smiling) {
                emoji = Emoji.CLOSED_SMILE;
            } else {
                emoji = Emoji.CLOSED_FROWN;
            }
        } else {
            if (smiling) {
                emoji = Emoji.SMILE;
            } else if (frowning) {
                emoji = Emoji.SAD;
            } else {
                emoji = Emoji.NEUTRAL;
            }
        }

        // Log the chosen Emoji
        Timber.d(emoji.name());

        return emoji;
    }

    /**
     * Combines the original picture with the emoji bitmaps
     *
     * @param backgroundBitmap The original picture
     * @param emojiBitmap      The chosen emoji
     * @param face             The detected face
     * @return The final bitmap, including the emojis over the faces
     */
    private static Bitmap addBitmapToFace(Bitmap backgroundBitmap, Bitmap emojiBitmap, Face face) {

        // Initialize the results bitmap to be a mutable copy of the original image
        Bitmap resultBitmap = Bitmap.createBitmap(backgroundBitmap.getWidth(),
                backgroundBitmap.getHeight(), backgroundBitmap.getConfig());

        // Scale the emoji so it looks better on the face
        float scaleFactor = .9f; // TODO create constant for this

        // Determine the size of the emoji to match the width of the face and preserve aspect ratio
        int newEmojiWidth = (int) (face.getWidth() * scaleFactor);
        int newEmojiHeight = (int) (emojiBitmap.getHeight() *
                newEmojiWidth / emojiBitmap.getWidth() * scaleFactor);


        // Scale the emoji
        emojiBitmap = Bitmap.createScaledBitmap(emojiBitmap, newEmojiWidth, newEmojiHeight, false);

        // Determine the emoji position so it best lines up with the face
        float emojiPositionX =
                (face.getPosition().x + face.getWidth() / 2) - emojiBitmap.getWidth() / 2;
        float emojiPositionY =
                (face.getPosition().y + face.getHeight() / 2) - emojiBitmap.getHeight() / 3;

        // Create the canvas and draw the bitmaps to it
        Canvas canvas = new Canvas(resultBitmap);
        canvas.drawBitmap(backgroundBitmap, new Matrix(), null); // TODO what's the matrix for?
        canvas.drawBitmap(emojiBitmap, emojiPositionX, emojiPositionY, null);

        return resultBitmap;
    }
}
