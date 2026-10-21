package org.llmasr.minimal.modelmanagement

import java.util.Locale
import org.llmasr.minimal.model.ModelReadiness
import org.llmasr.minimal.model.ModelReports

/** Snapshot-only presentation, with fixed storage-error sanitization. */
class ModelUiText private constructor() {
    companion object {
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun bytes(n: Long): String = if (n < 0) "未知（尚未取得快照）" else String.format(Locale.ROOT, "%.2f MiB（%d bytes）", n / 1048576.0, n)
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun readiness(s: ModelReadiness.State?): String = when (s!!) {
            ModelReadiness.State.NOT_INSTALLED -> "未安装 · 请导入固定 MNN 部署目录"
            ModelReadiness.State.INCOMPLETE -> "不完整 · 缺失/大小不符或存在残片"
            ModelReadiness.State.UNVERIFIED -> "待校验 · 文件大小匹配不等于 SHA 通过"
            ModelReadiness.State.VERIFYING -> "正在校验 · 尚不能作为可用证明"
            ModelReadiness.State.READY -> "文件已校验，可尝试转写（不是本机性能验证）"
            ModelReadiness.State.INVALID -> "模型无效 · 请校验或修复导入"
            else -> "未知 · 等待读取内部文件概况"
        }
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun phase(p: ModelManagementState.Phase?): String = when (p!!) {
            ModelManagementState.Phase.INSPECTING -> "读取概况 / 预检"
            ModelManagementState.Phase.ENUMERATING -> "枚举目录 / 回收残片 / 规划"
            ModelManagementState.Phase.CHECKING_EXISTING -> "校验可复用文件"
            ModelManagementState.Phase.COPYING -> "复制（完成后仍需 SHA 校验）"
            ModelManagementState.Phase.VERIFYING_FILE -> "校验临时文件"
            ModelManagementState.Phase.PUBLISHING -> "发布已校验文件"
            ModelManagementState.Phase.FINAL_VERIFY -> "全清单最终 SHA 校验"
            ModelManagementState.Phase.DELETING -> "删除受管文件（不可撤销）"
            ModelManagementState.Phase.CANCELLING -> "正在取消，等待文件读取/关闭/清理结束"
            ModelManagementState.Phase.SUCCEEDED -> "已完成"
            ModelManagementState.Phase.CANCELLED -> "已取消"
            ModelManagementState.Phase.FAILED -> "未完成"
            else -> "空闲"
        }
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun operation(k: ModelManagementState.OperationKind?): String = when (k!!) {
            ModelManagementState.OperationKind.IMPORT -> "导入"
            ModelManagementState.OperationKind.VERIFY -> "完整校验"
            ModelManagementState.OperationKind.DELETE -> "删除内部模型"
            ModelManagementState.OperationKind.INSPECT -> "概况检查（不进行 SHA 校验）"
            else -> "暂无操作"
        }
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun safeError(error: String?): String {
            if (error == null) return ""
            if (error.matches(Regex("(?s).*[A-Za-z0-9]/.*")) || error.matches(Regex("(?s).*[/\\\\][A-Za-z0-9].*")) || error.contains("://") || error.length > 512)
                return "存储或读取失败，请检查可用空间、重新选择并授权本地目录后重试"
            return error
        }
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun fileResult(s: ModelManagementState.Snapshot?, name: String?): String {
            val state = s!!
            if (!state.isTerminalPhase && name!!.equals(state.fileName)) return "本次任务：" + phase(state.phase)
            val outcome = state.fileResults[name]
            val reuse = if (state.importPlan != null && state.importPlan.reusableFiles.contains(name)) "；已复用" else ""
            if (outcome == null) return ""
            val body = when (outcome.status) {
                ModelReports.FileOutcome.Status.PENDING -> "本次任务：待复制 / 修复"
                ModelReports.FileOutcome.Status.REUSED -> "本次任务：已复用 / SHA 通过"
                ModelReports.FileOutcome.Status.PUBLISHED -> "本次任务：已复制并发布（临时文件 SHA 通过；仍需全清单核验）"
                ModelReports.FileOutcome.Status.FINAL_VERIFIED -> "本次任务：最终 SHA 通过"
                ModelReports.FileOutcome.Status.FAILED -> "本次任务：" + fileFailure(outcome.failure) + "：" + safeError(name)
            }
            return body + reuse
        }
        private fun fileFailure(failure: ModelReports.FileOutcome.Failure?): String = when (failure!!) {
            ModelReports.FileOutcome.Failure.SHA -> "SHA-256 不符"
            ModelReports.FileOutcome.Failure.SIZE -> "缺少或大小不符"
            ModelReports.FileOutcome.Failure.SOURCE_MISSING -> "源目录缺少"
            ModelReports.FileOutcome.Failure.TOO_LARGE -> "文件超出清单大小"
            ModelReports.FileOutcome.Failure.WRITE_SIZE -> "写入字节数不匹配清单"
            else -> "文件读取或存储失败"
        }
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun spacePlan(s: ModelManagementState.Snapshot?): String {
            val state = s!!
            val plan = state.importPlan ?: return "本次保守空间需求：未知 / 等待导入预检"
            return "最近导入计划需复制：" + bytes(plan.copyBytes) + "\n本次保守空间需求：" + bytes(plan.requiredBytes) +
                "\n预检时可用空间：" + bytes(plan.availableAtCheck) + "（需复制时含 64 MiB 余量；每文件仍独立检查）"
        }
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun task(s: ModelManagementState.Snapshot?): String {
            val state = s!!
            if (state.operationKind == ModelManagementState.OperationKind.NONE) return "暂无模型操作"
            val text = StringBuilder(operation(state.operationKind)).append(" · ").append(phase(state.phase))
            if (state.activeOperationId != null) text.append("\n任务：").append(state.activeOperationId)
            if (!state.isTerminalPhase && state.fileName != null) text.append("\n文件：").append(state.fileName).append("（")
                .append(if (state.phase == ModelManagementState.Phase.DELETING) state.fileIndex else state.fileIndex + 1).append(" / ").append(state.fileCount).append("）")
            if (!state.isTerminalPhase) {
                if (state.phase == ModelManagementState.Phase.DELETING) text.append("\n已处理受管项：").append(state.stageBytesDone).append(" / ").append(state.stageBytesTotal)
                    .append("；成功 ").append(state.deleteSucceeded).append("；失败 ").append(state.deleteFailed)
                else {
                    if (state.fileBytesTotal > 0) text.append("\n本文件：").append(bytes(state.fileBytesDone)).append(" / ").append(bytes(state.fileBytesTotal))
                    if (state.stageBytesTotal > 0) text.append("\n本阶段：").append(bytes(state.stageBytesDone)).append(" / ").append(bytes(state.stageBytesTotal))
                }
            }
            text.append("\n已复用（不是新复制）：").append(bytes(state.reusedBytes))
            if (state.operationKind == ModelManagementState.OperationKind.IMPORT && state.phase == ModelManagementState.Phase.COPYING)
                text.append("\n复制 100% 也不代表安装完成，后续仍需校验。")
            if (state.errorCode != null) text.append("\n原因：").append(safeError(state.errorCode))
            if (!state.failedFiles.isEmpty()) text.append("\n失败受管文件：").append(safeError(state.failedFiles.toString()))
            if (state.isTerminalPhase) {
                text.append(if (state.cleanupOutcome == ModelManagementState.Outcome.FAILED) "\n清理未完全成功：残片或状态未知，请检查并重试。" else "\n受管残片检查完成。")
                if (state.operationKind == ModelManagementState.OperationKind.IMPORT)
                    text.append("\n已发布的完整文件保留；重试需重新选择目录，复用通过 SHA 的文件，不从半文件偏移续传。")
                if (state.operationKind == ModelManagementState.OperationKind.DELETE)
                    text.append("\n删除成功 ").append(state.deleteSucceeded).append(" 项，失败 ").append(state.deleteFailed)
                        .append(" 项；剩余受管正式文件 ").append(bytes(state.installedBytes))
            }
            return text.toString()
        }
    }
}
