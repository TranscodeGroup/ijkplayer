//
// Created by ipcjs on 7/11/18.
//

#ifndef IJKPLAYER_IJKPLAYER_JNI_H
#define IJKPLAYER_IJKPLAYER_JNI_H

#include <jni.h>

void IjkMediaPlayer_onVideoFrame__catchAll(JNIEnv *env, jobject weakThiz,
                                           jobject buffer, jdouble pts,
                                           jint format, jint width, jint height);

void IjkMediaPlayer_onAudioFrame__catchAll(JNIEnv *env, jobject weakThiz,
                                           jobject buffer, jdouble pts);

#endif //IJKPLAYER_IJKPLAYER_JNI_H
