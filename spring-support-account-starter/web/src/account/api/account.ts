import { http, unwrapResult } from '@repo/utils';

/**
 * 后端统一返回结构 { code, data, msg } 解包。
 *
 * <p>账号模块自带 {@code ApiResult}（{@code success/data/code/error}），
 * 与全局 {@code ReturnResult} 是两套信封，但成功判定都靠 {@code success}，
 * 因此可以复用同一个解包器。</p>
 */
const unwrap = <T>(res: any): T => unwrapResult<T>(res);

/** 账号模块接口基址（spring-support-account-starter） */
const B = '/api/accounts';

/**
 * 提交体转换。
 *
 * <p>{@code http().data()} 只要求「可 JSON 序列化的对象」，而这里的接口类型
 * 没有索引签名，直接传会被拒绝；用一个显式转换把契约类型与传输层解耦，
 * 不动接口定义（那些字段名就是后端列名，改不得）。</p>
 */
const body = (payload: object): Record<string, unknown> => payload as unknown as Record<string, unknown>;

/** 账号种类：设备 / 第三方 API 密钥、POP/SMTP 邮箱、短信服务商 */
export type AccountKind = 'API_KEY' | 'EMAIL' | 'SMS';

/** 凭据校验状态 */
export type AccountVerifyStatus = 'UNKNOWN' | 'VALID' | 'INVALID';

/**
 * 统一账号。
 *
 * <p>与后端 {@code SysAccount} 同名同义：敏感值 {@code secret} 只进不出，
 * 读接口返回的永远是 {@code null} + {@code hasSecret}。</p>
 */
export interface SysAccount {
  /** 账号标识（UUID） */
  accountId: string;
  /** 账号编码（业务唯一键） */
  code?: string;
  /** 账号名称 */
  name?: string;
  /** 账号种类 */
  kind?: AccountKind;
  /** 厂商 / 服务商，如 aliyun / smtp / huawei */
  vendor?: string;
  /** 所属渠道标识 */
  channelKey?: string;
  /** 登录名 / 发件邮箱 / 手机号 */
  username?: string;
  /** 明文凭据（仅写入；读接口恒为 null） */
  secret?: string | null;
  /** 服务地址：SMTP/POP 主机、API baseUrl */
  endpoint?: string;
  /** 类型专属配置（端口 / SSL / 默认发件人 / 控制台地址等） */
  config?: Record<string, unknown> | null;
  /** 归属用户，空表示部署级共享 */
  owner?: string | null;
  /** 状态：1 启用 / 0 停用 */
  status?: number;
  /** 是否同种类默认账号 */
  defaultAccount?: boolean;
  /** 校验状态 */
  verifyStatus?: AccountVerifyStatus;
  /** 最近校验时间（毫秒） */
  verifyAt?: number | null;
  /** 最近校验说明 */
  verifyMessage?: string | null;
  /** 是否已配置敏感凭据（读接口回显） */
  hasSecret?: boolean;
  /** 创建时间（毫秒） */
  createdAt?: number;
  /** 更新时间（毫秒） */
  updatedAt?: number;
}

/** 账号种类选项 */
export interface AccountKindOption {
  value: AccountKind;
  label: string;
}

/** 校验结果 */
export interface AccountVerifyResult {
  /** 是否通过 */
  valid: boolean;
  /** 落库后的状态：VALID / INVALID / UNKNOWN */
  status: AccountVerifyStatus;
  /** 人类可读说明 */
  message: string;
}

/** 账号列表查询参数 */
export interface AccountQuery {
  /** 账号种类过滤 */
  kind?: string;
  /** 名称 / 编码 / 用户名模糊词 */
  keyword?: string;
}

/** 受支持的账号种类 */
export const getAccountKinds = () =>
  http().get(`${B}/kinds`).then(r => unwrap<AccountKindOption[]>(r));

/**
 * 账号列表。
 *
 * <p>后端按归属隔离：未登录只看部署级账号，登录后额外包含本人创建的账号。</p>
 */
export const listAccounts = (query?: AccountQuery) =>
  http()
    .get(B)
    .query({ kind: query?.kind, keyword: query?.keyword })
    .then(r => unwrap<SysAccount[]>(r));

/** 账号详情（不含明文凭据） */
export const getAccount = (accountId: string) =>
  http().get(`${B}/${accountId}`).then(r => unwrap<SysAccount>(r));

/** 新增账号（{@code secret} 传明文） */
export const createAccount = (payload: SysAccount) =>
  http().post(B).data(body(payload)).then(r => unwrap<SysAccount>(r));

/**
 * 更新账号。
 *
 * <p>{@code secret} 留空或含 {@code ****} 表示不修改，只有明文才覆盖。</p>
 */
