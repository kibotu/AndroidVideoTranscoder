package com.exozet.transcoder.mcvideoeditor;
/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import com.exozet.transcoder.ffmpeg.Progress;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.functions.Action;

import static com.exozet.transcoder.ffmpeg.DebugExtensions.log;

//20131122: minor tweaks to saveFrame() I/O
//20131205: add alpha to EGLConfig (huge glReadPixels speedup); pre-allocate pixel buffers;
//          log time to run saveFrame()
//20140123: correct error checks on glGet*Location() and program creation (they don't set error)
//20140212: eliminate byte swap

/**
 * To check how to convert time video frame times to frames number, go to getDesiredFrames() method
 */

/**
 * Extract frames from an MP4 using MediaExtractor, MediaCodec, and GLES.  Put a .mp4 file
 * in "/sdcard/source.mp4" and look for output files named "/sdcard/frame-XX.png".
 * <p>
 * This uses various features first available in Android "Jellybean" 4.1 (API 16).
 * <p>
 * (This was derived from bits and pieces of CTS tests, and is packaged as such, but is not
 * currently part of CTS.)
 */
public class MediaCodecExtractImages {

    private static final String TAG = "ExtractMpegFrames";

    /**
     * Tests extraction from an MP4 to a series of PNG files.
     * <p>
     * We scale the video to 640x480 for the PNG just to demonstrate that we can scale the
     * video with the GPU.  If the input video has a different aspect ratio, we could preserve
     * it by adjusting the GL viewport to get letterboxing or pillarboxing, but generally if
     * you're extracting frames you don't want black bars.
     */
    public Observable<Progress> extractMpegFrames(final Uri inputVideo, final List<Double> timeInSec, final Uri outputDir, int photoQuality) {

        long startTime = System.currentTimeMillis();

        final Cancelable cancelable = new Cancelable();

        return Observable.create((ObservableOnSubscribe<Progress>) emitter -> {
            String inputFilePath = inputVideo.getPath();
            String outputPath = outputDir.getPath();

            MediaCodec decoder = null;
            CodecOutputSurface outputSurface = null;
            MediaExtractor extractor = null;
            int saveWidth;
            int saveHeight;

            if (emitter.isDisposed())
                return;

            try {
                File inputFile = new File(inputFilePath);   // must be an absolute path
                // The MediaExtractor error messages aren't very useful.  Check to see if the input
                // file exists so we can throw a better one if it's not there.
                if (!inputFile.canRead()) {
                    emitter.onError(new FileNotFoundException("Unable to read " + inputFile));
                }

                extractor = new MediaExtractor();
                extractor.setDataSource(inputFile.toString());
                int trackIndex = selectTrack(extractor);
                if (trackIndex < 0) {
                    emitter.onError(new RuntimeException("No video track found in " + inputFile));
                }
                extractor.selectTrack(trackIndex);

                MediaFormat format = extractor.getTrackFormat(trackIndex);

                saveWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                saveHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
                log(TAG, "Video size is " + format.getInteger(MediaFormat.KEY_WIDTH) + "x" +
                        format.getInteger(MediaFormat.KEY_HEIGHT));

                int frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                long duration = format.getLong(MediaFormat.KEY_DURATION);

                int secToMicroSec = 1000000;
                int totalFrame = (int) ((duration * frameRate) / secToMicroSec);

                log(TAG, "Frame rate is = " + frameRate +
                        " Total duration is in microSec = " + duration +
                        " Total frame count = " + totalFrame);

                //Can't use timeStamp directly, instead we need to get which frame we need to get
                List<Integer> desiredFrames = getDesiredFrames(timeInSec, frameRate);

                log(TAG, "Desired frames list is " + desiredFrames.toString());
                // Could use width/height from the MediaFormat to get full-size frames.
                outputSurface = new CodecOutputSurface(saveWidth, saveHeight);

                // Create a MediaCodec decoder, and configure it with the MediaFormat from the
                // extractor.  It's very important to use the format from the extractor because
                // it contains a copy of the CSD-0/CSD-1 codec-specific data chunks.
                String mime = format.getString(MediaFormat.KEY_MIME);
                decoder = MediaCodec.createDecoderByType(mime);
                decoder.configure(format, outputSurface.getSurface(), null, 0);
                decoder.start();

                doExtract(extractor, trackIndex, decoder, outputSurface, desiredFrames, outputPath, photoQuality, emitter, totalFrame, startTime, cancelable);

            } catch (Exception e) {
                log(TAG, "Something goes wrong while extracting images " + e);
                emitter.onError(e);
            } finally {
                // release everything we grabbed
                if (outputSurface != null) {
                    outputSurface.release();
                    outputSurface = null;
                }
                if (decoder != null) {
                    decoder.stop();
                    decoder.release();
                    decoder = null;
                }
                if (extractor != null) {
                    extractor.release();
                    extractor = null;
                }
            }

        }).doOnDispose(() -> {
            cancelable.cancel.set(true);
        });
    }

