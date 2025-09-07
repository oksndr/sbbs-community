<template>
  <Teleport to="body">
    <div 
      class="notification-panel" 
      v-if="show" 
      @click.stop
      :style="{ 
        top: panelPosition.top, 
        right: panelPosition.right 
      }"
    >
    <div class="panel-content" @click.stop :style="{ height: dynamicHeight + 'px' }">
      <div class="panel-header">
        <h3><i class="ri-notification-line"></i> 站内通知</h3>
        <div class="header-actions">
          <button 
            @click.stop="refreshNotifications" 
            class="refresh-btn"
            :disabled="isLoading || isMarkingAll"
            title="刷新通知">
            <i class="ri-refresh-line" :class="{ 'rotating': isLoading }"></i>
          </button>
          <button 
            v-if="notifications.length > 0"
            @click.stop="markAllAsRead" 
            class="mark-all-btn"
            :disabled="isMarkingAll || isLoading">
            <i class="ri-check-double-line"></i>
            {{ isMarkingAll ? '标记中...' : '全部已读' }}
          </button>
          <button @click.stop="$emit('close')" class="close-btn">
            <i class="ri-close-line"></i>
          </button>
        </div>
      </div>
      
      <div class="panel-body">
        <!-- 加载状态 -->
        <div v-if="isLoading" class="loading-state">
          <div class="loading-dots">
            <div class="dot"></div>
            <div class="dot"></div>
            <div class="dot"></div>
          </div>
          <span>{{ isMarkingAll ? '检查新通知中...' : '加载通知中...' }}</span>
        </div>
        
        <!-- 空状态 -->
        <div v-else-if="notifications.length === 0" class="empty-state">
          <i class="ri-notification-off-line"></i>
          <p>暂无新通知</p>
        </div>
        
        <!-- 通知列表 -->
        <div v-else class="notification-list-container">
          <div class="notification-list">
            <div 
              v-for="notification in notifications" 
              :key="notification.id"
              class="notification-item"
              :class="{ 
                'unread': !notification.read,
                'non-clickable': !isNotificationClickable(notification.notificationType)
              }"
              @click="isNotificationClickable(notification.notificationType) ? handleNotificationClick(notification) : null">
              
              <div class="notification-content">
                <div class="notification-text">
                  <i :class="getNotificationIcon(notification.notificationType)"></i>
                  {{ notification.notificationText }}
                </div>
                <div class="notification-meta">
                  <span class="notification-time">{{ formatTimeAgo(notification.created) }}</span>
                  <span v-if="notification.relatedTitle" class="related-title">
                    「{{ notification.relatedTitle }}」
                  </span>
                  <!-- 为帖子被删除通知添加特殊标识 -->
                  <span v-if="notification.notificationType === 9" class="deleted-notice">
                    (仅通知)
                  </span>
                </div>
              </div>
              
              <div class="notification-actions">
                <button 
                  v-if="!notification.read"
                  @click.stop="markAsRead(notification.id)"
                  class="mark-read-btn"
                  title="标记为已读">
                  <i class="ri-check-line"></i>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 分页 -->
      <div v-if="totalPages > 1" class="pagination" @click.stop>
        <button 
          @click.stop="goToPage(currentPage - 1)"
          :disabled="currentPage <= 1"
          class="page-btn">
          <i class="ri-arrow-left-s-line"></i>
        </button>
        
        <span class="page-info">{{ currentPage }} / {{ totalPages }}</span>
        
        <button 
          @click.stop="goToPage(currentPage + 1)"
          :disabled="currentPage >= totalPages"
          class="page-btn">
          <i class="ri-arrow-right-s-line"></i>
        </button>
      </div>
    </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, watch, onMounted, computed, onUnmounted } from 'vue'
import { useRouter } from '#imports'
import { API } from '~/utils/api'
import notificationManager from '~/utils/notificationManager'

const router = useRouter()

// Props
const props = defineProps({
  show: {
    type: Boolean,
    default: false
  }
})

