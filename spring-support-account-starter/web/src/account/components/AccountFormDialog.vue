<template>
  <ReDialog
    v-model="visible"
    :title="isEdit ? '编辑账号' : '新增账号'"
    width="920"
    append-to-body
    destroy-on-close
  >
    <AccountFormBody
      :active="visible"
      :account="account"
      :draft="draft"
      @saved="onSaved"
      @cancel="visible = false"
    />
  </ReDialog>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import ReDialog from '@repo/ui/re-dialog';
import AccountFormBody from './AccountFormBody.vue';
import type { SysAccount } from '../api/account';

/**
 * 账号新增 / 编辑弹窗（壳）。
 *
 * <p>字段、回填与提交逻辑都在 {@code AccountFormBody} 里：账号资产页的「新增 / 编辑」
 * 是常驻分屏表单，和这里的弹窗共用同一份表单体，避免两处各写一份之后字段漂移。</p>
 *
 * <p>本组件只负责显隐、标题与宽度，保存成功后关闭并把账号抛给宿主。</p>
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

/** 弹窗显隐双向绑定 */
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

/** 是否编辑（只影响标题文案） */
const isEdit = computed(() => Boolean(props.account?.accountId));

/**
 * 表单体保存成功。
 *
 * @param account 已保存的账号
 */
function onSaved(account: SysAccount): void {
  visible.value = false;
  emit('saved', account);
}
</script>
