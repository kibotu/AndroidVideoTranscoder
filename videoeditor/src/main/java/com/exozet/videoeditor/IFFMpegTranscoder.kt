package com.exozet.videoeditor

import android.net.Uri
import androidx.annotation.IntRange
import io.reactivex.Observable

interface IFFMpegTranscoder {

    /**
     * @param inputUri Uri of the source video
     * @param carId Id of the car
     * @param photoQuality quality of extracted frames - Effective range for JPEG is 2-31 with 31 being the worst quality
     * @param frameTimes  ms of the requested frames at source video - example "1.023"</pre>
     */
    fun extractFramesFromVideo(inputUri: Uri, carId: String, @IntRange(from = 2, to = 31) photoQuality: Int = 2, frameTimes: List<String>): Observable<MetaData>

    //todo: put them into the parameter class
    //todo: naming!!
    /**
     * @param outputUri Uri of the requested output file with filename and type "/../Downloads/outputVideo.mp4"
     * @param frameFolder folder of the extracted frames
     * @param keyInt key frames
     * @param minKeyInt min key frames
     * @param gopValue Keyframe interval, also known as GOP length. This determines the maximum distance between I-frames.
     * @param videoQuality quality of output video - For x264 valid range is 0-51 - ffmpeg default is 23 and non-noticeable quality is 18
     * @param fps requested video frame rate
     * @param outputFps requested video frame rate after the filtering
     * @param pixelFormat type of the pixelFormat , default is yup420p
     * @param presetType type of the preset , default is ultrafast
     * @param encodeType type of the encode , default is libx264
     * @param threadType type of the thread , default is auto , 0 is optimal(not totally sure)
     * @param deleteAfter delete frame folder after the creating video
     */
    fun createVideoFromFrames(outputUri: Uri, frameFolder: Uri,@IntRange(from = 1, to = 60) keyInt: Int = 8,@IntRange(from = 1, to = 60) minKeyInt: Int = 8,@IntRange(from = 1, to = 60) gopValue: Int = 8, @IntRange(from = 0, to = 51) videoQuality: Int = 18, @IntRange(from = 1, to = 60) fps: Int = 3, @IntRange(from = 1, to = 60) outputFps: Int, pixelFormat: PixelFormatType = PixelFormatType.YUV420P, presetType: PresetType = PresetType.ULTRAFAST, encodeType: EncodeType = EncodeType.LIBX264, threadType: ThreadType = ThreadType.AUTO, deleteAfter: Boolean = true, maxrate: Int = 3000,
                              bufsize: Int =3500):
            Observable<MetaData>

    fun changeKeyframeInterval()

    fun deleteAllProcessFiles() : Boolean
    /**
     *  checking is FFmpeg available on your device
     */
    fun isSupported(): Boolean

    /**
     * @param folderUri path of the extracted images folder which desired to deleted
     */
    fun deleteExtractedFrameFolder(folderUri: Uri): Boolean

    fun transcode(inputUri: Uri, outputUri: Uri, carId: String, maxrate: Int, bufsize: Int): Observable<MetaData>
}