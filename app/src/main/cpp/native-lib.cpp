/*

Copyright (2020) Benoit Gschwind <gschwind@gnu-log.net>

This file is part of fiddle-assistant.

fiddle-assistant is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

fiddle-assistant is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with fiddle-assistant.  If not, see <https://www.gnu.org/licenses/>.

 */

#include <jni.h>
#include <string>
#include <iostream>

#include "tone_handler.hxx"

void setIntField(JNIEnv *env, jobject obj, char const * name, jint value) {
    jclass c = env->GetObjectClass(obj);
    // J is the type signature for long:
    jfieldID longID =  env->GetFieldID(c, name, "I");
    env->SetIntField(obj, longID, value);
}

jlong getLongField(JNIEnv *env, jobject obj, char const * name) {
    jclass c = env->GetObjectClass(obj);
    // J is the type signature for long:
    jfieldID longID =  env->GetFieldID(c, name, "J");
    jlong handle = env->GetLongField(obj, longID);
    return handle;
}

void setLongField(JNIEnv *env, jobject obj, char const * name, jlong value) {
    jclass c = env->GetObjectClass(obj);
    // J is the type signature for long:
    jfieldID longID =  env->GetFieldID(c, name, "J");
    env->SetLongField(obj, longID, value);
}

jfieldID getHandleField(JNIEnv *env, jobject obj)
{
    jclass c = env->GetObjectClass(obj);
    // J is the type signature for long:
    return env->GetFieldID(c, "opaqueNativeHandle", "J");
}

template <typename T>
T *getHandle(JNIEnv *env, jobject obj)
{
    jlong handle = env->GetLongField(obj, getHandleField(env, obj));
    return reinterpret_cast<T *>(handle);
}

template <typename T>
void setHandle(JNIEnv *env, jobject obj, T *t)
{
    jlong handle = reinterpret_cast<jlong>(t);
    env->SetLongField(obj, getHandleField(env, obj), handle);
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_github_gschwind_fiddle_1assistant_AudioThread_initSampleRate(JNIEnv *env, jobject thiz, jint sample_rate) {
    auto * thandler = getHandle<tone_handler<float, 1u<<15u>>(env, thiz);
    if (thandler == nullptr) {
        thandler = new tone_handler<float, 1u<<15u>;
        setHandle(env, thiz, thandler);
    }

    int err = thandler->init_sample_rate(sample_rate);

    setIntField(env, thiz, "length_of_sample", thandler->sample_length);

    return err;

}

extern "C"
JNIEXPORT jfloat JNICALL
Java_com_github_gschwind_fiddle_1assistant_AudioThread_computeFreq(JNIEnv *env, jobject thiz, jshortArray arr,
jint offset, jint length) {

    auto * thandler = getHandle<tone_handler<float, 1u<<15u>>(env, thiz);

    jsize len = env->GetArrayLength(arr);
    jshort * data = env->GetShortArrayElements(arr, 0);
    float freq = thandler->compute_freq(&data[offset], &data[offset+length]);
    env->ReleaseShortArrayElements(arr, data, 0);
    return freq;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_github_gschwind_fiddle_1assistant_AudioThread_dispose(JNIEnv *env, jobject thiz) {
    auto * thandler = getHandle<tone_handler<float, 1u<<15u>>(env, thiz);
    delete thandler;
    setHandle<tone_handler<float, 1u<<15u>>(env, thiz, nullptr);
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_com_github_gschwind_fiddle_1assistant_AudioThread_sampleEnergy(JNIEnv *env, jobject thiz, jshortArray arr,
                                                        jint offset, jint length) {
    auto * thandler = getHandle<tone_handler<float, 1u<<15u>>(env, thiz);

    jsize len = env->GetArrayLength(arr);
    jshort * data = env->GetShortArrayElements(arr, 0);
    float freq = thandler->absolute_volume(&data[offset], length);
    env->ReleaseShortArrayElements(arr, data, 0);
    return freq;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_github_gschwind_fiddle_1assistant_AudioThread__1setMinVolumeSensitivity(JNIEnv *env,
                                                                               jobject thiz,
                                                                               jdouble sensitivity) {
    auto * thandler = getHandle<tone_handler<float, 1u<<15u>>(env, thiz);
    if (thandler != nullptr)
        thandler->set_min_volume_sensitivity(sensitivity);
}