// Emits
const emit = defineEmits(['close', 'notifications-loaded'])

// 响应式数据
const notifications = ref([])
const isLoading = ref(false)
const isMarkingAll = ref(false)
const currentPage = ref(1)
const totalPages = ref(1)
const pageSize = 15

// 通知面板位置
const panelPosition = ref({ top: '60px', right: '20px' })

// 计算动态高度
const dynamicHeight = computed(() => {
  const baseHeight = 100 // 头部 + 分页的基础高度 (减少了)
  const itemHeight = 72 // 每个通知项的平均高度
  const minItems = 3 // 最少显示的项数
  const maxItems = 12 // 最多显示的项数 (避免太高)
  
  let targetItems = notifications.value.length
  
  // 如果有分页，至少显示当前页的全部内容
  if (totalPages.value > 1) {
    targetItems = Math.min(pageSize, notifications.value.length)
  }
  
  // 限制显示范围
  targetItems = Math.max(minItems, Math.min(maxItems, targetItems))
  
  const calculatedHeight = baseHeight + (targetItems * itemHeight)
  const maxHeight = window.innerHeight * 0.9 // 90vh
  
  return Math.min(calculatedHeight, maxHeight)
})

// 获取通知图标
const getNotificationIcon = (type) => {
  const icons = {
    1: 'ri-chat-1-line',        // 评论
    2: 'ri-reply-line',         // 回复
    4: 'ri-reply-all-line',     // 回复
    5: 'ri-thumb-up-line',      // 点赞
    7: 'ri-thumb-up-line',      // 评论点赞
    8: 'ri-thumb-down-line',    // 评论点踩
    9: 'ri-delete-bin-line',    // 帖子被删除
    10: 'ri-user-add-line',     // 收到关注
    11: 'ri-award-line',        // 升级通知
  }
  return icons[type] || 'ri-notification-line'
}

// 检查通知是否可点击
const isNotificationClickable = (notificationType) => {
  // type = 9 (帖子被删除) 不可点击，仅作为通知使用
  return notificationType !== 9
}

// 格式化时间
const formatTimeAgo = (timestamp) => {
  if (!timestamp) return ''
  const now = Date.now()
  const past = new Date(timestamp).getTime()
  const diffInSeconds = Math.floor((now - past) / 1000)
  
  if (diffInSeconds < 60) return `${diffInSeconds} 秒前`
  if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)} 分钟前`
  if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)} 小时前`
  return `${Math.floor(diffInSeconds / 86400)} 天前`
}

// 通知管理器监听器
const notificationListener = (data) => {
  notifications.value = data.notifications
  // 只更新第一页的数据，保持分页逻辑
  if (currentPage.value === 1) {
    emit('notifications-loaded', data.unreadCount)
  }
}

// 加载通知列表 - 优先使用缓存
const loadNotifications = async (page = 1, forceRefresh = false) => {
  // 如果是第一页且不是强制刷新，优先尝试使用缓存数据
  if (page === 1 && !forceRefresh) {
    const cachedData = notificationManager.getCachedNotifications()
    // 只有在缓存已加载且有通知数据时才使用缓存
    if (cachedData.isLoaded && cachedData.notifications.length > 0) {
      console.log('📫 通知面板使用缓存数据')
      notifications.value = cachedData.notifications.slice(0, pageSize)
      currentPage.value = 1
      totalPages.value = Math.ceil(cachedData.notifications.length / pageSize) || 1
      emit('notifications-loaded', cachedData.unreadCount)
      return
    }
  }

  isLoading.value = true
  try {
    const response = await API.notifications.getList({
      page,
      size: pageSize,
      onlyUnread: true
    })
    
    if (response.code === 200 && response.data) {
      notifications.value = response.data.records || []
      currentPage.value = response.data.current || 1
      totalPages.value = response.data.pages || 1
      
      // 通知父组件通知数量
      emit('notifications-loaded', notifications.value.length)
    }
  } catch (error) {
    console.error('加载通知失败:', error)
  } finally {
    isLoading.value = false
  }
}

