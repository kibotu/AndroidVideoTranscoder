#!/usr/bin/env bash

ffmpeg -i stabilized_deshake.mp4 -i source_video.mp4 -i stabilized_vidstab.mp4 -filter_complex "[0:v:0]pad=iw*2:ih[bg]; [bg][1:v:0]overlay=w" merged.mp4
