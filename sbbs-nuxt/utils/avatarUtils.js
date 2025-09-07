/**
 * 头像工具函数
 * 提供默认头像和头像URL处理
 */

// 默认头像URL - 使用一个可靠的在线服务
const DEFAULT_AVATAR_URL = 'https://img.opui.news/i/0/2025/05/24/001333-0.webp'

/**
 * 获取用户头像URL
 * @param {string|null} avatarUrl - 用户头像URL
 * @param {number|string} [userId] - 用户ID，用于生成个性化默认头像
 * @returns {string} 头像URL
 */
export const getUserAvatarUrl = (avatarUrl, userId = null) => {
  // 如果有头像URL且不为空，直接返回
  if (avatarUrl && avatarUrl.trim()) {
    return avatarUrl
  }
  
  // 如果没有头像，返回默认头像
  return DEFAULT_AVATAR_URL
}

/**
 * 获取默认头像URL
 * @returns {string} 默认头像URL
 */
export const getDefaultAvatarUrl = () => {
  return DEFAULT_AVATAR_URL
}

/**
 * 检查头像URL是否有效
 * @param {string} avatarUrl - 头像URL
 * @returns {boolean} 是否有效
 */
export const isValidAvatarUrl = (avatarUrl) => {
  if (!avatarUrl || typeof avatarUrl !== 'string') {
    return false
  }
  
  const trimmed = avatarUrl.trim()
  if (!trimmed) {
    return false
  }
  
  // 检查是否是有效的URL格式
  try {
    new URL(trimmed)
    return true
  } catch {
    // 如果不是完整URL，检查是否是相对路径
    return trimmed.startsWith('/') || trimmed.startsWith('./') || trimmed.startsWith('../')
  }
} 