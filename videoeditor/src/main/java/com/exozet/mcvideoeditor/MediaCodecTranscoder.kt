package com.exozet.mcvideoeditor

import android.content.Context
import android.net.Uri
import androidx.annotation.IntRange
import com.exozet.videoeditor.Progress
import io.reactivex.Observable

object MediaCodecTranscoder {


    fun extractFramesFromVideo(
        frameTimes: List<Double>,
        inputVideo: Uri,
        id: String,
        outputDir: Uri?,
        @IntRange(from = 1, to = 100) photoQuality: Int = 100
    ): Observable<Progress> {

        val mediaCodec = MediaCodecExtractImages()

        mediaCodec.extractMpegFrames(inputVideo,frameTimes, outputDir, photoQuality)

        return Observable.create<Progress> { emitter ->


        }

        }

}