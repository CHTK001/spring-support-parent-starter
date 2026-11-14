<template>
  <ReDialog
    v-model="visible"
    :title="isEdit ? '编辑密钥' : '新增密钥'"
    width="610px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" class="sk-form">
      <div class="form-grid">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如 阿里云短信-生产" maxlength="128" />
        </el-form-item>
        <el-form-item label="密钥类型">
          <el-select
            v-model="secretTypes"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择密钥类型（可多选）"
            class="w-full"
          >
            <el-option v-for="t in TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value">
              <span class="opt">
                <span class="opt__dot" :style="{ background: t.color }" />
                <span>{{ t.label }}</span>
              </span>
            </el-option>
          </el-select>
          <span class="form-hint">可多选：一条密钥可能同时服务多个业务（如短信 + 对象存储）。</span>
        </el-form-item>
      </div>
      <div class="form-grid">
        <el-form-item label="用途 / 厂商">
          <el-select
            v-model="form.provider"
            placeholder="选择或输入厂商"
            allow-create
            filterable
            clearable
            class="w-full"
          >
            <!-- 选中后输入框里也显示 logo，不只是下拉里 -->
            <template #label="{ value }">
              <span class="opt opt--picked">
                <ProviderLogo :provider="value" :size="14" />
                <span class="opt__text">{{ providerLabel(value) }}</span>
              </span>
            </template>
            <el-option v-for="p in SECRET_PROVIDERS" :key="p.value" :label="p.label" :value="p.value">
              <span class="opt">
                <ProviderLogo :provider="p.value" :size="16" />
                <span>{{ p.label }}</span>
              </span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="AccessKeyId">
          <el-input v-model="form.accessKey" placeholder="如 LTAI5t..." maxlength="255" spellcheck="false" />
        </el-form-item>
      </div>
      <el-form-item label="AccessKeySecret" prop="secret">
        <el-input
          v-model="form.secret"
          type="password"
          show-password
          autocomplete="new-password"
          :placeholder="secretPlaceholder"
          spellcheck="false"
        />
        <span class="form-hint">{{ secretHint }}</span>
      </el-form-item>
      <div class="form-grid">
        <el-form-item label="Region">
          <el-input v-model="form.region" placeholder="如 cn-hangzhou" maxlength="64" />
        </el-form-item>
        <el-form-item label="状态">
          <ReRadioGroup v-model="form.status" button capsule>
            <ReRadioButton :value="1">启用</ReRadioButton>
            <ReRadioButton :value="0">停用</ReRadioButton>
          </ReRadioGroup>
        </el-form-item>
      </div>
      <el-form-item label="服务地址">
        <el-input v-model="form.endpoint" placeholder="留空按厂商默认（如 dysmsapi.aliyuncs.com）" maxlength="255" />
      </el-form-item>
      <el-form-item label="归属">
        <span class="form-static">{{ isEdit ? ownerText : '仅本人（创建后按登录用户归属）' }}</span>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="用途说明" maxlength="255" />
      </el-form-item>
    </el-form>

    <template #footer>
      <ReButton @click="visible = false">取消</ReButton>
      <ReButton type="primary" :loading="saving" @click="submit">{{ isEdit ? '保存' : '创建' }}</ReButton>
    </template>
  </ReDialog>
</template>

<script setup lang="ts">
/**
 * 密钥库新增 / 编辑弹窗。
 *
 * <p>Secret 只进不出：编辑时输入框永远留空，占位文案提示「留空表示不修改」，
 * 只有重新粘贴才会覆盖，避免「只改备注却把密钥清空」。</p>
 */
import { computed, ref, watch } from 'vue';
import { message } from '@repo/core';
import ReButton from '@repo/ui/re-button';
import ReDialog from '@repo/ui/re-dialog';
import { ReRadioButton, ReRadioGroup } from '@repo/ui/re-kit';

import {
  createAccountSecret,
  updateAccountSecret,
  type SysAccountSecret,
} from '../../api/account';
import { SECRET_PROVIDERS, providerLabel } from '../../secretProviders';
import ProviderLogo from './ProviderLogo.vue';

/** 密钥类型选项（与后端 secretType 词表一致） */
const TYPE_OPTIONS = [
  { value: 'sms', label: '短信', color: '#67c23a' },
  { value: 'mail', label: '邮件', color: '#409eff' },
  { value: 'storage', label: '对象存储', color: '#e6a23c' },
  { value: 'payment', label: '支付', color: '#f56c6c' },
  { value: 'api', label: '通用 API', color: '#909399' },
  { value: 'other', label: '其他', color: '#b1b3b8' },
];

