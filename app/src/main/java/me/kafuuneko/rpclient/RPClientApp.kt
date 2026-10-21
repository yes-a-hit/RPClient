package me.kafuuneko.rpclient

import android.app.Application
import androidx.room.Room
import com.chibatching.kotpref.Kotpref
import com.google.gson.Gson
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.character.CharacterCardRepository
import me.kafuuneko.rpclient.libs.assistant.AssistantSessionStore
import me.kafuuneko.rpclient.libs.assistant.AssistantStore
import me.kafuuneko.rpclient.libs.assistant.AssistantToolExecutor
import me.kafuuneko.rpclient.libs.billing.PricingRepository
import me.kafuuneko.rpclient.libs.billing.ProviderBalanceClient
import me.kafuuneko.rpclient.libs.billing.UsageCostCalculator
import me.kafuuneko.rpclient.libs.gallery.GalleryStore
import me.kafuuneko.rpclient.libs.script.ScriptStore
import me.kafuuneko.rpclient.libs.script.ScriptPlayStore
import me.kafuuneko.rpclient.libs.story.NovelImportRepository
import me.kafuuneko.rpclient.libs.story.StoryExtractionService
import me.kafuuneko.rpclient.libs.character.CharacterProfileCodec
import me.kafuuneko.rpclient.libs.imagegen.ImageGenClient
import me.kafuuneko.rpclient.libs.imagegen.ImageGenRepository
import me.kafuuneko.rpclient.libs.skill.SkillPromptBuilder
import me.kafuuneko.rpclient.libs.skill.SkillRepository
import me.kafuuneko.rpclient.libs.chat.ChatArchiveCodec
import me.kafuuneko.rpclient.libs.chat.ChatArchiveImageStore
import me.kafuuneko.rpclient.libs.chat.ChatArchiveRepository
import me.kafuuneko.rpclient.libs.core.releaseObsoletePersistedUriPermissions
import me.kafuuneko.rpclient.libs.groupchat.GroupChatGreetingPlanner
import me.kafuuneko.rpclient.libs.groupchat.GroupChatOutputSanitizer
import me.kafuuneko.rpclient.libs.groupchat.GroupChatPromptBuilder
import me.kafuuneko.rpclient.libs.groupchat.GroupChatSpeakerSelector
import me.kafuuneko.rpclient.libs.groupchat.GroupChatSummaryPromptBuilder
import me.kafuuneko.rpclient.libs.llm.ImageInputCapabilityResolver
import me.kafuuneko.rpclient.libs.llm.LLMClientFactory
import me.kafuuneko.rpclient.libs.llm.LLMProviderSelectionResolver
import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogClientFactory
import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogRepository
import me.kafuuneko.rpclient.libs.llm.image.ImageTokenEstimatorRegistry
import me.kafuuneko.rpclient.libs.media.MessageImageRuntime
import me.kafuuneko.rpclient.libs.prompt.ChatPromptBuilder
import me.kafuuneko.rpclient.libs.prompt.FormattedHistoryBuilder
import me.kafuuneko.rpclient.libs.prompt.PromptMacroResolver
import me.kafuuneko.rpclient.libs.prompt.PromptPreferences
import me.kafuuneko.rpclient.libs.prompt.PromptRequestFinalizer
import me.kafuuneko.rpclient.libs.prompt.PromptTokenizerRegistry
import me.kafuuneko.rpclient.libs.prompt.SummaryPromptBuilder
import me.kafuuneko.rpclient.libs.prompt.WorldBookActivator
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehaviorProvider
import me.kafuuneko.rpclient.libs.regex.RegexMessageProcessor
import me.kafuuneko.rpclient.libs.regex.RegexScriptCodec
import me.kafuuneko.rpclient.libs.regex.RegexScriptEngine
import me.kafuuneko.rpclient.libs.regex.RegexScriptRepository
import me.kafuuneko.rpclient.libs.regex.RegexScriptRuntime
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMTokenUsageRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.room.repository.MessageImageRepository
import me.kafuuneko.rpclient.libs.room.repository.StoryRepository
import me.kafuuneko.rpclient.libs.story.StoryArchiveCodec
import me.kafuuneko.rpclient.libs.story.StoryArchiveRepository
import me.kafuuneko.rpclient.libs.story.StoryCharacterActivator
import me.kafuuneko.rpclient.libs.story.StoryContextSelector
import me.kafuuneko.rpclient.libs.story.StoryOutputSanitizer
import me.kafuuneko.rpclient.libs.story.StoryPromptBuilder
import me.kafuuneko.rpclient.libs.story.StorySummaryPromptBuilder
import me.kafuuneko.rpclient.libs.theme.AppThemeManager
import me.kafuuneko.rpclient.libs.upgrade.AndroidAppVersionCodeProvider
import me.kafuuneko.rpclient.libs.upgrade.AppModelUpgradeVersionStore
import me.kafuuneko.rpclient.libs.upgrade.AppUpgradeManager
import me.kafuuneko.rpclient.libs.upgrade.steps.Upgrade20260103
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import me.kafuuneko.rpclient.libs.net.CertificateGuard
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** 应用进程入口，初始化偏好存储与全局 Koin 依赖图。 */
class RPClientApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Kotpref.init(this)
        val koinApplication = startKoin {
            androidContext(this@RPClientApp)
            modules(appModules)
        }
        runBlocking(Dispatchers.IO) {
            contentResolver.releaseObsoletePersistedUriPermissions()
            koinApplication.koin.get<AppUpgradeManager>().upgrade()
            koinApplication.koin.get<FileRepository>().cleanupAbandonedFiles()
        }
    }
}

