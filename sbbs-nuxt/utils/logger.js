// 日志管理工具
class Logger {
  constructor() {
    // 在生产环境禁用调试日志
    this.isDev = process.env.NODE_ENV === 'development'
    this.isClient = typeof window !== 'undefined'
  }

  // 开发环境或错误信息始终输出
  error(...args) {
    console.error(...args)
  }

  // 警告信息在生产环境也输出（但可以考虑关闭）
  warn(...args) {
    console.warn(...args)
  }

  // 调试信息只在开发环境输出
  log(...args) {
    if (this.isDev) {
      console.log(...args)
    }
  }

  // 信息日志只在开发环境输出
  info(...args) {
    if (this.isDev) {
      console.info(...args)
    }
  }

  // 调试日志只在开发环境输出
  debug(...args) {
    if (this.isDev) {
      console.log('[DEBUG]', ...args)
    }
  }

  // 带前缀的日志方法
  user(...args) {
    if (this.isDev) {
      console.log('👤', ...args)
    }
  }

  api(...args) {
    if (this.isDev) {
      console.log('🌐', ...args)
    }
  }

  auth(...args) {
    if (this.isDev) {
      console.log('🔐', ...args)
    }
  }

  nav(...args) {
    if (this.isDev) {
      console.log('🧭', ...args)
    }
  }

  cache(...args) {
    if (this.isDev) {
      console.log('💾', ...args)
    }
  }

  // 性能相关的日志（可选择性开启）
  perf(...args) {
    if (this.isDev) {
      console.log('⚡', ...args)
    }
  }
}

// 创建全局实例
const logger = new Logger()

export default logger

// 兼容旧代码的别名
export const log = logger.log.bind(logger)
export const error = logger.error.bind(logger)
export const warn = logger.warn.bind(logger)
export const info = logger.info.bind(logger)
export const debug = logger.debug.bind(logger) 