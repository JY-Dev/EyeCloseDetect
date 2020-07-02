package com.tzutalin.dlibtest.Pytorch;

import android.graphics.Bitmap;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

public class ClassifierDetectEyes {
    Module model;
    float[] mean = {0.485f, 0.456f, 0.406f};
    float[] std = {0.229f, 0.224f, 0.225f};
    public ClassifierDetectEyes(String modelPath){
        model = Module.load(modelPath);
    }

    public void setMeanAndStd(float[] mean, float[] std){

        this.mean = mean;
        this.std = std;
    }

    public Tensor preprocess(Bitmap bitmap, int width, int heigth){
        long[] shape = {1L,1L,26L,34L};
        return TensorImageUtils.bitmapToFloat32Tensor(bitmap,mean,std);
    }

    public float predict(Bitmap bitmap){
        Tensor tensor = preprocess(bitmap,34,26);
        IValue inputs = IValue.from(tensor);
        float[] outputs = model.forward(inputs).toTensor().getDataAsFloatArray();
        System.out.println("check="+ outputs[0]);
        return outputs[0];
    }

    private static double sigmoid(double x)
    {
        return 1 / (1 + Math.exp(-x));
    }
}
