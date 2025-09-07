import logger from '~/utils/logger'

export default defineNuxtPlugin((nuxtApp) => {
  // 将logger注册到全局
  nuxtApp.provide('logger', logger)
  
  // 在开发环境中，将logger暴露到window对象供调试使用
  if (logger.isDev && typeof window !== 'undefined') {
    window.logger = logger
  }
}) 