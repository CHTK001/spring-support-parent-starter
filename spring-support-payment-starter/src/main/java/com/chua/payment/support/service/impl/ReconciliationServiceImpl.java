package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.utils.IdUtils;
import com.chua.payment.support.channel.support.AbstractMerchantPaymentChannel;
import com.chua.payment.support.dto.ReconciliationDiffQueryDTO;
import com.chua.payment.support.dto.ReconciliationStatementQueryDTO;
import com.chua.payment.support.dto.ReconciliationTriggerDTO;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.entity.ReconciliationDiff;
import com.chua.payment.support.entity.ReconciliationStatement;
import com.chua.payment.support.entity.TransactionRecord;
import com.chua.payment.support.enums.ChannelStatus;
import com.chua.payment.support.enums.ReconciliationDiffType;
import com.chua.payment.support.enums.ReconciliationStatus;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.mapper.ReconciliationDiffMapper;
import com.chua.payment.support.mapper.ReconciliationStatementMapper;
import com.chua.payment.support.mapper.TransactionRecordMapper;
import com.chua.payment.support.provider.PaymentProviderGatewayRegistry;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.payment.support.service.ReconciliationService;
import com.chua.payment.support.vo.ReconciliationDiffVO;
import com.chua.payment.support.vo.ReconciliationStatementVO;
import com.chua.starter.aliyun.support.properties.AliyunAlipayProperties;
import com.chua.starter.tencent.support.properties.TencentWechatPayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 渠道对账服务实现：下载微信/支付宝渠道账单，与本地成功支付流水按订单号比对，
 * 幂等登记对账单（pay_reconciliation_statement）与差异明细（pay_reconciliation_diff）。
 *
 * <p>对账口径：</p>
 * <ul>
 *     <li>渠道侧：仅统计交易成功（微信交易状态 SUCCESS / 支付宝收支=收入且订单状态含「成功」）的账单行；</li>
 *     <li>本地侧：{@code pay_transaction_record} 中 transactionType=PAY、status=1、
 *         createdAt 落在账单日 [00:00, 次日 00:00) 的成功支付流水；</li>
 *     <li>差异类型：渠道有本地无（LOCAL_MISSING）、本地有渠道无（CHANNEL_MISSING）、金额不一致（AMOUNT_MISMATCH）；</li>
 *     <li>幂等：同一（商户、渠道、账单日）重复对账时先删除旧差异再重算，不产生重复差异。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@Service
public class ReconciliationServiceImpl extends AbstractMerchantPaymentChannel implements ReconciliationService {

    /**
     * 微信渠道类型
     */
    private static final String CHANNEL_TYPE_WECHAT = "WECHAT";
    /**
     * 支付宝渠道类型
     */
    private static final String CHANNEL_TYPE_ALIPAY = "ALIPAY";
    /**
     * 对账单号前缀
     */
    private static final String STATEMENT_NO_PREFIX = "RCS";
    /**
     * 本地流水支付类型
     */
    private static final String TRANSACTION_TYPE_PAY = "PAY";
    /**
     * 本地流水成功状态
     */
    private static final int TRANSACTION_STATUS_SUCCESS = 1;
    /**
     * 本地流水分批扫描大小
     */
    private static final int LOCAL_SCAN_BATCH_SIZE = 1000;
    /**
     * 失败原因最大长度
     */
    private static final int FAIL_REASON_MAX_LENGTH = 500;
    /**
     * 支付宝账单 GBK 编码
     */
    private static final Charset ALIPAY_CSV_CHARSET = Charset.forName("GBK");

    /**
     * 商户渠道映射器
     */
    private final MerchantChannelMapper merchantChannelMapper;
    /**
     * 交易流水映射器
     */
    private final TransactionRecordMapper transactionRecordMapper;
    /**
     * 对账单映射器
     */
    private final ReconciliationStatementMapper statementMapper;
    /**
     * 对账差异映射器
     */
    private final ReconciliationDiffMapper diffMapper;
    /**
     * 支付渠道网关注册表
     */
    private final PaymentProviderGatewayRegistry providerGatewayRegistry;
    /**
     * HTTP 客户端（账单文件下载，线程安全可复用）
     */
    private final HttpClient httpClient;

