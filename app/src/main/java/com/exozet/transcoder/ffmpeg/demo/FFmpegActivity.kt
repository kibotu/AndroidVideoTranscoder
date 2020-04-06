package com.exozet.transcoder.ffmpeg.demo

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.exozet.android.core.extensions.isNotNullOrEmpty
import com.exozet.android.core.extensions.onClick
import com.exozet.android.core.extensions.parseExternalStorageFile
import com.exozet.android.core.extensions.show
import com.exozet.transcoder.mcvideoeditor.MediaCodecTranscoder
import com.exozet.transcoder.mcvideoeditor.MediaConfig
import com.exozet.transcoder.ffmpeg.EncodingConfig
import com.exozet.transcoder.ffmpeg.FFMpegTranscoder
import com.exozet.transcoder.ffmpeg.Progress
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import net.kibotu.logger.LogcatLogger
import net.kibotu.logger.Logger
import net.kibotu.logger.Logger.logv
import net.kibotu.logger.Logger.logw
import net.kibotu.logger.TAG
import java.io.IOException
import java.io.InputStream
import kotlin.math.roundToInt


class FFmpegActivity : AppCompatActivity() {

    var subscription: CompositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Logger.addLogger(LogcatLogger())

        checkWriteExternalStoragePermission()
    }

    // region location permission

    protected fun checkWriteExternalStoragePermission() {
        RxPermissions(this)
            .requestEachCombined(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            .subscribe({
                if (it.granted)
                    onWritePermissionGranted()
            }, {
                logw { "permission $it" }
            })
            .addTo(subscription)
    }

    private fun onWritePermissionGranted() {

        init_ffmpeg.text = "FFmpeg is ${if (FFMpegTranscoder.isSupported(this)) "" else "not"} supported."

        val frameFolder = "Download/process/".parseExternalStorageFile()
        val inputVideo = "Download/walkaround.mp4".parseExternalStorageFile()
        val outputVideo = "Download/output_${System.currentTimeMillis()}.mp4".parseExternalStorageFile()

        extractFrames(inputVideo, frameFolder)

        mergeFrames(frameFolder, outputVideo)

        transcode(inputVideo, outputVideo)

        stop_process.onClick {
            stopProcessing()
        }

        delete_folder.onClick {
            logv { "delete folder = ${FFMpegTranscoder.deleteExtractedFrameFolder(frameFolder)}" }
        }

        delete_all.onClick {
            logv { "delete all = ${FFMpegTranscoder.deleteAllProcessFiles(this)}" }
        }
    }


    private fun extractFrames(inputVideo: Uri, frameFolder: Uri) {

        logv { "uri=${inputVideo.assetFileExists}" }

        extract_frames.onClick {

            val increment = 63f / 120f

            val times = (0..120).map {
                increment * it.toDouble()
            }


            extractByFFMpeg(inputVideo,frameFolder)
            //extactByMediaCodec(times, inputVideo, frameFolder)

        }
    }

    private fun mergeFrames(frameFolder: Uri, outputVideo: Uri) {

        make_video.onClick {
            mergeByFFMpeg(frameFolder,outputVideo)
            //mergeByMediaCodec(frameFolder, outputVideo)
        }
    }

    private fun transcode(inputVideo: Uri, outputVideo: Uri) {

        transcode_video.onClick {

            logv { "transcode $inputVideo -> $outputVideo" }

            output.text = ""

            FFMpegTranscoder.transcode(
                inputVideo = inputVideo,
                outputUri = outputVideo
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                    logv { "transcode $it" }

                }, {
                    logv { "transcode fails ${it.message}" }
                    it.printStackTrace()
                }, { logv { "transcode on complete " } })
                .addTo(subscription)
        }
    }

    //region ffmpeg

    private fun extractByFFMpeg(inputVideo: Uri, frameFolder: Uri) {
        logv { "extractFramesFromVideo $inputVideo -> $frameFolder" }

        val increment = 63f / 120f

        val times = (0..120).map {
            increment * it.toDouble()
        }

        output.text = ""

        FFMpegTranscoder.extractFramesFromVideo(context = this, frameTimes = times.map { it.toString() }, inputVideo = inputVideo, id = "12345", outputDir = frameFolder)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({

                extract_frames_progress.show()
                extract_frames_progress.progress = it.progress

                logv { "extract frames $it" }

                output.text = "${(it.duration / 1000f).roundToInt()} s ${it.message?.trimMargin()}\n${output.text}"

                if (it.uri?.toString().isNotNullOrEmpty()) {
                    // logv { "${it.uri}" }
                }


            }, {
                logv { "extracting frames fail ${it.message}" }

                it.printStackTrace()
            }, {
                logv { "extractFramesFromVideo on complete" }
            })
            .addTo(subscription)
    }

    private fun mergeByFFMpeg(frameFolder: Uri, outputVideo: Uri) {
        logv { "mergeFrames $frameFolder -> $outputVideo" }

        output.text = ""

        FFMpegTranscoder.createVideoFromFrames(
            frameFolder = frameFolder,
            outputUri = outputVideo,
            config = EncodingConfig(
//                    sourceFrameRate = 120f / 63f, // original video length: 120f / 63f;
//                    outputFrameRate = 30f
            ),
            deleteFramesOnComplete = false
        ).subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({

                merge_frames_progress.show()
                merge_frames_progress.progress = it.progress

                logv { "extract frames $it" }

                output.text = "${(it.duration / 1000f).roundToInt()} s ${it.message?.trimMargin()}\n${output.text}"

            }, {
                logv { "creating video fails ${it.message}" }
            }, { logv { "createVideoFromFrames on complete " } })
            .addTo(subscription)
    }
    //endregion

    //region MediaCodec


    private fun extactByMediaCodec(times: List<Double>, inputVideo: Uri, frameFolder: Uri) {
        var progress: Progress? = null

        MediaCodecTranscoder.extractFramesFromVideo(
            context = this,
            frameTimes = times,
            inputVideo = inputVideo,
            id = "12345",
            outputDir = frameFolder

        )
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                Log.v(TAG, "1 extract frames $it")
                progress = it
                extract_frames_progress.show()
                extract_frames_progress.progress = it.progress

            }, {
                Log.v(TAG, "\"1 extracting frames fail ${it.message} $progress")


            }, {
                Log.v(TAG, "1 extractFramesFromVideo on complete $progress")

            })
            .addTo(subscription)
    }

    private fun mergeByMediaCodec(frameFolder: Uri, outputVideo: Uri) {

        MediaCodecTranscoder.createVideoFromFrames(
            frameFolder = frameFolder,
            outputUri = outputVideo,
            config = MediaConfig(
                //bitRate = 16000000,
                // frameRate = 30,
                //iFrameInterval = 1,
                // mimeType = "video/avc"
            ),
            deleteFramesOnComplete = false
        )
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                merge_frames_progress.show()
                merge_frames_progress.progress = it.progress

                logv { "merge frames $it" }

                output.text = "${(it.duration / 1000f).roundToInt()} s ${it.message?.trimMargin()}\n${output.text}"

            }, {
                logv { "creating video fails ${it.message}" }

            }, {
                logv { "createVideoFromFrames on complete " }
            })
            .addTo(subscription)


    }

    //endregion

    override fun onDestroy() {
        stopProcessing()
        super.onDestroy()
    }

    private fun stopProcessing() {
        if (!subscription.isDisposed) {
            subscription.dispose()
        }
        subscription = CompositeDisposable()
    }

    private val Uri.assetFileExists: Boolean
        get() {
            val mg = resources.assets
            var `is`: InputStream? = null
            return try {
                `is` = mg.open(this.toString())
                true
            } catch (ex: IOException) {
                false
            } finally {
                `is`?.close()
            }
        }
}