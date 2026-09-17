package org.llmasr.minimal.diagnostics;

/** Fixed user-facing meanings; no model/provider/user strings. */
public final class RuntimeLogText {
    private RuntimeLogText() { }
    public static String event(RuntimeLogEventKind kind) {
        switch (kind) {
            case APP_STARTUP: return "应用启动";
            case APP_READY: return "应用服务已初始化";
            case MODEL_ACCESS_STARTED: return "模型访问检查开始（路径 / 懒校验）";
            case MODEL_ACCESS_COMPLETED: return "模型访问检查通过（可复用当前 SHA 证明）";
            case MODEL_ACCESS_FAILED: return "模型访问检查失败";
            case MODEL_LOAD_STARTED: return "模型加载开始";
            case MODEL_LOAD_COMPLETED: return "模型加载完成";
            case MODEL_LOAD_FAILED: return "模型加载失败";
            case INFERENCE_STARTED: return "推理开始";
            case INFERENCE_COMPLETED: return "推理完成";
            case INFERENCE_FAILED: return "推理失败";
            case REQUEST_SUCCESS: return "转写响应解析完成（非报告保存 / 编辑器提交）";
            case REQUEST_FAILURE: return "转写请求失败";
            case LOG_TELEMETRY_FAILED: return "阶段日志不完整（不代表转写失败）";
            case MODEL_VERIFY_STARTED: return "完整 SHA 校验开始";
            case MODEL_VERIFY_COMPLETED: return "完整 SHA 校验完成";
            case MODEL_VERIFY_FAILED: return "完整 SHA 校验失败";
            case MODEL_VERIFY_CANCELLED: return "完整 SHA 校验取消";
            case MODEL_IMPORT_STARTED: return "模型导入开始";
            case MODEL_IMPORT_COMPLETED: return "模型导入完成";
            case MODEL_IMPORT_FAILED: return "模型导入失败";
            case MODEL_IMPORT_CANCELLED: return "模型导入取消";
            case MODEL_DELETE_STARTED: return "内部模型删除开始";
            case MODEL_DELETE_COMPLETED: return "内部模型删除完成";
            case MODEL_DELETE_FAILED: return "内部模型删除失败";
            case MODEL_DELETE_CANCELLED: return "内部模型删除取消";
            case LOG_PERSISTENCE_FAILED: return "日志本地保存异常";
            case LOG_EXPORT_COMPLETED: return "日志导出完成";
            case LOG_EXPORT_FAILED: return "日志导出失败";
            default: throw new IllegalArgumentException("unknown event");
        }
    }
    public static String export(LogExportController.State state) {
        switch (state.phase) {
            case SELECTING: return "正在选择保存位置；导出点击时的固定快照。";
            case WRITING: return "正在写入日志快照，等待文件提供方写入并关闭……";
            case SUCCEEDED: return "已导出 UTF-8 日志快照：" + state.bytes + " bytes。";
            case FAILED: return "日志导出失败，请重试或换一个位置；外部可能残留不完整文件。";
            case CANCELLED: return "已取消选择，未写入日志。";
            case EXPIRED: return "页面已重建或离开，旧选择失效；请重新导出。";
            default: return "导出采用点击时的快照，后续新事件不包含在该文件中。";
        }
    }
}
