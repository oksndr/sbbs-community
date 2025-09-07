// 前端WAF实现 - 请求监控与指纹识别
import { nanoid } from 'nanoid'

// 配置项
const CONFIG = {
  // 在指定时间窗口内允许的最大请求数
  MAX_REQUESTS_PER_WINDOW: 10,
  // 时间窗口大小(毫秒)
  TIME_WINDOW_MS: 5000,
  // 挑战通过后的豁免时间(毫秒)
  IMMUNITY_PERIOD_MS: 30 * 60 * 1000, // 30分钟
}

// 存储区键名
const STORAGE_KEYS = {
  DEVICE_ID: 'waf_device_id',
  REQUEST_HISTORY: 'waf_req_history',
  CHALLENGE_PASSED: 'waf_challenge_passed',
  CHALLENGE_PASS_TIME: 'waf_pass_time',
}

/**
 * 生成或获取设备标识符
 */
export function getDeviceId() {
  let deviceId = localStorage.getItem(STORAGE_KEYS.DEVICE_ID)
  
  if (!deviceId) {
    // 简单的浏览器指纹 - 实际应用中可使用更复杂的指纹库
    const userAgent = navigator.userAgent
    const screenPrint = `${screen.width}x${screen.height}x${screen.colorDepth}`
    const timeZone = new Date().getTimezoneOffset()
    const lang = navigator.language
    
    // 生成随机ID并与指纹信息结合
    deviceId = nanoid() + '-' + btoa(`${userAgent}|${screenPrint}|${timeZone}|${lang}`.slice(0, 100))
    localStorage.setItem(STORAGE_KEYS.DEVICE_ID, deviceId)
  }
  
  return deviceId
}

/**
 * 记录API请求并检查是否触发挑战
 */
export function recordApiRequest(url) {
  // 如果已通过挑战且在豁免期内，直接放行
  if (hasValidChallengePassed()) {
    return false // 不需要挑战
  }

  const now = Date.now()
  const deviceId = getDeviceId()
  
  // 获取历史请求记录
  let requestHistory = JSON.parse(localStorage.getItem(STORAGE_KEYS.REQUEST_HISTORY) || '[]')
  
  // 清理过期记录（仅保留当前时间窗口内的记录）
  requestHistory = requestHistory.filter(
    req => now - req.timestamp < CONFIG.TIME_WINDOW_MS
  )
  
  // 添加当前请求
  requestHistory.push({
    url,
    timestamp: now,
    deviceId
  })
  
  // 保存更新后的历史
  localStorage.setItem(STORAGE_KEYS.REQUEST_HISTORY, JSON.stringify(requestHistory))
  
  // 检查是否需要触发挑战
  return requestHistory.length > CONFIG.MAX_REQUESTS_PER_WINDOW
}

/**
 * 检查是否已通过挑战且在豁免期内
 */
export function hasValidChallengePassed() {
  const challengePassed = localStorage.getItem(STORAGE_KEYS.CHALLENGE_PASSED) === 'true'
  if (!challengePassed) return false
  
  const passTime = parseInt(localStorage.getItem(STORAGE_KEYS.CHALLENGE_PASS_TIME) || '0')
  const now = Date.now()
  
  // 检查是否在豁免期内
  return now - passTime < CONFIG.IMMUNITY_PERIOD_MS
}

/**
 * 标记挑战已通过
 */
export function markChallengeAsPassed() {
  localStorage.setItem(STORAGE_KEYS.CHALLENGE_PASSED, 'true')
  localStorage.setItem(STORAGE_KEYS.CHALLENGE_PASS_TIME, Date.now().toString())
}

/**
 * 重置挑战状态
 */
export function resetChallengeState() {
  localStorage.setItem(STORAGE_KEYS.CHALLENGE_PASSED, 'false')
  localStorage.setItem(STORAGE_KEYS.CHALLENGE_PASS_TIME, '0')
} 