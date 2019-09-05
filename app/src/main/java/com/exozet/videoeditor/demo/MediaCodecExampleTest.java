package com.exozet.videoeditor.demo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import androidx.annotation.RequiresApi;
import com.exozet.mcvideoeditor.BitmapToVideoEncoder;

import java.io.File;

public class MediaCodecExampleTest {

    String root = Environment.getExternalStorageDirectory().toString() + "/Download/process/";


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    void testCreateVideo(){
        BitmapToVideoEncoder bitmapToVideoEncoder = new BitmapToVideoEncoder(outputFile -> Log.i("MediaCodecExampleTest","video successfully created"));

        bitmapToVideoEncoder.startEncoding(1920, 1080, new File(root+"outputTests.mp4"));

        for (int i = 0 ;i<120 ; i++){

            Bitmap bMap = BitmapFactory.decodeFile(root + String.format("frame-%03d.jpg", i));

            Log.i("MediaCodecExampleTest","Video Create Progress " + i);

            bitmapToVideoEncoder.queueFrame(bMap);

        }
        bitmapToVideoEncoder.stopEncoding();
    }
}
