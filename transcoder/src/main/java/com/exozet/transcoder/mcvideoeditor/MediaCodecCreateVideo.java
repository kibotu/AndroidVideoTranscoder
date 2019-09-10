package com.exozet.transcoder.mcvideoeditor;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import io.reactivex.Completable;
import io.reactivex.disposables.Disposable;

import static com.exozet.transcoder.ffmpeg.DebugExtensions.log;

public class MediaCodecCreateVideo {
    private static final String TAG = MediaCodecCreateVideo.class.getSimpleName();

    private IBitmapToVideoEncoderCallback mCallback;
    private File mOutputFile;
    private Queue<Bitmap> mEncodeQueue = new ConcurrentLinkedQueue();
    private MediaCodec mediaCodec;
    private MediaMuxer mediaMuxer;

    private Object mFrameSync = new Object();
    private CountDownLatch mNewFrameLatch;

    private String mimeType; // H.264 Advanced Video Coding
    private static int mWidth;
    private static int mHeight;
    private int bitRate;
    private int frameRate; // Frames per second

    private int iFrameInterval;

    private int mGenerateIndex = 0;
    private int mTrackIndex;
    private boolean mNoMoreFrames = false;
    private boolean mAbort = false;

    int colorFormat;

    public interface IBitmapToVideoEncoderCallback {
        void onEncodingComplete(File outputFile);

        void onEncodingFail(Exception e);
    }

    public MediaCodecCreateVideo(MediaConfig mediaConfig, IBitmapToVideoEncoderCallback callback) {
        mCallback = callback;

        this.mimeType = mediaConfig.getMimeType();

        if (mediaConfig.getBitRate() != null) {
            this.bitRate = mediaConfig.getBitRate();
        }

        if (mediaConfig.getFrameRate() != null) {
            this.frameRate = mediaConfig.getFrameRate();
        }

        if (mediaConfig.getIFrameInterval() != null) {
            this.iFrameInterval = mediaConfig.getIFrameInterval();
        }

        int[] formats = this.getMediaCodecList();

        if (colorFormat <= 0) {
            colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
        }

        //need to select correct color format yuv420. In here we are deciding which one should we use
        //https://www.jianshu.com/p/54a702be01e1 check this link for more (Chinese)
        lab:
        for (int format : formats) {
            switch (format) {
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar: // yuv420sp
                    colorFormat = format;
                    break lab;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar: // yuv420p
                    colorFormat = format;
                    break lab;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar: // yuv420psp
                    colorFormat = format;
                    break lab;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar: // yuv420pp
                    colorFormat = format;
                    break lab;
            }
        }

    }

    public boolean isEncodingStarted() {
        return (mediaCodec != null) && (mediaMuxer != null) && !mNoMoreFrames && !mAbort;
    }

    public int getActiveBitmaps() {
        return mEncodeQueue.size();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void startEncoding(int width, int height, Uri outputUri, MediaCodecExtractImages.Cancelable cancelable) {
        mWidth = width;
        mHeight = height;

        mOutputFile = new File(outputUri.getPath());

        MediaCodecInfo codecInfo = selectCodec(mimeType);
        if (codecInfo == null) {
           log(TAG, "Unable to find an appropriate codec for " + mimeType);
            return;
        }
        log(TAG, "found codec: " + codecInfo.getName());

        try {
            mediaCodec = MediaCodec.createByCodecName(codecInfo.getName());
        } catch (IOException e) {
            log(TAG, "Unable to create MediaCodec " + e.getMessage());
            mCallback.onEncodingFail(e);
            return;
        }

        MediaFormat mediaFormat = MediaFormat.createVideoFormat(mimeType, mWidth, mHeight);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();
        try {
            mediaMuxer = new MediaMuxer(mOutputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            log(TAG, "MediaMuxer creation failed. " + e.getMessage());
            mCallback.onEncodingFail(e);
            return;
        }

        log(TAG, "Initialization complete. Starting encoder...");
        encode(cancelable);
    }

    public void stopEncoding() {
        if (mediaCodec == null || mediaMuxer == null) {
            log(TAG, "Failed to stop encoding since it never started");
            return;
        }
        log(TAG, "Stopping encoding");

        mNoMoreFrames = true;

        synchronized (mFrameSync) {
            if ((mNewFrameLatch != null) && (mNewFrameLatch.getCount() > 0)) {
                mNewFrameLatch.countDown();
            }
        }
    }

    public void abortEncoding() {
        if (mediaCodec == null || mediaMuxer == null) {
            log(TAG, "Failed to abort encoding since it never started");
            return;
        }
        log(TAG, "Aborting encoding");

        mNoMoreFrames = true;
        mAbort = true;
        mEncodeQueue = new ConcurrentLinkedQueue(); // Drop all frames

        synchronized (mFrameSync) {
            if ((mNewFrameLatch != null) && (mNewFrameLatch.getCount() > 0)) {
                mNewFrameLatch.countDown();
            }
        }
    }

    public void queueFrame(Bitmap bitmap) {
        if (mediaCodec == null || mediaMuxer == null) {
            log(TAG, "Failed to queue frame. Encoding not started");
            return;
        }


        log(TAG, "Queueing frame");
        mEncodeQueue.add(bitmap);

        synchronized (mFrameSync) {
            if ((mNewFrameLatch != null) && (mNewFrameLatch.getCount() > 0)) {
                mNewFrameLatch.countDown();
            }
        }
    }

    private void encode(final MediaCodecExtractImages.Cancelable cancelable) {

        log(TAG, "Encoder started");

        while (true) {

            if(cancelable.cancel.get())
                return;

            if (mNoMoreFrames && (mEncodeQueue.size() == 0)) break;

            Bitmap bitmap = mEncodeQueue.poll();
            if (bitmap == null) {
                synchronized (mFrameSync) {
                    mNewFrameLatch = new CountDownLatch(1);
                }

                try {
                    mNewFrameLatch.await();
                } catch (InterruptedException e) {
                    mCallback.onEncodingFail(e);
                }

                bitmap = mEncodeQueue.poll();
            }

            if (bitmap == null) continue;

            byte[] byteConvertFrame = getNV12(bitmap.getWidth(), bitmap.getHeight(), bitmap);

            long TIMEOUT_USEC = 500000;
            int inputBufIndex = mediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
            long ptsUsec = computePresentationTime(mGenerateIndex, frameRate);
            if (inputBufIndex >= 0) {
                final ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufIndex);
                inputBuffer.clear();
                inputBuffer.put(byteConvertFrame);
                mediaCodec.queueInputBuffer(inputBufIndex, 0, byteConvertFrame.length, ptsUsec, 0);
                mGenerateIndex++;
            }
            MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
            int encoderStatus = mediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                log(TAG, "No output from encoder available");
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // not expected for an encoder
                MediaFormat newFormat = mediaCodec.getOutputFormat();
                newFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 3000 * 3000);
                mTrackIndex = mediaMuxer.addTrack(newFormat);
                mediaMuxer.start();
            } else if (encoderStatus < 0) {
                log(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
            } else if (mBufferInfo.size != 0) {
                ByteBuffer encodedData = mediaCodec.getOutputBuffer(encoderStatus);
                if (encodedData == null) {
                    log(TAG, "encoderOutputBuffer " + encoderStatus + " was null");
                } else {
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                    mediaMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    mediaCodec.releaseOutputBuffer(encoderStatus, false);
                }
            }
        }

        release();

        if (mAbort) {
            mOutputFile.delete();
        } else {
            mCallback.onEncodingComplete(mOutputFile);
        }
    }

