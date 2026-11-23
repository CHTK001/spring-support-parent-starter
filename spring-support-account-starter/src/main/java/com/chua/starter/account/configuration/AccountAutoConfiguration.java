package com.chua.starter.account.configuration;

import com.chua.starter.account.template.MessageTemplateController;
import com.chua.starter.account.template.MessageTemplateService;
import com.chua.starter.account.mail.MailboxController;
import com.chua.starter.account.mail.MailboxService;
import com.chua.starter.account.mail.SmsTemplateSyncController;
import com.chua.starter.account.mail.SmsTemplateSyncService;
import com.chua.starter.account.sendlog.SendLogController;
import com.chua.starter.account.sendlog.SendLogService;
import com.chua.starter.account.mail.temp.GuerrillaMailService;
import com.chua.starter.account.mail.temp.TempMailController;
import com.chua.starter.account.mail.temp.TempMailIoService;
import com.chua.starter.account.mail.temp.TempMailService;
import com.chua.starter.account.schedule.MailNewMessagePoller;
import com.chua.starter.account.mail.notify.SocketNewMailPublisher;
import com.chua.starter.account.manage.event.AccountEventController;
import com.chua.starter.account.manage.event.AccountEventSseService;
import com.chua.starter.account.schedule.SmsDirectoryPoller;
import com.chua.starter.account.phone.TempPhoneController;
import com.chua.starter.account.phone.TempPhonePoolService;
import com.chua.starter.account.phone.TempPhoneService;
import com.chua.starter.account.manage.controller.AccountAppKeyController;
import com.chua.starter.account.manage.controller.AccountController;
import com.chua.starter.account.manage.controller.AccountCookieController;
import com.chua.starter.account.manage.controller.AccountCreditConfigController;
import com.chua.starter.account.manage.controller.AccountSecretController;
import com.chua.starter.account.manage.controller.AccountMockDraftController;
import com.chua.starter.account.manage.security.AccountSecretCodec;
import com.chua.starter.account.manage.service.impl.SysAccountAppKeyServiceImpl;
import com.chua.starter.account.manage.service.impl.SysAccountCookieServiceImpl;
import com.chua.starter.account.manage.service.impl.SysAccountCreditConfigServiceImpl;
import com.chua.starter.account.manage.service.impl.SysAccountSecretServiceImpl;
import com.chua.starter.account.manage.service.impl.ManageAccountServiceImpl;
import com.chua.starter.account.manage.support.AccountApiExceptionAdvice;
import com.chua.starter.account.channel.free.KeylessGenerateModelMaterializer;
import com.chua.starter.account.channel.migrator.ChannelConnectionSchemaMigrator;
import com.chua.starter.account.channel.service.AccountChannelConnectionService;
import com.chua.starter.account.channel.service.AccountChannelHealthService;
import com.chua.starter.account.channel.service.AccountChannelModelService;
import com.chua.starter.account.channel.service.AccountChannelService;
import com.chua.starter.account.web.ApiChannelAuthController;
import com.chua.starter.account.web.ChannelConfigImportController;
import com.chua.starter.account.web.ChatChannelController;
import com.chua.starter.account.web.ImageChannelController;
import com.chua.starter.account.web.Model3DChannelController;
import com.chua.starter.account.web.VideoChannelController;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import tools.jackson.core.StreamReadConstraints;

