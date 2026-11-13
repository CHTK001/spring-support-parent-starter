<template>
  <ReDialog
    v-model="visible"
    :title="isEdit ? '编辑密钥' : '新增密钥'"
    width="560px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" class="sk-form">
      <div class="form-grid">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如 阿里云短信-生产" maxlength="128" />
        </el-form-item>
        <el-form-item label="密钥类型" prop="secretType">
          <el-select v-model="form.secretType" placeholder="选择密钥类型" class="w-full">
            <el-option label="短信" value="sms" />
            <el-option label="邮件" value="mail" />
            <el-option label="对象存储" value="storage" />
            <el-option label="支付" value="payment" />
            <el-option label="通用 API" value="api" />
            <el-option label="其他" value="other" />
          </el-select>
          <span class="form-hint">账号引用密钥时按类型挑选（如短信账号只挑短信密钥）。</span>
        </el-form-item>
      </div>
      <div class="form-grid">
        <el-form-item label="用途 / 厂商">
          <el-select v-model="form.provider" placeholder="选择或输入" allow-create filterable clearable class="w-full">
            <el-option label="阿里云" value="aliyun" />
            <el-option label="腾讯云" value="tencent" />
            <el-option label="华为云" value="huawei" />
            <el-option label="自定义" value="custom" />
          </el-select>
        </el-form-item>
        <el-form-item label="AccessKeyId">
          <el-input v-model="form.accessKey" placeholder="如 LTAI5t..." maxlength="255" spellcheck="false" />
        </el-form-item>
      </div>
      <el-form-item label="AccessKeySecret" prop="secret">
        <el-input
          v-model="form.secret"
          type="textarea"
          :rows="3"
          :placeholder="secretPlaceholder"
          spellcheck="false"
        />
        <span class="form-hint">{{ secretHint }}</span>
      </el-form-item>
      <div class="form-grid">
        <el-form-item label="Region">
          <el-input v-model="form.region" placeholder="如 cn-hangzhou" maxlength="64" />
        </el-form-item>
        <el-form-item v-if="isEdit" label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
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

import {
  createAccountSecret,
  updateAccountSecret,
  type SysAccountSecret,
} from '../../api/account';

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
  secretType: [{ required: true, message: '请选择密钥类型', trigger: 'change' }],
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
    formRef.value?.clearValidate?.();
  },
);

/** 提交 */
async function submit(): Promise<void> {
  if (saving.value) return;
  const valid = await formRef.value?.validate?.().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    const payload: SysAccountSecret = {
      ...form.value,
      name: form.value.name?.trim(),
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
</style>
