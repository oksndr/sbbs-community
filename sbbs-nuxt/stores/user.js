import { defineStore } from 'pinia'
import { API } from '../utils/api'
import notificationManager from '../utils/notificationManager'
import { getUniversalCookieValue, setCookie, deleteCookie } from '../utils/cookieUtils'
import logger from '../utils/logger'

export const useUserStore = defineStore('user', {
  state: () => ({
    user: null,
    token: null,
    isLoggedIn: false,
    isLoading: false,
    error: null,
    // 添加初始化状态标记，避免重复初始化
    isInitialized: false
  }),
  
  getters: {
    userInfo: (state) => state.user,
    authToken: (state) => state.token
  },
  
  actions: {
    // 从本地存储和cookie加载用户信息（支持SSR）
    async initUserFromStorage(cookieString = null) {
      // 如果已经初始化过且有用户数据，直接返回，避免重复读取
      if (this.isInitialized && this.user !== null) {
        logger.cache('用户信息已缓存，跳过重复读取:', this.user)
        return;
      }

      // 客户端水合时的状态同步检查
      if (process.client && this.isInitialized && !this.isLoggedIn) {
        // 如果SSR阶段已经确定用户未登录，清除客户端localStorage中的过期数据
        const hasLocalToken = localStorage.getItem('token')
        const hasLocalUser = localStorage.getItem('userInfo')
        
        if (hasLocalToken || hasLocalUser) {
          logger.user('SSR阶段已确定未登录状态，清除客户端localStorage中的过期数据')
          localStorage.removeItem('userInfo')
          localStorage.removeItem('token')
          // 清除cookie中的token
          deleteCookie('Authorization')
        }
        return;
      }
      
      try {
        let storedUser = null
        let storedToken = null
        
        if (process.client) {
          // 客户端：优先从localStorage读取，然后从cookie
          storedUser = localStorage.getItem('userInfo')
          storedToken = localStorage.getItem('token')
          
          // 检查userInfo中是否包含token（兼容旧的数据格式）
          if (storedUser && !storedToken) {
            try {
              const userObj = JSON.parse(storedUser)
              if (userObj.token) {
                storedToken = userObj.token
                logger.user('从userInfo中提取token:', storedToken.slice(0, 10) + '...')
              }
            } catch (e) {
              logger.warn('解析userInfo失败:', e)
            }
          }
          
          // 如果localStorage没有，尝试从cookie获取
          if (!storedToken) {
            storedToken = getUniversalCookieValue('Authorization', cookieString)
            if (storedToken && storedToken.startsWith('Bearer ')) {
              storedToken = storedToken.slice(7)
            }
          }
        } else {
          // 服务端：从cookie中读取
          storedToken = getUniversalCookieValue('Authorization', cookieString)
          if (storedToken && storedToken.startsWith('Bearer ')) {
            storedToken = storedToken.slice(7)
          }
          
          // 服务端无法获取localStorage中的用户信息
          // 但token存在说明用户已登录，后续会通过API获取用户信息
        }
        
        if (storedToken) {
          this.token = storedToken
          this.isLoggedIn = true
          
          if (storedUser) {
            this.user = JSON.parse(storedUser)
            this.isInitialized = true
            logger.user('从存储加载用户:', this.user)
          } else {
            logger.user('从cookie加载token，用户信息待获取:', storedToken.slice(0, 10) + '...')
            // 在SSR阶段，需要同步获取用户信息
            if (process.server) {
              try {
                await this.fetchUserInfoFromToken()
              } catch (error) {
                logger.user('SSR阶段获取用户信息失败:', error)
                // 如果获取失败，清除登录状态
                this.clearUserData()
              }
            } else {
              // 客户端异步获取
              this.fetchUserInfoFromToken()
            }
            this.isInitialized = true
          }
        } else {
          // 确保状态清空
          this.clearUserData()
          this.isInitialized = true
        }
      } catch (error) {
        console.error('加载用户信息失败', error)
        this.clearUserData()
        this.isInitialized = true
      }
    },
    
    // 设置用户信息
    setUser(userData, token) {
      // 确保用户数据不包含token（避免数据混乱）
      const cleanUserData = { ...userData }
      delete cleanUserData.token
      
      this.user = cleanUserData
      this.token = token
      this.isLoggedIn = true
      this.isInitialized = true
      
      // 保存到本地存储
      if (process.client) {
        localStorage.setItem('userInfo', JSON.stringify(cleanUserData))
        localStorage.setItem('token', token)
        
        // 同时设置到cookie中，供SSR使用
        setCookie('Authorization', `Bearer ${token}`, 30)
        
        logger.user('用户信息已保存:', cleanUserData)
      }
    },
    
    // 清除用户数据
    clearUserData() {
      this.user = null
      this.token = null
      this.isLoggedIn = false
      this.isInitialized = true // 标记为已初始化（即使是清空状态）
      
      if (process.client) {
        localStorage.removeItem('userInfo')
        localStorage.removeItem('token')
        // localStorage.removeItem('loginRemindLater') // 保留稍后提醒状态，不要在用户状态重置时清除
        
        // 清除cookie中的token
        deleteCookie('Authorization')
        
        // localStorage.removeItem('readPosts') // 清除已读记录，保留用户的阅读历史
      }
    },
    
    // 检查是否已登录
    checkLoginStatus() {
      return this.isLoggedIn && this.user !== null && this.token !== null
    },
    
    // 登录
    async login(username, password) {
      this.isLoading = true
      this.error = null
      
      try {
        // 构建登录请求数据
        const loginData = { username, password }
        
        // 使用API模块进行登录
        const result = await API.user.login(loginData)
        
        if (result.code === 200 && result.data) {
          // 登录成功，设置用户信息
          this.setUser(result.data.userInfo, result.data.token)
          return { success: true }
        } else {
          // 登录失败
          throw new Error(result.msg || '登录失败')
        }
      } catch (error) {
        this.error = error.message || '登录失败，请稍后重试'
        return { success: false, error: this.error }
      } finally {
        this.isLoading = false
      }
    },
    
    // 注册
    async register(userData) {
      this.isLoading = true
      this.error = null
      
      try {
        // 使用API模块进行注册
        const result = await API.user.register(userData)
        
        if (result.code === 200 && result.data) {
          // 注册成功，设置用户信息
          this.setUser(result.data.userInfo, result.data.token)
          return { success: true }
        } else {
          // 注册失败
          throw new Error(result.msg || '注册失败')
        }
      } catch (error) {
        this.error = error.message || '注册失败，请稍后重试'
        return { success: false, error: this.error }
      } finally {
        this.isLoading = false
      }
    },
    
    // 退出登录
    logout() {
      logger.auth('执行退出登录')
      this.clearUserData()
      
      // 清除通知缓存
      notificationManager.clearCache()
      
      return { success: true }
    },
    
    // 从token获取用户信息（用于SSR）
    async fetchUserInfoFromToken() {
      if (!this.token) {
        logger.user('没有token，无法获取用户信息')
        return
      }
      
      try {
        // 首先尝试验证token并获取用户信息
        const result = await API.user.validateToken()
        
        logger.api('validateToken API响应:', result)
        
        if (result.code === 200) {
          // 检查返回的数据结构
          if (result.data && typeof result.data === 'object') {
            this.user = result.data
            logger.user('通过token获取用户信息成功:', result.data)
          } else if (result.msg === 'token有效' || result.message === 'token有效') {
            // 如果只是验证token有效但没有用户数据，保持登录状态但不设置用户信息
            logger.user('token有效，但未返回用户数据，保持当前登录状态')
            return
          } else {
            logger.user('token验证返回格式异常:', result)
          }
          
          // 如果在客户端，保存到localStorage
          if (process.client && this.user) {
            localStorage.setItem('userInfo', JSON.stringify(this.user))
          }
        } else {
          logger.user('通过token获取用户信息失败:', result.msg || result.message)
          // token可能无效，清除登录状态
          this.clearUserData()
        }
      } catch (error) {
        console.error('通过token获取用户信息出错:', error)
        // 网络错误时不清除token，可能是临时问题
      }
    },

    // 更新用户信息
    async updateUserInfo(updatedData) {
      if (!this.isLoggedIn) {
        return { success: false, error: '用户未登录' }
      }
      
      this.isLoading = true
      this.error = null
      
      try {
        // 使用API模块更新用户信息
        const result = await API.user.updateProfile(updatedData)
        
        if (result.code === 200 && result.data) {
          // 更新成功，合并用户信息
          this.user = { ...this.user, ...result.data.userInfo }
          if (process.client) {
            localStorage.setItem('userInfo', JSON.stringify(this.user))
          }
          
          return { success: true }
        } else {
          // 更新失败
          throw new Error(result.msg || '更新失败')
        }
      } catch (error) {
        this.error = error.message || '更新失败，请稍后重试'
        return { success: false, error: this.error }
      } finally {
        this.isLoading = false
      }
    }
  }
})