// 标记单个通知为已读
const markAsRead = async (notificationId) => {
  try {
    const response = await API.notifications.markAsRead(notificationId)
    if (response.code === 200) {
      // 更新通知管理器缓存
      notificationManager.markAsRead(notificationId)
      
      // 从当前列表中移除已读的通知
      const index = notifications.value.findIndex(n => n.id === notificationId)
      if (index !== -1) {
        notifications.value.splice(index, 1)
        emit('notifications-loaded', notifications.value.length)
      }
    }
  } catch (error) {
    console.error('标记通知已读失败:', error)
  }
}

// 标记所有通知为已读
const markAllAsRead = async () => {
  isMarkingAll.value = true
  try {
    // 获取所有未读通知的ID
    const unreadIds = notifications.value
      .filter(n => !n.read)
      .map(n => n.id)
      
    if (unreadIds.length === 0) return
    
    const response = await API.notifications.batchMarkAsRead(unreadIds)
    if (response.code === 200) {
      // 更新通知管理器缓存
      notificationManager.markAllAsRead()
      
      // 清空当前页面的通知并显示加载状态
      notifications.value = []
      emit('notifications-loaded', 0)
      
      // 设置加载状态（显示"检查新通知中..."）
      isLoading.value = true
      
      // 强制刷新通知管理器数据
      await notificationManager.refreshNotifications()
      
      // 强制刷新加载新数据（跳过缓存）
      // loadNotifications 会自己管理 isLoading 状态
      await loadNotifications(currentPage.value, true)
    }
  } catch (error) {
    console.error('标记所有通知已读失败:', error)
  } finally {
    isMarkingAll.value = false
  }
}

// 处理通知点击
const handleNotificationClick = async (notification) => {
  // 检查是否可点击
  if (!isNotificationClickable(notification.notificationType)) {
    return
  }
  
  // 标记为已读
  if (!notification.read) {
    markAsRead(notification.id)
  }
  
  // 立即显示页面跳转加载动画
  if (process.client && window.showPageTransitionLoader) {
    window.showPageTransitionLoader()
  }
  
  try {
    // 获取跳转信息
    const jumpInfoResponse = await API.notifications.getJumpInfo(notification.id)
    
    if (jumpInfoResponse.code !== 200) {
      console.error('获取跳转信息失败:', jumpInfoResponse.msg)
      // 隐藏加载动画
      if (process.client && window.hidePageTransitionLoader) {
        window.hidePageTransitionLoader()
      }
      return
    }
    
    const jumpInfo = jumpInfoResponse.data
    console.log('跳转信息:', jumpInfo)
    
    // 根据通知类型处理跳转
    if (jumpInfo.notificationType >= 1 && jumpInfo.notificationType <= 4) {
      // 评论相关通知 (type 1-4)
      await handleCommentNotificationJump(jumpInfo)
    } else if (jumpInfo.notificationType >= 5 && jumpInfo.notificationType <= 8) {
      // 点赞/点踩相关通知 (type 5-8)
      await handleLikeNotificationJump(jumpInfo)
    } else if (jumpInfo.notificationType === 10) {
      // 收到关注通知 (type 10)
      await handleFollowNotificationJump(jumpInfo)
    } else if (jumpInfo.notificationType === 11) {
      // 升级通知 (type 11)
      await handleLevelUpNotificationJump(jumpInfo)
    } else {
      // 其他类型通知，待实现
      console.log('其他类型通知跳转，待实现:', jumpInfo)
      // 暂时隐藏加载动画
      if (process.client && window.hidePageTransitionLoader) {
        window.hidePageTransitionLoader()
      }
    }
    
    // 不要立即关闭通知栏，让用户可以继续查看其他通知
    // emit('close')
  } catch (error) {
    console.error('处理通知跳转失败:', error)
    // 出错时隐藏加载动画
    if (process.client && window.hidePageTransitionLoader) {
      window.hidePageTransitionLoader()
    }
  }
}

