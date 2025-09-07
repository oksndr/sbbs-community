// 系统真实性检测工具
class SystemDetector {
  constructor() {
    this.systemInfo = null
    this.screenInfo = null
    this.isInitialized = false
  }

  /**
   * 初始化系统检测
   */
  async init() {
    if (this.isInitialized) {
      return
    }

    this.systemInfo = this.detectRealSystem()
    this.screenInfo = this.detectScreenInfo()
    this.isInitialized = true

    // 存储到本地，避免重复检测
    localStorage.setItem('system_detector_info', JSON.stringify({
      system: this.systemInfo,
      screen: this.screenInfo,
      timestamp: Date.now()
    }))
  }

  /**
   * 检测真实的操作系统信息
   */
  detectRealSystem() {
    const nav = navigator
    const platform = nav.platform || ''
    const userAgent = nav.userAgent || ''
    
    let os = 'unknown'
    let platformType = 'unknown'
    let browser = 'unknown'

    // 通过platform属性检测（更难伪造）
    if (platform.includes('Win')) {
      os = 'windows'
      platformType = 'desktop'
    } else if (platform.includes('Mac')) {
      os = 'macos'
      platformType = 'desktop'
    } else if (platform.includes('Linux')) {
      os = 'linux'
      platformType = 'desktop'
    } else if (/iPhone|iPad|iPod/.test(platform)) {
      os = 'ios'
      platformType = platform.includes('iPad') ? 'tablet' : 'mobile'
    } else if (platform.includes('Android')) {
      os = 'android'
      platformType = 'mobile'
    }

    // 如果platform检测失败，回退到userAgent检测
    if (os === 'unknown') {
      const ua = userAgent.toLowerCase()
      if (ua.includes('windows')) {
        os = 'windows'
        platformType = 'desktop'
      } else if (ua.includes('macintosh') || ua.includes('mac os')) {
        os = 'macos'
        platformType = 'desktop'
      } else if (ua.includes('linux')) {
        os = 'linux'
        platformType = 'desktop'
      } else if (ua.includes('android')) {
        os = 'android'
        platformType = 'mobile'
      } else if (ua.includes('iphone') || ua.includes('ipad')) {
        os = 'ios'
        platformType = ua.includes('ipad') ? 'tablet' : 'mobile'
      }
    }

    // 检测浏览器
    if (window.chrome && !window.edg) {
      browser = 'chrome'
    } else if (typeof InstallTrigger !== 'undefined') {
      browser = 'firefox'
    } else if (window.safari && !window.chrome) {
      browser = 'safari'
    } else if (window.edg) {
      browser = 'edge'
    }

    // 额外的检测方法（WebGL指纹等）
    const additionalInfo = this.getAdditionalFingerprint()

    return {
      os,
      platform: platformType,
      browser,
      platformProperty: platform,
      ...additionalInfo
    }
  }

  /**
   * 检测屏幕信息
   */
  detectScreenInfo() {
    const screen = window.screen
    return {
      width: screen.width,
      height: screen.height,
      availWidth: screen.availWidth,
      availHeight: screen.availHeight,
      colorDepth: screen.colorDepth,
      pixelDepth: screen.pixelDepth,
      devicePixelRatio: window.devicePixelRatio || 1
    }
  }

  /**
   * 获取额外的浏览器指纹信息
   */
  getAdditionalFingerprint() {
    const info = {}

    // 时区检测
    try {
      info.timezone = Intl.DateTimeFormat().resolvedOptions().timeZone
      info.timezoneOffset = new Date().getTimezoneOffset()
    } catch (e) {
      info.timezone = 'unknown'
      info.timezoneOffset = 0
    }

    // 语言检测
    info.language = navigator.language || navigator.languages?.[0] || 'unknown'
    info.languages = navigator.languages ? [...navigator.languages] : []

    // 硬件信息
    info.hardwareConcurrency = navigator.hardwareConcurrency || 0
    info.deviceMemory = navigator.deviceMemory || 0

    // 触摸支持检测
    info.touchSupport = 'ontouchstart' in window || navigator.maxTouchPoints > 0

    return info
  }

  /**
   * 获取系统信息（用于发送给服务器）
   */
  getSystemInfo() {
    if (!this.isInitialized) {
      console.warn('SystemDetector not initialized')
      return null
    }
    return this.systemInfo
  }

  /**
   * 获取屏幕信息（用于发送给服务器）
   */
  getScreenInfo() {
    if (!this.isInitialized) {
      console.warn('SystemDetector not initialized')
      return null
    }
    return this.screenInfo
  }

  /**
   * 从本地存储恢复信息（避免每次都重新检测）
   */
  loadFromCache() {
    try {
      const cached = localStorage.getItem('system_detector_info')
      if (cached) {
        const data = JSON.parse(cached)
        // 检查缓存是否过期（24小时）
        if (Date.now() - data.timestamp < 24 * 60 * 60 * 1000) {
          this.systemInfo = data.system
          this.screenInfo = data.screen
          this.isInitialized = true
          return true
        }
      }
    } catch (e) {
      console.warn('Failed to load system detector cache:', e)
    }
    return false
  }
}

// 创建全局实例
export const systemDetector = new SystemDetector()

/**
 * 获取系统信息头部（用于HTTP请求）
 */
export function getSystemHeaders() {
  if (!systemDetector.isInitialized) {
    return {}
  }

  const systemInfo = systemDetector.getSystemInfo()
  const screenInfo = systemDetector.getScreenInfo()

  return {
    'X-Real-System': JSON.stringify(systemInfo),
    'X-Screen-Info': JSON.stringify(screenInfo)
  }
}

/**
 * 初始化系统检测（在应用启动时调用）
 */
export async function initSystemDetection() {
  // 先尝试从缓存加载
  if (!systemDetector.loadFromCache()) {
    // 缓存无效，重新检测
    await systemDetector.init()
  }
  
  console.log('系统检测已初始化:', {
    system: systemDetector.getSystemInfo(),
    screen: systemDetector.getScreenInfo()
  })
} 