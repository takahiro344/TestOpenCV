package com.example.testopencv;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class Mosaic {
    public static Bitmap mosaic(Bitmap bitmap, MatOfRect faceRecogResult) {
        if (faceRecogResult.toArray().length > 0) {
            // 送られた画像データ(bitmap)をMat形式に変換
            Mat matImg = new Mat();
            Utils.bitmapToMat(bitmap, matImg);

            Mat intermediateMat = new Mat();
            for (Rect face : faceRecogResult.toArray()) {
                Mat faceArea = matImg.submat(face);
                Imgproc.resize(faceArea, intermediateMat, new Size(), 0.1, 0.1, Imgproc.INTER_NEAREST);
                Imgproc.resize(intermediateMat, faceArea, faceArea.size(), 0., 0., Imgproc.INTER_NEAREST);
                faceArea.release();
            }

            Utils.matToBitmap(matImg, bitmap);
            return bitmap;
        }

        return bitmap;
    }
}
