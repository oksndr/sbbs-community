<template>
  <div>
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>
    
    <!-- 安全挑战组件 -->
    <SecurityChallenge 
      :isVisible="securityChallengeState.isVisible" 
      :onComplete="handleChallengeComplete" 
    />
  </div>
</template>

<script setup>
import { useUserStore } from '~/stores/user'
import { useTagsStore } from '~/stores/tags'

// 初始化用户状态
const userStore = useUserStore()

// 初始化标签状态
const tagsStore = useTagsStore()

// 获取安全挑战状态
const { $securityChallengeState, $hideSecurityChallenge } = useNuxtApp()
const securityChallengeState = $securityChallengeState

// 挑战完成处理函数
const handleChallengeComplete = () => {
  $hideSecurityChallenge()
}

// 初始化用户状态 - 支持SSR
if (process.server) {
  // 服务端：从请求头中获取cookie
  const event = await useRequestEvent()
  const cookieHeader = event?.node?.req?.headers?.cookie
  await userStore.initUserFromStorage(cookieHeader)
  
  // 同时在服务端初始化标签数据（SSR）
  await tagsStore.initTags(cookieHeader)
} else {
  // 客户端：只在SSR未初始化时才从本地存储加载用户数据
  onMounted(async () => {
    if (!userStore.isInitialized) {
      await userStore.initUserFromStorage()
    }
    // 客户端初始化标签数据（如果服务端没有获取到）
    tagsStore.initTags()
  })
}
</script>

<style>
/* 导入全局样式 */
@import 'github-markdown-css/github-markdown-light.css';
@import 'highlight.js/styles/github.css';
</style>
