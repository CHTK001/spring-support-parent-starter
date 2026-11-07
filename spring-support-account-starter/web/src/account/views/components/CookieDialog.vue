<template>
  <ReDialog
    v-model="visible"
    :title="isEdit ? '编辑 Cookie' : '新增 Cookie'"
    width="560px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" class="ck-form">
      <div class="form-grid">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如 trae-web" maxlength="128" />
        </el-form-item>
        <el-form-item label="来源">
          <el-input v-model="form.source" placeholder="trae / duckduckgo / manual" maxlength="64" />
        </el-form-item>
      </div>
      <div class="form-grid">
        <el-form-item label="域名" prop="domain">
          <el-input v-model="form.domain" placeholder=".example.com 或 www.example.com" maxlength="255" />
        </el-form-item>
        <el-form-item label="路径">
          <el-input v-model="form.path" placeholder="/" maxlength="255" />
        </el-form-item>
      </div>
      <el-form-item label="Cookie 值" prop="cookieValue">
        <el-input
          v-model="form.cookieValue"
          type="textarea"
          :rows="3"
          :placeholder="secretPlaceholder"
          spellcheck="false"
        />
        <span class="form-hint">{{ secretHint }}</span>
      </el-form-item>
      <div class="form-grid">
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="expireAt"
            type="datetime"
            value-format="x"
            placeholder="留空表示长期有效"
            clearable
            class="w-full"
          />
        </el-form-item>
        <el-form-item v-if="isEdit" label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </div>
      <el-form-item label="归属">
        <span class="form-static">{{ ownerText }}</span>
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
 * Cookie 池新增 / 编辑弹窗。
 *
 * <p>Cookie 值同样只进不出：编辑时输入框永远留空，占位文案告诉用户
 * 「留空表示不修改」，只有重新粘贴才会覆盖。这样既不会把密文回显到页面，
 * 也不会因为用户只改了备注就把 Cookie 清空。</p>
 */
import { computed, ref, watch } from 'vue';
import { message } from '@repo/core';
import ReButton from '@repo/ui/re-button';
import ReDialog from '@repo/ui/re-dialog';

import {
  createAccountCookie,
  updateAccountCookie,
  type SysAccountCookie,
} from '../../../api/account';

const props = defineProps<{
  modelValue: boolean;
  cookie?: SysAccountCookie | null;
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
const isEdit = computed(() => Boolean(props.cookie?.cookieId));

/** 表单 */
const form = ref<SysAccountCookie>({
  cookieId: '',
  name: '',
  domain: '',
  path: '/',
  source: '',
  remark: '',
  status: 1,
  cookieValue: '',
});

/** 过期时间（毫秒） */
const expireAt = ref<string | number | null>(null);

/** 保存中 */
const saving = ref(false);

/** 归属展示 */
const ownerText = computed(() => (props.cookie?.owner ? `仅本人（${props.cookie.owner}）` : '部署级共享'));

/** 已配置过内容时提示掩码语义 */
const secretPlaceholder = computed(() =>
  isEdit.value && props.cookie?.hasValue ? '已配置（留空表示不修改）' : '粘贴 Cookie 原文',
);
const secretHint = computed(() =>
  isEdit.value && props.cookie?.hasValue
    ? '留空或含 **** 表示保留原有 Cookie，只有粘贴新内容才会覆盖。'
    : '写入后会以 enc: 加密存储，读接口只回「是否已配置」。',
);

/** 校验规则 */
const rules = computed(() => ({
  name: [{ required: true, message: '请填写名称', trigger: 'blur' }],
  domain: [{ required: true, message: '请填写域名', trigger: 'blur' }],
  cookieValue: isEdit.value ? [] : [{ required: true, message: '请粘贴 Cookie 内容', trigger: 'blur' }],
}));

/** 打开时回填 */
watch(
  () => props.modelValue,
  (open) => {
    if (!open) return;
    const source = props.cookie;
    form.value = {
      cookieId: source?.cookieId ?? '',
      name: source?.name ?? '',
      domain: source?.domain ?? '',
      path: source?.path || '/',
      source: source?.source ?? '',
      remark: source?.remark ?? '',
      status: source?.status ?? 1,
      cookieValue: '',
    };
    expireAt.value = source?.expireAt ? Number(source.expireAt) : null;
  },
  { immediate: true },
);

/** 提交 */
async function submit(): Promise<void> {
  if (!form.value.name?.trim()) {
    message.warning('请填写名称');
    return;
  }
  if (!form.value.domain?.trim()) {
    message.warning('请填写域名');
    return;
  }
  if (!isEdit.value && !form.value.cookieValue?.trim()) {
    message.warning('请粘贴 Cookie 内容');
    return;
  }
  const payload: SysAccountCookie = {
    ...form.value,
    name: form.value.name.trim(),
    domain: form.value.domain.trim(),
    path: form.value.path?.trim() || '/',
    expireAt: expireAt.value ? Number(expireAt.value) : null,
    // 留空 = 不修改：不能把空串当新值写回去
    cookieValue: form.value.cookieValue?.trim() ? form.value.cookieValue : null,
  };
  saving.value = true;
  try {
    if (isEdit.value) {
      await updateAccountCookie(payload.cookieId, payload);
      message.success('Cookie 已更新');
    } else {
      await createAccountCookie(payload);
      message.success('Cookie 已创建');
    }
    emit('saved');
    visible.value = false;
  } catch (e: any) {
    message.error(e?.message ?? '保存失败');
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.ck-form {
  padding-top: 4px;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 12px;
}
.form-static {
  font-size: 12.5px;
  color: var(--text-tertiary);
}
.form-hint {
  display: block;
  margin-top: 3px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--text-tertiary);
}
@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
