package me.kafuuneko.rpclient.libs.imagegen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.room.model.PreparedFile
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
/**
 * 生图流程编排层。
 *
 * 负责把网络返回的图片字节落成待提交的文件凭据，使生成结果与用户上传图片走完全相同的
 * 去重、暂存与提交路径；任何一步失败都会回收已生成的草稿，不留下孤儿文件。
 *
 * @param mClient 生图服务客户端
 * @param mFileRepository 文件索引与暂存区仓库
 */
class ImageGenRepository(
    private val mClient: ImageGenClient,
    private val mFileRepository: FileRepository
) {
    /**
     * 生成图片并返回待提交的文件凭据。
     *
     * @param ownerId 草稿所有者，使用聊天会话标识便于失败清理
     * @param settings 服务连接与默认参数
     * @param prompt 用户输入的原始提示词
     * @param aspect 请求画幅
     * @return 与图片数量一致的待提交凭据；顺序与网络返回一致
     * @throws ImageGenFailure 配置缺失、请求失败或返回内容不可用
     */
    suspend fun generate(
        ownerId: String,
        settings: ImageGenSettings,
        prompt: String,
        aspect: ImageGenAspect
    ): List<PreparedFile> {
        require(ownerId.isNotBlank()) { "The image owner cannot be empty" }
        require(prompt.isNotBlank()) { "The image prompt cannot be empty" }
        val request = ImageGenRequest(
            prompt = applyPromptTemplate(settings.promptTemplate, prompt),
            aspect = aspect
        )
        val payloads = mClient.generate(settings, request)
        val prepared = mutableListOf<PreparedFile>()
        try {
            payloads.forEach { payload ->
                val inline = when (payload) {
                    is ImageGenPayload.Inline -> payload
                    is ImageGenPayload.Remote -> mClient.download(payload.url)
                }
                prepared += mFileRepository.prepareGenerated(ownerId) { target ->
                    withContext(Dispatchers.IO) { target.writeBytes(inline.bytes) }
                    inline.mimeType
                }
            }
            return prepared
        } catch (error: Throwable) {
            // 中途失败时回收已写入的草稿，避免暂存区残留无人引用的图片
            withContext(NonCancellable) {
                prepared.forEach { runCatching { mFileRepository.releasePrepared(it) } }
            }
            throw error
        }
    }

    /** 把用户提示词填入模板；模板缺少占位符时直接返回原始提示词。 */
    private fun applyPromptTemplate(template: String, prompt: String): String {
        val trimmed = prompt.trim()
        if (template.isBlank() || !template.contains(PROMPT_PLACEHOLDER)) return trimmed
        return template.replace(PROMPT_PLACEHOLDER, trimmed).trim()
    }

    private companion object {
        const val PROMPT_PLACEHOLDER = "{{prompt}}"
    }
}
