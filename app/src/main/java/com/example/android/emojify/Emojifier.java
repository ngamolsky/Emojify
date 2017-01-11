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

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;



class Emojifier {

    // Enum for all possible Emojis
    private enum Emoji {
        SMILE,
        SAD,
        NEUTRAL,
        LEFT_WINK,
        RIGHT_WINK,
        CLOSED_SMILE,
        CLOSED_FROWN
    }

    static Bitmap detectAndDrawFaces(Context context, Bitmap picture){

        FaceDetector detector = new FaceDetector.Builder(context)
                .setTrackingEnabled(false)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        Frame frame = new Frame.Builder().setBitmap(picture).build();

        SparseArray<Face> faces = detector.detect(frame);
        Log.d(TAG, "detectAndDrawFaces: numFaces: " + faces.size());

        Bitmap resultBitmap = picture;

        for (int i = 0; i < faces.size(); ++i) {
            Face face = faces.valueAt(i);
            Log.d(TAG, "detectAndDrawFaces: faceposition x " + face.getPosition().x + " / faceposition y " + face.getPosition().y);
            Log.d(TAG, "detectAndDrawFaces: picture width " + picture.getWidth() + " / picture height " + picture.getHeight());
            Bitmap emojiBitmap;
            switch (whichEmoji(face)){
                case SMILE:
                    emojiBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.smile);
                    break;
                case SAD:
                    emojiBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.frown);
                    break;
                case NEUTRAL:
                    emojiBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.neutral);
                    break;
                case LEFT_WINK:
                    emojiBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.leftwink);
                    break;
                case RIGHT_WINK:
                    emojiBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.rightwink);
                    break;
                case CLOSED_SMILE:
                    emojiBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.closed_smile);
                    break;
                case CLOSED_FROWN:
                    emojiBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.closed_frown);
                    break;
                default:
                    emojiBitmap = null;
                    Toast.makeText(context, R.string.no_emoji, Toast.LENGTH_SHORT).show();
            }

            resultBitmap = addBitmapToFace(resultBitmap, emojiBitmap, face);
        }

        detector.release();
        return resultBitmap;
    }

    private static Emoji whichEmoji(Face face){

        Log.d(TAG, "whichEmoji: smilingProb: " + face.getIsSmilingProbability() +
                " / leftEyeOpenProb: " + face.getIsLeftEyeOpenProbability() +
                " / rightEyeOpenProb: " + face.getIsRightEyeOpenProbability());

        boolean smiling = face.getIsSmilingProbability() > .15;
        boolean frowning = face.getIsSmilingProbability() < .01;

        boolean leftEyeWink = (face.getIsLeftEyeOpenProbability() < .5) &&
                (face.getIsRightEyeOpenProbability() > .5);
        boolean rightEyeWink = (face.getIsRightEyeOpenProbability() < .5) &&
                (face.getIsLeftEyeOpenProbability() > .5);
        boolean bothEyesClosed = (face.getIsLeftEyeOpenProbability() < .5) &&
                (face.getIsRightEyeOpenProbability() < .5);

        Emoji emoji;

        if(leftEyeWink){
            emoji = Emoji.LEFT_WINK;
        } else if (rightEyeWink){
            emoji = Emoji.RIGHT_WINK;
        } else if (bothEyesClosed){
            if(smiling){
                emoji = Emoji.CLOSED_SMILE;
            } else {
                emoji = Emoji.CLOSED_FROWN;
            }
        } else {
            if(smiling){
                emoji = Emoji.SMILE;
            } else if (frowning){
                emoji = Emoji.SAD;
            } else {
                emoji = Emoji.NEUTRAL;
            }
        }

        return emoji;
    }

    private static Bitmap addBitmapToFace(Bitmap backgroundBitmap, Bitmap emojiBitmap, Face face){

        Bitmap resultBitmap = Bitmap.createBitmap(backgroundBitmap.getWidth(),
                backgroundBitmap.getHeight(), backgroundBitmap.getConfig());

        float scaleFactor = .9f;

        int newEmojiWidth = (int) (face.getWidth() * scaleFactor);
        int newEmojiHeight = (int) (emojiBitmap.getHeight() * newEmojiWidth/emojiBitmap.getWidth() * scaleFactor);


        emojiBitmap = Bitmap.createScaledBitmap(emojiBitmap, newEmojiWidth, newEmojiHeight, false);

        float emojiPositionX = (face.getPosition().x + face.getWidth()/2) - emojiBitmap.getWidth()/2;
        float emojiPositionY = (face.getPosition().y + face.getHeight()/2) - emojiBitmap.getHeight()/3;

        Canvas canvas = new Canvas(resultBitmap);
        canvas.drawBitmap(backgroundBitmap, new Matrix(), null);
        canvas.drawBitmap(emojiBitmap, emojiPositionX, emojiPositionY, null);
        return resultBitmap;
    }
}
