LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)


LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := I2C
LOCAL_CERTIFICATE := platform

LOCAL_JNI_SHARED_LIBRARIES := libSerialPort libVibrator
LOCAL_STATIC_JAVA_LIBRARIES :=

#LOCAL_PROGUARD_FLAG_FILES := proguard.flags


include $(BUILD_PACKAGE)

# Use the folloing include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
