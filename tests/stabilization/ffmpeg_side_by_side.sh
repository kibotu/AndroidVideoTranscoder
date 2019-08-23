#!/usr/bin/env bash

# 2 videos side by side
ffmpeg -i stabilized_deshake.mp4 -i stabilized_vidstab.mp4 -filter_complex "[0:v:0]pad=iw*2:ih[bg]; [bg][1:v:0]overlay=w" deshake_vidstab.mp4

# 3 videos side by side

ffmpeg -i stabilized_deshake.mp4 -i source_video.mp4 -i stabilized_vidstab.mp4 -filter_complex "[1:v][0:v]scale2ref=oh*mdar:ih[1v][0v];[2:v][0v]scale2ref=oh*mdar:ih[2v][0v];[0v][1v][2v]hstack=3,scale='2*trunc(iw/2)':'2*trunc(ih/2)'" deshake_source_vidstab.mp4