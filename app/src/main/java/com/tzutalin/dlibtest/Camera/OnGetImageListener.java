/*
 * Copyright 2016-present Tzutalin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tzutalin.dlibtest.Camera;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Trace;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.ImageView;

import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.VisionDetRet;
import com.tzutalin.dlibtest.Pytorch.Classifier;
import com.tzutalin.dlibtest.Utils.FileUtils;
import com.tzutalin.dlibtest.ImageUtils;
import com.tzutalin.dlibtest.R;
import com.tzutalin.dlibtest.CustomView.TrasparentTitleView;
import com.tzutalin.dlibtest.Utils.Utils;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Class that takes in preview frames and converts the image to Bitmaps to process with dlib lib.
 */
public class OnGetImageListener implements OnImageAvailableListener {
    private static final boolean SAVE_PREVIEW_BITMAP = false;

    //324, 648, 972, 1296, 224, 448, 672, 976, 1344
    private static final int INPUT_SIZE = 976;
    private static final String TAG = "OnGetImageListener";
    private Runnable updateRunnable;
    private int mScreenRotation = 90;
    private boolean flag = true;
    private Bitmap bmp;
    private Bitmap bmp2;
    private Handler handler;
    private List<VisionDetRet> results;
    private int mPreviewWdith = 0;
    private int mPreviewHeight = 0;
    private byte[][] mYUVBytes;
    private int[] mRGBBytes = null;
    private Bitmap mRGBframeBitmap = null;
    private Bitmap mCroppedBitmap = null;
    private Bitmap mResizedBitmap = null;
    private Bitmap mInversedBipmap = null;
    private Bitmap mResultBipmap = null;
    private long time = 0L;
    private Vibrator vibrator;
    private int noDectCT = 0;

    private boolean mIsComputing = false;
    private Handler mInferenceHandler;
    private Classifier classifier;
    private Context mContext;
    private FaceDet mFaceDet;
    private TrasparentTitleView mTransparentTitleView;
    private FloatingCameraWindow mWindow;
    private FloatingCameraWindow mWindow2;
    private FloatingCameraWindow mWindow3;
    private ImageView test1;
    private ImageView test2;
    private ImageView mainView;
    private CameraConnectionFragment fragment;
    Activity mActivity ;
    private Paint mFaceLandmardkPaint;

    private int mframeNum = 0;

    public void initialize(
            final Context context,
            final AssetManager assetManager,
            final TrasparentTitleView scoreView,
            final Handler handler,
            final CameraConnectionFragment fragment,
            final ImageView test1,
            final ImageView test2,
            final ImageView mainView,
            Activity activity) {
        this.mContext = context;
        this.mTransparentTitleView = scoreView;
        this.mInferenceHandler = handler;
        this.test1 = test1;
        this.test2 = test2;
        this.mainView = mainView;
        this.fragment = fragment;
        mFaceDet = new FaceDet(Constants.getFaceShapeModelPath());
        mWindow = new FloatingCameraWindow(mContext);
        mWindow2 = new FloatingCameraWindow(mContext);
        mWindow3 = new FloatingCameraWindow(mContext);
        mFaceLandmardkPaint = new Paint();
        mFaceLandmardkPaint.setColor(Color.GREEN);
        mFaceLandmardkPaint.setStrokeWidth(2);
        mFaceLandmardkPaint.setStyle(Paint.Style.STROKE);
        classifier = new Classifier(Utils.assetFilePath(mContext, "weight_for_android.pt"));
        vibrator = (Vibrator)mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mActivity  = activity;
    }

    public void deInitialize() {
        synchronized (OnGetImageListener.this) {
            if (mFaceDet != null) {
                mFaceDet.release();
            }

            if (mWindow != null) {
                mWindow.release();
                if(mWindow2!=null) mWindow2.release();
                if(mWindow3!=null) mWindow3.release();
            }
        }
    }