// 处理评论相关通知跳转
const handleCommentNotificationJump = async (jumpInfo) => {
  const { postId, pageNumber, targetCommentId, parentCommentId } = jumpInfo
  
  // 构建跳转URL
  let url = `/post/${postId}?page=${pageNumber}`
  
  // 添加评论相关参数
  if (targetCommentId) {
    url += `&highlight=${targetCommentId}`
  }
  
  if (parentCommentId) {
    url += `&expand=${parentCommentId}`
  }
  
  console.log('跳转到评论页面:', url)
  
  // 执行跳转 - navigateWithPageTransition会自动处理加载动画
  if (process.client && window.navigateWithPageTransition) {
    window.navigateWithPageTransition(url);
  } else {
    // 如果没有navigateWithPageTransition，手动隐藏之前显示的加载动画
    if (process.client && window.hidePageTransitionLoader) {
      window.hidePageTransitionLoader()
    }
    router.push(url);
  }
}

// 处理点赞/点踩相关通知跳转
const handleLikeNotificationJump = async (jumpInfo) => {
  const { postId, pageNumber, targetCommentId, parentCommentId, jumpType } = jumpInfo
  
  if (jumpType === 'post') {
    // 帖子被点赞/点踩，跳转到帖子首页
    const url = `/post/${postId}?page=1`
    console.log('跳转到帖子页面:', url)
    
    // 执行跳转
    if (process.client && window.navigateWithPageTransition) {
      window.navigateWithPageTransition(url);
    } else {
      if (process.client && window.hidePageTransitionLoader) {
        window.hidePageTransitionLoader()
      }
      router.push(url);
    }
  } else if (jumpType === 'comment') {
    // 评论被点赞/点踩，跳转到评论所在页面并高亮
    let url = `/post/${postId}?page=${pageNumber}`
    
    // 添加评论高亮参数
    if (targetCommentId) {
      url += `&highlight=${targetCommentId}`
    }
    
    // 如果是二级评论，需要展开父评论
    if (parentCommentId) {
      url += `&expand=${parentCommentId}`
    }
    
    console.log('跳转到评论页面（点赞/点踩）:', url)
    
    // 执行跳转
    if (process.client && window.navigateWithPageTransition) {
      window.navigateWithPageTransition(url);
    } else {
      if (process.client && window.hidePageTransitionLoader) {
        window.hidePageTransitionLoader()
      }
      router.push(url);
    }
  } else {
    console.error('未知的跳转类型:', jumpType)
    // 隐藏加载动画
    if (process.client && window.hidePageTransitionLoader) {
      window.hidePageTransitionLoader()
    }
  }
}

// 处理收到关注通知跳转
const handleFollowNotificationJump = async (jumpInfo) => {
  const { userId } = jumpInfo
  
  const url = `/user/${userId}`
  console.log('跳转到用户主页:', url)
  
  // 执行跳转
  if (process.client && window.navigateWithPageTransition) {
    window.navigateWithPageTransition(url);
  } else {
    if (process.client && window.hidePageTransitionLoader) {
      window.hidePageTransitionLoader()
    }
    router.push(url);
  }
}

// 处理升级通知跳转
const handleLevelUpNotificationJump = async (jumpInfo) => {
  const { userId } = jumpInfo
  
  const url = `/user/${userId}`
  console.log('跳转到用户主页（升级通知）:', url)
  
  // 执行跳转
  if (process.client && window.navigateWithPageTransition) {
    window.navigateWithPageTransition(url);
  } else {
    if (process.client && window.hidePageTransitionLoader) {
      window.hidePageTransitionLoader()
    }
    router.push(url);
  }
}

// 手动刷新通知
const refreshNotifications = async () => {
  console.log('📫 用户手动刷新通知')
  
  // 设置加载状态
  isLoading.value = true
  
  try {
    // 强制刷新通知管理器数据
    await notificationManager.refreshNotifications()
    
    // 重新加载第一页数据（强制刷新）
    currentPage.value = 1
    await loadNotifications(1, true)
    
    // 可选：显示刷新成功的提示
    if (process.client && window.$toast) {
      window.$toast.success('通知已刷新')
    }
  } catch (error) {
    console.error('刷新通知失败:', error)
    if (process.client && window.$toast) {
      window.$toast.error('刷新失败，请稍后重试')
    }
  }
  // isLoading 会在 loadNotifications 中被设置为 false
}

