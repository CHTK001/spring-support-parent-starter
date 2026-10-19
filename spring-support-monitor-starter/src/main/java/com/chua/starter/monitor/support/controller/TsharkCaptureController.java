package com.chua.starter.monitor.support.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsTsharkPacket;
import com.chua.starter.monitor.support.entity.MsTsharkSession;
import com.chua.starter.monitor.support.model.TsharkFlowVO;
import com.chua.starter.monitor.support.model.TsharkInterfaceVO;
import com.chua.starter.monitor.support.model.TsharkHandshakeVO;
import com.chua.starter.monitor.support.model.TsharkProtocolStatVO;
import com.chua.starter.monitor.support.model.dto.TsharkCaptureStartDTO;
import com.chua.starter.monitor.support.service.impl.TsharkCaptureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 网络抓包接口
 *
 * @author CH
 * @since 4.0.0
 */
@Tag(name = "网络抓包", description = "网卡抓包会话/数据包/统计")
@RestController
@RequestMapping("/ms/monitor/tshark")
public class TsharkCaptureController {

    private final TsharkCaptureService tsharkCaptureService; // tshark抓包服务

    /**
     * 构造器，注入 tshark 抓包服务
     *
     * @param tsharkCaptureService tshark 抓包服务
     */
    public TsharkCaptureController(TsharkCaptureService tsharkCaptureService) {
        this.tsharkCaptureService = tsharkCaptureService;
    }

    /**
     * 检查 tshark 可用性
     *
     * @return tshark 是否可用
     */
    @Operation(summary = "检查 tshark 可用性")
    @GetMapping("/check")
    public ReturnResult<Boolean> check() {
        return this.tsharkCaptureService.checkTshark();
    }

    /**
     * 列出可用网卡
     *
     * <p>带友好名与本机 IPv4，并把承载默认出口的那张排在最前 —— Windows 上 tshark 只给
     * {@code \Device\NPF_{GUID}}，不加工的话页面上是一串读不懂的 GUID。</p>
     *
     * @return 网卡列表（设备名 + 友好名 + IPv4 + 是否本机出口）
     */
    @Operation(summary = "列出可用网卡")
    @GetMapping("/interfaces")
    public ReturnResult<List<TsharkInterfaceVO>> interfaces() {
        return this.tsharkCaptureService.listInterfaces();
    }

    /**
     * 启动抓包会话
     *
     * @param body 抓包请求（name/captureInterface/captureFilter/filterType/count/timeout）
     * @return 新建的抓包会话
     */
    @Operation(summary = "启动抓包会话")
    @PostMapping("/capture")
    public ReturnResult<MsTsharkSession> start(@RequestBody TsharkCaptureStartDTO body) {
        return this.tsharkCaptureService.startCapture(body.name(), body.captureInterface(),
                body.captureFilter(), body.filterType(), body.count(), body.timeout(), body.maxSizeMb());
    }

    /**
     * 停止抓包会话
     *
     * @param id 会话 ID
     * @return 操作结果
     */
    @Operation(summary = "停止抓包会话")
    @PostMapping("/capture/{id}/stop")
    public ReturnResult<Void> stop(@PathVariable Long id) {
        return this.tsharkCaptureService.stopCapture(id);
    }

    /**
     * 导入 pcap 文件（PCAP 分析）
     *
     * <p>上传的 pcap 落到抓包目录、以 {@code source=import} 建一条会话，随后在后台用
     * {@code tshark -r} 整份解析入库。解析出的报文因此复用同一套接口（分页 / 详情 /
     * 协议分布 / Top 流），原始文件用 {@link #pcap(Long)} 下回去。</p>
     *
     * <p>返回时解析通常还没结束（会话状态 1 = 解析中），包数在后台累加并通过会话推送
     * 抵达前端，页面据此显示进度。</p>
     *
     * @param file pcap 文件（multipart 字段名 {@code file}）
     * @param name 会话名（可空，默认取上传的原始文件名）
     * @return 新建的会话
     */
    @Operation(summary = "导入 pcap 文件")
    @PostMapping("/pcap/import")
    public ReturnResult<MsTsharkSession> importPcap(@RequestParam("file") MultipartFile file,
                                                    @RequestParam(required = false) String name) {
        return this.tsharkCaptureService.importPcap(name, file);
    }