export const updateAccount = (accountId: string, payload: SysAccount) =>
  http().put(`${B}/${accountId}`).data(body(payload)).then(r => unwrap<SysAccount>(r));

/** 删除账号（逻辑删除） */
export const deleteAccount = (accountId: string) =>
  http().delete(`${B}/${accountId}`).then(r => unwrap<boolean>(r));

/** 校验账号凭据（走 AccountVerifier SPI，未安装对应实现时返回 UNKNOWN） */
export const verifyAccount = (accountId: string) =>
  http().post(`${B}/${accountId}/verify`).then(r => unwrap<AccountVerifyResult>(r));

/* ==================== 外部访问密钥（AppKey） ==================== */

/**
 * 账号模块的外部访问密钥。
 *
 * <p>与 AI 的模型计费 AppKey 无关，这里给设备 / 脚本 / 外部系统发凭据。</p>
 */
export interface SysAccountAppKey {
  appKeyId: string;
  name?: string;
  /** 可检索的 Key 标识 */
  appKey?: string;
  /** 落库密文，接口不回显 */
  secret?: string | null;
  owner?: string | null;
  status?: number;
  /** 过期时间（毫秒，空表示永不过期） */
  expireAt?: number | null;
  /** IP 白名单（逗号分隔） */
  ipWhitelist?: string | null;
  remark?: string | null;
  lastUsedAt?: number | null;
  createdAt?: number;
  updatedAt?: number;
  /** 创建 / 轮换时回显一次的明文 Secret */
  plainSecret?: string | null;
}

const AK = `${B}/app-keys`;

/** 访问密钥列表 */
export const listAccountAppKeys = (keyword?: string) =>
  http().get(AK).query({ keyword }).then(r => unwrap<SysAccountAppKey[]>(r));

/** 新增访问密钥（响应里带一次明文 plainSecret） */
export const createAccountAppKey = (payload: SysAccountAppKey) =>
  http().post(AK).data(body(payload)).then(r => unwrap<SysAccountAppKey>(r));

/** 更新访问密钥属性（名称 / 状态 / 过期 / 白名单） */
export const updateAccountAppKey = (appKeyId: string, payload: SysAccountAppKey) =>
  http().put(`${AK}/${appKeyId}`).data(body(payload)).then(r => unwrap<SysAccountAppKey>(r));

/** 轮换 Secret（响应里带一次明文 plainSecret） */
export const rotateAccountAppKey = (appKeyId: string) =>
  http().post(`${AK}/${appKeyId}/rotate`).then(r => unwrap<SysAccountAppKey>(r));

/** 删除访问密钥 */
export const deleteAccountAppKey = (appKeyId: string) =>
  http().delete(`${AK}/${appKeyId}`).then(r => unwrap<boolean>(r));

/* ==================== Cookie 池 ==================== */

/** 免登录渠道 / 站点会话的 Cookie */
export interface SysAccountCookie {
  cookieId: string;
  name?: string;
  domain?: string;
  path?: string;
  /** 落库密文，接口不回显 */
  cookieValue?: string | null;
  owner?: string | null;
  status?: number;
  expireAt?: number | null;
  /** 来源：trae / duckduckgo / manual… */
  source?: string | null;
  remark?: string | null;
  /** 是否已配置内容（读接口回显） */
  hasValue?: boolean;
  createdAt?: number;
  updatedAt?: number;
}

const CK = `${B}/cookies`;

/** Cookie 列表 */
export const listAccountCookies = (query?: { domain?: string; source?: string; keyword?: string }) =>
  http()
    .get(CK)
    .query({ domain: query?.domain, source: query?.source, keyword: query?.keyword })
    .then(r => unwrap<SysAccountCookie[]>(r));

/** Cookie 详情（不含内容） */
export const getAccountCookie = (cookieId: string) =>
  http().get(`${CK}/${cookieId}`).then(r => unwrap<SysAccountCookie>(r));

/** 新增 Cookie（{@code cookieValue} 传明文） */
export const createAccountCookie = (payload: SysAccountCookie) =>
  http().post(CK).data(body(payload)).then(r => unwrap<SysAccountCookie>(r));

/** 更新 Cookie（{@code cookieValue} 留空或掩码表示不修改） */
export const updateAccountCookie = (cookieId: string, payload: SysAccountCookie) =>
  http().put(`${CK}/${cookieId}`).data(body(payload)).then(r => unwrap<SysAccountCookie>(r));

/** 删除 Cookie */
export const deleteAccountCookie = (cookieId: string) =>
  http().delete(`${CK}/${cookieId}`).then(r => unwrap<boolean>(r));
