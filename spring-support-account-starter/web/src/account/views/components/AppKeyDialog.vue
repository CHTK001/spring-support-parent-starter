<template>
  <ReDialog
    v-model="visible"
    :title="isEdit ? '编辑访问密钥' : '新增访问密钥'"
    width="520px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @closed="reset"
  >
    <!-- 新建 / 轮换后明文只出现这一次，关掉就再也取不回来 -->
    <div v-if="plainSecret" class="reveal">
      <div class="reveal-head">
        <span class="reveal-badge">仅显示一次</span>
        <b>Secret</b>
      </div>
      <div class="reveal-value">{{ plainSecret }}</div>
      <p class="reveal-tip">关闭弹窗后无法再次查看，请现在复制并保存到调用方的配置里。</p>
      <div class="reveal-ops">
        <ReButton type="primary" @click="copySecret">复制 Secret</ReButton>
        <ReButton @click="visible = false">我已保存</ReButton>
      </div>
    </div>

    <el-form v-else ref="formRef" :model="form" :rules="rules" label-width="96px" class="ak-form">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="如 采集调度器 / 运维脚本" maxlength="128" />
      </el-form-item>
      <el-form-item label="归属">
        <span class="form-static">{{ isEdit ? ownerText : '仅本人（创建后按登录用户归属）' }}</span>
      </el-form-item>
      <el-form-item label="过期时间">
        <el-date-picker
          v-model="expireAt"
          type="datetime"
          value-format="x"
          placeholder="留空表示永不过期"
          clearable
          class="w-full"
        />
      </el-form-item>
      <el-form-item label="IP 白名单">
        <el-input v-model="form.ipWhitelist" placeholder="逗号分隔，如 10.0.0.1,10.0.0.0/24" maxlength="512" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="用途说明" maxlength="255" />
      </el-form-item>
      <el-form-item v-if="isEdit" label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">停用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <template #footer>
      <template v-if="!plainSecret">
        <ReButton @click="visible = false">取消</ReButton>
        <ReButton type="primary" :loading="saving" @click="submit">{{ isEdit ? '保存' : '创建' }}</ReButton>
      </template>
    </template>
  </ReDialog>
</template>

<script setup lang="ts">
/**
 * 访问密钥（AppKey）新增 / 编辑弹窗。
 *
 * <p>Secret 由后端生成并以 {@code enc:} 落库，创建或轮换时**只回显一次**
 * （{@code plainSecret}）。所以这里把「显示明文」做成独立状态：拿到明文后
 * 表单整体让位给一段可复制的文本，避免用户以为过后还能在列表里再看到。</p>
 */
import { computed, ref, watch } from 'vue';
import { message } from '@repo/core';
import ReButton from '@repo/ui/re-button';
import ReDialog from '@repo/ui/re-dialog';

import {
  createAccountAppKey,
  updateAccountAppKey,
  type SysAccountAppKey,
} from '../../../api/account';
import { copyText } from '../../../shared/clipboard';

const props = defineProps<{
  modelValue: boolean;
  appKey?: SysAccountAppKey | null;
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
const isEdit = computed(() => Boolean(props.appKey?.appKeyId));

/** 表单 */
const form = ref<SysAccountAppKey>({ appKeyId: '', name: '', ipWhitelist: '', remark: '', status: 1 });

/** 过期时间（毫秒，字符串形态的 x 时间戳） */
const expireAt = ref<string | number | null>(null);

/** 保存中 */
const saving = ref(false);
/** 一次性明文 */
const plainSecret = ref('');

/** 归属展示 */
const ownerText = computed(() => (props.appKey?.owner ? `仅本人（${props.appKey.owner}）` : '部署级共享'));

/** 校验规则 */
const rules = {
  name: [{ required: true, message: '请填写名称', trigger: 'blur' }],
};

/** 打开时回填 */
watch(
  () => props.modelValue,
  (open) => {
    if (!open) return;
    plainSecret.value = '';
    const source = props.appKey;
    form.value = {
      appKeyId: source?.appKeyId ?? '',
      name: source?.name ?? '',
      ipWhitelist: source?.ipWhitelist ?? '',
      remark: source?.remark ?? '',
      status: source?.status ?? 1,
    };
    expireAt.value = source?.expireAt ? Number(source.expireAt) : null;
  },
  { immediate: true },
);

/**
 * 提交。
 */
async function submit(): Promise<void> {
  if (!form.value.name?.trim()) {
    message.warning('请填写名称');
    return;
  }
  const payload: SysAccountAppKey = {
    ...form.value,
    name: form.value.name.trim(),
    expireAt: expireAt.value ? Number(expireAt.value) : null,
  };
  saving.value = true;
  try {
    const saved = isEdit.value
      ? await updateAccountAppKey(payload.appKeyId, payload)
      : await createAccountAppKey(payload);
    emit('saved');
    if (saved?.plainSecret) {
      plainSecret.value = saved.plainSecret;
    } else {
      message.success(isEdit.value ? '访问密钥已更新' : '访问密钥已创建');
      visible.value = false;
    }
  } catch (e: any) {
    message.error(e?.message ?? '保存失败');
  } finally {
    saving.value = false;
  }
}

/** 复制明文 Secret */
async function copySecret(): Promise<void> {
  const ok = await copyText(plainSecret.value);
  if (ok) message.success('Secret 已复制');
  else message.error('复制失败，请手动选中文本');
}

/** 关闭清场 */
function reset(): void {
  plainSecret.value = '';
  saving.value = false;
}
</script>

<style scoped>
.ak-form {
  padding-top: 4px;
}
.form-static {
  font-size: 12.5px;
  color: var(--text-tertiary);
}

.reveal {
  border: 1px solid var(--sev-p2-border);
  border-radius: var(--radius-md);
  background: var(--sev-p2-bg);
  padding: 12px 14px 14px;
}
.reveal-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.reveal-badge {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--sev-p2-text);
  color: #fff;
  font-size: 10px;
  letter-spacing: 0.04em;
}
.reveal-head b {
  font-size: 12.5px;
  color: var(--sev-p2-text);
}
.reveal-value {
  padding: 9px 11px;
  border: 1px solid var(--sev-p2-border);
  border-radius: var(--radius-sm);
  background: var(--bg-container);
  font-family: var(--font-mono, ui-monospace, Consolas, monospace);
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-primary);
  word-break: break-all;
  user-select: all;
}
.reveal-tip {
  margin: 8px 0 0;
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--text-secondary);
}
.reveal-ops {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
</style>
