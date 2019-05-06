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
    fun extractFramesFromVideo(inputUri: Uri,carId : Long, @IntRange(from = 2, to = 31) photoQuality: Int = 2, frameTimes: List<String>): Observable<MetaData>

    //todo: naming!!
    /**
     * @param outputUri Uri of the requested output file with filename and type "/../Downloads/outputVideo.mp4"
     * @param frameFolder folder of the extracted frames
     * @param videoQuality quality of output video - For x264 valid range is 0-51 - ffmpeg default is 23 and non-noticeable quality is 18
     * @param fps requested video frame rate
     * @param pixelFormat type of the pixelFormat , default is yup420p
     * @param deleteAfter delete frame folder after the creating video
     */
    fun createVideoFromFrames(outputUri: Uri, frameFolder: Uri, @IntRange(from = 0, to = 51) videoQuality: Int = 18, @IntRange(from = 1, to = 60) fps: Int = 3, pixelFormat : PixelFormatType = PixelFormatType.YUV420P, deleteAfter: Boolean = true):
            Observable<MetaData>

    fun stopAllProcesses()
}