// UA验证配置
export const UA_VALIDATION_CONFIG = {
  // 是否启用UA验证
  enabled: true,
  
  // 验证模式
  // 'strict': 严格模式，任何不匹配都拒绝
  // 'loose': 宽松模式，只拒绝明显的伪造
  // 'log': 仅记录模式，不拒绝请求
  mode: 'strict',
  
  // 白名单路径（这些路径不进行UA验证）
  whitelist: [
    '/api/health',
    '/api/status',
    '/api/public'
  ],
  
  // 验证规则配置
  rules: {
    // 是否验证操作系统匹配
    validateOS: true,
    
    // 是否验证平台类型匹配（桌面/移动）
    validatePlatform: true,
    
    // 是否验证浏览器匹配（较宽松，因为用户可能切换浏览器）
    validateBrowser: false,
    
    // 是否验证屏幕分辨率合理性
    validateScreen: true,
    
    // 是否验证时区信息
    validateTimezone: false
  },
  
  // 屏幕分辨率验证规则
  screenValidation: {
    // 移动设备最大分辨率
    mobile: {
      maxWidth: 1920,
      maxHeight: 1920,
      minWidth: 240,
      minHeight: 320
    },
    
    // 桌面设备分辨率
    desktop: {
      minWidth: 800,
      minHeight: 600,
      maxWidth: 8192,
      maxHeight: 8192
    },
    
    // 色深范围
    colorDepth: {
      min: 16,
      max: 32
    }
  },
  
  // 可疑请求处理
  suspiciousRequestHandling: {
    // 是否记录可疑请求
    logSuspicious: true,
    
    // 是否对可疑IP进行临时封禁
    tempBan: false,
    
    // 临时封禁时长（毫秒）
    banDuration: 10 * 60 * 1000, // 10分钟
    
    // 是否发送通知
    sendNotification: false
  },
  
  // 错误响应配置
  errorResponse: {
    // 返回的HTTP状态码
    statusCode: 403,
    
    // 错误消息
    message: 'User Agent validation failed',
    
    // 是否返回详细错误信息（生产环境建议关闭）
    includeDetails: false
  },
  
  // 缓存配置
  cache: {
    // 系统信息缓存时长（毫秒）
    systemInfoTTL: 24 * 60 * 60 * 1000, // 24小时
    
    // 验证结果缓存时长
    validationResultTTL: 60 * 60 * 1000, // 1小时
  },
  
  // 开发环境配置
  development: {
    // 开发环境是否启用验证
    enabled: false,
    
    // 开发环境是否显示详细日志
    verboseLogging: true
  }
}

/**
 * 获取当前环境的配置
 */
export function getUAValidationConfig() {
  const config = { ...UA_VALIDATION_CONFIG }
  
  // 开发环境特殊处理
  if (process.env.NODE_ENV === 'development') {
    config.enabled = config.development.enabled
    config.mode = config.development.verboseLogging ? 'log' : config.mode
  }
  
  return config
}

/**
 * 检查路径是否在白名单中
 */
export function isWhitelisted(path) {
  const config = getUAValidationConfig()
  return config.whitelist.some(pattern => {
    if (pattern.includes('*')) {
      // 支持通配符匹配
      const regexPattern = pattern.replace(/\*/g, '.*')
      return new RegExp(`^${regexPattern}$`).test(path)
    }
    return path.startsWith(pattern)
  })
}

/**
 * 更新配置（运行时）
 */
export function updateUAValidationConfig(newConfig) {
  Object.assign(UA_VALIDATION_CONFIG, newConfig)
}

/**
 * 重置配置为默认值
 */
export function resetUAValidationConfig() {
  // 这里可以重置为默认配置
  console.log('UA validation config reset to defaults')
} 