    /**
     * 构造渠道对账服务实现。
     *
     * @param merchantChannelService  商户渠道服务
     * @param objectMapper            对象映射器
     * @param merchantChannelMapper   商户渠道映射器
     * @param transactionRecordMapper 交易流水映射器
     * @param statementMapper         对账单映射器
     * @param diffMapper              对账差异映射器
     * @param providerGatewayRegistry 支付渠道网关注册表
     */
    public ReconciliationServiceImpl(MerchantChannelService merchantChannelService,
                                     ObjectMapper objectMapper,
                                     MerchantChannelMapper merchantChannelMapper,
                                     TransactionRecordMapper transactionRecordMapper,
                                     ReconciliationStatementMapper statementMapper,
                                     ReconciliationDiffMapper diffMapper,
                                     PaymentProviderGatewayRegistry providerGatewayRegistry) {
        super(merchantChannelService, objectMapper);
        this.merchantChannelMapper = merchantChannelMapper;
        this.transactionRecordMapper = transactionRecordMapper;
        this.statementMapper = statementMapper;
        this.diffMapper = diffMapper;
        this.providerGatewayRegistry = providerGatewayRegistry;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public List<ReconciliationStatementVO> reconcile(ReconciliationTriggerDTO dto) {
        LocalDate billDate = dto != null && dto.billDate() != null
                ? dto.billDate() : LocalDate.now().minusDays(1);
        List<MerchantChannel> channels = listReconcileChannels(dto);
        if (channels.isEmpty()) {
            throw new PaymentException("未找到可对账的启用渠道");
        }
        List<ReconciliationStatementVO> results = new ArrayList<>(channels.size());
        for (MerchantChannel channel : channels) {
            try {
                results.add(reconcileChannel(channel, billDate));
            } catch (Exception e) {
                log.error("渠道对账失败: merchantId={}, channelType={}, billDate={}",
                        channel.getMerchantId(), channel.getChannelType(), billDate, e);
                results.add(markStatementFailed(channel, billDate, e.getMessage()));
            }
        }
        return results;
    }

    @Override
    public Page<ReconciliationStatementVO> pageStatements(ReconciliationStatementQueryDTO dto) {
        int pageNum = dto == null || dto.pageNum() == null || dto.pageNum() < 1 ? 1 : dto.pageNum();
        int pageSize = dto == null || dto.pageSize() == null || dto.pageSize() < 1 ? 10 : dto.pageSize();
        LambdaQueryWrapper<ReconciliationStatement> wrapper = new LambdaQueryWrapper<>();
        if (dto != null) {
            if (dto.merchantId() != null) {
                wrapper.eq(ReconciliationStatement::getMerchantId, dto.merchantId());
            }
            if (StringUtils.hasText(dto.channelType())) {
                wrapper.eq(ReconciliationStatement::getChannelType, dto.channelType());
            }
            if (dto.billDate() != null) {
                wrapper.eq(ReconciliationStatement::getBillDate, dto.billDate());
            }
            if (dto.billDateFrom() != null) {
                wrapper.ge(ReconciliationStatement::getBillDate, dto.billDateFrom());
            }
            if (dto.billDateTo() != null) {
                wrapper.le(ReconciliationStatement::getBillDate, dto.billDateTo());
            }
            if (StringUtils.hasText(dto.status())) {
                wrapper.eq(ReconciliationStatement::getStatus, dto.status());
            }
        }
        wrapper.orderByDesc(ReconciliationStatement::getBillDate)
                .orderByDesc(ReconciliationStatement::getId);
        Page<ReconciliationStatement> page = statementMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<ReconciliationStatementVO> voPage = new Page<>(pageNum, pageSize, page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    public Page<ReconciliationDiffVO> pageDiffs(ReconciliationDiffQueryDTO dto) {
        int pageNum = dto == null || dto.pageNum() == null || dto.pageNum() < 1 ? 1 : dto.pageNum();
        int pageSize = dto == null || dto.pageSize() == null || dto.pageSize() < 1 ? 10 : dto.pageSize();
        LambdaQueryWrapper<ReconciliationDiff> wrapper = new LambdaQueryWrapper<>();
        if (dto != null) {
            if (dto.statementId() != null) {
                wrapper.eq(ReconciliationDiff::getStatementId, dto.statementId());
            }
            if (StringUtils.hasText(dto.statementNo())) {
                wrapper.eq(ReconciliationDiff::getStatementNo, dto.statementNo());
            }
            if (dto.merchantId() != null) {
                wrapper.eq(ReconciliationDiff::getMerchantId, dto.merchantId());
            }
            if (StringUtils.hasText(dto.channelType())) {
                wrapper.eq(ReconciliationDiff::getChannelType, dto.channelType());
            }
            if (dto.billDate() != null) {
                wrapper.eq(ReconciliationDiff::getBillDate, dto.billDate());
            }
            if (StringUtils.hasText(dto.diffType())) {
                wrapper.eq(ReconciliationDiff::getDiffType, dto.diffType());
            }
        }
        wrapper.orderByDesc(ReconciliationDiff::getId);
        Page<ReconciliationDiff> page = diffMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<ReconciliationDiffVO> voPage = new Page<>(pageNum, pageSize, page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    public ReconciliationStatementVO getByStatementNo(String statementNo) {
        return convertToVO(requireByStatementNo(statementNo));
    }

    @Override
    public List<ReconciliationDiffVO> listDiffsByStatementNo(String statementNo) {
        ReconciliationStatement statement = requireByStatementNo(statementNo);
        LambdaQueryWrapper<ReconciliationDiff> wrapper = new LambdaQueryWrapper<ReconciliationDiff>()
                .eq(ReconciliationDiff::getStatementId, statement.getId())
                .orderByAsc(ReconciliationDiff::getDiffType)
                .orderByAsc(ReconciliationDiff::getOrderNo);
        return diffMapper.selectList(wrapper).stream().map(this::convertToVO).toList();
    }

    /**
     * 查询需要对账的启用渠道
     *
     * @param dto 对账触发参数
     * @return 启用的微信/支付宝渠道列表
     */
    private List<MerchantChannel> listReconcileChannels(ReconciliationTriggerDTO dto) {
        LambdaQueryWrapper<MerchantChannel> wrapper = new LambdaQueryWrapper<MerchantChannel>()
                .eq(MerchantChannel::getStatus, ChannelStatus.ENABLED.getCode())
                .in(MerchantChannel::getChannelType, CHANNEL_TYPE_WECHAT, CHANNEL_TYPE_ALIPAY);
        if (dto != null) {
            if (dto.merchantId() != null) {
                wrapper.eq(MerchantChannel::getMerchantId, dto.merchantId());
            }
            if (StringUtils.hasText(dto.channelType())) {
                wrapper.eq(MerchantChannel::getChannelType, dto.channelType());
            }
        }
        wrapper.orderByAsc(MerchantChannel::getMerchantId)
                .orderByAsc(MerchantChannel::getId);
        return merchantChannelMapper.selectList(wrapper);
    }

    /**
     * 对单条渠道执行对账：下载账单 → 比对 → 幂等落库
     *
     * @param channel  支付渠道
     * @param billDate 账单日期
     * @return 对账单视图
     */
    private ReconciliationStatementVO reconcileChannel(MerchantChannel channel, LocalDate billDate) {
        Map<String, ChannelBill> channelBills = downloadChannelBills(channel, billDate);
        LocalAggregation local = scanLocalTransactions(channel, billDate);
        ReconResult result = compare(channel, channelBills, local);
        ReconciliationStatement statement = upsertStatement(channel, billDate, result);
        replaceDiffs(statement, result.diffs);
        log.info("渠道对账完成: statementNo={}, merchantId={}, channelType={}, billDate={}, "
                        + "channelCount={}, channelAmount={}, localCount={}, localAmount={}, diffCount={}, status={}",
                statement.getStatementNo(), channel.getMerchantId(), channel.getChannelType(), billDate,
                statement.getChannelCount(), statement.getChannelAmount(), statement.getLocalCount(),
                statement.getLocalAmount(), statement.getDiffCount(), statement.getStatus());
        return convertToVO(statement);
    }

    /**
     * 按渠道类型下载并解析渠道账单
     *
     * @param channel  支付渠道
     * @param billDate 账单日期
     * @return 订单号 → 渠道账单记录（同订单号多行金额累加）
     */
    private Map<String, ChannelBill> downloadChannelBills(MerchantChannel channel, LocalDate billDate) {
        String channelType = channel.getChannelType();
        if (CHANNEL_TYPE_WECHAT.equalsIgnoreCase(channelType)) {
            return downloadWechatBills(channel, billDate);
        }
        if (CHANNEL_TYPE_ALIPAY.equalsIgnoreCase(channelType)) {
            return downloadAlipayBills(channel, billDate);
        }
        throw new PaymentException("渠道类型不支持对账: " + channelType);
    }

    /**
     * 下载并解析微信交易账单
     *
     * @param channel  支付渠道
     * @param billDate 账单日期
     * @return 订单号 → 渠道账单记录
     */
    private Map<String, ChannelBill> downloadWechatBills(MerchantChannel channel, LocalDate billDate) {
        TencentWechatPayProperties properties = buildWechatProperties(channel);
        String billDateStr = billDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String downloadUrl = providerGatewayRegistry.tencentWechatPayGateway()
                .queryTradeBillDownloadUrl(properties, billDateStr);
        if (!StringUtils.hasText(downloadUrl)) {
            throw new PaymentException("微信账单下载地址为空");
        }
        byte[] body = httpGet(downloadUrl, "微信账单");
        String csv = decodeText(body, StandardCharsets.UTF_8);
        return parseWechatCsv(csv);
    }

    /**
     * 下载并解析支付宝对账单
     *
     * @param channel  支付渠道
     * @param billDate 账单日期
     * @return 订单号 → 渠道账单记录
     */
    private Map<String, ChannelBill> downloadAlipayBills(MerchantChannel channel, LocalDate billDate) {
        AliyunAlipayProperties properties = buildAlipayProperties(channel);
        String downloadUrl = providerGatewayRegistry.aliyunAlipayGateway()
                .queryBillDownloadUrl(properties, billDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        if (!StringUtils.hasText(downloadUrl)) {
            throw new PaymentException("支付宝账单下载地址为空");
        }
        byte[] body = httpGet(downloadUrl, "支付宝账单");
        String csv = extractAlipayCsv(body);
        return parseAlipayCsv(csv);
    }

    /**
     * GET 下载账单文件
     *
     * @param url  下载地址
     * @param name 账单名称（用于错误提示）
     * @return 响应字节
     */
    private byte[] httpGet(String url, String name) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(Duration.ofSeconds(60))
                    .header("User-Agent", "payment-reconciliation")
                    .GET()
                    .build();
            HttpResponse<byte[]> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new PaymentException(name + "下载失败，HTTP 状态码: " + response.statusCode());
            }
            return response.body();
        } catch (PaymentException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentException(name + "下载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解码账单文本：自动识别 gzip 压缩（magic 0x1F 0x8B）
     *
     * @param body    原始字节
     * @param charset 文本编码
     * @return 解码后的文本
     */
    private String decodeText(byte[] body, Charset charset) {
        byte[] bytes = body;
        if (bytes != null && bytes.length > 2
                && (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                bytes = gzip.readAllBytes();
            } catch (IOException e) {
                throw new PaymentException("账单 gzip 解压失败: " + e.getMessage(), e);
            }
        }
        return new String(bytes, charset);
    }

    /**
     * 从支付宝 zip 压缩包中提取第一个 CSV 文件并按 GBK 解码
     *
     * @param body zip 字节
     * @return CSV 文本
     */
    private String extractAlipayCsv(byte[] body) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName();
                if (entryName != null && entryName.toLowerCase().contains(".csv")) {
                    return new String(zip.readAllBytes(), ALIPAY_CSV_CHARSET);
                }
            }
        } catch (IOException e) {
            throw new PaymentException("支付宝账单 zip 解压失败: " + e.getMessage(), e);
        }
        throw new PaymentException("支付宝账单压缩包内未找到 CSV 文件");
    }

    /**
     * 解析微信交易账单 CSV
     *
     * <p>首行为表头，按表头名取「商户订单号」「微信支付订单号」「订单金额」「交易状态」列；
     * 仅交易状态为 SUCCESS 的行入比对；金额自适应（表头含「分」或值无小数点按分转元）；
     * 跳过「总交易单数」汇总行。</p>
     *
     * @param csv 账单 CSV 文本
     * @return 订单号 → 渠道账单记录
     */
    private Map<String, ChannelBill> parseWechatCsv(String csv) {
        String[] lines = splitLines(csv);
        if (lines.length == 0) {
            throw new PaymentException("微信账单内容为空");
        }
        String[] header = splitCsvLine(lines[0]);
        int orderNoIndex = headerIndexOf(header, "商户订单号", true);
        int thirdPartyIndex = headerIndexOf(header, "微信支付订单号", false);
        if (thirdPartyIndex < 0) {
            thirdPartyIndex = headerIndexOf(header, "微信订单号", false);
        }
        int amountIndex = headerIndexOf(header, "订单金额", true);
        int statusIndex = headerIndexOf(header, "交易状态", true);
        boolean amountInFen = header[amountIndex].contains("分");
        Map<String, ChannelBill> bills = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (!StringUtils.hasText(line)) {
                continue;
            }
            if (line.contains("总交易单数") || line.contains("总交易额") || line.contains("总手续费")) {
                continue;
            }
            String[] cells = splitCsvLine(line);
            if (cells.length <= statusIndex) {
                continue;
            }
            if (!"SUCCESS".equalsIgnoreCase(cells[statusIndex].trim())) {
                continue;
            }
            String orderNo = text(cells, orderNoIndex);
            if (!StringUtils.hasText(orderNo)) {
                continue;
            }
            String thirdPartyNo = text(cells, thirdPartyIndex);
            BigDecimal amount = parseFlexibleAmount(text(cells, amountIndex), amountInFen);
            bills.merge(orderNo, new ChannelBill(orderNo, thirdPartyNo, amount), ChannelBill::merge);
        }
        if (bills.isEmpty()) {
            log.warn("微信账单未解析到成功交易行: lineCount={}", lines.length);
        }
        return bills;
    }

    /**
     * 解析支付宝对账单 CSV
     *
     * <p>定位含「订单号」的表头行，按表头名取「商家订单号」「交易号」「订单金额」「订单状态」「收支」列；
     * 仅收支=收入且订单状态含「成功」的行入比对；跳过 # 注释行、- 分隔行与合计行。</p>
     *
     * @param csv 账单 CSV 文本
     * @return 订单号 → 渠道账单记录
     */
    private Map<String, ChannelBill> parseAlipayCsv(String csv) {
        String[] lines = splitLines(csv);
        int headerIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i] != null && lines[i].contains("订单号")) {
                headerIndex = i;
                break;
            }
        }
        if (headerIndex < 0) {
            throw new PaymentException("支付宝账单未找到表头行");
        }
        String[] header = splitCsvLine(lines[headerIndex]);
        int orderNoIndex = headerIndexOf(header, "商家订单号", true);
        int thirdPartyIndex = headerIndexOf(header, "交易号", true);
        int amountIndex = headerIndexOf(header, "订单金额", true);
        int statusIndex = headerIndexOf(header, "订单状态", true);
        int incomeExpenseIndex = headerIndexOf(header, "收支", false);
        Map<String, ChannelBill> bills = new LinkedHashMap<>();
        for (int i = headerIndex + 1; i < lines.length; i++) {
            String line = lines[i];
            if (!StringUtils.hasText(line)) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("#") || trimmed.startsWith("-")) {
                continue;
            }
            String[] cells = splitCsvLine(line);
            if (incomeExpenseIndex >= 0 && cells.length > incomeExpenseIndex
                    && StringUtils.hasText(cells[incomeExpenseIndex])
                    && !"收入".equals(cells[incomeExpenseIndex].trim())) {
                continue;
            }
            if (statusIndex >= 0 && cells.length > statusIndex
                    && !cells[statusIndex].contains("成功")) {
                continue;
            }
            String orderNo = text(cells, orderNoIndex);
            if (!StringUtils.hasText(orderNo)) {
                continue;
            }
            String thirdPartyNo = text(cells, thirdPartyIndex);
            BigDecimal amount = parseYuanAmount(text(cells, amountIndex));
            bills.merge(orderNo, new ChannelBill(orderNo, thirdPartyNo, amount), ChannelBill::merge);
        }
        if (bills.isEmpty()) {
            log.warn("支付宝账单未解析到成功交易行: lineCount={}", lines.length);
        }
        return bills;
    }

    /**
     * 按行拆分账单文本（兼容 \r\n 与 \n）
     *
     * @param csv 账单文本
     * @return 行数组
     */
    private String[] splitLines(String csv) {
        if (csv == null || csv.isEmpty()) {
            return new String[0];
        }
        return csv.split("\r\n|\r|\n");
    }

    /**
     * 切分单行 CSV（支持双引号包裹、引号内逗号与转义引号）
     *
     * @param line CSV 行
     * @return 单元格数组
     */
    private String[] splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells.toArray(new String[0]);
    }

    /**
     * 按表头名定位列下标
     *
     * @param header      表头数组
     * @param name        列名
     * @param required    是否必填
     * @return 列下标；未找到且非必填返回 -1，必填缺失抛出异常
     */
    private int headerIndexOf(String[] header, String name, boolean required) {
        for (int i = 0; i < header.length; i++) {
            if (header[i] != null && header[i].trim().equals(name)) {
                return i;
            }
        }
        if (required) {
            throw new PaymentException("账单缺少必需列: " + name);
        }
        return -1;
    }

    /**
     * 读取单元格文本
     *
     * @param cells 单元格数组
     * @param index 列下标
     * @return 单元格文本；越界或空白返回 null
     */
    private String text(String[] cells, int index) {
        if (index < 0 || index >= cells.length) {
            return null;
        }
        String value = cells[index];
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 解析金额：表头含「分」或数值无小数点时按分转元，否则按元
     *
     * @param raw        原始金额文本
     * @param headerInFen 表头是否标注为分
     * @return 元为单位的金额（两位小数）
     */
    private BigDecimal parseFlexibleAmount(String raw, boolean headerInFen) {
        if (!StringUtils.hasText(raw)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal value = new BigDecimal(raw.trim());
        if (headerInFen || raw.trim().indexOf('.') < 0) {
            value = value.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 解析元为单位金额（两位小数）
     *
     * @param raw 原始金额文本
     * @return 金额；空白按 0
     */
    private BigDecimal parseYuanAmount(String raw) {
        if (!StringUtils.hasText(raw)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 分批扫描本地成功支付流水并按订单号聚合
     *
     * @param channel  支付渠道
     * @param billDate 账单日期
     * @return 本地流水聚合
     */
    private LocalAggregation scanLocalTransactions(MerchantChannel channel, LocalDate billDate) {
        LocalDateTime start = billDate.atStartOfDay();
        LocalDateTime end = billDate.plusDays(1).atStartOfDay();
        LambdaQueryWrapper<TransactionRecord> wrapper = new LambdaQueryWrapper<TransactionRecord>()
                .eq(TransactionRecord::getMerchantId, channel.getMerchantId())
                .eq(TransactionRecord::getChannelType, channel.getChannelType())
                .eq(TransactionRecord::getTransactionType, TRANSACTION_TYPE_PAY)
                .eq(TransactionRecord::getStatus, TRANSACTION_STATUS_SUCCESS)
                .ge(TransactionRecord::getCreatedAt, start)
                .lt(TransactionRecord::getCreatedAt, end)
                .orderByAsc(TransactionRecord::getId);
        Map<String, LocalTransaction> transactions = new LinkedHashMap<>();
        int count = 0;
        BigDecimal amount = BigDecimal.ZERO;
        int pageNum = 1;
        while (true) {
            Page<TransactionRecord> page =
                    transactionRecordMapper.selectPage(new Page<>(pageNum, LOCAL_SCAN_BATCH_SIZE, false), wrapper);
            List<TransactionRecord> records = page.getRecords();
            if (records.isEmpty()) {
                break;
            }
            for (TransactionRecord record : records) {
                if (!StringUtils.hasText(record.getOrderNo())) {
                    continue;
                }
                BigDecimal recordAmount = record.getAmount() == null ? BigDecimal.ZERO : record.getAmount();
                transactions.merge(record.getOrderNo(),
                        new LocalTransaction(record.getOrderNo(), record.getThirdPartyTransactionNo(), recordAmount),
                        LocalTransaction::merge);
                count++;
                amount = amount.add(recordAmount);
            }
            if (records.size() < LOCAL_SCAN_BATCH_SIZE) {
                break;
            }
            pageNum++;
        }
        return new LocalAggregation(transactions, count, amount.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * 渠道账单与本地流水比对
     *
     * @param channel      支付渠道
     * @param channelBills 渠道账单
     * @param local        本地流水聚合
     * @return 比对结果
     */
    private ReconResult compare(MerchantChannel channel, Map<String, ChannelBill> channelBills,
                                LocalAggregation local) {
        Set<String> orderNos = new LinkedHashSet<>(channelBills.keySet());
        orderNos.addAll(local.transactions().keySet());
        List<ReconciliationDiff> diffs = new ArrayList<>();
        for (String orderNo : orderNos) {
            ChannelBill bill = channelBills.get(orderNo);
            LocalTransaction transaction = local.transactions().get(orderNo);
            if (bill != null && transaction == null) {
                diffs.add(buildDiff(channel, ReconciliationDiffType.LOCAL_MISSING, orderNo,
                        bill.thirdPartyNo(), bill.amount(), null, "渠道账单存在、本地无对应支付流水"));
            } else if (bill == null && transaction != null) {
                diffs.add(buildDiff(channel, ReconciliationDiffType.CHANNEL_MISSING, orderNo,
                        transaction.thirdPartyNo(), null, transaction.amount(), "本地存在支付流水、渠道账单无对应记录"));
            } else if (bill.amount().compareTo(transaction.amount()) != 0) {
                diffs.add(buildDiff(channel, ReconciliationDiffType.AMOUNT_MISMATCH, orderNo,
                        firstNonBlank(bill.thirdPartyNo(), transaction.thirdPartyNo()),
                        bill.amount(), transaction.amount(), "双方均有记录但金额不一致"));
            }
        }
        return new ReconResult(channelBills.size(), sumAmounts(channelBills),
                local.count(), local.amount(), diffs);
    }

    /**
     * 汇总渠道账单总金额
     *
     * @param channelBills 渠道账单
     * @return 总金额
     */
    private BigDecimal sumAmounts(Map<String, ChannelBill> channelBills) {
        BigDecimal total = BigDecimal.ZERO;
        for (ChannelBill bill : channelBills.values()) {
            total = total.add(bill.amount());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 构建差异明细（不含对账单关联，落库前统一回填）
     *
     * @param channel      支付渠道
     * @param diffType     差异类型
     * @param orderNo      商户订单号
     * @param thirdPartyNo 第三方交易号
     * @param channelAmount 渠道账单金额
     * @param localAmount  本地流水金额
     * @param remark       备注
     * @return 差异明细
     */
    private ReconciliationDiff buildDiff(MerchantChannel channel, ReconciliationDiffType diffType,
                                         String orderNo, String thirdPartyNo,
                                         BigDecimal channelAmount, BigDecimal localAmount, String remark) {
        ReconciliationDiff diff = new ReconciliationDiff();
        diff.setMerchantId(channel.getMerchantId());
        diff.setChannelType(channel.getChannelType());
        diff.setDiffType(diffType.getCode());
        diff.setOrderNo(orderNo);
        diff.setThirdPartyNo(thirdPartyNo);
        diff.setChannelAmount(channelAmount);
        diff.setLocalAmount(localAmount);
        if (channelAmount != null && localAmount != null) {
            diff.setDiffAmount(channelAmount.subtract(localAmount).setScale(2, RoundingMode.HALF_UP));
        }
        diff.setRemark(remark);
        return diff;
    }

    /**
     * 幂等登记对账单：不存在则新建，存在则覆盖统计并删除旧差异
     *
     * @param channel  支付渠道
     * @param billDate 账单日期
     * @param result   比对结果
     * @return 对账单
     */
    private ReconciliationStatement upsertStatement(MerchantChannel channel, LocalDate billDate, ReconResult result) {
        ReconciliationStatement statement = findByUniqueKey(channel.getMerchantId(), channel.getChannelType(), billDate);
        boolean isNew = statement == null;
        if (isNew) {
            statement = new ReconciliationStatement();
            statement.setStatementNo(IdUtils.createOrderNo(STATEMENT_NO_PREFIX));
            statement.setMerchantId(channel.getMerchantId());
            statement.setChannelId(channel.getId());
            statement.setChannelType(channel.getChannelType());
            statement.setBillDate(billDate);
        }
        statement.setChannelCount(result.channelCount);
        statement.setChannelAmount(result.channelAmount);
        statement.setLocalCount(result.localCount);
        statement.setLocalAmount(result.localAmount);
        statement.setDiffCount(result.diffs.size());
        statement.setStatus(result.diffs.isEmpty()
                ? ReconciliationStatus.MATCHED.getCode() : ReconciliationStatus.DIFF.getCode());
        statement.setFailReason(null);
        if (isNew) {
            statementMapper.insert(statement);
        } else {
            statementMapper.updateById(statement);
            LambdaQueryWrapper<ReconciliationDiff> deleteWrapper = new LambdaQueryWrapper<ReconciliationDiff>()
                    .eq(ReconciliationDiff::getStatementId, statement.getId());
            diffMapper.delete(deleteWrapper);
        }
        return statement;
    }

    /**
     * 批量写入差异明细并回填对账单关联
     *
     * @param statement 对账单
     * @param diffs     差异明细
     */
    private void replaceDiffs(ReconciliationStatement statement, List<ReconciliationDiff> diffs) {
        if (diffs.isEmpty()) {
            return;
        }
        for (ReconciliationDiff diff : diffs) {
            diff.setStatementId(statement.getId());
            diff.setStatementNo(statement.getStatementNo());
            diff.setBillDate(statement.getBillDate());
            diffMapper.insert(diff);
        }
    }

    /**
     * 标记对账失败（账单下载/解析异常），保留失败对账单不中断整体
     *
     * @param channel  支付渠道
     * @param billDate 账单日期
     * @param reason   失败原因
     * @return 失败对账单视图
     */
    private ReconciliationStatementVO markStatementFailed(MerchantChannel channel, LocalDate billDate, String reason) {
        ReconciliationStatement statement = findByUniqueKey(channel.getMerchantId(), channel.getChannelType(), billDate);
        if (statement == null) {
            statement = new ReconciliationStatement();
            statement.setStatementNo(IdUtils.createOrderNo(STATEMENT_NO_PREFIX));
            statement.setMerchantId(channel.getMerchantId());
            statement.setChannelId(channel.getId());
            statement.setChannelType(channel.getChannelType());
            statement.setBillDate(billDate);
            statement.setChannelCount(0);
            statement.setChannelAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            statement.setLocalCount(0);
            statement.setLocalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            statement.setDiffCount(0);
            statementMapper.insert(statement);
        }
        statement.setStatus(ReconciliationStatus.FAILED.getCode());
        statement.setFailReason(truncateReason(reason));
        statementMapper.updateById(statement);
        return convertToVO(statement);
    }

    /**
     * 截断失败原因到列宽
     *
     * @param reason 原始原因
     * @return 截断后的原因
     */
    private String truncateReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        String message = reason.length() > FAIL_REASON_MAX_LENGTH
                ? reason.substring(0, FAIL_REASON_MAX_LENGTH) : reason;
        return message;
    }

    /**
     * 按唯一键（商户、渠道类型、账单日）查询对账单
     *
     * @param merchantId  商户ID
     * @param channelType 渠道类型
     * @param billDate    账单日期
     * @return 对账单；不存在返回 {@code null}
     */
    private ReconciliationStatement findByUniqueKey(Long merchantId, String channelType, LocalDate billDate) {
        LambdaQueryWrapper<ReconciliationStatement> wrapper = new LambdaQueryWrapper<ReconciliationStatement>()
                .eq(ReconciliationStatement::getMerchantId, merchantId)
                .eq(ReconciliationStatement::getChannelType, channelType)
                .eq(ReconciliationStatement::getBillDate, billDate);
        List<ReconciliationStatement> statements = statementMapper.selectList(wrapper);
        return statements.isEmpty() ? null : statements.get(0);
    }

    /**
     * 按对账单号查询对账单，不存在时抛出异常
     *
     * @param statementNo 对账单号
     * @return 对账单
     */
    private ReconciliationStatement requireByStatementNo(String statementNo) {
        LambdaQueryWrapper<ReconciliationStatement> wrapper = new LambdaQueryWrapper<ReconciliationStatement>()
                .eq(ReconciliationStatement::getStatementNo, statementNo);
        List<ReconciliationStatement> statements = statementMapper.selectList(wrapper);
        if (statements.isEmpty()) {
            throw new PaymentException("对账单不存在: " + statementNo);
        }
        return statements.get(0);
    }

    /**
     * 构建微信支付配置属性
     *
     * @param channel 支付渠道
     * @return 微信支付配置属性
     */
    private TencentWechatPayProperties buildWechatProperties(MerchantChannel channel) {
        TencentWechatPayProperties properties = new TencentWechatPayProperties();
        properties.setAppId(requiredText(channel.getAppId(), "微信 AppId 不能为空"));
        properties.setMerchantId(requiredText(channel.getMerchantNo(), "微信商户号不能为空"));
        properties.setPrivateKey(requiredText(decryptValue(channel.getPrivateKey()), "微信商户私钥不能为空"));
        properties.setApiV3Key(requiredText(decryptValue(channel.getApiKey()), "微信 APIv3 Key 不能为空"));
        properties.setMerchantSerialNumber(requiredText(extText(channel, "merchantSerialNumber"), "微信商户证书序列号不能为空"));
        properties.setNotifyUrl(channel.getNotifyUrl());
        return properties;
    }

    /**
     * 构建支付宝配置属性
     *
     * @param channel 支付渠道
     * @return 支付宝配置属性
     */
    private AliyunAlipayProperties buildAlipayProperties(MerchantChannel channel) {
        AliyunAlipayProperties properties = new AliyunAlipayProperties();
        properties.setAppId(requiredText(channel.getAppId(), "支付宝应用ID不能为空"));
        properties.setPrivateKey(requiredText(decryptValue(channel.getPrivateKey()), "支付宝私钥不能为空"));
        properties.setAlipayPublicKey(requiredText(channel.getPublicKey(), "支付宝公钥不能为空"));
        properties.setSandbox(channel.getSandboxMode() != null && channel.getSandboxMode() == 1);
        properties.setServerUrl(extText(channel, "serverUrl"));
        properties.setCharset(firstNonBlank(extText(channel, "charset"), "UTF-8"));
        properties.setFormat(firstNonBlank(extText(channel, "format"), "json"));
        properties.setSignType(firstNonBlank(extText(channel, "signType"), "RSA2"));
        return properties;
    }

    /**
     * 对账单转视图对象
     *
     * @param statement 对账单
     * @return 对账单视图
     */
    private ReconciliationStatementVO convertToVO(ReconciliationStatement statement) {
        String statusDesc = statement.getStatus() == null ? null : statusDescription(statement.getStatus());
        return new ReconciliationStatementVO(statement.getId(), statement.getStatementNo(),
                statement.getMerchantId(), statement.getChannelId(), statement.getChannelType(),
                statement.getBillDate(), statement.getChannelCount(), statement.getChannelAmount(),
                statement.getLocalCount(), statement.getLocalAmount(), statement.getDiffCount(),
                statement.getStatus(), statusDesc, statement.getFailReason(),
                statement.getCreatedAt(), statement.getUpdatedAt());
    }

    /**
     * 差异明细转视图对象
     *
     * @param diff 差异明细
     * @return 差异明细视图
     */
    private ReconciliationDiffVO convertToVO(ReconciliationDiff diff) {
        String diffTypeDesc = diff.getDiffType() == null ? null : diffTypeDescription(diff.getDiffType());
        return new ReconciliationDiffVO(diff.getId(), diff.getStatementId(), diff.getStatementNo(),
                diff.getMerchantId(), diff.getChannelType(), diff.getBillDate(), diff.getDiffType(),
                diffTypeDesc, diff.getOrderNo(), diff.getThirdPartyNo(), diff.getChannelAmount(),
                diff.getLocalAmount(), diff.getDiffAmount(), diff.getRemark(), diff.getCreatedAt());
    }

    /**
     * 解析对账单状态描述
     *
     * @param code 状态码
     * @return 状态描述；未知状态返回原码
     */
    private String statusDescription(String code) {
        for (ReconciliationStatus status : ReconciliationStatus.values()) {
            if (status.getCode().equals(code)) {
                return status.getDescription();
            }
        }
        return code;
    }

    /**
     * 解析差异类型描述
     *
     * @param code 类型码
     * @return 类型描述；未知类型返回原码
     */
    private String diffTypeDescription(String code) {
        for (ReconciliationDiffType type : ReconciliationDiffType.values()) {
            if (type.getCode().equals(code)) {
                return type.getDescription();
            }
        }
        return code;
    }

    /**
     * 渠道账单行（同订单号多行时金额累加）
     *
     * @param orderNo      商户订单号
     * @param thirdPartyNo 第三方交易号
     * @param amount       金额（元）
     */
    private record ChannelBill(String orderNo, String thirdPartyNo, BigDecimal amount) {

        /**
         * 合并两行账单（金额累加，第三方交易号取首个非空）
         *
         * @param left  左值
         * @param right 右值
         * @return 合并结果
         */
        private static ChannelBill merge(ChannelBill left, ChannelBill right) {
            String thirdPartyNo = StringUtils.hasText(left.thirdPartyNo) ? left.thirdPartyNo : right.thirdPartyNo;
            return new ChannelBill(left.orderNo, thirdPartyNo, left.amount.add(right.amount));
        }
    }

    /**
     * 本地流水行（同订单号多笔时金额累加）
     *
     * @param orderNo      商户订单号
     * @param thirdPartyNo 第三方交易号
     * @param amount       金额（元）
     */
    private record LocalTransaction(String orderNo, String thirdPartyNo, BigDecimal amount) {

        /**
         * 合并两笔流水（金额累加，第三方交易号取首个非空）
         *
         * @param left  左值
         * @param right 右值
         * @return 合并结果
         */
        private static LocalTransaction merge(LocalTransaction left, LocalTransaction right) {
            String thirdPartyNo = StringUtils.hasText(left.thirdPartyNo) ? left.thirdPartyNo : right.thirdPartyNo;
            return new LocalTransaction(left.orderNo, thirdPartyNo, left.amount.add(right.amount));
        }
    }

    /**
     * 本地流水聚合结果
     *
     * @param transactions 订单号 → 流水行
     * @param count        成功流水总笔数
     * @param amount       成功流水总金额
     */
    private record LocalAggregation(Map<String, LocalTransaction> transactions, int count, BigDecimal amount) {
    }

    /**
     * 比对结果
     *
     * @param channelCount  渠道账单笔数
     * @param channelAmount 渠道账单总金额
     * @param localCount    本地流水笔数
     * @param localAmount   本地流水总金额
     * @param diffs         差异明细
     */
    private record ReconResult(int channelCount, BigDecimal channelAmount,
                               int localCount, BigDecimal localAmount,
                               List<ReconciliationDiff> diffs) {
    }
}
