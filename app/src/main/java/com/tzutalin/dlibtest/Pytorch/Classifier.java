package com.tzutalin.dlibtest.Pytorch;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class Classifier {
    Module model;
    float[] mean = {0.485f, 0.456f, 0.406f};
    float[] std = {0.229f, 0.224f, 0.225f};
    public Classifier(String modelPath){
        model = Module.load(modelPath);
    }

    public void setMeanAndStd(float[] mean, float[] std){

        this.mean = mean;
        this.std = std;
    }

    public Tensor preprocess(float[] bitmap, int width, int heigth){
        long[] shape = {1L,1L,26L,34L};
        return Tensor.fromBlob(bitmap,shape);
    }

    public float predict(float[] bitmap){
        Tensor tensor = preprocess(bitmap,34,26);
        IValue inputs = IValue.from(tensor);
        float[] outputs = model.forward(inputs).toTensor().getDataAsFloatArray();
        System.out.println("check="+ sigmoid(outputs[0]));
        return Math.round(sigmoid(outputs[0]));
    }

    private static double sigmoid(double x)
    {
        return 1 / (1 + Math.exp(-x));
    }

//    private float[] bitmapToIntArray(final Bitmap bitmap){
//        int x = bitmap.getWidth();
//        int y = bitmap.getHeight();
//        int[] intArray = new int[x * y];
//        bitmap.getPixels(intArray, 0, x, 0, 0, x, y);
//        float[] floatArray = new float[intArray.length];
//        for(int i =0;i<=intArray.length-1;i++){
//            System.out.println("test"+i+"= "+intArray[i]);
//            floatArray[i] = (float) intArray[i];
//        }
//        return floatArray;
//    }
}



