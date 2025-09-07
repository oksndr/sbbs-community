// UA真实性验证中间件
import { getUAValidationConfig, isWhitelisted } from '~/utils/uaValidationConfig'

export default defineEventHandler(async (event) => {
  const config = getUAValidationConfig()
  
  // 如果UA验证被禁用，直接通过
  if (!config.enabled) {
    return
  }
  
  // 只处理API请求
  if (!event.node.req.url?.startsWith('/api/')) {
    return
  }
  
  // 检查是否在白名单中
  if (isWhitelisted(event.node.req.url)) {
    return
  }

  const userAgent = getHeader(event, 'user-agent') || ''
  const xRealSystem = getHeader(event, 'x-real-system') || ''
  const xScreenInfo = getHeader(event, 'x-screen-info') || ''
  
  // 如果客户端没有发送真实系统信息，根据模式处理
  if (!xRealSystem) {
    if (config.mode === 'strict') {
      throw createError({
        statusCode: config.errorResponse.statusCode,
        statusMessage: 'Missing system information'
      })
    }
    return // 宽松模式或日志模式允许通过
  }

  // 解析UA中的操作系统信息
  const uaSystemInfo = parseUserAgentSystem(userAgent)
  
  // 解析客户端发送的真实系统信息
  let realSystemInfo
  try {
    realSystemInfo = JSON.parse(xRealSystem)
  } catch (e) {
    if (config.mode === 'log') {
      console.warn('无效的系统信息格式:', { userAgent, xRealSystem })
      return
    }
    
    throw createError({
      statusCode: config.errorResponse.statusCode,
      statusMessage: config.errorResponse.includeDetails 
        ? 'Invalid system information format' 
        : config.errorResponse.message
    })
  }

  // 验证系统信息是否匹配
  const validationResult = validateSystemMatch(uaSystemInfo, realSystemInfo, xScreenInfo, config)
  
  if (!validationResult.isValid) {
    // 记录可疑请求
    if (config.suspiciousRequestHandling.logSuspicious) {
      console.warn('检测到可疑UA请求:', {
        ip: getClientIP(event),
        userAgent,
        realSystem: realSystemInfo,
        reason: validationResult.reason,
        timestamp: new Date().toISOString()
      })
    }
    
    // 根据模式处理
    if (config.mode === 'log') {
      return // 仅记录模式，不拒绝请求
    }
    
    throw createError({
      statusCode: config.errorResponse.statusCode,
      statusMessage: config.errorResponse.includeDetails 
        ? `UA validation failed: ${validationResult.reason}`
        : config.errorResponse.message
    })
  }
})

/**
 * 从User Agent中解析操作系统信息
 */
function parseUserAgentSystem(userAgent: string) {
  const ua = userAgent.toLowerCase()
  
  let os = 'unknown'
  let platform = 'unknown'
  
  // 检测操作系统
  if (ua.includes('windows nt 10')) {
    os = 'windows'
    platform = 'desktop'
  } else if (ua.includes('windows nt')) {
    os = 'windows' 
    platform = 'desktop'
  } else if (ua.includes('macintosh') || ua.includes('mac os x')) {
    os = 'macos'
    platform = 'desktop'
  } else if (ua.includes('x11') || ua.includes('linux')) {
    os = 'linux'
    platform = 'desktop'
  } else if (ua.includes('android')) {
    os = 'android'
    platform = 'mobile'
  } else if (ua.includes('iphone') || ua.includes('ipad')) {
    os = 'ios'
    platform = ua.includes('ipad') ? 'tablet' : 'mobile'
  }

  // 检测浏览器
  let browser = 'unknown'
  if (ua.includes('chrome') && !ua.includes('edg')) {
    browser = 'chrome'
  } else if (ua.includes('firefox')) {
    browser = 'firefox'
  } else if (ua.includes('safari') && !ua.includes('chrome')) {
    browser = 'safari'
  } else if (ua.includes('edg')) {
    browser = 'edge'
  }

  return { os, platform, browser }
}

/**
 * 验证系统信息是否匹配
 */
function validateSystemMatch(uaInfo: any, realInfo: any, screenInfo: string, config: any) {
  const rules = config.rules
  
  // 基本操作系统验证
  if (rules.validateOS && uaInfo.os !== realInfo.os) {
    return { isValid: false, reason: `OS mismatch: UA=${uaInfo.os}, Real=${realInfo.os}` }
  }
  
  // 平台类型验证（桌面/移动端）
  if (rules.validatePlatform && uaInfo.platform !== realInfo.platform) {
    return { isValid: false, reason: `Platform mismatch: UA=${uaInfo.platform}, Real=${realInfo.platform}` }
  }

  // 浏览器特征验证
  if (rules.validateBrowser && realInfo.browser && uaInfo.browser !== realInfo.browser) {
    return { isValid: false, reason: `Browser mismatch: UA=${uaInfo.browser}, Real=${realInfo.browser}` }
  }

  // 屏幕分辨率合理性验证
  if (rules.validateScreen && screenInfo) {
    const screenValidation = validateScreenInfo(uaInfo, screenInfo, config)
    if (!screenValidation.isValid) {
      return { isValid: false, reason: `Screen validation failed: ${screenValidation.reason}` }
    }
  }

  // 时区验证
  if (rules.validateTimezone && realInfo.timezone) {
    // 这里可以添加时区验证逻辑
    // 例如检查时区是否与IP地理位置匹配
  }

  return { isValid: true, reason: 'All validations passed' }
}

/**
 * 获取客户端IP地址
 */
function getClientIP(event: any): string {
  const forwarded = getHeader(event, 'x-forwarded-for')
  const realIP = getHeader(event, 'x-real-ip')
  const cloudflareIP = getHeader(event, 'cf-connecting-ip')
  
  if (cloudflareIP) return cloudflareIP
  if (realIP) return realIP
  if (forwarded) return forwarded.split(',')[0].trim()
  
  return event.node.req.socket?.remoteAddress || 'unknown'
}

/**
 * 验证屏幕信息的合理性
 */
function validateScreenInfo(uaInfo: any, screenInfo: string, config: any) {
  try {
    const screen = JSON.parse(screenInfo)
    const { width, height, colorDepth } = screen
    const validation = config.screenValidation
    
    // 移动设备分辨率验证
    if (uaInfo.platform === 'mobile') {
      if (width > validation.mobile.maxWidth || height > validation.mobile.maxHeight) {
        return { isValid: false, reason: `Mobile screen too large: ${width}x${height}` }
      }
      if (width < validation.mobile.minWidth || height < validation.mobile.minHeight) {
        return { isValid: false, reason: `Mobile screen too small: ${width}x${height}` }
      }
    }
    
    // 桌面设备分辨率验证
    if (uaInfo.platform === 'desktop') {
      if (width < validation.desktop.minWidth || height < validation.desktop.minHeight) {
        return { isValid: false, reason: `Desktop screen too small: ${width}x${height}` }
      }
      if (width > validation.desktop.maxWidth || height > validation.desktop.maxHeight) {
        return { isValid: false, reason: `Desktop screen too large: ${width}x${height}` }
      }
    }
    
    // 色深验证
    if (colorDepth < validation.colorDepth.min || colorDepth > validation.colorDepth.max) {
      return { isValid: false, reason: `Invalid color depth: ${colorDepth}` }
    }
    
         return { isValid: true, reason: 'Screen validation passed' }
   } catch (e) {
     return { isValid: false, reason: 'Invalid screen info format' }
   }
 } 