package org.llmasr.minimal.modelmanagement;

import java.util.Locale;
import org.llmasr.minimal.model.ModelReadiness;
import org.llmasr.minimal.model.ModelReports;

/** Snapshot-only presentation; no disk IO, provider handles or transcript state. */
public final class ModelUiText {
    private ModelUiText() {}
    public static String bytes(long n) {
        return n < 0 ? "未知（尚未取得快照）" : String.format(Locale.ROOT, "%.2f MiB（%d bytes）", n / 1048576.0, n);
    }
    public static String readiness(ModelReadiness.State s) {
        switch (s) {
            case NOT_INSTALLED: return "未安装 · 请导入固定 MNN 部署目录";
            case INCOMPLETE: return "不完整 · 缺失/大小不符或存在残片";
            case UNVERIFIED: return "待校验 · 文件大小匹配不等于 SHA 通过";
            case VERIFYING: return "正在校验 · 尚不能作为可用证明";
            case READY: return "文件已校验，可尝试转写（不是本机性能验证）";
            case INVALID: return "模型无效 · 请校验或修复导入";
            default: return "未知 · 等待读取内部文件概况";
        }
    }
    public static String phase(ModelManagementState.Phase p) {
        switch (p) {
            case INSPECTING: return "读取概况 / 预检";
            case ENUMERATING: return "枚举目录 / 回收残片 / 规划";
            case CHECKING_EXISTING: return "校验可复用文件";
            case COPYING: return "复制（完成后仍需 SHA 校验）";
            case VERIFYING_FILE: return "校验临时文件";
            case PUBLISHING: return "发布已校验文件";
            case FINAL_VERIFY: return "全清单最终 SHA 校验";
            case DELETING: return "删除受管文件（不可撤销）";
            case CANCELLING: return "正在取消，等待文件读取/关闭/清理结束";
            case SUCCEEDED: return "已完成";
            case CANCELLED: return "已取消";
            case FAILED: return "未完成";
            default: return "空闲";
        }
    }
    public static String operation(ModelManagementState.OperationKind k) {
        switch (k) {
            case IMPORT: return "导入";
            case VERIFY: return "完整校验";
            case DELETE: return "删除内部模型";
            case INSPECT: return "概况检查（不进行 SHA 校验）";
            default: return "暂无操作";
        }
    }
    public static String safeError(String error) {
        if (error == null) return "";
        // Final UI guard for platform filesystem errors; no private absolute paths/URIs.
        if (error.matches("(?s).*[A-Za-z0-9]/.*") || error.matches("(?s).*[/\\\\][A-Za-z0-9].*") || error.contains("://") || error.length() > 512)
            return "存储或读取失败，请检查可用空间、重新选择并授权本地目录后重试";
        return error;
    }
    public static String fileResult(ModelManagementState.Snapshot s, String name) {
        if (!s.isTerminalPhase() && name.equals(s.fileName)) return "本次任务："+phase(s.phase);
        ModelReports.FileOutcome outcome = s.fileResults.get(name);
        String reuse=s.importPlan != null && s.importPlan.reusableFiles.contains(name) ? "；已复用" : "";
        if (outcome == null) return "";
        String body;
        switch (outcome.status) {
            case PENDING: body = "本次任务：待复制 / 修复"; break;
            case REUSED: body = "本次任务：已复用 / SHA 通过"; break;
            case PUBLISHED: body = "本次任务：已复制并发布（临时文件 SHA 通过；仍需全清单核验）"; break;
            case FINAL_VERIFIED: body = "本次任务：最终 SHA 通过"; break;
            case FAILED: body = "本次任务："+fileFailure(outcome.failure)+"："+safeError(name); break;
            default: body = ""; break;
        }
        return body + reuse;
    }
    private static String fileFailure(ModelReports.FileOutcome.Failure failure) {
        switch(failure) {
            case SHA: return "SHA-256 不符";
            case SIZE: return "缺少或大小不符";
            case SOURCE_MISSING: return "源目录缺少";
            case TOO_LARGE: return "文件超出清单大小";
            case WRITE_SIZE: return "写入字节数不匹配清单";
            default: return "文件读取或存储失败";
        }
    }
    public static String spacePlan(ModelManagementState.Snapshot s) {
        if (s.importPlan == null) return "本次保守空间需求：未知 / 等待导入预检";
        return "最近导入计划需复制："+bytes(s.importPlan.copyBytes)
            +"\n本次保守空间需求："+bytes(s.importPlan.requiredBytes)
            +"\n预检时可用空间："+bytes(s.importPlan.availableAtCheck)
            +"（需复制时含 64 MiB 余量；每文件仍独立检查）";
    }
    public static String task(ModelManagementState.Snapshot s) {
        if (s.operationKind == ModelManagementState.OperationKind.NONE) return "暂无模型操作";
        StringBuilder text = new StringBuilder(operation(s.operationKind)).append(" · ").append(phase(s.phase));
        if (s.activeOperationId != null) text.append("\n任务：").append(s.activeOperationId);
        if (!s.isTerminalPhase() && s.fileName != null) text.append("\n文件：").append(s.fileName).append("（").append(s.phase == ModelManagementState.Phase.DELETING ? s.fileIndex : s.fileIndex + 1).append(" / ").append(s.fileCount).append("）");
        if (!s.isTerminalPhase()) {
            if (s.phase == ModelManagementState.Phase.DELETING) text.append("\n已处理受管项：").append(s.stageBytesDone).append(" / ").append(s.stageBytesTotal).append("；成功 ").append(s.deleteSucceeded).append("；失败 ").append(s.deleteFailed);
            else {
                if (s.fileBytesTotal > 0) text.append("\n本文件：").append(bytes(s.fileBytesDone)).append(" / ").append(bytes(s.fileBytesTotal));
                if (s.stageBytesTotal > 0) text.append("\n本阶段：").append(bytes(s.stageBytesDone)).append(" / ").append(bytes(s.stageBytesTotal));
            }
        }
        text.append("\n已复用（不是新复制）：").append(bytes(s.reusedBytes));
        if (s.operationKind == ModelManagementState.OperationKind.IMPORT && s.phase == ModelManagementState.Phase.COPYING)
            text.append("\n复制 100% 也不代表安装完成，后续仍需校验。");
        if (s.errorCode != null) text.append("\n原因：").append(safeError(s.errorCode));
        if (!s.failedFiles.isEmpty()) text.append("\n失败受管文件：").append(safeError(s.failedFiles.toString()));
        if (s.isTerminalPhase()) {
            text.append(s.cleanupOutcome == ModelManagementState.Outcome.FAILED
                ? "\n清理未完全成功：残片或状态未知，请检查并重试。" : "\n受管残片检查完成。");
            if (s.operationKind == ModelManagementState.OperationKind.IMPORT)
                text.append("\n已发布的完整文件保留；重试需重新选择目录，复用通过 SHA 的文件，不从半文件偏移续传。");
            if (s.operationKind == ModelManagementState.OperationKind.DELETE)
                text.append("\n删除成功 ").append(s.deleteSucceeded).append(" 项，失败 ").append(s.deleteFailed)
                    .append(" 项；剩余受管正式文件 ").append(bytes(s.installedBytes));
        }
        return text.toString();
    }
}
