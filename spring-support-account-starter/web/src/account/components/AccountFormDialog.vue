<template>
  <ReDialog
    v-model="visible"
    :title="isEdit ? '编辑账号' : '新增账号'"
    width="720"
    append-to-body
    destroy-on-close
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="104px"
      class="account-form"
    >
      <div class="form-section">基础信息</div>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="账号名称" prop="name">
            <el-input v-model="form.name" placeholder="如 海康开放平台 / 运维邮箱" maxlength="128" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="账号编码" prop="code">
            <el-input v-model="form.code" placeholder="业务唯一键，如 hikvision-api" maxlength="128" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="账号种类" prop="kind">
            <el-select v-model="form.kind" style="width: 100%" @change="onKindChange">
              <el-option
                v-for="option in KIND_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="VENDOR_LABELS[form.kind]">
            <el-input v-model="form.vendor" :placeholder="VENDOR_PLACEHOLDERS[form.kind]" maxlength="64" />
          </el-form-item>
        </el-col>
        <el-col v-if="!useKeyRef" :span="12">
          <el-form-item :label="USERNAME_LABELS[form.kind]">
            <el-input v-model="form.username" :placeholder="USERNAME_PLACEHOLDERS[form.kind]" maxlength="255" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="ENDPOINT_LABELS[form.kind]">
            <el-input v-model="form.endpoint" :placeholder="ENDPOINT_PLACEHOLDERS[form.kind]" maxlength="512" />
          </el-form-item>
        </el-col>
        <el-col v-if="form.kind !== 'EMAIL'" :span="24">
          <el-form-item label="密钥来源">
            <el-radio-group v-model="form.keyMode">
              <el-radio value="inline">直接填写</el-radio>
              <el-radio value="ref">引用密钥管理</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col v-if="useKeyRef" :span="24">
          <el-form-item label="选择密钥">
            <el-select v-model="form.secretId" filterable placeholder="从密钥管理里选择" class="w-full">
              <el-option
                v-for="item in secretOptions"
                :key="item.secretId"
                :label="`${item.name || item.accessKey}（${typeLabel(item.secretType)} · ${item.provider || 'custom'}）`"
                :value="item.secretId"
              />
            </el-select>
            <div class="field-hint">
              只列出「{{ typeLabel(SECRET_TYPE_BY_KIND[form.kind]) }}」类型的密钥；调用时使用该密钥的
              AccessKeyId / Secret，换密钥不必改账号。可去「密钥管理」新建。
            </div>
          </el-form-item>
        </el-col>
        <el-col v-if="!useKeyRef" :span="24">
          <el-form-item label="敏感凭据">
            <el-input
              v-model="form.secret"
              type="password"
              show-password
              autocomplete="new-password"
              :placeholder="secretPlaceholder"
            />
            <div class="field-hint">{{ secretHint }}</div>
          </el-form-item>
        </el-col>
      </el-row>

      <div class="form-section">类型专属配置</div>
      <el-row :gutter="16">
        <template v-if="form.kind === 'EMAIL'">
          <el-col :span="8">
            <el-form-item label="发信端口">
              <el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="SSL / TLS">
              <el-switch v-model="form.ssl" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="默认发件人">
              <el-input v-model="form.from" placeholder="noreply@example.com" maxlength="255" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="收信主机">
              <el-input v-model="form.imapHost" placeholder="如 imap.qq.com" maxlength="255" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="收信端口">
              <el-input-number
                v-model="form.imapPort"
                :min="1"
                :max="65535"
                controls-position="right"
                class="w-full"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <div class="field-hint hint-block">
              收信（IMAP）与发信（SMTP）不同主机时才需填；留空按服务地址推导，端口留空按 SSL 开 993、关 143。
            </div>
          </el-col>
        </template>
        <template v-else-if="form.kind === 'SMS'">
          <el-col :span="12">
            <el-form-item label="短信签名">
              <el-input v-model="form.signName" placeholder="如 某某科技" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="应用 ID">
              <el-input v-model="form.appId" placeholder="腾讯云 SmsSdkAppid（阿里/百度可留空）" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模板编码">
              <MessageTemplateSelect
                v-model="form.templateCode"
                kind="SMS"
                allow-create
                placeholder="选择模板，或直接输入运营商模板码"
              />
              <div class="field-hint">
                可从「模板管理 → 消息模板」选择；运营商下发的模板码（如 SMS_123456789）可直接输入。
              </div>
            </el-form-item>
          </el-col>
        </template>
        <template v-else>
          <el-col :span="12">
            <el-form-item label="区域">
              <el-input v-model="form.region" placeholder="如 cn-shanghai（可留空）" maxlength="64" />
            </el-form-item>
          </el-col>
        </template>
        <el-col :span="24">
          <el-form-item label="控制台地址">
            <el-input v-model="form.webUrl" placeholder="可留空：邮箱 / 短信会自动按域名或服务商推导" maxlength="512" />
            <div class="field-hint">右键「打开邮箱 / 打开短信」时打开的网页地址；留空则按内置映射推导。</div>
          </el-form-item>
        </el-col>
      </el-row>

      <div class="form-section">状态与归属</div>
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="启用">
            <el-switch v-model="form.enabled" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="同种类默认">
            <el-switch v-model="form.defaultAccount" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="归属">
            <span class="owner-hint">
              {{ isEdit ? (account?.owner ? `仅本人：${account.owner}` : '部署级共享') : '部署级共享（可留空）' }}
            </span>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        {{ isEdit ? '保存' : '创建' }}
      </el-button>
    </template>
  </ReDialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { message } from '@repo/core';
