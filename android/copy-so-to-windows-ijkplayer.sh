#! /usr/bin/env bash

LINUX_PATH=".."
WINDOWS_PATH="/mnt/c/GitHub/tg/ijkplayer"

do_cp(){
    ABI=$1 # "armv7a"
    echo copy $ABI...
    SOURCE_PATH=$LINUX_PATH/android/ijkplayer/ijkplayer-$ABI/src/main/libs/
    TARGET_PATH=$WINDOWS_PATH/android/ijkplayer/ijkplayer-$ABI/src/main/libs/
    if [ -e $SOURCE_PATH ]; then
        cp -r $SOURCE_PATH* $TARGET_PATH  
    else
        echo $SOURCE_PATH no exist.      
    fi
}

for abi in "armv5" "armv7a" "arm64" "x86" "x86_64"; do
    do_cp $abi
done