/**
 * 应用级单例依赖。
 *
 * 业务对象保持无 Activity 引用；页面 ViewModel 通过 KoinComponent 按需获取这些实例。
 */

/**
 * 取系统 TrustManager，**包含用户安装的 CA**。
 *
 * 这里必须用 `AndroidCAStore`：它把系统根与用户装进「受信任凭据」的 CA 合在一起。
 * 直接 `factory.init(null)` 拿到的是纯系统根，会让我们自己的
 * `network_security_config`（声明信任 user）形同虚设——抓包证书装了也连不上。
 *
 * 用户证书本身不可怕，可怕的是被污染的那几条链；
 * eMudhra / emSign 由 [CertificateGuard] 在外层按名称拦掉，两头都不耽误。
 */
private fun platformTrustManager(): X509TrustManager {
    val keyStore = runCatching {
        KeyStore.getInstance("AndroidCAStore").apply { load(null) }
    }.getOrNull()
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(keyStore)
    return factory.trustManagers
        .filterIsInstance<X509TrustManager>()
        .firstOrNull()
        ?: error("找不到系统 X509TrustManager")
}

internal val appModules = module {
    single {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
        // 在默认 TrustManager 外面再包一层：拒绝已泄露私钥的 CA 签发的证书。
        // 系统的证书校验照常跑，这里只多做一道名称检查。
        val trustManager = CertificateGuard.wrap(platformTrustManager())
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }
        builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        builder.build()
    }

    single { Gson() }

    single {
        LLMClientFactory(
            mOkHttpClient = get(),
            mLLMRequestLogRepository = get(),
            mLLMTokenUsageRepository = get(),
            mPromptTokenizerRegistry = get(),
            mImageRuntime = get(),
            mImageCapabilities = get()
        )
    }
    singleOf(::LLMProviderSelectionResolver)
    singleOf(::AppThemeManager)
    singleOf(::LLMModelCatalogClientFactory)
    singleOf(::LLMModelCatalogRepository)
    singleOf(::ImageInputCapabilityResolver)
    singleOf(::FormattedHistoryBuilder)
    singleOf(::PromptMacroResolver)
    singleOf(::WorldBookActivator)
    single { ImageTokenEstimatorRegistry(get()) }
    single { PromptTokenizerRegistry(get()) }
    single { PromptRequestFinalizer(get<PromptTokenizerRegistry>()) }
    single<ExampleDialogueBehaviorProvider> {
        ExampleDialogueBehaviorProvider {
            ExampleDialogueBehavior.fromPersistedValue(
                AppModel.exampleDialogueBehavior
            )
        }
    }
    single<PromptPreferences> { AppModel }
    singleOf(::ChatPromptBuilder)
    singleOf(::SummaryPromptBuilder)
    singleOf(::ChatArchiveCodec)
    singleOf(::ChatArchiveImageStore)
    singleOf(::ChatArchiveRepository)
    singleOf(::GroupChatPromptBuilder)
    singleOf(::GroupChatGreetingPlanner)
    singleOf(::GroupChatSpeakerSelector)
    singleOf(::GroupChatSummaryPromptBuilder)
    singleOf(::GroupChatOutputSanitizer)
    singleOf(::StoryCharacterActivator)
    singleOf(::StoryContextSelector)
    singleOf(::StoryPromptBuilder)
    singleOf(::StorySummaryPromptBuilder)
    singleOf(::StoryOutputSanitizer)
    singleOf(::StoryArchiveCodec)
    singleOf(::StoryArchiveRepository)
    singleOf(::RegexScriptCodec)
    // 角色扩展档案编解码器：Prompt 构建器与角色编辑页共用同一实例
    single { CharacterProfileCodec(get()) }
    singleOf(::RegexScriptEngine)
    singleOf(::RegexScriptRuntime)
    singleOf(::RegexMessageProcessor)

    // 业务数据升级
    singleOf(::AndroidAppVersionCodeProvider)
    singleOf(::AppModelUpgradeVersionStore)
    singleOf(::Upgrade20260103)
    single {
        AppUpgradeManager(
            versionCodeProvider = get<AndroidAppVersionCodeProvider>(),
            versionStore = get<AppModelUpgradeVersionStore>(),
            upgrades = listOf(get<Upgrade20260103>())
        )
    }

    single {
        Room.databaseBuilder(get(), AppDatabase::class.java, "primary.sqlite")
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()
    }

    single {
        Room.databaseBuilder(get(), RequestLogDatabase::class.java, "request_logs.sqlite")
            .build()
    }

    singleOf(::CharacterRepository)
    singleOf(::LorebookRepository)
    singleOf(::ChatRepository)
    singleOf(::LLMRepository)
    singleOf(::LLMRequestLogRepository)
    singleOf(::LLMTokenUsageRepository)
    singleOf(::FileRepository)
    singleOf(::MessageImageRepository)
    single { MessageImageRuntime(get(), get()) }
    singleOf(::CharacterCardRepository)
    singleOf(::GroupChatRepository)
    singleOf(::RegexScriptRepository)
    singleOf(::StoryRepository)

    // 外接生图链路：客户端负责协议收发，仓库负责把结果落成可提交的文件凭据
    singleOf(::ImageGenClient)
    singleOf(::ImageGenRepository)

    // 助手 Skill：文件式能力包仓库 + 无状态 Prompt 渲染器
    single { SkillRepository(androidContext()) }
    singleOf(::SkillPromptBuilder)

    // 助手会话：沙箱内文件工具与角色卡编辑工具
    single { AssistantStore(androidContext()) }
    single { AssistantSessionStore(androidContext()) }
    single { GalleryStore(androidContext(), get()) }
    single { AssistantToolExecutor(get<AssistantStore>().sandboxRoot, get(), get(), get(), get()) }

    // 计费：模型报价快照与服务商余额查询
    single { PricingRepository(get(), get()) }
    single { ProviderBalanceClient(get(), get()) }
    single { UsageCostCalculator(get()) }

    // 剧本存储：一个剧本一个 JSON 文件，不做数据库迁移
    single { ScriptStore(androidContext()) }
    single { ScriptPlayStore(androidContext()) }

    // 本地小说导入：文本读取、分章建故事与设定提取
    single { NovelImportRepository(androidContext(), get()) }
    single { StoryExtractionService(get(), get(), get(), get()) }

}
