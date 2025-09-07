import { API } from '~/utils/api'

class NotificationManager {
  constructor() {
    this.notifications = []
    this.unreadCount = 0
    this.isLoaded = false
    this.isLoading = false
    this.lastFetchTime = 0
    this.fetchCooldown = 30000 // 30秒冷却时间，避免频繁请求
    this.listeners = new Set()
  }

  // 添加监听器
  addListener(listener) {
    this.listeners.add(listener)
  }

  // 移除监听器
  removeListener(listener) {
    this.listeners.delete(listener)
  }

  // 通知所有监听器
  notifyListeners() {
    this.listeners.forEach(listener => {
      try {
        listener({
          notifications: this.notifications,
          unreadCount: this.unreadCount,
          isLoaded: this.isLoaded
        })
      } catch (error) {
        console.error('通知监听器执行失败:', error)
      }
    })
  }

  // 自动加载通知（带缓存和冷却）
  async autoLoadNotifications(force = false) {
    const now = Date.now()
    
    // 如果不是强制刷新，且在冷却时间内或已经加载过，直接返回缓存
    if (!force && (this.isLoaded && (now - this.lastFetchTime < this.fetchCooldown))) {
      console.log('📫 使用缓存的通知数据')
      return {
        notifications: this.notifications,
        unreadCount: this.unreadCount,
        fromCache: true
      }
    }

    // 避免重复请求
    if (this.isLoading) {
      console.log('📫 通知正在加载中，跳过重复请求')
      return {
        notifications: this.notifications,
        unreadCount: this.unreadCount,
        fromCache: true
      }
    }

    console.log('📫 开始自动加载通知数据')
    this.isLoading = true

    try {
      const response = await API.notifications.getList({
        page: 1,
        size: 50, // 一次加载更多通知，减少请求次数
        onlyUnread: true
      })

      if (response.code === 200 && response.data) {
        this.notifications = response.data.records || []
        this.unreadCount = this.notifications.length
        this.isLoaded = true
        this.lastFetchTime = now

        console.log(`📫 通知加载成功，未读通知: ${this.unreadCount} 条`)
        
        // 通知所有监听器
        this.notifyListeners()

        return {
          notifications: this.notifications,
          unreadCount: this.unreadCount,
          fromCache: false
        }
      } else {
        console.error('📫 获取通知失败:', response.msg)
        return {
          notifications: [],
          unreadCount: 0,
          error: response.msg
        }
      }
    } catch (error) {
      console.error('📫 通知请求异常:', error)
      return {
        notifications: [],
        unreadCount: 0,
        error: error.message
      }
    } finally {
      this.isLoading = false
    }
  }

  // 获取缓存的通知数据
  getCachedNotifications() {
    return {
      notifications: this.notifications,
      unreadCount: this.unreadCount,
      isLoaded: this.isLoaded
    }
  }

  // 标记通知为已读并更新缓存
  markAsRead(notificationId) {
    const index = this.notifications.findIndex(n => n.id === notificationId)
    if (index !== -1) {
      this.notifications.splice(index, 1)
      this.unreadCount = Math.max(0, this.unreadCount - 1)
      console.log(`📫 通知 ${notificationId} 已标记为已读，剩余未读: ${this.unreadCount}`)
      
      // 通知所有监听器
      this.notifyListeners()
    }
  }

  // 批量标记为已读并更新缓存
  markAllAsRead() {
    const unreadCount = this.notifications.length
    this.notifications = []
    this.unreadCount = 0
    console.log(`📫 已标记 ${unreadCount} 条通知为已读`)
    
    // 通知所有监听器
    this.notifyListeners()
  }

  // 清除缓存（退出登录时调用）
  clearCache() {
    this.notifications = []
    this.unreadCount = 0
    this.isLoaded = false
    this.lastFetchTime = 0
    console.log('📫 通知缓存已清除')
    
    // 通知所有监听器
    this.notifyListeners()
  }

  // 强制刷新通知
  async refreshNotifications() {
    return await this.autoLoadNotifications(true)
  }
}

// 创建单例实例
const notificationManager = new NotificationManager()

// 导出单例
export default notificationManager 