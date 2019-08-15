package com.exozet.videoeditor.demo

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.os.Environment.DIRECTORY_DOWNLOADS
import android.os.Environment.getExternalStoragePublicDirectory
import androidx.appcompat.app.AppCompatActivity
import com.exozet.android.core.extensions.isNotNullOrEmpty
import com.exozet.android.core.extensions.parseExternalStorageFile
import com.exozet.android.core.extensions.parseFile
import com.exozet.android.core.extensions.show
import com.exozet.videoeditor.FFMpegTranscoder
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

    lateinit var frameUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Logger.addLogger(LogcatLogger())

        checkWriteExternalStoragePermission()
    }

    override fun onDestroy() {
        if (!subscription.isDisposed) {
            subscription.dispose()
        }
        super.onDestroy()
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

        val downloadPath = Uri.parse(getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS).absolutePath)

        val ffMpegTranscoder = FFMpegTranscoder(this)

        init_ffmpeg.text = "FFmpeg is ${if (ffMpegTranscoder.isSupported(this)) "" else "not"} supported."

        extractFrames(ffMpegTranscoder, downloadPath)

        mergeFrames(ffMpegTranscoder, downloadPath)

        delete_folder.setOnClickListener {
            logv { "delete folder = ${ffMpegTranscoder.deleteExtractedFrameFolder(frameUri)}" }
        }

        delete_all.setOnClickListener {
            logv { "delete all = ${ffMpegTranscoder.deleteAllProcessFiles()}" }
        }
    }

    private fun mergeFrames(encoder: FFMpegTranscoder, downloadPath: Uri) {

        make_video.setOnClickListener {

            output.text = ""

            encoder.createVideoFromFrames(
                context = this,
                frameFolder = frameUri,
                outputUri = "$downloadPath/output_${System.currentTimeMillis()}.mp4".parseFile(),
                config = FFMpegTranscoder.EncodingConfig(
                    bufsize = (8 * 1024).toInt(),
                    maxrate = (8 * 1024).toInt(),
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

    private fun extractFrames(encoder: FFMpegTranscoder, downloadPath: Uri) {

        val uri = "Download/walkaround.mp4".parseExternalStorageFile()
        logv { "uri=${uri.assetFileExists}" }

        extract_frames.setOnClickListener {

            logv { "extractFramesFromVideo $uri" }

            val increment = 63f / 360f

            val times = (0..360).map {
                increment * it.toDouble()
            }

            output.text = ""

            encoder.extractFramesFromVideo(context = this, frameTimes = times.map { it.toString() }, inputVideo = uri, id = "11113", outputDir = downloadPath)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                    extract_frames_progress.show()
                    extract_frames_progress.progress = it.progress

                    logv { "extract frames $it" }

                    output.text = "${(it.duration / 1000f).roundToInt()} s ${it.message?.trimMargin()}\n${output.text}"

                    if (it.uri?.toString().isNotNullOrEmpty()) {
                        frameUri = it.uri!!
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

    val Uri.assetFileExists: Boolean
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