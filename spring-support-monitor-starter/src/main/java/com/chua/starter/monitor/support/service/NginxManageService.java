package com.chua.starter.monitor.support.service;

import com.chua.starter.monitor.support.model.NginxAccessTopVO;
import com.chua.starter.monitor.support.model.NginxApplyResultVO;
import com.chua.starter.monitor.support.model.NginxBackupVO;
import com.chua.starter.monitor.support.model.NginxCommandResultVO;
import com.chua.starter.monitor.support.model.NginxConfigFileVO;
import com.chua.starter.monitor.support.model.NginxConfigParseVO;
import com.chua.starter.monitor.support.model.NginxDirectiveMetaVO;
import com.chua.starter.monitor.support.model.NginxLogChunkVO;
import com.chua.starter.monitor.support.model.NginxLogFileVO;
import com.chua.starter.monitor.support.model.NginxOverviewVO;
import com.chua.starter.monitor.support.model.NginxSiteVO;
import com.chua.starter.monitor.support.model.NginxStatusVO;
import com.chua.starter.monitor.support.model.NginxTrendVO;
import com.chua.starter.monitor.support.model.NginxTemplateVO;
import com.chua.starter.monitor.support.model.NginxVersionVO;
import com.chua.starter.monitor.support.model.dto.NginxApplyDTO;
import com.chua.starter.monitor.support.model.dto.NginxSaveConfigDTO;

import java.util.List;

