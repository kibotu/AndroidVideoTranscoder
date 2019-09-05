package com.exozet.videoeditor.demo.extractImages;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.api.MediaInfo;
import org.jcodec.api.android.AndroidFrameGrab;
import org.jcodec.common.io.FileChannelWrapper;
import org.jcodec.common.io.NIOUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JCodecExtractImages extends AsyncTask<File, Integer, Integer> {

    private static final String TAG = "DECODER";

    final String outPath = Environment.getExternalStorageDirectory().toString() + "/Download/process/";

    protected Integer doInBackground(File... params) {
        FileChannelWrapper ch = null;

        final String SrcPath = Environment.getExternalStorageDirectory().toString() + "/Download/walkaround.mp4";

        try {
            ch = NIOUtils.readableChannel(new File(SrcPath));
            AndroidFrameGrab frameGrab = AndroidFrameGrab.createAndroidFrameGrab(ch);
            MediaInfo mi = frameGrab.getMediaInfo();
            Bitmap frame = Bitmap.createBitmap(mi.getDim().getWidth(), mi.getDim().getHeight(), Bitmap.Config.ARGB_8888);

            ArrayList<Integer> times = new ArrayList();
            for (int i = 0; i<60000; i+=500) {
                times.add(i);
            }

            final ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(40);


            Flowable.fromIterable(times)
                        .parallel(times.size())
                        .runOn(Schedulers.from(threadPoolExecutor))
                        .sequential()
                    .forEach(data ->{

                        Log.e("JCodecExtractImages", data + " forEach, thread is " +
                                Thread.currentThread().getName());

                        frameGrab.getFrame(frame);

                        OutputStream os = null;
                        try {
                            Log.e("JCodecExtractImages", "extracting "+ data);

                            os = new BufferedOutputStream(new FileOutputStream(new File(outPath, String.format("img%08d.jpg", data))));
                            frame.compress(Bitmap.CompressFormat.JPEG, 90, os);
                        } finally {
                            if (os != null)
                                os.close();
                        }
                        publishProgress(data);
                    });
                       // .subscribe();



        } catch (IOException e) {
            Log.e(TAG, "IO", e);
        } catch (JCodecException e) {
            Log.e(TAG, "JCodec", e);
        } finally {
            //NIOUtils.closeQuietly(ch);
        }
        return 0;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
       // progress.setText(String.valueOf(values[0]));
    }


    private void saveBitmap(Bitmap bmpOriginal, int index) {
        final String outPath = Environment.getExternalStorageDirectory().toString() + "/Download/process/";

        String filename = String.format("image_%03d.png", index);
        File dest = new File(outPath, filename);

        try {
            FileOutputStream out = new FileOutputStream(dest);
            bmpOriginal.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