import type { FormInstance, FormRules } from 'element-plus';
import ReDialog from '@repo/ui/re-dialog';
import MessageTemplateSelect from '../../template/components/MessageTemplateSelect.vue';
import {
  createAccount,
  listAccountSecrets,
  updateAccount,
  type AccountKind,
  type SysAccount,
  type SysAccountSecret,
} from '../api/account';

/**
 * 账号新增 / 编辑弹窗。
 *
 * <p>字段按账号种类收敛：密钥只关心服务地址与区域，邮箱多出端口 / SSL / 发件人，
 * 短信多出签名 / 模板编码。所有类型专属字段都落到 {@code config} JSON，
 * 后端的 {@code SysAccount#config} 原样保存，不做列扩张。</p>
 *
 * <p>编辑时敏感值不回显：留空或含 {@code ****} 表示不修改（后端按掩码判定）。</p>
 */
const props = defineProps<{
  /** 是否显示 */
  modelValue: boolean;
  /** 正在编辑的账号；null 表示新增 */
  account?: SysAccount | null;
  /**
   * 复制来源：新增时用它预填表单。
   *
   * <p>与 {@code account} 的区别是「不进入编辑态」——保存走新增，
   * 因此凭据不会被带过去（读接口本来也拿不到明文），编码自动加后缀避免撞唯一键。</p>
   */
  draft?: SysAccount | null;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  /** 保存成功 */
  (e: 'saved', account: SysAccount): void;
}>();

/** 账号种类选项（与后端 /api/accounts/kinds 保持一致） */
const KIND_OPTIONS: Array<{ value: AccountKind; label: string }> = [
  { value: 'API_KEY', label: 'API 密钥' },
  { value: 'EMAIL', label: '邮箱账号' },
  { value: 'SMS', label: '短信账号' },
];

/** 厂商字段标签 */
const VENDOR_LABELS: Record<AccountKind, string> = {
  API_KEY: '厂商 / 平台',
  EMAIL: '邮件服务商',
  SMS: '短信服务商',
};

/** 厂商占位提示 */
const VENDOR_PLACEHOLDERS: Record<AccountKind, string> = {
  API_KEY: '如 hikvision / aliyun / openai',
  EMAIL: '如 qq / 163 / smtp',
  SMS: '如 aliyun / tencent / huawei',
};

/** 登录名字段标签 */
const USERNAME_LABELS: Record<AccountKind, string> = {
  API_KEY: 'AccessKey / 账号',
  EMAIL: '邮箱地址',
  SMS: '手机号 / 账号',
};

/** 登录名占位提示 */
const USERNAME_PLACEHOLDERS: Record<AccountKind, string> = {
  API_KEY: '如 AccessKeyId',
  EMAIL: '如 ops@example.com',
  SMS: '如 13800000000',
};

/** 服务地址字段标签 */
const ENDPOINT_LABELS: Record<AccountKind, string> = {
  API_KEY: 'API 地址',
  EMAIL: 'SMTP / POP 主机',
  SMS: '服务地址',
};

/** 服务地址占位提示 */
const ENDPOINT_PLACEHOLDERS: Record<AccountKind, string> = {
  API_KEY: '如 https://api.example.com',
  EMAIL: '如 smtp.qq.com',
  SMS: '如 dysms.aliyun.com',
};

/** 表单模型 */
interface AccountForm {
  name: string;
  code: string;
  kind: AccountKind;
  vendor: string;
  username: string;
  secret: string;
  endpoint: string;
  enabled: boolean;
  defaultAccount: boolean;
  from: string;
  port: number | undefined;
  ssl: boolean;
  imapHost: string;
  imapPort: number | undefined;
  region: string;
  signName: string;
  templateCode: string;
  appId: string;
  webUrl: string;
  /** 密钥来源：inline 直接填写 / ref 引用密钥库（仅短信） */
  keyMode: 'inline' | 'ref';
  /** 引用的密钥标识 */
  secretId: string;
}

