LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := user development

# TODO: Remove dependency of application on the test runner (android.test.runner) 
# library.
LOCAL_JAVA_LIBRARIES := android.test.runner

LOCAL_STATIC_JAVA_LIBRARIES := googlelogin-client

LOCAL_SRC_FILES := $(call all-subdir-java-files) \
	src/com/android/browser/IGearsDialogService.aidl

LOCAL_PACKAGE_NAME := Browser

include $(BUILD_PACKAGE)
