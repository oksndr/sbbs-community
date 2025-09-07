// Cookie工具函数 - 支持客户端和服务端

/**
 * 从客户端获取cookie值
 * @param {string} name - cookie名称
 * @returns {string|null} cookie值
 */
export const getCookieValue = (name) => {
  if (process.client) {
    const match = document.cookie.match(new RegExp('(^| )' + name + '=([^;]+)'))
    return match ? match[2] : null
  }
  return null
}

/**
 * 从cookie字符串中获取值 (用于SSR)
 * @param {string} cookieString - cookie字符串
 * @param {string} name - cookie名称
 * @returns {string|null} cookie值
 */
export const getCookieValueFromString = (cookieString, name) => {
  if (!cookieString) return null
  const match = cookieString.match(new RegExp('(^| )' + name + '=([^;]+)'))
  return match ? match[2] : null
}

/**
 * 通用获取cookie值的方法 - 自动处理客户端和服务端
 * @param {string} name - cookie名称
 * @param {string} [cookieString] - 服务端传入的cookie字符串
 * @returns {string|null} cookie值
 */
export const getUniversalCookieValue = (name, cookieString = null) => {
  if (process.client) {
    return getCookieValue(name)
  } else if (cookieString) {
    return getCookieValueFromString(cookieString, name)
  }
  return null
}

/**
 * 设置cookie (仅客户端)
 * @param {string} name - cookie名称
 * @param {string} value - cookie值
 * @param {number} [days=30] - 过期天数，默认30天
 */
export const setCookie = (name, value, days = 30) => {
  if (process.client) {
    const expires = new Date()
    expires.setTime(expires.getTime() + days * 24 * 60 * 60 * 1000)
    document.cookie = `${name}=${value}; expires=${expires.toUTCString()}; path=/`
  }
}

/**
 * 删除cookie (仅客户端)
 * @param {string} name - cookie名称
 */
export const deleteCookie = (name) => {
  if (process.client) {
    document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/`
  }
}

/**
 * 从请求头中获取token (专门用于SSR)
 * @param {Object} event - Nuxt事件对象
 * @returns {string|null} token值
 */
export const getTokenFromRequest = (event) => {
  if (!event) return null
  
  // 尝试从Authorization头获取
  const authHeader = event.node?.req?.headers?.authorization
  if (authHeader && authHeader.startsWith('Bearer ')) {
    return authHeader.slice(7)
  }
  
  // 尝试从cookie中获取
  const cookieHeader = event.node?.req?.headers?.cookie
  if (cookieHeader) {
    // 先尝试获取Authorization cookie
    let token = getCookieValueFromString(cookieHeader, 'Authorization')
    if (token && token.startsWith('Bearer ')) {
      return token.slice(7)
    }
    
    // 再尝试获取token cookie
    token = getCookieValueFromString(cookieHeader, 'token')
    if (token) {
      return token
    }
  }
  
  return null
} 