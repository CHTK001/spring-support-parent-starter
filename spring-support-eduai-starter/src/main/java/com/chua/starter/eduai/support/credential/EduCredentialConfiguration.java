package com.chua.starter.eduai.support.credential;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.eduai.dimension.mapper.EduStudentMapper;
import com.chua.starter.eduai.dimension.mapper.EduTeacherMapper;
import com.chua.starter.oauth.client.support.credential.CredentialProvider;
import com.chua.starter.system.handler.LoginHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把教育域的凭据校验与登录处理器注册进 oauth 的扩展点。
 *
 * <p>两类扩展点分工不同，缺一不可：
 * <ul>
 *     <li>{@link CredentialProvider}（注册名 {@code student} / {@code teacher}）负责「怎么校验」
 *     ——按学号 / 工号反查档案、比对口令、签发带对应角色的令牌；</li>
 *     <li>{@link LoginHandler}（注册名 {@code STUDENT} / {@code TEACHER}）负责「登录分发口认这个
 *     账号来源」——{@code POST /v2/user/login} 先按 {@code loginType-accountType} 取处理器，
 *     取不到会直接抛空指针（对外固定 500），连口令校验都到不了。</li>
 * </ul></p>
 *
 * <p><b>为什么必须用显式注册 + 资源索引，而不能只靠 {@code @Spi} 注解</b>：eduai 位于另一个模块、
 * 另一个包，默认的两个扫描器
 * （{@code SamePackageServiceResolver} 只扫接口自身所在包、{@code ServiceLoaderServiceResolver}
 * 只认 {@code META-INF/services}）都扫不到它；可用的路只有显式
 * {@link ServiceProvider#register} 与 {@code META-INF/extensions} 索引
 * （由 {@code CustomServiceResolver} 在 provider 构造期读取）。两条都写上：
 * 前者让「谁提供登录」在代码里一眼可见，后者保证 provider 实例被回收重建后注册不丢
 * （{@code ServiceProvider.CACHE} 是 SOFT 引用，见 {@link AccountTypeLoginHandler} 类注释）。</p>
 *
 * <p>注册名 {@code student} / {@code teacher} 与登录请求里的 {@code accountType} 对应；
 * oauth 侧解析顺序是「自定义同名实现优先、内置 {@code builtin-*} 实现其次」，
 * 因此不会与内置实现互相覆盖。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Configuration
public class EduCredentialConfiguration {

    /**
     * 注册学生凭据校验实现。
     *
     * @param studentMapper 学生档案 Mapper，用于按学号 / 家长手机号反查
     * @return 已注册的凭据实现，同时交由 Spring 管理其生命周期
     */
    @Bean
    public CredentialProvider studentCredentialProvider(EduStudentMapper studentMapper) {
        var provider = new StudentCredentialProvider(studentMapper);
        ServiceProvider.of(CredentialProvider.class).register("student", provider);
        // 同时绑定到登录处理器的静态表：SPI 缓存是 SOFT 引用，内存吃紧时会被回收重建，
        // 那时注册会丢；静态绑定让登录请求不依赖那个缓存（详见 AccountTypeLoginHandler 类注释）。
        AccountTypeLoginHandler.bind("student", provider);
        log.info("[EduCredential] 已注册学生凭据校验实现 - 注册名: student");
        return provider;
    }

    /**
     * 注册教师凭据校验实现（工号 + 口令）。
     *
     * @param teacherMapper 教师档案 Mapper，用于按工号反查
     * @return 已注册的凭据实现，同时交由 Spring 管理其生命周期
     */
    @Bean
    public CredentialProvider teacherCredentialProvider(EduTeacherMapper teacherMapper) {
        var provider = new TeacherCredentialProvider(teacherMapper);
        ServiceProvider.of(CredentialProvider.class).register("teacher", provider);
        AccountTypeLoginHandler.bind("teacher", provider);
        log.info("[EduCredential] 已注册教师凭据校验实现 - 注册名: teacher");
        return provider;
    }

    /**
     * 注册学生登录处理器（oauth {@link LoginHandler} 扩展点，注册名 {@code STUDENT}）。
     *
     * @return 学生登录处理器，同时交由 Spring 管理其生命周期
     */
    @Bean
    public LoginHandler studentLoginHandler() {
        registerLoginHandler("STUDENT", StudentLoginHandler.class);
        return new StudentLoginHandler();
    }

    /**
     * 注册教师登录处理器（oauth {@link LoginHandler} 扩展点，注册名 {@code TEACHER}）。
     *
     * @return 教师登录处理器，同时交由 Spring 管理其生命周期
     */
    @Bean
    public LoginHandler teacherLoginHandler() {
        registerLoginHandler("TEACHER", TeacherLoginHandler.class);
        return new TeacherLoginHandler();
    }

    /**
     * 把「按账号来源登录」的实现登记进 oauth 的 {@link LoginHandler} 扩展点。
     *
     * <p><b>注册名写大写</b>：SPI 的注册与查找都会 {@code toUpperCase()}、名字匹配用
     * {@code equalsIgnoreCase}，所以注册 {@code TEACHER} / {@code STUDENT} 与请求组合出的
     * {@code teacher} / {@code student} 等价命中。</p>
     *
     * <p><b>注册的是类而不是实例</b>：登录口走 {@code getNewExtension(...)}，它按 {@code implClass}
     * 的构造器<b>反射新建</b>，不返回 {@code register(name, obj)} 预置的实例；因此处理器必须无参可构造。</p>
     *
     * <p><b>实参必须声明成 {@code Class<LoginHandler>}</b>：{@code register} 有
     * {@code (String, Object)} 与 {@code (String, Class<T>)} 两个重载，泛型不变性会让
     * {@code TeacherLoginHandler.class}（{@code Class<TeacherLoginHandler>}）落不到后者，
     * 被当成普通对象传进 {@code (String, Object)}——那样 {@code setImplClass(ref.getClass())}
     * 得到的是 {@code java.lang.Class}，反射建不出实例（实测报
     * 「无参兜底实例化失败（该类无可用无参构造器）… java.lang.Class」）。</p>
     *
     * @param name 扩展名（大写，如 {@code TEACHER}）
     * @param impl 处理器实现类
     */
    private static void registerLoginHandler(String name, Class<? extends LoginHandler> impl) {
        @SuppressWarnings("unchecked")
        Class<LoginHandler> serviceImpl = (Class<LoginHandler>) (Class<?>) impl;
        ServiceProvider.of(LoginHandler.class).register(name, serviceImpl);
        log.info("[EduCredential] 已注册登录处理器 - 注册名: {} -> {}", name, impl.getSimpleName());
    }
}
