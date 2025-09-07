// 前端WAF拦截器插件
import { recordApiRequest } from '~/utils/requestGuard'
import { getSystemHeaders, initSystemDetection } from '~/utils/systemDetector'

export default defineNuxtPlugin((nuxtApp) => {
  // 安全挑战状态
  const securityChallengeState = reactive({
    isVisible: false,
    pendingRequests: [],
    isProcessingRequest: false
  })

  // 全局组件方法，用于显示/隐藏安全挑战
  const showSecurityChallenge = () => {
    securityChallengeState.isVisible = true
  }

  const hideSecurityChallenge = () => {
    securityChallengeState.isVisible = false
    
    // 处理等待中的请求
    processPendingRequests()
  }

  // 处理等待中的请求
  const processPendingRequests = () => {
    if (securityChallengeState.isProcessingRequest || securityChallengeState.pendingRequests.length === 0) {
      return
    }
    
    securityChallengeState.isProcessingRequest = true
    
    // 取出最早的请求并执行
    const request = securityChallengeState.pendingRequests.shift()
    
    // 执行请求
    request.execute().finally(() => {
      securityChallengeState.isProcessingRequest = false
      // 继续处理下一个请求
      processPendingRequests()
    })
  }

  // 初始化系统检测
  nuxtApp.hook('app:mounted', async () => {
    await initSystemDetection()
  })

  // 拦截$fetch请求
  nuxtApp.hook('app:created', () => {
    const originalFetch = globalThis.$fetch

    globalThis.$fetch = async function (...args) {
      const url = args[0]
      let options = args[1] || {}
      
      // 为API请求添加系统信息头部
      if (typeof url === 'string' && url.startsWith('/api/')) {
        const systemHeaders = getSystemHeaders()
        options.headers = {
          ...options.headers,
          ...systemHeaders
        }
        args[1] = options
      }
      
      // 检查请求频率，决定是否需要挑战
      const needsChallenge = recordApiRequest(url)
      
      // 创建请求执行函数
      const executeRequest = () => originalFetch.apply(this, args)
      
      // 如果需要挑战并且挑战界面未显示，则显示挑战
      if (needsChallenge && !securityChallengeState.isVisible) {
        showSecurityChallenge()
        
        // 将请求加入队列
        return new Promise((resolve, reject) => {
          securityChallengeState.pendingRequests.push({
            execute: () => executeRequest().then(resolve).catch(reject)
          })
        })
      }
      
      // 如果挑战界面已显示，将请求加入队列
      if (securityChallengeState.isVisible) {
        return new Promise((resolve, reject) => {
          securityChallengeState.pendingRequests.push({
            execute: () => executeRequest().then(resolve).catch(reject)
          })
        })
      }
      
      // 正常执行请求
      return executeRequest()
    }
  })

  // 提供状态和方法给应用
  return {
    provide: {
      securityChallengeState,
      showSecurityChallenge,
      hideSecurityChallenge
    }
  }
}) 