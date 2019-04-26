package com.exozet.videoeditor

import androidx.annotation.IntRange
import io.reactivex.Observable

interface IFFMpegTranscoder {

    /**
     * @param inputPath path of the source video
     * @param fileName   name of the video file
     * @param outputPath path of the requested output file with filename and type "/../Downloads/outputVideo.mp4"
     * @param photoQuality quality of extracted frames - Effective range for JPEG is 2-31 with 31 being the worst quality
     * @param videoQuality quality of output video - For x264 valid range is 0-51 - ffmpeg default is 23 and non-noticeable quality is 18
     * @param fps requested video frame rate
     * @param frameTimes  ms of the requested frames at source video - example "1.023"</pre>
     */
    fun createVideo(inputPath: String, fileName: String, outputPath: String, @IntRange(from = 2, to = 31) photoQuality: Int = 2, @IntRange(from = 0, to = 51) videoQuality: Int = 18,
                    @IntRange(from = 1, to = 60) fps: Int = 3,
                    frameTimes: List<String>): Observable<FFMpegTranscoder.MetaData>

    fun stopAllProcesses()
}