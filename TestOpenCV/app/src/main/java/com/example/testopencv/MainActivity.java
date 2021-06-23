package com.example.testopencv;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import  java.io.InputStream;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.objdetect.CascadeClassifier;

public class MainActivity extends AppCompatActivity {
    static {
        System.loadLibrary("opencv_java4");
    }

    private static final String TAG = MainActivity.class.getName();

    private final static int REQUEST_GALLERY = 1000; //　ギャラリー用

    // ギャラリーから取得した画像のイメージビュー
    private ImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 先にImageViewをレイアウトビューのIDと紐づけ
        mImageView = findViewById(R.id.ImageView);

        Button galleyButton = findViewById(R.id.galleryButton);
        //普通のインナークラスを使っての実装
        galleyButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(intent, REQUEST_GALLERY);
        });
    }

    //これからImageViewにとった写真を張り付け。
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK) {
            Log.e(TAG, "onActivityResult error.");
            return;
        }

        if (requestCode == REQUEST_GALLERY) {
            // ギャラリーからの画像選択の場合
            Bitmap bitmap = loadImageFile(data.getData());
            if (bitmap == null) {
                Toast.makeText(this, "画像の読み込みに失敗しました。", Toast.LENGTH_LONG).show();
                return;
            }

            // 顔認識データ
            MatOfRect faceRecogResult = recognizeFace(bitmap);
            showModifiedImageFile(bitmap, faceRecogResult);

            String outputText = makeOutputText(faceRecogResult);
            Toast.makeText(this, outputText, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 画像ファイルを読み込む。
     * @param uri 画像ファイルのURI
     * @return 画像データ
     */
    private Bitmap loadImageFile(Uri uri) {
        Log.i(TAG, "URI: " + uri.toString());
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            // MutableからImmutableなBitmapへ変換。
            return bitmap.copy(Bitmap.Config.ARGB_8888, true);
        } catch (Exception e) {
            Toast.makeText(this, "ファイルが見つかりません。", Toast.LENGTH_LONG).show();
        }
        return null;
    }

    /**
     * 渡された画像を元に顔認識を行う
     *
     * @param bitmap 顔認識対象の画像
     * @return text 顔認識情報
     */
    protected MatOfRect recognizeFace(Bitmap bitmap) {
        // 送られた画像データ(bitmap)をMat形式に変換
        Mat matImg = new Mat();
        Utils.bitmapToMat(bitmap, matImg);

        // 顔認識を行うカスケード分類器インスタンスの生成(一度ファイルを書き出してファイルパスを取得)
        // 一度raw配下に格納されたxmlファイルを取得
        InputStream inStream = getResources().openRawResource(R.raw.haarcascade_frontalface_alt2);
        MatOfRect faceRects = new MatOfRect(); // 顔認識データを格納する

        try {
            // 出力したxmlファイルのパスをCascadeClassfleの引数に
            CascadeClassifier faceDetector = outputCascadeFile(inStream);
            // カスケード分類器に画像データを与えて、顔認識
            faceDetector.detectMultiScale(matImg, faceRects);
        } catch (Exception e) {
            Toast.makeText(this, "解析情報ファイルオープンに失敗しました。", Toast.LENGTH_SHORT).show();
        }

        return faceRects;
    }

    /**
     * あらかじめ用意されたopenCV分類器を一度取り込んで、書き出し使用可能にする。
     *
     * @param inStream 分類器の元データ
     * @return faceDetector 分類器データ
     */
    protected CascadeClassifier outputCascadeFile(InputStream inStream) {
        File cascadeDir = getDir("cscade", Context.MODE_PRIVATE);
        File cascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");

        // 取得したxmlファイルを特定ディレクトリに出力
        try (FileOutputStream outputStream = new FileOutputStream(cascadeFile)) {
            byte[] buf = new byte[2048];
            int rdBytes;
            while ((rdBytes = inStream.read(buf)) != -1) {
                try {
                    outputStream.write(buf, 0, rdBytes);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            return null;
        }

        // 出力したxmlファイルのパスをCascadeClassfleの引数に
        return new CascadeClassifier((cascadeFile.getAbsolutePath()));
    }

    /**
     * 顔を四角で囲う。
     * @param bitmap 画像データ
     * @param faceRecogResult 顔認識情報
     */
    private void showModifiedImageFile(Bitmap bitmap, MatOfRect faceRecogResult) {
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(5);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.RED);

        /**
         * 顔を四角で囲う。
        if (faceRecogResult.toArray().length > 0) {
            for (Rect face : faceRecogResult.toArray()) {
                canvas.drawRect(face.x, face.y, face.x + face.width, face.y + face.height, paint);
            }
        }
         */

        Bitmap mosaic = Mosaic.mosaic(bitmap, faceRecogResult);
        mImageView.setImageBitmap(mosaic);
    }

    /**
     * 出力用テキスト作成
     * 顔認識情報を元に座標情報をtextにする
     *
     * @param faceRects 顔認識情報
     * @return 座標情報
     */
    protected String makeOutputText(MatOfRect faceRects) {
        String text = "";

        // 顔認識結果をStringでreturn
        if (faceRects.toArray().length > 0) {
            for (Rect face : faceRects.toArray()) {
                text = "顔の縦幅:" + face.height + "\n" +
                        "顔の横幅" + face.width + "\n" +
                        "顔の位置(Y座標)" + face.y + "\n" +
                        "顔の位置(X座標)" + face.x;
            }
        }
        else {
            text = "顔が検出されませんでした。";
        }

        return text;
    }
}