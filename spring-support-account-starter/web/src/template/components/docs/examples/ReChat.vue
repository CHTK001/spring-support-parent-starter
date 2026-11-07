<!--
  ReChat 示例（ReKit 示例站生成） — 所有参数均已填入示例数据
-->
<template>
  <ReChat
    url="https://example.com/api"
    apiUrl="https://example.com/image.png"
    :protocol="'openai'"
    model="示例模型"
    :models="models"
    modelsUrl="https://example.com/image.png"
    :capabilityGating="false"
    systemPrompt="示例"
    :temperature="0"
    :contextSize="0"
    :headers="headers"
    userId="示例"
    apiKey="示例"
    conversationId="示例"
    :showAvatar="false"
    placeholder="请输入"
    emptyText="示例文本"
    :menus="menus"
    :messages="messages"
    userAvatar="https://example.com/image.png"
    aiAvatar="https://example.com/image.png"
    sendText="示例文本"
    :renderMarkdown="false"
    :markdownRenderer="markdownRenderer"
    :showHeader="false"
    :showClear="false"
  />
</template>

<script setup lang="ts">
import ReChat from '@repo/ui/re-chat';
import { ref } from 'vue';

/** 从组件推导单个 prop 的类型；prop 不在组件上时退化为 any */
type PropOf<T, K extends string> = T extends new (...args: any) => any
  ? K extends keyof InstanceType<T>['$props'] ? InstanceType<T>['$props'][K] : any
  : any;

const models = ref<PropOf<typeof ReChat, 'models'>>([{ label: '示例', value: '示例', description: '示例', capabilities: [] }]);
const headers = ref<PropOf<typeof ReChat, 'headers'>>({ '示例': '示例' });
const menus = ref<PropOf<typeof ReChat, 'menus'>>([{ key: '示例', label: '示例', icon: undefined, onClick: (menu: any) => undefined }]);
const messages = ref<PropOf<typeof ReChat, 'messages'>>([{ id: '示例', content: '示例', isSelf: false, time: new Date(), status: 'sent' }]);
const markdownRenderer = ref<PropOf<typeof ReChat, 'markdownRenderer'>>((text: any) => '示例');
</script>