/** 空表单 */
function emptyForm(): AccountForm {
  return {
    name: '',
    code: '',
    kind: 'API_KEY',
    vendor: '',
    username: '',
    secret: '',
    endpoint: '',
    enabled: true,
    defaultAccount: false,
    from: '',
    port: 465,
    ssl: true,
    imapHost: '',
    imapPort: undefined,
    region: '',
    signName: '',
    templateCode: '',
    appId: '',
    webUrl: '',
    keyMode: 'inline',
    secretId: '',
  };
}

const formRef = ref<FormInstance>();
const form = reactive<AccountForm>(emptyForm());
const submitting = ref(false);

/** 弹窗显隐双向绑定 */
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

/** 是否编辑 */
const isEdit = computed(() => Boolean(props.account?.accountId));

/** 敏感值占位提示 */
const secretPlaceholder = computed(() =>
  isEdit.value
    ? props.account?.hasSecret
      ? '已配置，留空表示不修改'
      : '尚未配置，可在此补充'
    : 'AccessKey Secret / 邮箱密码 / 短信密钥',
);

/** 敏感值说明 */
const secretHint = computed(() =>
  isEdit.value
    ? '留空或含 **** 表示不修改；填写明文则覆盖原值。'
    : '明文只在保存时提交，读取接口永不回显。',
);

/** 是否引用密钥管理（短信 / API 密钥两类账号可引用） */
const useKeyRef = computed(
  () => (form.kind === 'SMS' || form.kind === 'API_KEY') && form.keyMode === 'ref',
);

/** 密钥类型中文名（与密钥管理页保持一致） */
function typeLabel(secretType?: string | null): string {
  const map: Record<string, string> = {
    sms: '短信',
    mail: '邮件',
    storage: '对象存储',
    payment: '支付',
    api: '通用 API',
    other: '其他',
  };
  return map[String(secretType || '').toLowerCase()] ?? '其他';
}

/** 账号种类 → 可选的密钥类型 */
const SECRET_TYPE_BY_KIND: Record<AccountKind, string> = {
  SMS: 'sms',
  EMAIL: 'mail',
  API_KEY: 'api',
};

/** 密钥库全量（供 secretOptions 过滤） */
const secretList = ref<SysAccountSecret[]>([]);

/** 密钥库选项（按当前账号种类对应的密钥类型过滤） */
const secretOptions = computed(() =>
  secretList.value.filter((item) =>
    !item.secretType || item.secretType === SECRET_TYPE_BY_KIND[form.kind],
  ),
);

/**
 * 加载密钥库选项。
 *
 * <p>失败时留空即可，不阻塞账号表单：没有密钥库的部署仍可手工填密钥。</p>
 */
async function loadSecretOptions(): Promise<void> {
  try {
    secretList.value = await listAccountSecrets();
  } catch {
    secretList.value = [];
  }
}

/** 表单校验规则 */
const rules: FormRules = {
  name: [{ required: true, message: '请输入账号名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入账号编码', trigger: 'blur' }],
  kind: [{ required: true, message: '请选择账号种类', trigger: 'change' }],
};

/**
 * 从配置 JSON 读字符串。
 *
 * @param config 配置
 * @param key    键
 * @returns 字符串值；不存在返回空串
 */
function readString(config: Record<string, unknown> | null | undefined, key: string): string {
  const value = config?.[key];
  return value == null ? '' : String(value);
}

/** 用账号数据回填表单（新增时重置为空表单；有复制来源时按来源预填） */
function fillForm(): void {
  const source = props.account ?? props.draft ?? null;
  Object.assign(form, emptyForm());
  if (!source) {
    return;
  }
  const config = source.config || {};
  form.name = props.account ? source.name || '' : copyName(source.name || source.code || '');
  form.code = props.account ? source.code || '' : copyCode(source.code || '');
  form.kind = source.kind || 'API_KEY';
  form.vendor = source.vendor || '';
  form.username = source.username || '';
  form.endpoint = source.endpoint || '';
  form.enabled = source.status !== 0;
  // 复制出来的是新账号，默认标记必须显式再设，否则会顶掉原账号的默认位
  form.defaultAccount = props.account ? Boolean(source.defaultAccount) : false;
  form.secret = '';
  form.from = readString(config, 'from');
  form.region = readString(config, 'region');
  form.signName = readString(config, 'signName');
  form.templateCode = readString(config, 'templateCode');
  form.appId = readString(config, 'appId');
  form.webUrl = readString(config, 'webUrl');
  form.secretId = readString(config, 'secretId');
  form.keyMode = form.secretId ? 'ref' : 'inline';
  const port = Number(config.port);
  form.port = Number.isFinite(port) && port > 0 ? port : 465;
  form.ssl = config.ssl == null ? true : Boolean(config.ssl);
  form.imapHost = readString(config, 'imapHost');
  const imapPort = Number(config.imapPort);
  form.imapPort = Number.isFinite(imapPort) && imapPort > 0 ? imapPort : undefined;
}

