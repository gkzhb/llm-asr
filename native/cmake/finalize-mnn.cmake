# Runs in MNN's directory after all of its normal build configuration.
if(NOT ANDROID OR NOT ANDROID_ABI STREQUAL "arm64-v8a" OR
   NOT ANDROID_PLATFORM STREQUAL "android-29" OR NOT ANDROID_STL STREQUAL "c++_static")
    message(FATAL_ERROR "Qwen artifact requires Android29 arm64 with one static C++ runtime")
endif()
if(NOT MNN_KLEIDIAI OR NOT EXISTS "${KLEIDIAI_SRC_DIR}/kai")
    message(FATAL_ERROR "Verified KleidiAI source is required; no download/degradation allowed")
endif()
# Keep MNN_BUILD_SHARED_LIBS=ON so original MNN object compilation is unchanged;
# consume its OBJECT targets, not its DSO or a static build with different defines.
add_library(qwen_mnn_dummy OBJECT "${CMAKE_CURRENT_SOURCE_DIR}/cmake/dummy.cpp")
set(qwen_objects ${MNN_OBJECTS_TO_LINK} $<TARGET_OBJECTS:qwen_mnn_dummy>)
file(GENERATE OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/qwen-objects.txt"
    CONTENT "$<JOIN:${qwen_objects},\n>\n")
find_package(Python3 REQUIRED COMPONENTS Interpreter)
add_custom_command(
    OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/artifact/libqwen_asr_jni.so"
           "${CMAKE_CURRENT_BINARY_DIR}/artifact/native-link-provenance.json"
    COMMAND "${Python3_EXECUTABLE}" "${QWEN_LINK_SCRIPT}"
        --objects "${CMAKE_CURRENT_BINARY_DIR}/qwen-objects.txt"
        --compiler "${QWEN_COMPILER}" --source "${QWEN_JNI_SOURCE}"
        --mnn-source "${CMAKE_CURRENT_SOURCE_DIR}" --header "${QWEN_JNI_HEADER}"
        --output "${CMAKE_CURRENT_BINARY_DIR}/artifact/libqwen_asr_jni.so"
    DEPENDS ${qwen_objects} "${QWEN_LINK_SCRIPT}" "${QWEN_JNI_SOURCE}" "${QWEN_JNI_HEADER}"
            "${CMAKE_CURRENT_LIST_DIR}/../apk/model_graph_policy.h"
    VERBATIM)
add_custom_target(qwen_asr_artifact DEPENDS
    "${CMAKE_CURRENT_BINARY_DIR}/artifact/libqwen_asr_jni.so"
    "${CMAKE_CURRENT_BINARY_DIR}/artifact/native-link-provenance.json")
