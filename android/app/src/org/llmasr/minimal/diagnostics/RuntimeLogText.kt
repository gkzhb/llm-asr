package org.llmasr.minimal.diagnostics

/** Fixed user-facing meanings; never model/provider/user text. */
class RuntimeLogText private constructor() {
    companion object {
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun event(kind: RuntimeLogEventKind?): String = when (kind!!) {
            RuntimeLogEventKind.APP_STARTUP -> "应用启动"
            RuntimeLogEventKind.APP_READY -> "应用服务已初始化"
            RuntimeLogEventKind.MODEL_ACCESS_STARTED -> "模型访问检查开始（路径 / 懒校验）"
            RuntimeLogEventKind.MODEL_ACCESS_COMPLETED -> "模型访问检查通过（可复用当前 SHA 证明）"
            RuntimeLogEventKind.MODEL_ACCESS_FAILED -> "模型访问检查失败"
            RuntimeLogEventKind.MODEL_LOAD_STARTED -> "模型加载开始"
            RuntimeLogEventKind.MODEL_LOAD_COMPLETED -> "模型加载完成"
            RuntimeLogEventKind.MODEL_LOAD_FAILED -> "模型加载失败"
            RuntimeLogEventKind.INFERENCE_STARTED -> "推理开始"
            RuntimeLogEventKind.INFERENCE_COMPLETED -> "推理完成"
            RuntimeLogEventKind.INFERENCE_FAILED -> "推理失败"
            RuntimeLogEventKind.REQUEST_SUCCESS -> "转写响应解析完成（非报告保存 / 编辑器提交）"
            RuntimeLogEventKind.REQUEST_FAILURE -> "转写请求失败"
            RuntimeLogEventKind.LOG_TELEMETRY_FAILED -> "阶段日志不完整（不代表转写失败）"
            RuntimeLogEventKind.MODEL_VERIFY_STARTED -> "完整 SHA 校验开始"
            RuntimeLogEventKind.MODEL_VERIFY_COMPLETED -> "完整 SHA 校验完成"
            RuntimeLogEventKind.MODEL_VERIFY_FAILED -> "完整 SHA 校验失败"
            RuntimeLogEventKind.MODEL_VERIFY_CANCELLED -> "完整 SHA 校验取消"
            RuntimeLogEventKind.MODEL_IMPORT_STARTED -> "模型导入开始"
            RuntimeLogEventKind.MODEL_IMPORT_COMPLETED -> "模型导入完成"
            RuntimeLogEventKind.MODEL_IMPORT_FAILED -> "模型导入失败"
            RuntimeLogEventKind.MODEL_IMPORT_CANCELLED -> "模型导入取消"
            RuntimeLogEventKind.MODEL_DELETE_STARTED -> "内部模型删除开始"
            RuntimeLogEventKind.MODEL_DELETE_COMPLETED -> "内部模型删除完成"
            RuntimeLogEventKind.MODEL_DELETE_FAILED -> "内部模型删除失败"
            RuntimeLogEventKind.MODEL_DELETE_CANCELLED -> "内部模型删除取消"
            RuntimeLogEventKind.LOG_PERSISTENCE_FAILED -> "日志本地保存异常"
            RuntimeLogEventKind.LOG_EXPORT_COMPLETED -> "日志导出完成"
            RuntimeLogEventKind.LOG_EXPORT_FAILED -> "日志导出失败"
        }
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun export(state: LogExportController.State?): String = when (state!!.phase) {
            LogExportController.Phase.SELECTING -> "正在选择保存位置；导出点击时的固定快照。"
            LogExportController.Phase.WRITING -> "正在写入日志快照，等待文件提供方写入并关闭……"
            LogExportController.Phase.SUCCEEDED -> "已导出 UTF-8 日志快照：" + state.bytes + " bytes。"
            LogExportController.Phase.FAILED -> "日志导出失败，请重试或换一个位置；外部可能残留不完整文件。"
            LogExportController.Phase.CANCELLED -> "已取消选择，未写入日志。"
            LogExportController.Phase.EXPIRED -> "页面已重建或离开，旧选择失效；请重新导出。"
            else -> "导出采用点击时的快照，后续新事件不包含在该文件中。"
        }
    }
}