    /**
     * 删除抓包会话（含数据包）
     *
     * @param id 会话 ID
     * @return 操作结果
     */
    @Operation(summary = "删除抓包会话(含数据包)")
    @DeleteMapping("/sessions/{id}")
    public ReturnResult<Void> delete(@PathVariable Long id) {
        return this.tsharkCaptureService.deleteSession(id);
    }

    /**
     * 抓包会话分页列表
     *
     * @param page 页码
     * @param size 每页大小
     * @param source 来源过滤（live 实时抓包 / import 导入 pcap，可空表示不过滤）
     * @return 分页结果
     */
    @Operation(summary = "抓包会话分页列表")
    @GetMapping("/sessions")
    public ReturnResult<IPage<MsTsharkSession>> sessions(@RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size,
                                                          @RequestParam(required = false) String source) {
        return this.tsharkCaptureService.pageSessions(page, size, source);
    }

    /**
     * 数据包分页列表
     *
     * @param id 会话 ID
     * @param page 页码
     * @param size 每页大小
     * @param displayFilter 显示过滤表达式（可空），如 {@code http}、{@code ip.addr == 1.2.3.4}、
     *                      {@code tcp.port == 80 and http.request.method == "GET"}；
     *                      不认识的裸词退化为关键字匹配
     * @param keyword 旧版关键字参数，displayFilter 为空时兼容使用（可空）
     * @return 分页结果
     */
    @Operation(summary = "数据包分页列表")
    @GetMapping("/sessions/{id}/packets")
    public ReturnResult<IPage<MsTsharkPacket>> packets(@PathVariable Long id,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        @RequestParam(required = false) String displayFilter,
                                                        @RequestParam(required = false) String keyword) {
        String filter = displayFilter != null && !displayFilter.isBlank() ? displayFilter : keyword;
        return this.tsharkCaptureService.pagePackets(id, page, size, filter);
    }

    /**
     * 还原某个包所属 TCP 流的握手 / 关闭生命周期
     *
     * <p>抓包页在包上右键 → 「查看生命周期」就调这里，返回该包的整条流按时序排好的
     * 报文序列与握手判定（三次 / 四次 / 未完成），前端据此画时序图。</p>
     *
     * <p>HTTP / TLS / SSH 等跑在 TCP 上的协议同样支持：数据帧的协议与握手帧的协议
     * 不同，匹配只按四元组，不按协议列过滤。</p>
     *
     * @param packetId 数据包 ID
     * @param limit    返回的最大步骤数（默认 600，超出只保留首尾与锚点包）
     * @return 生命周期；包不存在或协议不跑在 TCP 上时 404
     */
    @Operation(summary = "查看 TCP 握手生命周期")
    @GetMapping("/packets/{packetId}/handshake")
    public ResponseEntity<TsharkHandshakeVO> handshake(@PathVariable Long packetId,
                                                       @RequestParam(required = false) Integer limit) {
        TsharkHandshakeVO vo = this.tsharkCaptureService.handshakeOf(packetId, limit);
        return vo == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(vo);
    }

