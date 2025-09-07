import { ref, readonly } from 'vue'
import logger from '~/utils/logger'

// 全局的登录弹窗状态
const showLoginModal = ref(false)

// 检查是否在"稍后提醒"期间
const isInRemindLaterPeriod = () => {
  if (typeof window === 'undefined') return false
  
  try {
    const remindLaterTime = localStorage.getItem('loginRemindLater')
    if (!remindLaterTime) {
      logger.auth('⏰ 无稍后提醒记录')
      return false
    }
    
    const remindTime = parseInt(remindLaterTime)
    const now = Date.now()
    const remainingMinutes = Math.round((remindTime - now) / (1000 * 60))
    
    logger.auth(`⏰ 稍后提醒状态检查：剩余 ${remainingMinutes} 分钟`)
    
    if (now < remindTime) {
      logger.auth(`⏰ 还在稍后提醒期间，剩余 ${remainingMinutes} 分钟`)
      return true // 还在"稍后提醒"期间
    } else {
      logger.auth('⏰ 稍后提醒时间已过，清除记录')
      // 时间已过，清除记录
      localStorage.removeItem('loginRemindLater')
      return false
    }
  } catch (error) {
    console.error('检查稍后提醒状态失败:', error)
    return false
  }
}

export const useLoginModal = () => {
  // 显示登录弹窗（会检查稍后提醒状态）
  const openLoginModal = () => {
    // 如果在"稍后提醒"期间，不显示弹窗
    if (isInRemindLaterPeriod()) {
      logger.auth('⏰ 当前在"稍后提醒"期间，不显示登录弹窗')
      return false
    }
    
    logger.auth('🔐 显示登录弹窗')
    showLoginModal.value = true
    return true
  }
  
  // 隐藏登录弹窗
  const closeLoginModal = () => {
    showLoginModal.value = false
  }

  // 强制显示登录弹窗（忽略稍后提醒状态，用于用户主动操作）
  const forceOpenLoginModal = () => {
    showLoginModal.value = true
    logger.auth('🔐 用户主动操作，强制显示登录弹窗')
  }
  
  // 检查登录状态，如果未登录则显示弹窗（用于UI交互操作）
  const requireLogin = () => {
    const userStore = useUserStore()
    
    // 对于点赞、评论等UI操作，只需检查本地登录状态
    // 因为没有登录的话，这些操作按钮根本不会显示或无法点击
    if (!userStore.checkLoginStatus()) {
      openLoginModal()
      return false
    }
    
    return true
  }

  // 需要Token验证的登录检查（用于重要操作）
  const requireLoginWithValidation = async () => {
    const userStore = useUserStore()
    
    // 首先检查本地登录状态
    if (!userStore.checkLoginStatus()) {
      openLoginModal()
      return false
    }
    
    // 如果本地有登录状态，进行Token验证
    try {
      const { forceValidateWithLogin } = useTokenValidator()
      
      // 进行Token验证
      logger.auth('🔍 开始验证Token有效性')
      const result = await forceValidateWithLogin()
      
      if (!result.valid) {
        // Token无效，登录弹窗已经在验证器中显示了
        return false
      }
      
      return true
    } catch (error) {
      console.error('登录验证失败:', error)
      openLoginModal()
      return false
    }
  }
  
  // 同步版本的登录检查（不验证Token，只检查本地状态）
  const requireLoginSync = () => {
    const userStore = useUserStore()
    if (!userStore.checkLoginStatus()) {
      openLoginModal()
      return false
    }
    return true
  }

  // 用户主动操作的登录检查（点赞、回复等，强制弹窗）
  const requireLoginForAction = () => {
    const userStore = useUserStore()
    if (!userStore.checkLoginStatus()) {
      forceOpenLoginModal()
      return false
    }
    return true
  }
  
  return {
    showLoginModal: readonly(showLoginModal),
    openLoginModal,
    forceOpenLoginModal,
    closeLoginModal,
    requireLogin,
    requireLoginWithValidation,
    requireLoginSync,
    requireLoginForAction
  }
} 