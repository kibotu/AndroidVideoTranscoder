package com.exozet.videoeditor

import android.content.Context
import android.net.Uri
import androidx.annotation.IntRange
import io.reactivex.Observable

interface IFFMpegTranscoder {

    /**
     * @param frameTimes  ms of the requested frames at source video - example "1.023"</pre>
     * @param inputVideo Uri of the source video
     * @param id unique output folder id
     * @param outputDir optional - output directory, if not provided internal storage will be used
     * @param photoQuality quality of extracted frames - Effective range for JPEG is 2-31 with 31 being the worst quality
     */
    fun extractFramesFromVideo(context: Context, frameTimes: List<String>, inputVideo: Uri, id: String, outputDir: Uri? = null, @IntRange(from = 1, to = 31) photoQuality: Int = 5): Observable<MetaData>

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
    fun createVideoFromFrames(context: Context, frameFolder: Uri, outputUri: Uri, config: FFMpegTranscoder.EncodingConfig, deleteFramesOnComplete: Boolean = true): Observable<MetaData>

    fun changeKeyframeInterval()

    fun deleteAllProcessFiles(): Boolean
    /**
     *  checking is FFmpeg available on your device
     */
    fun isSupported(context: Context): Boolean

    /**
     * @param folderUri path of the extracted images folder which desired to deleted
     */
    fun deleteExtractedFrameFolder(folderUri: Uri): Boolean

    fun transcode(context: Context, inputUri: Uri, outputUri: Uri, carId: String, maxrate: Int, bufsize: Int): Observable<MetaData>
}