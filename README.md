[![Build Status](https://app.bitrise.io/app/09accd151a795e36/status.svg?token=qOMQISBTgdxyqBD6NSOzTg)](https://app.bitrise.io/app/09accd151a795e36) [ ![Download](https://api.bintray.com/packages/exozetag/maven/Android-FFmpeg-Transcoder/images/download.svg) ](https://bintray.com/exozetag/maven/Android-FFmpeg-Transcoder/_latestVersion)


# How to use


### Extracting frames

 	FFMpegTranscoder.extractFramesFromVideo(
 			context = application, 
 			frameTimes = times, 
 			inputVideo = inputVideo, 
 			id = "12345", 
 			outputDir = frameFolder
 		)
   		.subscribeOn(Schedulers.io())
   		.observeOn(AndroidSchedulers.mainThread())
   		.subscribe(
        	{ logv { "extract frames ${it.progress} ${it.message} ${(it.duration / 1000f).roundToInt()} s" } },
	      	{ logv { "extracting frames failed ${it.message}" }}, 
        	{ logv { "extracting frames successfully completed" } }
        )
        .addTo(subscription)
        
### Merging frames to create video

    FFMpegTranscoder.createVideoFromFrames(
        	context = application,
        	frameFolder = frameFolder,
        	outputUri = outputVideo,
        	config = EncodingConfig(
            	sourceFrameRate = 30 // every source image is a frame
        	)
      	)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
        	{ logv { "merging frames ${it.progress} ${it.message} ${(it.duration / 1000f).roundToInt()} s" } },
	      	{ logv { "merging frames to create a video failed ${it.message}" }}, 
        	{ logv { "video creation successfully completed" } }
        )
        .addTo(subscription)
        

# How to install

Step 1. Add the JitPack repository to your build file

Add it in your root build.gradle at the end of repositories:

	allprojects {
		repositories {
			...
			maven { url 'https://dl.bintray.com/exozetag/maven' }
		}
	}
Step 2. Add the dependency

	dependencies {
		implementation 'com.exozet:videoeditor:{version}'
	}    