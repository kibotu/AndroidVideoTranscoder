#!/usr/bin/env bash

# https://ffmpeg.org/ffmpeg-filters.html#vidstabdetect-1
#
# shakiness
#   Set how shaky the video is and how quick the camera is. It accepts an integer in the range 1-10,
#   a value of 1 means little shakiness, a value of 10 means strong shakiness. Default value is 5.
#
# accuracy
#   Set the accuracy of the detection process. It must be a value in the range 1-15.
#   A value of 1 means low accuracy, a value of 15 means high accuracy. Default value is 15.
#
# smoothing
#   Set the number of frames (value*2 + 1) used for lowpass filtering the camera movements.
#   Default value is 10.
#
#   For example a number of 10 means that 21 frames are used (10 in the past and 10 in the future)
#   to smoothen the motion in the video. A larger value leads to a smoother video,
#   but limits the acceleration of the camera (pan/tilt movements). 0 is a special case where a static camera is simulated.

ffmpeg -i source_video.mp4 -vf vidstabdetect=shakiness=10:accuracy=15 -f null -
ffmpeg -i source_video.mp4 -vf vidstabtransform=smoothing=30:input="transforms.trf" stabilized_vidstab.mp4