    private void drawResizedBitmap(final Bitmap src, final Bitmap dst) {

        Display getOrient = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int orientation = Configuration.ORIENTATION_UNDEFINED;
        Point point = new Point();
        getOrient.getSize(point);
        int screen_width = point.x;
        int screen_height = point.y;
        Log.d(TAG, String.format("screen size (%d,%d)", screen_width, screen_height));
        if (screen_width < screen_height) {
            orientation = Configuration.ORIENTATION_PORTRAIT;
            mScreenRotation = -90;
        } else {
            orientation = Configuration.ORIENTATION_LANDSCAPE;
            mScreenRotation = 0;
        }
        final float minDim = Math.min(src.getWidth(), src.getHeight());

        final Matrix matrix = new Matrix();

        // We only want the center square out of the original rectangle.
        final float translateX = -Math.max(0, (src.getWidth() - minDim) / 2);
        final float translateY = -Math.max(0, (src.getHeight() - minDim) / 2);
        matrix.preTranslate(translateX, translateY);

        final float scaleFactor = dst.getHeight() / minDim;
        matrix.postScale(scaleFactor, scaleFactor);

        // Rotate around the center if necessary.
        if (mScreenRotation != 0) {
            matrix.postTranslate(-dst.getWidth() / 2.0f, -dst.getHeight() / 2.0f);
            matrix.postRotate(mScreenRotation);
            matrix.postTranslate(dst.getWidth() / 2.0f, dst.getHeight() / 2.0f);
        }

        final Canvas canvas = new Canvas(dst);
        canvas.drawBitmap(src, matrix, null);
    }

