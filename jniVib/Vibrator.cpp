    /* 
     * Copyright 2015 MITAC
     * 
     * Licensed under the Apache License, Version 2.0 (the "License"); 
     * you may not use this file except in compliance with the License. 
     * You may obtain a copy of the License at 
     * 
     * http://www.apache.org/licenses/LICENSE-2.0 
     * 
     * Unless required by applicable law or agreed to in writing, software 
     * distributed under the License is distributed on an "AS IS" BASIS, 
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
     * See the License for the specific language governing permissions and 
     * limitations under the License. 
     */



    #include <stdlib.h>
    #include <stdio.h>
    #include <assert.h>

    #include <termios.h>
    #include <unistd.h>
    #include <sys/types.h>
    #include <sys/stat.h>
    #include <fcntl.h>
    #include <string.h>
    #include <jni.h>
    #include "com_mitac_i2c_Vibrator.h"

    #include "android/log.h"
    static const char *TAG = "Vibrator";
    #define LOGI(fmt, args...) __android_log_print(ANDROID_LOG_INFO,  TAG, fmt, ##args)  
    #define LOGD(fmt, args...) __android_log_print(ANDROID_LOG_DEBUG, TAG, fmt, ##args)  
    #define LOGE(fmt, args...) __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, ##args)  

    #define DEVICE_PATH "/sys/class/timed_output/vibrator/intensity"//success 03.12.2014

/*
 * Class:     com_mitac_i2c_Vibrator
 * Method:    setIntensity
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_mitac_i2c_Vibrator_setIntensity
  (JNIEnv *env, jobject thiz, jint intensity_level)
{
    int nwr, ret, fd;
    char value[20];

    fd = open(DEVICE_PATH, O_RDWR);
    if(fd < 0)
        return -1;

    nwr = sprintf(value, "%d\n", intensity_level);
    ret = write(fd, value, nwr);

    close(fd);

    return (ret == nwr) ? 0 : -1;
}

/*
 * Class:     com_mitac_i2c_Vibrator
 * Method:    getIntensity
 * Signature: ()I
 */
JNIEXPORT jstring JNICALL Java_com_mitac_i2c_Vibrator_getIntensity
  (JNIEnv *env, jobject thiz)
{
    int pos, fd;
    char value[20];

    fd = open(DEVICE_PATH, O_RDWR);
    if(fd < 0)
        return env->NewStringUTF("");

    pos = read(fd, value, sizeof(value)-1);
    if(pos < 0)  {
        close(fd);
        return env->NewStringUTF("");
    }
    value[pos] = '\0';
    close(fd);

    //return atoi(value);
	  return env->NewStringUTF(value);
}

