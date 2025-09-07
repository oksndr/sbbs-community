// 积分奖励系统
// 管理用户每日行为的积分奖励记录

// 获取今天的日期字符串 (YYYY-MM-DD)
const getTodayKey = () => {
  const today = new Date()
  return today.toISOString().split('T')[0]
}

// 获取用户ID（优化版本 - 使用store）
const getUserId = () => {
  if (process.client) {
    // 直接从localStorage获取，但添加错误处理
    try {
      const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
      return userInfo.id || 'unknown'
    } catch (error) {
      console.error('从localStorage获取用户ID失败:', error)
      return 'unknown'
    }
  }
  return 'unknown'
}

// 优化版：异步获取用户ID（用于需要store的场景）
const getUserIdFromStore = async () => {
  if (process.client) {
    try {
      const { useUserStore } = await import('~/stores/user')
      const userStore = useUserStore()
      if (userStore.user && userStore.user.id) {
        return userStore.user.id
      }
    } catch (error) {
      console.warn('无法从store获取用户ID，回退到localStorage:', error)
    }
    
    // 回退方案
    return getUserId()
  }
  return 'unknown'
}

// 生成存储键
const getStorageKey = (userId, date, action) => {
  return `sbbs_points_${userId}_${date}_${action}`
}

// 积分奖励配置
export const POINTS_CONFIG = {
  POST_FIRST_DAILY: {
    base: 10,      // 基础积分
    bonus: 20,     // 每日首次奖励
    total: 30,     // 总积分
    name: '发帖奖励'
  },
  POST_NORMAL: {
    base: 10,      // 非首次发帖只有基础积分
    bonus: 0,
    total: 10,
    name: '发帖奖励'
  },
  COMMENT: {
    base: 5,       // 评论固定积分
    bonus: 0,
    total: 5,
    name: '评论奖励'
  },
  LIKE_FIRST_DAILY: {
    base: 0,       // 点赞基础积分
    bonus: 1,      // 每日首次点赞奖励
    total: 1,
    name: '点赞奖励'
  },
  LIKE_NORMAL: {
    base: 0,       // 非首次点赞无奖励
    bonus: 0,
    total: 0,
    name: '点赞'
  }
}

// 积分奖励管理器
export class PointsManager {
  constructor() {
    this.userId = getUserId()
    this.today = getTodayKey()
  }

  // 检查今天是否已经进行过某个行为
  hasActionToday(action) {
    if (!process.client) return false
    
    const key = getStorageKey(this.userId, this.today, action)
    return localStorage.getItem(key) === 'true'
  }

  // 记录今天已经进行过某个行为
  markActionToday(action) {
    if (!process.client) return
    
    const key = getStorageKey(this.userId, this.today, action)
    localStorage.setItem(key, 'true')
  }