    public Bitmap imageSideInversion(Bitmap src){
        Matrix sideInversion = new Matrix();
        sideInversion.setScale(-1, 1);
        Bitmap inversedImage = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), sideInversion, false);
        return inversedImage;
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            // No mutex needed as this method is not reentrant.
            if (mIsComputing) {
                image.close();
                return;
            }
            mIsComputing = true;

            Trace.beginSection("imageAvailable");

            final Plane[] planes = image.getPlanes();

            // Initialize the storage bitmaps once when the resolution is known.
            if (mPreviewWdith != image.getWidth() || mPreviewHeight != image.getHeight()) {
                mPreviewWdith = image.getWidth();
                mPreviewHeight = image.getHeight();
                System.out.println("check="+mPreviewHeight+"_"+mPreviewWdith);
                //Log.d(TAG, String.format("Initializing at size %dx%d", mPreviewWdith, mPreviewHeight));
                mRGBBytes = new int[mPreviewWdith * mPreviewHeight];
                mRGBframeBitmap = Bitmap.createBitmap(mPreviewWdith, mPreviewHeight, Config.ARGB_8888);
                mCroppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);

                mYUVBytes = new byte[planes.length][];
                for (int i = 0; i < planes.length; ++i) {
                    mYUVBytes[i] = new byte[planes[i].getBuffer().capacity()];
                }
            }

            for (int i = 0; i < planes.length; ++i) {
                planes[i].getBuffer().get(mYUVBytes[i]);
            }

            final int yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();
            ImageUtils.convertYUV420ToARGB8888(
                    mYUVBytes[0],
                    mYUVBytes[1],
                    mYUVBytes[2],
                    mRGBBytes,
                    mPreviewWdith,
                    mPreviewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    false);

            image.close();
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
            //Log.e(TAG, "Exception!", e);
            Trace.endSection();
            return;
        }

        mRGBframeBitmap.setPixels(mRGBBytes, 0, mPreviewWdith, 0, 0, mPreviewWdith, mPreviewHeight);
        drawResizedBitmap(mRGBframeBitmap, mCroppedBitmap);

        mInversedBipmap = imageSideInversion(mCroppedBitmap);
        mResizedBitmap = Bitmap.createScaledBitmap(mInversedBipmap, (int)(INPUT_SIZE/4.5), (int)(INPUT_SIZE/4.5), true);
        mResultBipmap = imageSideInversion(mCroppedBitmap);
        mInferenceHandler.post(
                new Runnable() {
                    @Override
                    public void run() {

                        if (!new File(Constants.getFaceShapeModelPath()).exists()) {
                            mTransparentTitleView.setText("Copying landmark model to " + Constants.getFaceShapeModelPath());
                            FileUtils.copyFileFromRawToOthers(mContext, R.raw.shape_predictor_68_face_landmarks, Constants.getFaceShapeModelPath());
                        }

                        if(mframeNum % 3 == 0){
                            long startTime = System.currentTimeMillis();
                            synchronized (OnGetImageListener.this) {
                                results = mFaceDet.detect(mResizedBitmap);
                            }
			                long endTime = System.currentTimeMillis();
                            mTransparentTitleView.setText("Time cost: " + String.valueOf((endTime - startTime) / 1000f) + " sec");
                        }
                        int leftX=0;
                        int leftY=0;
                        int rightX=0;
                        int rightY=0;
                        // Draw on bitmap
                        if (results.size() != 0) {
                            for (final VisionDetRet ret : results) {
                                float resizeRatio = 4.5f;
                                Canvas canvas = new Canvas(mResultBipmap);
                                int count = 1;
                                // Draw landmark
                                ArrayList<Point> landmarks = ret.getFaceLandmarks();
                                for (Point point : landmarks) {

                                    int pointX = (int) (point.x * resizeRatio);
                                    int pointY = (int) (point.y * resizeRatio);
                                    if(count==37){
                                        leftX = pointX+20;

                                    }else if(count == 38){
                                        leftY = pointY-20;
                                    } else if(count == 43){
                                        rightX = pointX+15;

                                    } else if(count==44){
                                        rightY = pointY-20;
                                    }
                                    count++;
                                    canvas.drawCircle(pointX, pointY, 4, mFaceLandmardkPaint);
                                }
                            }
                            bmp = Bitmap.createBitmap(mInversedBipmap,leftX,leftY,90,82);
                            bmp2 = Bitmap.createBitmap(mInversedBipmap,rightX,rightY,90,82);
                            bmp = Bitmap.createScaledBitmap(bmp,34,26,false);
                            bmp2 = Bitmap.createScaledBitmap(bmp2,34,26,false);
                            float right = classifier.predict(doGreyscale(bmp2));
                            float left = classifier.predict(doGreyscale(bmp));
                            fragment.setEyeText(Float.toString(right), Float.toString(left));
                            if(right!=0F||left!=0F){
                                time = System.currentTimeMillis();
                            }
                            if(time+2000<System.currentTimeMillis()){
                                vibrator.vibrate(1000);
                                handlere.post(runnable);
                            } else {
                                handlere.removeCallbacks(runnable);
                                if(fragment.getVisibility()) fragment.setVisible(false);
                            }
                            noDectCT = 0;
                        }
                        noDectCT++;
                        if(noDectCT>20) {
                            time = System.currentTimeMillis();
                            handlere.removeCallbacks(runnable);
                            if(fragment.getVisibility()) fragment.setVisible(false);
                        }

                        fragment.setImage(bmp,bmp2,mResultBipmap);
                        mframeNum++;
                        //time = System.currentTimeMillis();
                        //mainView.setImageBitmap(mResultBipmap);
                        //mWindow.setRGBBitmap(mResultBipmap);

                        mIsComputing = false;
                    }

                });

        Trace.endSection();
    }


    private Handler handlere = new Handler();
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {

            fragment.setVisible(flag);
            flag = !flag;
            handlere.postDelayed(runnable,800);
        }
    };


    public static float[] doGreyscale(Bitmap src) {
        // constant factors
        final double GS_RED = 0.299;
        final double GS_GREEN = 0.587;
        final double GS_BLUE = 0.114;

        // pixel information
        int R, G, B;
        int pixel;

        // get image size
        int width = src.getWidth();
        int height = src.getHeight();
        float[] pixels = new float[width*height];
        int count = 0;
        // scan through every single pixel
        for(int x = 0; x < height; ++x) {
            for(int y = 0; y < width; ++y) {
                // get one pixel color
                pixel = src.getPixel(y, x);
                R = Color.red(pixel);
                G = Color.green(pixel);
                B = Color.blue(pixel);
                pixels[count] = (Math.round(GS_RED*R)+Math.round(GS_GREEN*G)+Math.round(GS_BLUE*B));
                count++;
            }
        }

        // return final image
        return pixels;
    }

    private static byte[] writeInt(int value) throws IOException {
        byte[] b = new byte[4];

        b[0] = (byte)(value & 0x000000FF);
        b[1] = (byte)((value & 0x0000FF00) >> 8);
        b[2] = (byte)((value & 0x00FF0000) >> 16);
        b[3] = (byte)((value & 0xFF000000) >> 24);

        return b;
    }

    /**
     * Write short to little-endian byte array
     * @param value
     * @return
     * @throws IOException
     */
    private static byte[] writeShort(short value) throws IOException {
        byte[] b = new byte[2];

        b[0] = (byte)(value & 0x00FF);
        b[1] = (byte)((value & 0xFF00) >> 8);

        return b;
    }
}
