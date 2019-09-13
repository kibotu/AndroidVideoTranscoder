# AndroidVideoTranscoder [![Build Status](https://app.bitrise.io/app/09accd151a795e36/status.svg?token=qOMQISBTgdxyqBD6NSOzTg)](https://app.bitrise.io/app/09accd151a795e36) [ ![Download](https://api.bintray.com/packages/exozetag/maven/VideoTranscoder/images/download.svg) ](https://bintray.com/exozetag/maven/VideoTranscoder/_latestVersion) [![](https://jitpack.io/v/exozet/AndroidVideoTranscoder/month.svg)](https://jitpack.io/#exozet/AndroidVideoTranscoder) [![Hits-of-Code](https://hitsofcode.com/github/exozet/AndroidVideoTranscoder)](https://hitsofcode.com/view/github/exozet/AndroidVideoTranscoder) [![Javadoc](https://img.shields.io/badge/javadoc-SNAPSHOT-green.svg)](https://jitpack.io/com/github/exozet/AndroidVideoTranscoder/master-SNAPSHOT/javadoc/index.html)  [![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=21) [![Gradle Version](https://img.shields.io/badge/gradle-5.6.1-green.svg)](https://docs.gradle.org/current/release-notes) [![Kotlin](https://img.shields.io/badge/kotlin-1.3.50-green.svg)](https://kotlinlang.org/) [![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE) [![androidx](https://img.shields.io/badge/androidx-brightgreen.svg)](https://developer.android.com/topic/libraries/support-library/refactor)

Surprisingly fast on device video transcoding.

Features

- extracting images from video either ffmpeg or mediacodec
- creating video from image seither ffmpeg or mediacodec

[![Screenshot](screenshot.png)](screenshot.png)

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
		implementation 'com.exozet:transcoder:{version}'
	}

### License
<pre>
Copyright 2019 Exozet GmbH

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
</pre>


## Contributors

[Jan Rabe](jan.rabe@exozet.com)

[Ömür Kumru](https://github.com/orgs/exozet/people/omurkmr)