/**
 * 账号与渠道模块自动配置。
 *
 * <p>宿主引入 {@code spring-support-account-starter} 即获得：</p>
 * <ul>
 *   <li>渠道管理：渠道 / 渠道模型 / 渠道连接 / 渠道健康四张表的读写与
 *       {@code /api/channels} 管理接口（绑定、轮询、刷新、签到、模型开关）</li>
 *   <li>免登录 ChatClient：{@code /api/channels/{channel}/chat}、文生图、文生视频、图生 3D</li>
 *   <li>账号管理：统一账号的增删改查、密钥加密、归属隔离与引用校验</li>
 * </ul>
 *
 * <p><b>表口径</b>：渠道四表统一按账号模块命名（{@code sys_account_channel*}），
 * 不再持有任何 {@code sys_ai_provider*} / {@code sys_ai_channel_health}。
 * 仅消息模板沿用历史表名 {@code sys_ai_channel_message_template}（与本模块一同归于账号侧，
 * 改名会牵动独立迁移，不在本次范围内）。</p>
 *
 * <p><b>依赖方向</b>：本模块不依赖 AI 模块。渠道数据的变更通过
 * {@code ChannelCatalogChangedEvent} 广播，AI 侧监听后失效路由缓存、复位探针状态；
 * 反向 AI 拿渠道能力只能经 SPI（{@code ChatChannelProvider}、{@code AccountChannelAuthManager}）。</p>
 *
 * <p>扫描口径按类白名单固定，避免宿主 classpath 上还有兄弟模块时装配结果漂移。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
@Slf4j
@AutoConfiguration
@ComponentScan(
        basePackages = {
                // 免登录对话 / 文生图渠道：/api/channels/{channel}/chat、/images
                "com.chua.starter.account.web",
                // 短信 / 邮件消息模板：/api/channels/templates
                "com.chua.starter.account.template",
                // 统一账号管理：/api/accounts
                "com.chua.starter.account.manage",
                // 渠道业务：service / migrator 不在 web / manage 包下，
                // 不加这条 includeFilters 里的类根本扫不到（宿主会缺 AccountChannelService 这个 bean）
                "com.chua.starter.account.channel",
                // 收 / 发信箱：/api/accounts/{accountId}/mailbox
                "com.chua.starter.account.mail",
                // 统一发送日志：/api/accounts/send-logs
                "com.chua.starter.account.sendlog",
                // 临时手机号：领取 / 列表 / 销毁（/api/accounts/temp-phone）
                "com.chua.starter.account.phone",
                // 后台定时任务：短信目录轮询 / 新邮件轮询（见本包 README）
                "com.chua.starter.account.schedule"
        },
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        ApiChannelAuthController.class,
                        ChannelConnectionSchemaMigrator.class,
                        // 免登录生成模型物化器：启动时把渠道 SPI 的内置模型（图/视频等全部任务类型）写进模型管理
                        KeylessGenerateModelMaterializer.class,
                        AccountChannelService.class,
                        AccountChannelModelService.class,
                        AccountChannelConnectionService.class,
                        // 渠道健康记录：ai 侧 ChannelHealthServiceImpl 以构造注入依赖它，
                        // 白名单漏登记会让宿主启动直接失败（Parameter 0 ... could not be found）
                        AccountChannelHealthService.class,
                        ChatChannelController.class,
                        ApiChannelAuthController.class,
                        // 从配置导入供应商：POST /api/providers/import/parse
                        ChannelConfigImportController.class,
                        ImageChannelController.class,
                        VideoChannelController.class,
                        Model3DChannelController.class,
                        MessageTemplateController.class,
                        MessageTemplateService.class,
                        AccountController.class,
                        AccountAppKeyController.class,
                        AccountCookieController.class,
                        AccountCreditConfigController.class,
                        AccountSecretController.class,
                        AccountMockDraftController.class,
                        ManageAccountServiceImpl.class,
                        SysAccountAppKeyServiceImpl.class,
                        SysAccountCookieServiceImpl.class,
                        SysAccountCreditConfigServiceImpl.class,
                        SysAccountSecretServiceImpl.class,
                        AccountSecretCodec.class,
                        AccountApiExceptionAdvice.class,
                        MailboxController.class,
                        MailboxService.class,
                        SmsTemplateSyncController.class,
                        SmsTemplateSyncService.class,
                        // 统一发送日志：/api/accounts/send-logs
                        SendLogController.class,
                        SendLogService.class,
                        TempMailController.class,
                        TempMailService.class,
                        GuerrillaMailService.class,
                        TempMailIoService.class,
                        TempPhoneController.class,
                        TempPhoneService.class,
                        TempPhonePoolService.class,
                        // 短信目录轮询：@ConditionalOnProperty 门控，默认不装配
                        SmsDirectoryPoller.class,
                        // 账号页统一 SSE 事件推送（manage.event 归 manage 包扫描覆盖）
                        AccountEventController.class,
                        AccountEventSseService.class,
                        // 新邮件轮询 / Socket 推送：@ConditionalOnClass + @ConditionalOnProperty 门控
                        MailNewMessagePoller.class,
                        SocketNewMailPublisher.class
                })
)
@MapperScan({
        // 短信 / 邮件消息模板持久化
        "com.chua.starter.account.template.mapper",
        // 统一账号持久化
        "com.chua.starter.account.manage.mapper",
        // 渠道管理：渠道开关与渠道模型开关
        "com.chua.starter.account.channel.mapper",
        // 统一发送日志持久化
        "com.chua.starter.account.sendlog.mapper"
})
public class AccountAutoConfiguration {

    /**
     * 构造方法。
     *
     * <p>启动日志留下可检索锚点，便于排查「账号/渠道能力是否被装配」。</p>
     */
    public AccountAutoConfiguration() {
        log.info("[account] 账号与渠道模块已装配：渠道管理 /api/channels（渠道/模型/连接/健康）、"
                + "账号管理 /api/accounts、免登录对话 /api/channels/{channel}/chat、"
                + "文生图 /api/channels/{channel}/images、文生视频 /api/channels/{channel}/videos、"
                + "图生 3D /api/channels/{channel}/3d-models、消息模板 /api/channels/templates");
    }

    /**
     * 放宽 JSON 流读取的字符串长度上限，适配「从配置导入供应商」的 base64 大文件：
     * 端点允许 128MB 字节（base64 约 1.7 亿字符），超过 Jackson 默认 2000 万字符上限
     * （真实 cc-switch 库 ~15MB 即触发 500）。仅放大 maxStringLength，其余约束保持默认。
     */
    @Bean
    public JsonFactoryBuilderCustomizer channelImportStreamReadConstraints() {
        return factoryBuilder -> factoryBuilder.streamReadConstraints(
                StreamReadConstraints.builder().maxStringLength(200_000_000).build());
    }
}
