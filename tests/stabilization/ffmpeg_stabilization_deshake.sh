#!/usr/bin/env bash

# https://www.ffmpeg.org/ffmpeg-filters.html#deshake

ffmpeg -i source_video.mp4 -vf deshake stabilized_deshake.mp4