// 分页跳转
const goToPage = (page) => {
  if (page >= 1 && page <= totalPages.value) {
    loadNotifications(page)
  }
}

// 计算通知面板位置
const calculatePosition = () => {
  if (!process.client) return
  
  try {
    // 查找用户头像元素
    const userAvatar = document.querySelector('.user-avatar')
    if (userAvatar) {
      const rect = userAvatar.getBoundingClientRect()
      panelPosition.value = {
        top: `${rect.bottom + 8}px`,
        right: `${window.innerWidth - rect.right}px`
      }
    }
  } catch (error) {
    console.error('计算通知面板位置失败:', error)
    // 使用默认位置
    panelPosition.value = { top: '60px', right: '20px' }
  }
}

// 监听显示状态变化
watch(() => props.show, (newVal) => {
  if (newVal) {
    // 计算位置
    calculatePosition()
    // 重置到第一页并加载通知
    currentPage.value = 1
    loadNotifications(1)
  }
})

// 组件挂载时添加通知监听器
onMounted(() => {
  // 添加通知管理器监听器
  notificationManager.addListener(notificationListener)
  
  if (props.show) {
    loadNotifications()
  }
})

// 组件卸载时移除监听器
onUnmounted(() => {
  notificationManager.removeListener(notificationListener)
})
</script>

<style scoped>
.notification-panel {
  position: fixed !important;
  z-index: 9999 !important; /* 高于评论表单的1000 */
  animation: slideInScale 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  transform-origin: top right;
  pointer-events: auto !important;
}

.panel-content {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 12px;
  box-shadow: 
    0 12px 40px rgba(0, 0, 0, 0.1),
    0 4px 12px rgba(0, 0, 0, 0.06),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
  width: 420px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(15px) saturate(150%);
  -webkit-backdrop-filter: blur(15px) saturate(150%);
}

.panel-header {
  padding: 0.625rem 1rem;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(249, 250, 251, 0.9);
  backdrop-filter: blur(8px);
}

.panel-header h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: #111827;
  display: flex;
  align-items: center;
}

