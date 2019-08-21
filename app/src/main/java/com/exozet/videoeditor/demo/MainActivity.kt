package com.exozet.videoeditor.demo

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Environment.DIRECTORY_DOWNLOADS
import android.os.Environment.getExternalStoragePublicDirectory
import androidx.appcompat.app.AppCompatActivity
import com.exozet.android.core.extensions.*
import com.exozet.videoeditor.EncodingConfig
import com.exozet.videoeditor.FFMpegTranscoder
import com.exozet.videoeditor.FFMpegTranscoder.transcode
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
import java.io.IOException
import java.io.InputStream
import kotlin.math.roundToInt


class MainActivity : AppCompatActivity() {

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

        val frameFolder = "Download/processing/".parseExternalStorageFile()
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

            logv { "extractFramesFromVideo $inputVideo -> $frameFolder" }

            val increment = 63f / 120f

            val times = (0..120).map {
                increment * it.toDouble()
            }

            output.text = ""

            FFMpegTranscoder.extractFramesFromVideo(context = this, frameTimes = times.map { it.toString() }, inputVideo = inputVideo, id = "12345", outputDir = frameFolder)
                .subscribeOn(Schedulers.newThread())
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
    }

    private fun mergeFrames(frameFolder: Uri, outputVideo: Uri) {

        make_video.onClick {

            logv { "mergeFrames $frameFolder -> $outputVideo" }

            output.text = ""

            FFMpegTranscoder.createVideoFromFrames(
                context = this,
                frameFolder = frameFolder,
                outputUri = outputVideo,
                config = EncodingConfig(
                    sourceFrameRate = 30 // for encoding back to original video: 10, however with duplicate frames then
                )
            ).subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                    merge_frames_progress.show()
                    merge_frames_progress.progress = it.progress

                    logv { "merge frames $it" }

                    output.text = "${(it.duration / 1000f).roundToInt()} s ${it.message?.trimMargin()}\n${output.text}"

                }, {
                    logv { "creating video fails ${it.message}" }
                    it.printStackTrace()
                }, { logv { "createVideoFromFrames on complete " } })
                .addTo(subscription)
        }
    }

    private fun transcode(inputVideo: Uri, outputVideo: Uri) {

        transcode_video.onClick {

            logv { "transcode $inputVideo -> $outputVideo" }

            output.text = ""

            FFMpegTranscoder.transcode(
                context = this,
                inputVideo = inputVideo,
                outputUri = outputVideo
            )
                .subscribeOn(Schedulers.computation())
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