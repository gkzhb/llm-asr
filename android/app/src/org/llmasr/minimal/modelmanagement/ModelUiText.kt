package org.llmasr.minimal.modelmanagement

import java.util.Collections
import java.util.Locale
import org.llmasr.minimal.model.ModelCatalog
import org.llmasr.minimal.model.ModelPackagePolicy
import org.llmasr.minimal.model.ModelResidency
import org.llmasr.minimal.model.ModelReadiness
import org.llmasr.minimal.model.ModelReports

/** Snapshot-only presentation, with fixed storage-error sanitization. */
class ModelUiText private constructor() {
    /** Display values only. No deployment, repository, live readiness or operation authority escapes. */
    class Card internal constructor(@JvmField val key: String, @JvmField val text: String)
    class DetailPresentation internal constructor(
        @JvmField val readinessText: String, @JvmField val storageText: String,
        @JvmField val taskText: String, @JvmField val filesText: String, @JvmField val riskText: String,
        @JvmField val selected: Boolean, @JvmField val canVerify: Boolean,
        @JvmField val canDelete: Boolean, @JvmField val canPrepare: Boolean)
    class PagePresentation internal constructor(cards: List<Card>,
        @JvmField val detail: DetailPresentation?, @JvmField val taskText: String,
        @JvmField val importText: String) {
        @JvmField val cards: List<Card> = Collections.unmodifiableList(ArrayList(cards))
    }
    /** Internal in-memory inputs, retained only while projecting; existing immutable snapshots are shared. */
    internal class DetailFacts(val policy: ModelPackagePolicy, val readiness: ModelReadiness.Snapshot,
        val selected: Boolean, val cachedFiles: List<ModelReports.FileDetail>, val bound: ModelManagementState.Snapshot?) {
        val files: List<ModelReports.FileDetail> get() = bound?.files ?: cachedFiles
    }
    companion object {
        internal fun card(row: ModelCatalog.Row, ready: ModelReadiness.Snapshot): Card {
            val policy = row.deployment.policy
            val verified = if (ready.ready() && ready.epoch == row.epoch) row.verifiedCount else 0
            return Card(row.deployment.key, policy.displayName + " · " + policy.precision +
                (if (policy.trustedLegacy) "" else "（声明）") + "\n" + bytes(policy.totalBytes) +
                (if (row.selected) " · 当前选用" else "") + "\n" + readiness(ready.state) + " · 已校验 " + verified + "/7")
        }
        internal fun page(cards: List<Card>, target: DetailFacts?, latest: ModelManagementState.Snapshot,
            importing: ModelManagementState.Snapshot?, archiveBytes: Long, resident: ModelResidency.Snapshot?): PagePresentation {
            val detail = target?.let { detail(it, resident) }
            val importText = importing?.let { "最近模型包导入（任务独立于模型详情）\n" + task(it) +
                "\n私有 ZIP 已读取：" + bytes(archiveBytes) } ?: ""
            return PagePresentation(cards, detail, detail?.taskText ?: taskStatus(latest, "最近模型操作\n" + task(latest)), importText)
        }
        private fun detail(facts: DetailFacts, resident: ModelResidency.Snapshot?): DetailPresentation {
            val any = facts.files.any { it.present || it.partExists }
            val completeFile = facts.files.any { it.present && it.sizeMatch }
            val bound = facts.bound
            val taskText = if (bound == null) "此模型尚无当前操作记录" else taskStatus(bound, task(bound))
            val risk = if (bound == null) "等待此模型检查" else task(bound) +
                (if (bound.unexpectedFiles.isEmpty()) "" else "\n未知项 " + bound.unexpectedFiles.size + "：保留、不递归删除")
            return DetailPresentation(readinessText(facts, resident), storageText(facts), taskText, fileDetails(facts), risk,
                facts.selected, completeFile, any || !facts.policy.trustedLegacy, facts.readiness.ready() && facts.selected)
        }
        private fun readinessText(facts: DetailFacts, resident: ModelResidency.Snapshot?): String =
            facts.policy.displayName + "\n" + (if (facts.policy.trustedLegacy) "内置固定可信清单" else "完整性按导入者提供清单；非可信来源认证 · 精度仅声明") +
                "\n文件：" + readiness(facts.readiness.state) + "\nepoch " + facts.readiness.epoch +
                "\n内存：" + (resident?.state?.name ?: "未绑定") + (if (resident?.pendingRelease == true) " · 等待释放" else "")
        private fun storageText(facts: DetailFacts): String {
            val bound = facts.bound
            return "预期模型：" + bytes(facts.policy.totalBytes) + "\n内部正式文件：" + bytes(bound?.installedBytes ?: -1) +
                "\n临时残片：" + bytes(bound?.orphanPartBytes ?: -1) + "\n可用存储（最近检查）：" + bytes(bound?.availableBytes ?: -1) +
                "\n" + (if (bound == null) "等待此模型概况检查" else spacePlan(bound))
        }
        private fun taskStatus(snapshot: ModelManagementState.Snapshot, text: String): String = text +
            (if (snapshot.inspectBusy) "\n正在刷新内部概况（最近操作保留）" else "") +
            (snapshot.inspectError?.let { "\n概况刷新：" + it } ?: "")
        private fun fileDetails(facts: DetailFacts): String {
            val text = StringBuilder()
            for (file in facts.files) {
                text.append(file.name).append("\n").append(fileEvidence(facts, file)).append("\n")
                    .append(if (facts.bound == null) "最近目录快照；未运行新校验" else fileResult(facts.bound, file.name))
                    .append(" · ").append(bytes(file.actualBytes)).append("\n预期：").append(bytes(file.expectedBytes))
                    .append(if (file.partExists) "\n有受管 .part 残片" else "")
                    .append("\nSHA-256: ").append(expectedHash(facts.policy, file.name)).append("\n\n")
            }
            if (facts.files.isEmpty()) text.append("尚未取得文件快照。\n")
            val unexpected = facts.bound?.unexpectedFiles ?: emptyList()
            if (unexpected.isNotEmpty()) text.append("内部目录有 ").append(unexpected.size).append(" 项未知文件/目录：保留、不递归删除。")
            return text.toString()
        }
        private fun fileEvidence(facts: DetailFacts, file: ModelReports.FileDetail): String = when {
            !file.present -> "缺失"
            !file.sizeMatch -> "大小不符"
            facts.readiness.ready() && (facts.bound == null || facts.readiness.epoch == facts.bound.modelEpoch) -> "SHA 已通过"
            else -> "大小匹配 / 尚无当前 SHA 证明"
        }
        private fun expectedHash(policy: ModelPackagePolicy, file: String?): String =
            try { policy.manifest.require(file).sha256() } catch (_: java.io.IOException) { "未知" }

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
            ModelManagementState.Phase.COPYING_ARCHIVE -> "复制私有模型包（另需展开模型与 64 MiB 预留）"
            ModelManagementState.Phase.CHECKING_ARCHIVE -> "全包格式 / CRC / 可信 SHA 审核（尚未发布）"
            ModelManagementState.Phase.CLEANING_ARCHIVE -> "关闭模型包并清理私有暂存"
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
                "\n预检时可用空间：" + bytes(plan.availableAtCheck) + "（需复制时含 64 MiB 余量；每文件仍独立检查）" +
                (if (plan.zipPackage) "\n另已占用私有 ZIP：" + bytes(plan.archiveBytes) + "（以上可用空间已扣除此项）；落包阶段保守预留完整模型，低空间可改选目录。" else "")
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
                    text.append("\n已发布的完整文件保留；重试需重新选择 ZIP 或目录，复用通过 SHA 的文件，不从半文件偏移续传。")
                if (state.operationKind == ModelManagementState.OperationKind.DELETE)
                    text.append("\n删除成功 ").append(state.deleteSucceeded).append(" 项，失败 ").append(state.deleteFailed)
                        .append(" 项；剩余受管正式文件 ").append(bytes(state.installedBytes))
            }
            return text.toString()
        }
    }
}