.panel-header h3 i {
  margin-right: 0.5rem;
  color: #3b82f6;
  font-size: 1.1rem;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.refresh-btn {
  padding: 0.5rem;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: white;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 0.9rem;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
}

.refresh-btn:hover:not(:disabled) {
  background: #f3f4f6;
  color: #374151;
  border-color: #d1d5db;
  transform: translateY(-1px);
}

.refresh-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.refresh-btn .rotating {
  animation: rotate 1s linear infinite;
}

.mark-all-btn {
  padding: 0.375rem 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: white;
  color: #374151;
  font-size: 0.8rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.mark-all-btn:hover:not(:disabled) {
  background: #f3f4f6;
  border-color: #9ca3af;
  color: #111827;
}

.mark-all-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.close-btn {
  padding: 0.375rem;
  border: none;
  background: transparent;
  color: #6b7280;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.close-btn:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.panel-body {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.loading-state,
.empty-state {
  padding: 2.5rem;
  text-align: center;
  color: #6b7280;
}

.loading-dots {
  display: flex;
  gap: 4px;
  margin-bottom: 0.5rem;
  justify-content: center;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #3b82f6;
  animation: dotPulse 1.5s ease-in-out infinite;
}

.dot:nth-child(1) {
  animation-delay: 0s;
}

.dot:nth-child(2) {
  animation-delay: 0.3s;
}

.dot:nth-child(3) {
  animation-delay: 0.6s;
}

.empty-state i {
  font-size: 3.5rem;
  background: linear-gradient(135deg, #d1d5db, #9ca3af);
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
  margin-bottom: 1rem;
  display: block;
  opacity: 0.8;
}

.notification-list {
  padding: 0.5rem 0;
}

.notification-item {
  position: relative;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  margin: 0;
  border-radius: 0;
  background: rgba(255, 255, 255, 0.7);
}

.notification-item:hover {
  background: rgba(239, 246, 255, 0.8);
}

.notification-item.unread .notification-text {
  font-weight: 600;
}

.notification-item.unread::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
  border-radius: 0;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.notification-item.unread:hover::before {
  opacity: 1;
}

.notification-item.unread {
  background: rgba(59, 130, 246, 0.05);
}

.notification-item.unread:hover {
  background: rgba(219, 234, 254, 0.8);
}

.notification-item.non-clickable {
  opacity: 0.5;
  cursor: not-allowed;
}

.notification-item.non-clickable:hover {
  background: rgba(156, 163, 175, 0.1);
}

.notification-list-container {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

.notification-list-container::-webkit-scrollbar {
  width: 6px;
}

.notification-list-container::-webkit-scrollbar-track {
  background: rgba(0, 0, 0, 0.05);
  border-radius: 3px;
}

.notification-list-container::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.2);
  border-radius: 3px;
}

.notification-list-container::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.3);
}

.notification-list {
  /* 通知列表本身不需要额外样式 */
}

.notification-content {
  flex: 1;
  min-width: 0;
}

.notification-text {
  font-size: 0.9rem;
  color: #333;
  margin-bottom: 0.25rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.notification-text i {
  color: #3b82f6;
  flex-shrink: 0;
  font-size: 1rem;
}

.notification-meta {
  font-size: 0.75rem;
  color: #999;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.related-title {
  color: #666;
  font-weight: 500;
}

.deleted-notice {
  color: #ef4444;
  font-size: 0.7rem;
  font-weight: 500;
}

.notification-actions {
  flex-shrink: 0;
}

.mark-read-btn {
  padding: 0.25rem;
  border: none;
  background: transparent;
  color: #9ca3af;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.mark-read-btn:hover {
  background: rgba(34, 197, 94, 0.1);
  color: #22c55e;
}

.pagination {
  padding: 0.75rem 1rem;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  background: rgba(249, 250, 251, 0.9);
  backdrop-filter: blur(8px);
}

.page-btn {
  padding: 0.5rem 0.625rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: white;
  color: #374151;
  cursor: pointer;
  transition: all 0.2s ease;
  font-weight: 500;
  font-size: 0.8rem;
  display: flex;
  align-items: center;
  gap: 0.25rem;
  min-width: 36px;
  justify-content: center;
}

.page-btn:hover:not(:disabled) {
  background: #f3f4f6;
  border-color: #9ca3af;
  color: #111827;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background: #f9fafb;
  color: #9ca3af;
  border-color: #e5e7eb;
}

.page-info {
  font-size: 0.8rem;
  font-weight: 500;
  color: #6b7280;
  min-width: 60px;
  text-align: center;
  padding: 0.25rem 0.5rem;
}

@keyframes dotPulse {
  0%, 80%, 100% {
    transform: scale(0.8);
    opacity: 0.5;
  }
  40% {
    transform: scale(1.2);
    opacity: 1;
  }
}

@keyframes slideInScale {
  0% {
    opacity: 0;
    transform: translateY(-10px) scale(0.95);
  }
  50% {
    opacity: 0.8;
    transform: translateY(-5px) scale(1.02);
  }
  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.7;
    transform: scale(1.1);
  }
}

@keyframes fadeInDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .notification-panel {
    right: -1rem;
    left: 1rem;
  }
  
  .panel-content {
    width: auto;
    max-height: 60vh;
  }
  
  .panel-header {
    padding: 0.75rem 1rem;
  }
  
  .panel-header h3 {
    font-size: 1rem;
  }
  
  .notification-item {
    padding: 0.625rem 1rem;
  }
  
  .mark-all-btn {
    padding: 0.25rem 0.5rem;
    font-size: 0.75rem;
  }
}
</style> 