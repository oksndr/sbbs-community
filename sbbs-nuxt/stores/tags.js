import { defineStore } from 'pinia'
import logger from '~/utils/logger'

export const useTagsStore = defineStore('tags', {
  state: () => ({
    tags: [],
    selectedTagId: null,
    isLoading: false,
    error: null,
    isInitialized: false
  }),

  getters: {
    allTags: (state) => state.tags,
    selectedTag: (state) => {
      if (!state.selectedTagId) return null
      return state.tags.find(tag => tag.id === state.selectedTagId)
    },
    hasData: (state) => state.tags.length > 0
  },

  actions: {
    // 初始化标签数据
    async initTags(cookieString = null) {
      // 如果已经有数据且已初始化，跳过
      if (this.isInitialized && this.hasData) {
        logger.cache('标签数据已缓存，跳过重复获取')
        return this.tags
      }

      this.isLoading = true
      this.error = null

      try {
        // 获取API基础URL
        const getApiBaseUrl = () => {
          if (process.server || typeof window === 'undefined') {
            const isDev = process.env.NODE_ENV === 'development'
            return isDev
              ? (process.env.SBBS_DEV_API_URL || 'http://localhost:12367')
              : (process.env.SBBS_API_URL || 'http://example:port')
          } else {
            return '/api'
          }
        }

        const response = await fetch(`${getApiBaseUrl()}/tags`)
        const data = await response.json()

        logger.api('标签API响应:', {
          code: data.code,
          tagsCount: data.data?.length || 0,
          environment: process.client ? '客户端' : '服务端'
        })

        if (data.code === 200) {
          this.tags = data.data || []
          this.isInitialized = true
          return this.tags
        } else {
          throw new Error(data.msg || '获取标签失败')
        }
      } catch (error) {
        console.error('获取标签失败:', error)
        this.error = error.message
        this.isInitialized = true // 即使失败也标记为已初始化，避免重复请求
        return []
      } finally {
        this.isLoading = false
      }
    },

    // 设置选中的标签ID
    setSelectedTagId(tagId) {
      this.selectedTagId = tagId
    },

    // 清除选中的标签
    clearSelectedTag() {
      this.selectedTagId = null
    },

    // 获取标签图标
    getTagIcon(tagId, tagName) {
      const tagIcons = {
        '1': 'ri-code-s-slash-line',      // 技术相关
        '2': 'ri-terminal-box-line',      // 终端/工具
        '3': 'ri-question-line',          // 问题/疑问
        '4': 'ri-discuss-line',           // 讨论
        '5': 'ri-gift-line',              // 福利羊毛
        '6': 'ri-feedback-line',          // 运营反馈
        '7': 'ri-landscape-line',         // 海阔天空
        '8': 'ri-heart-line',             // 额外标签1
        '9': 'ri-star-line',              // 额外标签2
        '10': 'ri-fire-line',             // 额外标签3
        'default': 'ri-hashtag'
      }

      // 如果通过ID找不到，就根据名称来匹配
      if (!tagIcons[tagId] && tagName) {
        if (tagName.includes('运营反馈')) return 'ri-feedback-line'
        if (tagName.includes('海阔天空')) return 'ri-landscape-line'
        if (tagName.includes('福利羊毛') || tagName.includes('羊毛')) return 'ri-gift-line'
      }

      return tagIcons[tagId] || tagIcons.default
    }
  }
})