/**
 * 复制来源的账号编码加后缀，避免撞唯一键。
 *
 * @param code 原编码
 * @returns 新编码（原编码为空时留空让用户填）
 */
function copyCode(code: string): string {
  return code ? `${code}-copy` : '';
}

/**
 * 复制来源的账号名称加后缀。
 *
 * @param name 原名称
 * @returns 新名称
 */
function copyName(name: string): string {
  return name ? `${name}（副本）` : '';
}

/** 切换种类：把不适用的专属字段清掉，避免串到另一种账号上 */
function onKindChange(): void {
  if (form.kind !== 'EMAIL') {
    form.from = '';
  }
  if (form.kind === 'EMAIL') {
    form.signName = '';
    form.templateCode = '';
    form.appId = '';
    form.keyMode = 'inline';
    form.secretId = '';
  }
  if (form.kind !== 'API_KEY') {
    form.region = '';
  }
}

/**
 * 组装提交体：保留后端已有但本表单未展示的扩展配置。
 *
 * @returns 提交体
 */
function buildPayload(): SysAccount {
  const account = props.account;
  const source = account ?? props.draft ?? null;
  const config: Record<string, unknown> = { ...(source?.config || {}) };
  const setOrDelete = (key: string, value: unknown) => {
    if (value === '' || value === undefined || value === null) {
      delete config[key];
    } else {
      config[key] = value;
    }
  };
  setOrDelete('from', form.kind === 'EMAIL' ? form.from.trim() : '');
  setOrDelete('port', form.kind === 'EMAIL' ? form.port : undefined);
  if (form.kind === 'EMAIL') {
    config.ssl = form.ssl;
    const imapHost = form.imapHost.trim();
    if (imapHost) {
      config.imapHost = imapHost;
    } else {
      delete config.imapHost;
    }
    if (form.imapPort) {
      config.imapPort = form.imapPort;
    } else {
      delete config.imapPort;
    }
  } else {
    delete config.ssl;
    delete config.imapHost;
    delete config.imapPort;
  }
  setOrDelete('region', form.kind === 'API_KEY' ? form.region.trim() : '');
  setOrDelete('signName', form.kind === 'SMS' ? form.signName.trim() : '');
  setOrDelete('templateCode', form.kind === 'SMS' ? form.templateCode.trim() : '');
  setOrDelete('appId', form.kind === 'SMS' ? form.appId.trim() : '');
  setOrDelete('secretId', useKeyRef.value ? form.secretId : '');
  setOrDelete('webUrl', form.webUrl.trim());

  const payload: SysAccount = {
    accountId: account?.accountId || '',
    name: form.name.trim(),
    code: form.code.trim(),
    kind: form.kind,
    vendor: form.vendor.trim() || undefined,
    username: form.username.trim() || undefined,
    endpoint: form.endpoint.trim() || undefined,
    config,
    status: form.enabled ? 1 : 0,
    defaultAccount: form.defaultAccount,
  };
  const secret = form.secret.trim();
  if (secret && !useKeyRef.value) {
    payload.secret = secret;
  }
  return payload;
}

/** 提交保存 */
async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  submitting.value = true;
  try {
    const payload = buildPayload();
    const saved = isEdit.value
      ? await updateAccount(props.account!.accountId, payload)
      : await createAccount(payload);
    message.success(isEdit.value ? '账号已更新' : '账号已创建');
    visible.value = false;
    emit('saved', saved);
  } catch (e: any) {
    message.error(e?.message || '保存失败');
  } finally {
    submitting.value = false;
  }
}

watch(
  () => [props.modelValue, props.account, props.draft] as const,
  ([open]) => {
    if (open) {
      fillForm();
      void loadSecretOptions();
      formRef.value?.clearValidate();
    }
  },
  { immediate: true },
);
</script>

<style scoped>
.account-form {
  padding: 4px 8px 0;
}
.form-section {
  margin: 6px 0 14px;
  padding-left: 10px;
  border-left: 3px solid var(--el-color-primary);
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}
.field-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-tertiary);
}
.hint-block {
  margin: -4px 0 10px 8px;
}
.owner-hint {
  font-size: 12px;
  color: var(--text-secondary);
}
.w-full {
  width: 100%;
}
</style>