  // 清理过期的记录（保留最近7天）
  cleanOldRecords() {
    if (!process.client) return
    
    try {
      const sevenDaysAgo = new Date()
      sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7)
      
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i)
        if (key && key.startsWith(`sbbs_points_${this.userId}_`)) {
          // 提取日期部分
          const datePart = key.split('_')[3]
          if (datePart && new Date(datePart) < sevenDaysAgo) {
            localStorage.removeItem(key)
          }
        }
      }
    } catch (error) {
      console.error('清理积分记录失败:', error)
    }
  }

  // 获取发帖奖励信息
  getPostReward() {
    const isFirstToday = !this.hasActionToday('post')
    
    if (isFirstToday) {
      this.markActionToday('post')
      return {
        isFirst: true,
        config: POINTS_CONFIG.POST_FIRST_DAILY,
        message: `🎉 今日首次发帖奖励！获得 ${POINTS_CONFIG.POST_FIRST_DAILY.total} 积分 (基础${POINTS_CONFIG.POST_FIRST_DAILY.base} + 首次奖励${POINTS_CONFIG.POST_FIRST_DAILY.bonus})`
      }
    } else {
      return {
        isFirst: false,
        config: POINTS_CONFIG.POST_NORMAL,
        message: `📝 发帖成功！获得 ${POINTS_CONFIG.POST_NORMAL.total} 积分`
      }
    }
  }

  // 获取评论奖励信息
  getCommentReward() {
    return {
      isFirst: false, // 评论没有首次奖励
      config: POINTS_CONFIG.COMMENT,
      message: `💬 评论成功！获得 ${POINTS_CONFIG.COMMENT.total} 积分`
    }
  }

  // 获取点赞奖励信息
  getLikeReward() {
    const isFirstToday = !this.hasActionToday('like')
    
    if (isFirstToday) {
      this.markActionToday('like')
      return {
        isFirst: true,
        config: POINTS_CONFIG.LIKE_FIRST_DAILY,
        message: `👍 今日首次点赞！获得 ${POINTS_CONFIG.LIKE_FIRST_DAILY.total} 积分`
      }
    } else {
      return {
        isFirst: false,
        config: POINTS_CONFIG.LIKE_NORMAL,
        message: null // 不显示提示
      }
    }
  }

  // 显示积分奖励通知 - 已禁用，由各个操作处理自己的toast
  showPointsReward(action) {
    // 已禁用，避免重复显示toast
    // let reward = null
    // 
    // switch (action) {
    //   case 'post':
    //     reward = this.getPostReward()
    //     break
    //   case 'comment':
    //     reward = this.getCommentReward()
    //     break
    //   case 'like':
    //     reward = this.getLikeReward()
    //     break
    //   default:
    //     return
    // }

    // if (reward && reward.message) {
    //   // 使用特殊的积分Toast样式
    //   this.showPointsToast(reward.message, reward.isFirst ? 'success' : 'info')
    // }
  }

  // 添加积分（新的统一接口）
  addPoints(action, points, reason) {
    this.lastResult = null
    
    if (action === 'comment') {
      // 评论固定给5积分，不需要检查首次
      this.lastResult = {
        awarded: true,
        points: 5,
        message: '加 5 积分',
        isFirst: false
      }
      return this.lastResult
    } else if (action === 'post') {
      // 发帖检查是否首次
      const isFirstToday = !this.hasActionToday('post')
      if (isFirstToday) {
        this.markActionToday('post')
        this.lastResult = {
          awarded: true,
          points: 30,
          message: '今日首次发帖获得 30 积分',
          isFirst: true
        }
      } else {
        this.lastResult = {
          awarded: true,
          points: 10,
          message: '发帖获得 10 积分',
          isFirst: false
        }
      }
      return this.lastResult
    } else if (action === 'like') {
      // 点赞检查是否首次
      const isFirstToday = !this.hasActionToday('like')
      if (isFirstToday) {
        this.markActionToday('like')
        this.lastResult = {
          awarded: true,
          points: 1,
          message: '今日首次点赞获得 1 积分',
          isFirst: true
        }
      } else {
        this.lastResult = {
          awarded: false,
          points: 0,
          message: null,
          isFirst: false
        }
      }
      return this.lastResult
    }
    
    // 默认情况
    this.lastResult = {
      awarded: false,
      points: 0,
      message: null,
      isFirst: false
    }
    return this.lastResult
  }

  // 显示积分专用Toast - 已禁用，由各个操作处理自己的toast
  showPointsToast(message, type = 'success') {
    // 已禁用，避免重复显示toast
    // if (process.client && window.$toast) {
    //   // 延迟显示，让用户看到操作结果
    //   setTimeout(() => {
    //     window.$toast[type](message, type === 'success' ? 5000 : 3000)
    //   }, 500)
    // }
  }

  // 初始化（页面加载时调用）
  init() {
    if (process.client) {
      this.userId = getUserId()
      this.today = getTodayKey()
      this.lastResult = null
      
      // 清理过期记录
      this.cleanOldRecords()
    }
  }
}

// 创建全局实例
export const pointsManager = new PointsManager()

// 在客户端初始化
if (process.client) {
  // 确保在DOM加载完成后初始化
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      pointsManager.init()
    })
  } else {
    pointsManager.init()
  }
}

export default pointsManager 