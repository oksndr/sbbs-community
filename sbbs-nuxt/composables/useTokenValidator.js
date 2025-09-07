import { ref, readonly } from 'vue'
import logger from '~/utils/logger'

// 全局验证状态
const isValidating = ref(false)

export const useTokenValidator = () => {
  const userStore = useUserStore()
  const { openLoginModal, forceOpenLoginModal } = useLoginModal()

  // 验证Token的核心函数
  const validateToken = async (showLoginOnFail = true) => {
    // 如果正在验证中，避免重复验证
    if (isValidating.value) {
      return { valid: false, reason: 'validating' }
    }

    // 检查是否有token
    const token = userStore.token
    if (!token) {
      if (showLoginOnFail) {
        // 如果是在已登录状态下发现没有Token，说明Token被清除了，强制显示弹窗
        if (userStore.isLoggedIn) {
          forceOpenLoginModal()
        } else {
          openLoginModal()
        }
      }
      return { valid: false, reason: 'no_token' }
    }

    isValidating.value = true

    try {
      // 使用代理路径发送验证请求
      const response = await fetch('/api/v1/validateToken', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({})
      })

      const data = await response.json()

      if (data.code === 200) {
        // Token有效
        return { valid: true, data: data }
      } else if (data.code === 401) {
        // Token无效，立即清除用户数据
        logger.auth('🚨 收到401响应，Token已失效，立即清除本地登录状态')
        userStore.clearUserData()
        
        if (showLoginOnFail) {
          // Token失效是紧急情况，强制显示登录弹窗，忽略稍后提醒状态
          forceOpenLoginModal()
        }
        
        return { valid: false, reason: 'invalid_token', data: data }
      } else {
        // 其他错误
        console.error('Token验证失败:', data.msg)
        return { valid: false, reason: 'validation_error', data: data }
      }
    } catch (error) {
      console.error('Token验证请求失败:', error)
      return { valid: false, reason: 'network_error', error }
    } finally {
      isValidating.value = false
    }
  }

  // 静默验证（不弹出登录窗口）
  const silentValidate = async () => {
    return await validateToken(false)
  }

  // 自动验证（简化逻辑，直接进行验证）
  const autoValidate = async () => {
    if (!userStore.isLoggedIn) {
      return { valid: true, reason: 'not_logged_in' }
    }
    
    return await silentValidate()
  }

  // 强制验证并弹出登录窗口（用于用户操作时）
  const forceValidateWithLogin = async () => {
    return await validateToken(true)
  }

  return {
    isValidating: readonly(isValidating),
    validateToken,
    silentValidate,
    autoValidate,
    forceValidateWithLogin
  }
} 