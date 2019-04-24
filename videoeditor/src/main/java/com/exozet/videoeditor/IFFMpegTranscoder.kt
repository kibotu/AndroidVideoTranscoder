package com.exozet.videoeditor

interface IFFMpegTranscoder {

    fun extractFramesFromVideo(inputPath: String, fileName: String, outputPath: String)

    fun createVideoFromFrames(savePath: String, saveName: String, outputPath: String)
}