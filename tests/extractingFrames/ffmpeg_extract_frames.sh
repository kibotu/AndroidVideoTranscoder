#!/usr/bin/env bash

ffmpeg -i output_120_frames.mp4 output/120/thumb%03d.jpg

ffmpeg -i output_360_frames.mp4 output/360/thumb%03d.jpg