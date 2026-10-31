package eduai.credential;

import com.chua.osgi.support.source.SourceContextHolder;
import com.chua.osgi.support.source.SourceSpi;
import com.chua.starter.eduai.dimension.mapper.EduStudentMapper;
import com.chua.starter.eduai.support.credential.StudentCredentialProvider;
import com.chua.starter.oauth.client.support.credential.CredentialProvider;

/**
 * 教育域 SPI 扩展点声明（源码形式）。
 *
 * <p><b>为什么放在源码目录而不是 jar 里</b>：学生凭据校验规则是会频繁调整的业务逻辑
 * （加验证码、换口令策略、接家校通），每次调整都要重新构建 600MB+ 的 fat jar。
 * 放进 {@code osgi-source} 后，改完这个文件即生效，不必重新打包部署。</p>
 *
 * <p><b>字段即扩展点</b>：字段名 {@code student} 即注册名，与登录请求里的
 * {@code accountType=student} 对应；字段静态类型即 SPI 类型。</p>
 *
 * <p><b>Mapper 来自容器</b>：源码产物不在 Spring 容器内，拿不到注入的 Bean，
 * 故通过 {@link SourceContextHolder} 取容器已就绪的单例——数据访问仍走应用既有的
 * 数据源与事务，不额外开连接。</p>
 *
 * <p><b>降级</b>：容器 Bean 尚未就绪时该字段为 {@code null}，源码编译器会跳过并告警，
 * 登录回落到 oauth 内置逻辑，而不是抛异常把整个登录面打挂。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@SourceSpi
public class EduCredentialExtensions {

    /**
     * 学生凭据校验：支持学号或家长手机号 + 口令登录。
     *
     * <p>取值失败（容器 Bean 不可用）时为 {@code null}，登录自动回落内置实现。</p>
     */
    public final CredentialProvider student = buildStudentProvider();

    /**
     * 构造学生凭据实现。
     *
     * @return 实例；容器 Bean 不可用时返回 {@code null}
     */
    private static CredentialProvider buildStudentProvider() {
        var mapper = SourceContextHolder.get(EduStudentMapper.class);
        if (mapper == null) {
            return null;
        }
        return new StudentCredentialProvider(mapper);
    }
}