    /**
     * @param timeInSec = desired video frame times in sec
     * @param frameRate = video frame rate
     * @return list of frame numbers which points exact frame in given time
     * <p>
     * While using mediaCodec we can't seek to desired time, instead of that need to figure out which frame we needed
     * to calculate that, need to multiply desired frame time with frame rate
     * <p>
     * Example = Want to get the frame at 6.34 sec. We have a 30 frame rate video
     * 6.34*30 = 190,2 th frame -> we need int or long number so need to round it down
     */
    private List<Integer> getDesiredFrames(List<Double> timeInSec, int frameRate) {

        ArrayList<Integer> desiredFrames = new ArrayList<>();

        for (int i = 0; i < timeInSec.size(); i++) {
            int desiredTimeFrames = (int) (timeInSec.get(i) * frameRate);
            desiredFrames.add(desiredTimeFrames);
        }
        return desiredFrames;
    }

    /**
     * Selects the video track, if any.
     *
     * @return the track index, or -1 if no video track is found.
     */
    private int selectTrack(MediaExtractor extractor) {
        // Select the first video track we find, ignore the rest.
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                    log(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
                return i;
            }
        }

        return -1;
    }

    static class Cancelable {
        final AtomicBoolean cancel = new AtomicBoolean(false);
    }

    /**
     * Work loop.
     */
    static void doExtract(MediaExtractor extractor, int trackIndex, MediaCodec decoder,
                          CodecOutputSurface outputSurface, List<Integer> desiredFrames, String outputPath, int photoQuality, ObservableEmitter<Progress> observer, int totalFrame, long startTime, final Cancelable cancel) throws IOException {
        final int TIMEOUT_USEC = 10000;
        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int inputChunk = 0;
        int decodeCount = 0;
        long frameSaveTime = 0;
        int frameCounter = 0;

        boolean outputDone = false;
        boolean inputDone = false;
        while (!outputDone) {

            if (cancel.cancel.get())
                return;

            log(TAG, "loop");

            // Feed more data to the decoder.
            if (!inputDone) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    log(TAG, "inputBufIndex " + inputBufIndex);

                    ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                    // Read the sample data into the ByteBuffer.  This neither respects nor
                    // updates inputBuf's position, limit, etc.
                    int chunkSize = extractor.readSampleData(inputBuf, 0);
                    if (chunkSize < 0) {
                        // End of stream -- send empty frame with EOS flag set.
                        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        log(TAG, "sent input EOS");
                    } else {
                        if (extractor.getSampleTrackIndex() != trackIndex) {
                            log(TAG, "WEIRD: got sample from track " +
                                    extractor.getSampleTrackIndex() + ", expected " + trackIndex);
                        }

                        log(TAG, "decode count = " + decodeCount);

                        long presentationTimeUs = extractor.getSampleTime();
                        decoder.queueInputBuffer(inputBufIndex, 0, chunkSize,
                                presentationTimeUs, 0 /*flags*/);

                            log(TAG, "submitted frame " + inputChunk + " to dec, size=" +
                                    chunkSize);

                        inputChunk++;
                        extractor.advance();
                    }

                } else {
                   log(TAG, "input buffer not available");
                }
            }

            if (!outputDone) {
                int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    log(TAG, "no output from decoder available");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not important for us, since we're using Surface
                    log(TAG, "decoder output buffers changed");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    log(TAG, "decoder output format changed: " + newFormat);
                } else if (decoderStatus < 0) {
                    //fail("unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                } else { // decoderStatus >= 0
                     log(TAG, "surface decoder given buffer " + decoderStatus +
                            " (size=" + info.size + ")");
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                      log(TAG, "output EOS");
                        outputDone = true;
                    }

                    boolean doRender = (info.size != 0);

                    // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                    // to SurfaceTexture to convert to a texture.  The API doesn't guarantee
                    // that the texture will be available before the call returns, so we
                    // need to wait for the onFrameAvailable callback to fire.
                    decoder.releaseOutputBuffer(decoderStatus, doRender);
                    if (doRender) {
                        log(TAG, "awaiting decode of frame " + decodeCount);
                        observer.onNext(new Progress((int) (((float) decodeCount / (float) totalFrame) * 100), null, null, System.currentTimeMillis() - startTime));

                        if (desiredFrames.contains(decodeCount)) {
                            outputSurface.awaitNewImage();
                            outputSurface.drawImage(true);
                            File outputFile = new File(outputPath,
                                    String.format("frame-%03d.jpg", frameCounter));
                            long startWhen = System.nanoTime();
                            outputSurface.saveFrame(outputFile.toString(), photoQuality);
                            frameSaveTime += System.nanoTime() - startWhen;
                            frameCounter++;

                            log(TAG, "saving frames " + decodeCount);

                        }
                        if (decodeCount < totalFrame) {
                            decodeCount++;
                        }
                    }
                }
            }
        }

        int numSaved = (120 < decodeCount) ? 120 : decodeCount;
        log(TAG, "Saving " + numSaved + " frames took " +
                (frameSaveTime / numSaved / 1000) + " us per frame");

        observer.onNext(new Progress((int) (((float) decodeCount / (float) totalFrame) * 100), "total saved frame = " + numSaved, null, System.currentTimeMillis() - startTime));

        observer.onComplete();
    }
}