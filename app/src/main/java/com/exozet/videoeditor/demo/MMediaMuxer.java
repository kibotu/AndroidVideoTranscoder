package com.exozet.videoeditor.demo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MMediaMuxer {
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video Coding
    private static int _width = 512;
    private static int _height = 512;
    private static final int BIT_RATE = 800000;
    private static final int INFLAME_INTERVAL = 1;
    private static final int FRAME_RATE = 10;
    private static boolean DEBUG = false;
    private MediaCodec mediaCodec;
    private MediaMuxer mediaMuxer;
    private boolean mRunning;
    private int generateIndex = 0;
    private int mTrackIndex;
    private int MAX_FRAME_VIDEO = 0;
    private List<byte[]> bitList;
    private List<byte[]> bitFirst;
    private List<byte[]> bitLast;
    private int current_index_frame = 0;

    private static final String TAG = "CODEC";
    private String outputPath;
    private Activity _activity;

    private ProgressDialog pd;
    private String _title;
    private String _mess;

    public void Init(Activity activity, int width, int height, String title, String mess) {
        _title = title;
        _mess = mess;
        _activity = activity;
        _width = width;
        _height = height;
        Logd("MMediaMuxer Init");
        ShowProgressBar();

    }

    private Handler aHandler = new Handler();



    public void AddFrame(final byte[] byteFrame) {
        CheckDataListState();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Logd("Android get Frame");
                Bitmap bit = BitmapFactory.decodeByteArray(byteFrame, 0, byteFrame.length);
                Logd("Android convert Bitmap");
                byte[] byteConvertFrame = getNV21(bit.getWidth(), bit.getHeight(), bit);
                Logd("Android convert getNV21");
                bitList.add(byteConvertFrame);
            }
        }).start();

    }

    public void AddFrame(byte[] byteFrame, int count, boolean isLast) {
        CheckDataListState();
        Logd("Android get Frames = " + count);
        Bitmap bit = BitmapFactory.decodeByteArray(byteFrame, 0, byteFrame.length);
        Logd("Android convert Bitmap");
        byteFrame = getNV21(bit.getWidth(), bit.getHeight(), bit);
        Logd("Android convert getNV21");
        for (int i = 0; i < count; i++) {
            if (isLast) {
                bitLast.add(byteFrame);
            } else {
                bitFirst.add(byteFrame);
            }
        }
    }

    public void CreateVideo() {
        current_index_frame = 0;
        Logd("Prepare Frames Data");
        bitFirst.addAll(bitList);
        bitFirst.addAll(bitLast);
        MAX_FRAME_VIDEO = bitFirst.size();
        Logd("CreateVideo");
        mRunning = true;
        bufferEncoder();
    }

    public boolean GetStateEncoder() {
        return mRunning;
    }

    public String GetPath() {
        return outputPath;
    }

    public void onBackPressed() {
        mRunning = false;
    }

    public void ShowProgressBar() {
        _activity.runOnUiThread(new Runnable() {
            public void run() {
                pd = new ProgressDialog(_activity);
                pd.setTitle(_title);
                pd.setCancelable(false);
                pd.setMessage(_mess);
                pd.setCanceledOnTouchOutside(false);
                pd.show();
            }
        });
    }

    public void HideProgressBar() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                _activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pd.dismiss();
                    }
                });
            }
        }).start();
    }

    private void bufferEncoder() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    Logd("PrepareEncoder start");
                    PrepareEncoder();
                    Logd("PrepareEncoder end");
                } catch (IOException e) {
                    Loge(e.getMessage());
                }
                try {
                    while (mRunning) {
                        Encode();
                    }
                } finally {
                    Logd("release");
                    Release();
                    HideProgressBar();
                    bitFirst = null;
                    bitLast = null;
                }
            }
        };

        Thread thread = new Thread(runnable);
        thread.start();
    }

    public void ClearTask() {
        bitList = null;
        bitFirst = null;
        bitLast = null;
    }


    private void PrepareEncoder() throws IOException {
        MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
        if (codecInfo == null) {
            Loge("Unable to find an appropriate codec for " + MIME_TYPE);
        }
        Logd("found codec: " + codecInfo.getName());
        int colorFormat;
        try {
            colorFormat = selectColorFormat(codecInfo, MIME_TYPE);
        } catch (Exception e) {
            colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
        }

        mediaCodec = MediaCodec.createByCodecName(codecInfo.getName());
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, _width, _height);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, INFLAME_INTERVAL);
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();
        try {
            String currentDateTimeString = DateFormat.getDateTimeInstance().format(new Date());
            outputPath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "pixel"+currentDateTimeString+".mp4").toString();
            mediaMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            Loge("MediaMuxer creation failed");
        }
    }


    private void Encode() {
        while (true) {
            if (!mRunning) {
                break;
            }
            Logd("Encode start");
            long TIMEOUT_USEC = 5000;
            int inputBufIndex = mediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
            long ptsUsec = computePresentationTime(generateIndex, FRAME_RATE);
            if (inputBufIndex >= 0) {
                byte[] input = bitFirst.get(current_index_frame);
                final ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufIndex);
                inputBuffer.clear();
                inputBuffer.put(input);
                mediaCodec.queueInputBuffer(inputBufIndex, 0, input.length, ptsUsec, 0);
                generateIndex++;
            }
            MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
            int encoderStatus = mediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                Loge("No output from encoder available");
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // not expected for an encoder
                MediaFormat newFormat = mediaCodec.getOutputFormat();
                mTrackIndex = mediaMuxer.addTrack(newFormat);
                mediaMuxer.start();
            } else if (encoderStatus < 0) {
                Loge("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
            } else if (mBufferInfo.size != 0) {
                ByteBuffer encodedData = mediaCodec.getOutputBuffer(encoderStatus);
                if (encodedData == null) {
                    Loge("encoderOutputBuffer " + encoderStatus + " was null");
                } else {
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                    mediaMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    mediaCodec.releaseOutputBuffer(encoderStatus, false);
                }
            }
            current_index_frame++;
            if (current_index_frame > MAX_FRAME_VIDEO - 1) {
                Log.d(TAG, "mRunning = false;");
                mRunning = false;
            }
            Logd("Encode end");
        }
    }


    private void Release() {
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
            Logd("RELEASE CODEC");
        }
        if (mediaMuxer != null) {
            mediaMuxer.stop();
            mediaMuxer.release();
            mediaMuxer = null;
            Logd("RELEASE MUXER");
        }
    }


    /**
     * Returns the first codec capable of encoding the specified MIME type, or
     * null if no match was found.
     */
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

    /**
     * Returns a color format that is supported by the codec and by this test
     * code. If no match is found, this throws a test failure -- the set of
     * formats known to the test should be expanded for new platforms.
     */
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

    /**
     * Returns true if this is a color format that this test code understands
     * (i.e. we know how to read and generate frames in this format).
     */
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

    private byte[] getNV21(int inputWidth, int inputHeight, Bitmap scaled) {

        int[] argb = new int[inputWidth * inputHeight];

        scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        byte[] yuv = new byte[inputWidth * inputHeight * 3 / 2];
        encodeYUV420SP(yuv, argb, inputWidth, inputHeight);

        scaled.recycle();

        return yuv;
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


                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;


                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                    yuv420sp[uvIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));

                }

                index++;
            }
        }
    }

    private void CheckDataListState() {
        if (bitList == null) {
            bitList = new ArrayList<>();
        }
        if (bitFirst == null) {
            bitFirst = new ArrayList<>();
        }
        if (bitLast == null) {
            bitLast = new ArrayList<>();
        }
    }

    private long computePresentationTime(long frameIndex, int framerate) {
        return 132 + frameIndex * 1000000 / framerate;
    }

    private static void Logd(String Mess) {
        if (DEBUG) {
            Log.d(TAG, Mess);
        }
    }

    private static void Loge(String Mess) {
        Log.e(TAG, Mess);
    }
}