    private void release() {
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
            log(TAG, "RELEASE CODEC");
        }
        if (mediaMuxer != null) {
            mediaMuxer.stop();
            mediaMuxer.release();
            mediaMuxer = null;
            log(TAG, "RELEASE MUXER");
        }
    }

    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private static int selectColorFormat(MediaCodecInfo codecInfo,
                                         String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo
                .getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        return 0; // not reached
    }

    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    private byte[] getNV12(int inputWidth, int inputHeight, Bitmap scaled) {

        int[] argb = new int[inputWidth * inputHeight];

        //Log.i(TAG, "scaled : " + scaled);
        scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        byte[] yuv = new byte[inputWidth * inputHeight * 3 / 2];

        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar: // yuv420sp
                encodeYUV420SP(yuv, argb, inputWidth, inputHeight);
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar: // yuv420p
                encodeYUV420P(yuv, argb, inputWidth, inputHeight);
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar: // yuv420psp
                encodeYUV420PSP(yuv, argb, inputWidth, inputHeight);
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar: // yuv420pp
                encodeYUV420PP(yuv, argb, inputWidth, inputHeight);
                break;
        }
//        scaled.recycle();

        return yuv;
    }

    private void encodeYUV420P(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uIndex = frameSize;
        int vIndex = frameSize + width * height / 4;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                V = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128; // Previously U
                U = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128; // Previously V

                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[vIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                    yuv420sp[uIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                }

                index++;
            }
        }
    }

    private void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uvIndex = frameSize;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                V = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128; // Previously U
                U = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128; // Previously V

                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[uvIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                }

                index++;
            }
        }
    }

    private void encodeYUV420PSP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
//        int uvIndex = frameSize;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;
//                R = (argb[index] & 0xff000000) >>> 24;
//                G = (argb[index] & 0xff0000) >> 16;
//                B = (argb[index] & 0xff00) >> 8;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                V = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128; // Previously U
                U = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128; // Previously V

                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[yIndex + 1] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[yIndex + 3] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                }
                if (index % 2 == 0) {
                    yIndex++;
                }
                index++;
            }
        }
    }

    private void encodeYUV420PP(byte[] yuv420sp, int[] argb, int width, int height) {

        int yIndex = 0;
        int vIndex = yuv420sp.length / 2;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;
//                R = (argb[index] & 0xff000000) >>> 24;
//                G = (argb[index] & 0xff0000) >> 16;
//                B = (argb[index] & 0xff00) >> 8;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                V = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128; // Previously U
                U = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128; // Previously V

                if (j % 2 == 0 && index % 2 == 0) {// 0
                    yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                    yuv420sp[yIndex + 1] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[vIndex + 1] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                    yIndex++;

                } else if (j % 2 == 0 && index % 2 == 1) { //1
                    yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                } else if (j % 2 == 1 && index % 2 == 0) { //2
                    yuv420sp[vIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                    vIndex++;
                } else if (j % 2 == 1 && index % 2 == 1) { //3
                    yuv420sp[vIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                }
                index++;
            }
        }
    }

    public int[] getMediaCodecList() {
        int numCodecs = MediaCodecList.getCodecCount();
        MediaCodecInfo codecInfo = null;
        for (int i = 0; i < numCodecs && codecInfo == null; i++) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if (!info.isEncoder()) {
                continue;
            }
            String[] types = info.getSupportedTypes();
            boolean found = false;
            for (int j = 0; j < types.length && !found; j++) {
                if (types[j].equals(mimeType)) {
                    found = true;
                }
            }
            if (!found) {
                continue;
            }
            codecInfo = info;
        }
        log(TAG, "found" + codecInfo.getName() + "supporting " + mimeType);
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        return capabilities.colorFormats;
    }

    private long computePresentationTime(long frameIndex, int framerate) {
        return 132 + frameIndex * 1000000 / framerate;
    }
}