const props = defineProps<{
  modelValue: boolean;
  secret?: SysAccountSecret | null;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'saved'): void;
}>();

/** 弹窗显隐双向绑定 */
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

/** 是否编辑 */
const isEdit = computed(() => Boolean(props.secret?.secretId));

/** 表单 */
const form = ref<SysAccountSecret>({
  secretId: '',
  name: '',
  secretType: 'sms',
  provider: 'aliyun',
  accessKey: '',
  secret: '',
  region: '',
  endpoint: '',
  remark: '',
  status: 1,
});

/** 表单引用 */
const formRef = ref();

/**
 * 密钥类型（多选）。
 *
 * <p>后端一列 {@code secret_type} 存逗号分隔的多值（如 {@code sms,storage}），
 * 这里用数组承接多选，提交时再拼成字符串，读取时按 token 拆开。</p>
 */
const secretTypes = ref<string[]>(['sms']);

/** 类型串 → 类型数组（兼容单值历史数据） */
function parseTypes(raw?: string | null): string[] {
  const valid = TYPE_OPTIONS.map((item) => item.value);
  return String(raw || '')
    .split(',')
    .map((token) => token.trim().toLowerCase())
    .filter((token) => valid.includes(token));
}

/** 保存中 */
const saving = ref(false);

/** 归属展示 */
const ownerText = computed(() => (props.secret?.owner ? `仅本人（${props.secret.owner}）` : '部署级共享'));

/** 已配置过密钥时提示掩码语义 */
const secretPlaceholder = computed(() =>
  isEdit.value && props.secret?.hasSecret ? '已配置（留空表示不修改）' : '粘贴 AccessKeySecret',
);
const secretHint = computed(() =>
  isEdit.value && props.secret?.hasSecret
    ? '留空或含 **** 表示保留原有 Secret，只有粘贴新内容才会覆盖。'
    : '写入后会以 enc: 加密存储，读接口只回「是否已配置」。',
);

/** 校验规则 */
const rules = computed(() => ({
  name: [{ required: true, message: '请填写名称', trigger: 'blur' }],
  secret: isEdit.value ? [] : [{ required: true, message: '请粘贴 AccessKeySecret', trigger: 'blur' }],
}));

/** 打开时回填 */
watch(
  () => props.modelValue,
  (open) => {
    if (!open) return;
    const source = props.secret;
    form.value = {
      secretId: source?.secretId ?? '',
      name: source?.name ?? '',
      secretType: source?.secretType ?? 'sms',
      provider: source?.provider ?? 'aliyun',
      accessKey: source?.accessKey ?? '',
      secret: '',
      region: source?.region ?? '',
      endpoint: source?.endpoint ?? '',
      remark: source?.remark ?? '',
      status: source?.status ?? 1,
    };
    const parsed = parseTypes(source?.secretType);
    secretTypes.value = parsed.length ? parsed : ['sms'];
    formRef.value?.clearValidate?.();
  },
);

/** 提交 */
async function submit(): Promise<void> {
  if (saving.value) return;
  if (!secretTypes.value.length) {
    message.error('请至少选择一个密钥类型');
    return;
  }
  const valid = await formRef.value?.validate?.().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    const payload: SysAccountSecret = {
      ...form.value,
      name: form.value.name?.trim(),
      secretType: secretTypes.value.join(','),
      accessKey: form.value.accessKey?.trim(),
      region: form.value.region?.trim(),
      endpoint: form.value.endpoint?.trim(),
      remark: form.value.remark?.trim(),
    };
    if (isEdit.value) {
      await updateAccountSecret(form.value.secretId, payload);
      message.success('密钥已更新');
    } else {
      await createAccountSecret(payload);
      message.success('密钥已创建');
    }
    emit('saved');
    visible.value = false;
  } catch (e: any) {
    message.error(e?.message || '保存密钥失败');
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: 1fr;
}

.form-hint {
  display: block;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.form-static {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.w-full {
  width: 100%;
}

/* 下拉选项：类型色点 / 厂商 logo + 名称 */
.opt {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

/* 输入框里已选中的一项：logo + 名称，超长省略 */
.opt--picked {
  gap: 6px;
  min-width: 0;
  max-width: 100%;
}

.opt__text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.opt__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

</style>
