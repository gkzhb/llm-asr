# Injected via CMAKE_PROJECT_MNN_INCLUDE into the pinned MNN top-level project.
# Defer until all upstream OBJECT targets and effective flags have been defined.
cmake_language(DEFER CALL include "${QWEN_ARTIFACT_FINALIZER}")
