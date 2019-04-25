package com.exozet.videoeditor

import io.reactivex.Observable

interface IFFMpegTranscoder {

    /**
     * inputPath: path of the source video
     * fileName:   name of the video file
     * outputPath: path of the requested output file with filename and type "/../Downloads/outputVideo.mp4"
     * photoQuality: quality of extracted frames - Effective range for JPEG is 2-31 with 31 being the worst quality
     * videoQuality: quality of output video - For x264 valid range is 0-51 - ffmpeg default is 23 and non-noticeable quality is 18
     * fps: requested video frame rate
     * frameTimes : ms of the requested frames at source video - example "1.023"
     */
    fun createVideo(inputPath: String, fileName: String, outputPath: String, photoQuality: Int = 2, videoQuality: Int = 18, fps: Int = 3, frameTimes: List<String>): Observable<String>

}