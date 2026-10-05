package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.mapper.SysAiSshHostMapper;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 远程主机（SSH 主机簿）持久化存储服务。
 *
 * <p>基于 MyBatis-Plus 持久化到 {@code sys_ai_ssh_host} 表，替代原先的进程内内存实现。
 * 密钥字段在写入时以掩码 {@code "***"} 落库（连接测试时再解密使用），
 * 查询返回恒脱敏，避免明文凭据出现在响应与日志中。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
public class SysAiSshHostStore {

    /**
     * 主机表 映射器：提供主机增删改查
     */
    private final SysAiSshHostMapper sshHostMapper;

    /**
     * 构造注入 映射器。
     *
     * @param sshHostMapper 主机 映射器
     */
    public SysAiSshHostStore(SysAiSshHostMapper sshHostMapper) {
        this.sshHostMapper = sshHostMapper;
    }

    /**
     * 生成主机主键（uuidv7 字符串）。
     *
     * @return 36 位 uuidv7 字符串
     */
    public String newId() {
        return generateUuidV7();
    }

    /**
     * 列出全部主机（按创建时间升序，与存储顺序一致）。
     *
     * @return 主机列表
     */
    public List<SysAiSshHost> listAll() {
        // 凭据仅归属者可见（含管理员）
        LambdaQueryWrapper<SysAiSshHost> wrapper = new LambdaQueryWrapper<SysAiSshHost>()
                .eq(!AiUserContext.canAccessOthersData(), SysAiSshHost::getUserId, AiUserContext.requireUserId())
                .orderByAsc(SysAiSshHost::getCreatedAt);
        return sshHostMapper.selectList(wrapper);
    }

    /**
     * 获取主机（不存在抛 NOT_FOUND）。
     *
     * @param sshHostId 主机 标识
     * @return 主机
     */
    public SysAiSshHost get(String sshHostId) {
        SysAiSshHost host = sshHostMapper.selectById(sshHostId);
        if (host == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "SSH 主机不存在: " + sshHostId);
        }
        if (!AiUserContext.canAccessOthersData() && !AiUserContext.requireUserId().equals(host.getUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权访问他人数据");
        }
        return host;
    }

    /**
     * 写出口主机读取：仅归属者可改删，只读账号拒绝。
     *
     * @param sshHostId 主机 标识
     * @return 主机
     */
    public SysAiSshHost requireWritable(String sshHostId) {
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
        SysAiSshHost host = sshHostMapper.selectById(sshHostId);
        if (host == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "SSH 主机不存在: " + sshHostId);
        }
        if (!AiUserContext.requireUserId().equals(host.getUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "管理员对他人数据只读，不可修改或删除");
        }
        return host;
    }

    /**
     * 新增主机并落库。
     *
     * @param host 主机（须已填充主键与时间戳）
     * @return 创建的主机
     */
    public SysAiSshHost create(SysAiSshHost host) {
        // 归属由服务端按当前登录用户写入，客户端传值一律覆盖
        host.setUserId(AiUserContext.requireUserId());
        sshHostMapper.insert(host);
        return host;
    }

    /**
     * 部分更新主机（仅覆盖非 空 字段；密钥字段以掩码写回）。
     *
     * @param sshHostId 主机 标识
     * @param updates   更新内容（非 空 字段生效）
     * @return 更新后的主机
     */
    public SysAiSshHost update(String sshHostId, SysAiSshHost updates) {
        SysAiSshHost current = requireWritable(sshHostId);
        if (updates.getName() != null) {
            current.setName(updates.getName());
        }
        if (updates.getHost() != null) {
            current.setHost(updates.getHost());
        }
        if (updates.getPort() != null) {
            current.setPort(updates.getPort());
        }
        if (updates.getUsername() != null) {
            current.setUsername(updates.getUsername());
        }
        if (updates.getAuthType() != null) {
            current.setAuthType(updates.getAuthType());
        }
        applySecret(current, updates.getPassword(), current::setPassword);
        applySecret(current, updates.getPrivateKey(), current::setPrivateKey);
        applySecret(current, updates.getPassphrase(), current::setPassphrase);
        applySecret(current, updates.getCertificate(), current::setCertificate);
        applySecret(current, updates.getSudoPassword(), current::setSudoPassword);
        current.setUpdatedAt(System.currentTimeMillis());
        sshHostMapper.updateById(current);
        return current;
    }

    /**
     * 删除主机。
     *
     * @param sshHostId 主机 标识
     */
    public void delete(String sshHostId) {
        requireWritable(sshHostId);
        sshHostMapper.deleteById(sshHostId);
    }

    /**
     * 密钥写回：仅当请求携带非空值且非掩码哨兵（{@code "***"}）时覆盖。
     *
     * <p>前端对未修改的密钥用掩码哨兵表示（不重发），收到哨兵即视为保留原值；
     * 空 表示不修改；空串表示显式清空。</p>
     *
     * @param host    当前主机
     * @param secret  请求中的密钥值
     * @param setter  密钥字段设置器
     */
    private void applySecret(SysAiSshHost host, String secret,
                             java.util.function.Consumer<String> setter) {
        if (secret == null || MASKED_SECRET.equals(secret)) {
            return;
        }
        setter.accept(secret.isEmpty() ? null : secret);
    }

    /**
     * 前端掩码哨兵（表示"未修改，保留原值"）
     */
    private static final String MASKED_SECRET = "***";

    /**
     * 生成 uuidv7：前 48 位为毫秒时间戳，随后填充版本/变体位与随机位。
     *
     * @return 36 位 uuidv7 字符串
     */
    private String generateUuidV7() {
        long millis = System.currentTimeMillis();
        byte[] bytes = new byte[16];
        java.util.concurrent.ThreadLocalRandom.current().nextBytes(bytes);
        bytes[0] = (byte) (millis >>> 40);
        bytes[1] = (byte) (millis >>> 32);
        bytes[2] = (byte) (millis >>> 24);
        bytes[3] = (byte) (millis >>> 16);
        bytes[4] = (byte) (millis >>> 8);
        bytes[5] = (byte) millis;
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder sb = new StringBuilder(36);
        for (int i = 0; i < 16; i++) {
            if (i == 4 || i == 6 || i == 8 || i == 10) {
                sb.append('-');
            }
            sb.append(hex[(bytes[i] >> 4) & 0xF]).append(hex[bytes[i] & 0xF]);
        }
        return sb.toString();
    }
}