/**
 * Nginx 管理服务：经 SSH 在目标主机执行 nginx 命令、读写配置、查看日志与状态。
 *
 * <p>数据源字段口径：{@code host}/{@code port}/{@code username}/{@code password} 用于 SSH 连接
 * （端口缺省 22），{@code database} 存 nginx 配置根路径（缺省 /etc/nginx）。</p>
 *
 * <p><strong>写操作一律先备份再落盘</strong>，落盘后可选立即语法检查与重载，
 * 每一步结果都在 {@link NginxApplyResultVO} 里如实返回，不静默吞掉失败。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface NginxManageService {

    /**
     * 汇总总览：运行状态、版本、连接数、配置与日志概况、巡检提示。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 总览
     */
    NginxOverviewVO overview(Long datasourceId);

    /**
     * 列出配置根目录下递归的 *.conf 文件。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 配置文件列表
     */
    List<NginxConfigFileVO> listFiles(Long datasourceId);

    /**
     * 读取配置文件内容。
     *
     * @param datasourceId 数据源 ID，可为空
     * @param path         配置文件绝对路径
     * @return 文件内容
     */
    String readConfig(Long datasourceId, String path);

    /**
     * 把配置文件解析成无损语法树，供 UI 模式渲染表单。
     *
     * @param datasourceId 数据源 ID，可为空
     * @param path         配置文件绝对路径
     * @return 解析结果
     */
    NginxConfigParseVO parseConfig(Long datasourceId, String path);

    /**
     * 以文本模式保存配置文件（自动备份，可选校验与重载）。
     *
     * @param request 保存请求
     * @return 执行结果
     */
    NginxApplyResultVO saveConfig(NginxSaveConfigDTO request);

    /**
     * 保存配置文件：文件模式整篇覆盖，UI 模式按补丁增量应用。
     *
     * @param request 保存请求
     * @return 执行结果，含落盘、备份、校验、重载每一步的结果
     */
    NginxApplyResultVO applyConfig(NginxApplyDTO request);

    /**
     * 取指令元数据，供表单渲染控件。
     *
     * @param scope 作用域标识，为空返回全部
     * @return 指令元数据列表
     */
    List<NginxDirectiveMetaVO> directives(String scope);

    /**
     * 按站点维度汇总所有 server 块。
     *
     * <p>遍历配置根下全部 .conf，把散落在各文件里的 server 块抽平成一张清单，
     * 并跨文件检测「同一域名 + 同一端口」重复定义——这类冲突在按文件查看时看不出来，
     * 却会导致其中一个站点被 nginx 静默忽略。</p>
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 站点清单；某个文件读取或解析失败时跳过该文件并在站点级不影响其它结果
     */
    List<NginxSiteVO> sites(Long datasourceId);

    /**
     * 取配置模板库。
     *
     * @return 模板列表
     */
    List<NginxTemplateVO> templates();

    /**
     * 执行 nginx -t 语法检查。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 检查结果
     */
    NginxCommandResultVO test(Long datasourceId);

    /**
     * 重载 nginx（nginx -s reload，先语法检查）。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 执行结果
     */
    NginxCommandResultVO reload(Long datasourceId);

    /**
     * 重启 nginx：先语法检查，再优雅退出（quit）等待进程结束，最后重新拉起。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 执行结果
     */
    NginxCommandResultVO restart(Long datasourceId);

    /**
     * 停止 nginx（nginx -s stop）。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 执行结果
     */
    NginxCommandResultVO stop(Long datasourceId);

    /**
     * 启动 nginx（未运行时才执行；已在运行则直接返回提示，不重复拉起）。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 执行结果
     */
    NginxCommandResultVO start(Long datasourceId);

    /**
     * 读取 nginx -V 版本与编译参数。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 版本信息
     */
    NginxVersionVO version(Long datasourceId);

    /**
     * 读取 stub_status 实时状态。
     *
     * <p>依次尝试：系统配置里显式指定的 URL、配置里出现过的监听端口、默认 80 端口。
     * 全部失败时返回 {@code available=false} 并说明试过哪些地址与失败原因。</p>
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 状态信息
     */
    NginxStatusVO status(Long datasourceId);

    /**
     * 列出日志文件。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 日志文件列表
     */
    List<NginxLogFileVO> listLogFiles(Long datasourceId);

    /**
     * 取日志文件尾部 N 行（全量）。
     *
     * @param datasourceId 数据源 ID，可为空
     * @param path         日志文件路径，为空用默认 access 日志
     * @param lines        尾部行数，缺省 200
     * @return 日志文本
     */
    String log(Long datasourceId, String path, Integer lines);

    /**
     * 增量读取日志：只返回游标之后的新内容。
     *
     * <p>文件被轮转（当前大小小于游标）时自动降级为全量尾部并置 {@code rotated}，
     * 前端据此清空本地累积内容。</p>
     *
     * @param datasourceId 数据源 ID，可为空
     * @param path         日志文件路径，为空用默认 access 日志
     * @param lines        首次读取的尾部行数；增量读取时忽略
     * @param cursor       上次读到的字节位置，首次传 null
     * @return 增量结果
     */
    NginxLogChunkVO logChunk(Long datasourceId, String path, Integer lines, Long cursor);

    /**
     * 备份指定配置文件到备份目录。
     *
     * @param datasourceId 数据源 ID，可为空
     * @param path         配置文件绝对路径
     * @param reason       备份原因
     * @return 备份条目
     */
    NginxBackupVO backup(Long datasourceId, String path, String reason);

    /**
     * 列出备份。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 备份列表，倒序（最新在前）
     */
    List<NginxBackupVO> listBackups(Long datasourceId);

    /**
     * 从备份恢复配置文件（恢复前再备份一次当前内容）。
     *
     * @param datasourceId 数据源 ID，可为空
     * @param backupName   备份文件名
     * @param targetPath   还原到的目标路径；为空时按备份文件名反推原始路径。
     *                      备份名把 {@code /} 换成了 {@code _}，原始路径含下划线时无法唯一反推，
     *                      此时必须显式指定 targetPath，否则本方法返回失败而不是写到错误位置
     * @return 执行结果
     */
    NginxCommandResultVO restore(Long datasourceId, String backupName, String targetPath);

    /**
     * 采集一次 stub_status 并落库。
     *
     * <p>取不到数据（{@code available=false}）时静默跳过，不写脏数据，
     * 也不抛异常打断其它数据源的采集。</p>
     *
     * @param datasourceId 数据源 ID
     * @return 本次写入的指标条数
     */
    int collectMetric(Long datasourceId);

    /**
     * 查询指标趋势。
     *
     * @param datasourceId 数据源 ID
     * @param hours       回溯小时数，缺省 24
     * @param interval    采样间隔（分钟），缺省 30；超出数据量时后端会放大
     * @return 趋势数据
     */
    NginxTrendVO trend(Long datasourceId, Integer hours, Integer interval);

    /**
     * 列出全部 nginx 类型的数据源 id。
     *
     * <p>供定时采集调度器使用；没有登记时返回空列表而不是抛异常。</p>
     *
     * @return 数据源 id 列表
     */
    List<Long> listNginxDatasourceIds();

    /**
     * 访问日志 Top 统计（路径 / 客户端 IP / 状态码分布）。
     *
     * <p>拉取 access log 尾部后在应用内解析，不入库——日志行数不可控，入库会吃磁盘。</p>
     *
     * @param datasourceId 数据源 ID
     * @param path         日志文件路径，缺省 access 日志
     * @param lines        扫描的尾部行数，缺省 2000，最大 20000
     * @param minutes      只统计最近 N 分钟内的行，缺省不限（0）
     * @param topN         每个维度取前几，缺省 10
     * @return Top 统计
     */
    NginxAccessTopVO accessTop(Long datasourceId, String path, Integer lines, Integer minutes, Integer topN);
}
