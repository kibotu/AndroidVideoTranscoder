#!/usr/bin/env bash

# change iframe rate
# https://gist.github.com/kibotu/27c5d665d19b9f782e6b4f26eff878b1
#
# https://ffmpeg.org/ffmpeg-formats.html#mov_002c-mp4_002c-ismv
# -movflags faststart
#  Run a second pass moving the index (moov atom) to the beginning of the file. This operation can take a while, and will not work in various situations such as fragmented output, thus it is not enabled by default.
#
# -movflags rtphint
#  Add RTP hinting tracks to the output file.
#
# -c:v libx264
# -c codec codec name
#
# -profile profile set profile
# Another optional setting is -profile:v which will limit the output to a specific H.264 profile.
# Omit this unless your target device only supports a certain profile (see Compatibility).
# Current profiles include: baseline, main, high, high10, high422, high444. Note that usage of -profile:v is incompatible with lossless encoding.
# https://trac.ffmpeg.org/wiki/Encode/H.264
# https://trac.ffmpeg.org/wiki/Encode/H.264#Compatibility
#
#  -keyint_min E..V…. minimum interval between IDR-frames (from INT_MIN to INT_MAX) (default 25)
ffmpeg -i example_walkaround.mov -c:v libx264 -profile:v baseline -level 3.0 -x264opts keyint=10:min-keyint=10 -g 10 -movflags +faststart+rtphint -maxrate:v 3000k -bufsize:v 3500k walkaround-quick.mp4