    /**
     * 订阅数据包的实时推送（SSE）
     *
     * <p>建连即回 {@code ready}，之后：每入库一个包推一个 {@code packet}，
     * pcap 解析收尾推一个 {@code session}（前端据此重拉协议分布与 Top 流）。
     * 另有 20s 一次的注释帧保活，前端不会把它当事件。</p>
     *
     * <p><strong>首屏历史不在这里给</strong>：SSE 只做增量，历史走
     * {@code /sessions/{id}/packets}。否则建连那一刻就要推上百个包。</p>
     *
     * @param id 会话 ID
     * @return SSE 连接；会话不存在时 404
     */
    @Operation(summary = "数据包实时推送（SSE）", description = "按抓包会话分组推送，逐包增量")
    @GetMapping("/sessions/{id}/packets/stream")
    public ResponseEntity<SseEmitter> packetStream(@PathVariable Long id) {
        if (this.tsharkCaptureService.getSession(id) == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(this.tsharkCaptureService.subscribePacketStream(id));
    }

    /**
     * 下载会话的原始 pcap 文件
     *
     * <p>页面上的协议摘要只是 tshark 解析后的结果，要做更深的分析（重组、专家信息、
     * 自定义过滤）得把原始 pcap 拿去 Wireshark 打开，所以提供原件下载。</p>
     *
     * @param id 会话 ID
     * @return pcap 文件流；会话不存在或文件已清理时返回 404
     */
    @Operation(summary = "下载原始 pcap")
    @GetMapping("/sessions/{id}/pcap")
    public ResponseEntity<Resource> pcap(@PathVariable Long id) {
        Resource resource = this.tsharkCaptureService.pcapResource(id);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        String name = this.tsharkCaptureService.pcapFileName(id);
        return ResponseEntity.ok()
                // 文件名含中文，ASCII 段兜底一个 pcap.pcap，避免老客户端拿到乱码名
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"capture.pcap\"; filename*=UTF-8''" + URLEncoder.encode(name, StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * 数据包详情
     *
     * @param id 数据包 ID
     * @return 数据包详情
     */
    @Operation(summary = "数据包详情")
    @GetMapping("/packets/{id}")
    public ReturnResult<MsTsharkPacket> packet(@PathVariable Long id) {
        return this.tsharkCaptureService.getPacket(id);
    }

    /**
     * 协议分布统计
     *
     * @param id 会话 ID
     * @param displayFilter 显示过滤（可空）
     * @return 协议分布列表
     */
    @Operation(summary = "协议分布统计")
    @GetMapping("/sessions/{id}/stats/protocol")
    public ReturnResult<List<TsharkProtocolStatVO>> protocolStats(@PathVariable Long id,
                                                                  @RequestParam(required = false) String displayFilter) {
        return this.tsharkCaptureService.protocolStats(id, displayFilter);
    }

    /**
     * 会话 Top 流统计
     *
     * @param id 会话 ID
     * @param limit 返回条数
     * @param displayFilter 显示过滤（可空）
     * @return Top 流列表
     */
    @Operation(summary = "会话 Top 流统计")
    @GetMapping("/sessions/{id}/stats/top")
    public ReturnResult<List<TsharkFlowVO>> topFlows(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "10") int limit,
                                                     @RequestParam(required = false) String displayFilter) {
        return this.tsharkCaptureService.topFlows(id, limit, displayFilter);
    }

    /**
     * 时间轴统计：按时间桶聚合报文的数量与总字节数。
     *
     * <p>用于前端绘制「报文量随时间变化」的折线图。</p>
     *
     * @param id            会话 ID
     * @param bucketSeconds 桶大小（秒），默认 10
     * @param displayFilter 显示过滤（可空）
     * @return 时间轴数据，格式：[{"t": "2026-10-19 10:00:00", "packets": 123, "bytes": 45678}, ...]
     */
    @Operation(summary = "时间轴统计")
    @GetMapping("/sessions/{id}/stats/timeline")
    public ReturnResult<List<Map<String, Object>>> timeline(
            @PathVariable Long id,
            @RequestParam(defaultValue = "10") Integer bucketSeconds,
            @RequestParam(required = false) String displayFilter) {
        return ReturnResult.success(this.tsharkCaptureService.timeline(id, bucketSeconds, displayFilter));
    }
}
