<template>
  <LayoutWithSidebar>
    <!-- 用户信息区域 -->
    <div class="content-section">
      <div v-if="error && !error.includes('时时刻刻')" class="message error-message">
        <i class="ri-error-warning-line"></i>
        {{ error }}
      </div>
      
      <div v-else-if="userInfo" class="user-profile">
        <!-- 用户基本信息区 -->
        <div class="user-header">
          <div class="user-avatar-section">
            <img :src="userInfo.avatar" :alt="userInfo.username" class="user-avatar" />
          </div>
          
          <div class="user-info-section">
            <div class="username-row">
              <h1 class="username">{{ userInfo.username }}</h1>
              <!-- 关注按钮 -->
              <button 
                v-if="userStore.isLoggedIn && userStore.user?.id !== parseInt(route.params.id)"
                @click="toggleFollow" 
                :disabled="isFollowLoading"
                class="btn follow-btn"
                :class="userInfo.isFollowing ? 'btn-outline' : 'btn-primary'">
                <i :class="userInfo.isFollowing ? 'ri-user-unfollow-line' : 'ri-user-add-line'"></i>
                {{ isFollowLoading ? '处理中...' : (userInfo.isFollowing ? '取消关注' : '关注') }}
              </button>
            </div>
            <div class="user-meta">
              <span class="user-group">
                <i class="ri-shield-star-line"></i>
                {{ userInfo.groupId }}
              </span>
              <span class="join-date">
                <i class="ri-calendar-line"></i>
                {{ formatDate(userInfo.created) }} 加入
              </span>
            </div>
          </div>
        </div>
        
        <!-- 等级系统区 -->
        <div class="level-section">
          <div class="level-info">
            <span class="level-name">{{ userInfo.levelName }}</span>
            <span class="level-number">Lv.{{ userInfo.level }}</span>
          </div>
          <div class="experience-bar">
            <div class="experience-progress" :style="{ width: userInfo.progressPercent + '%' }"></div>
          </div>
          <div class="experience-text">
            {{ userInfo.currentStageExp }} / {{ userInfo.expNeededForNextLevel }} EXP
            ({{ Math.round(userInfo.progressPercent) }}%)
          </div>
          <div class="total-experience-info">
            <div class="total-exp">
              <span class="exp-label">总经验:</span>
              <span class="exp-value">{{ userInfo.experience || 0 }}</span>
            </div>
            <div class="next-level-exp">
              <span class="exp-label">下一级需要:</span>
              <span class="exp-value">{{ (userInfo.nextLevelRequiredExp || 0) + 1 }}</span>
            </div>
          </div>
        </div>
        
        <!-- 统计数据区 -->
        <div class="stats-section">
          <div class="stat-item" :class="{ active: activeTab === 'posts' }" @click="switchTab('posts')">
            <div class="stat-number">{{ userInfo.postCount }}</div>
            <div class="stat-label">发帖</div>
          </div>
          <div class="stat-item" :class="{ active: activeTab === 'comments' }" @click="switchTab('comments')">
            <div class="stat-number">{{ userInfo.commentCount }}</div>
            <div class="stat-label">评论</div>
          </div>
          <div class="stat-item" :class="{ active: activeTab === 'followers' }" @click="switchTab('followers')">
            <div class="stat-number">{{ userInfo.followerCount }}</div>
            <div class="stat-label">粉丝</div>
          </div>
          <div class="stat-item" :class="{ active: activeTab === 'following' }" @click="switchTab('following')">
            <div class="stat-number">{{ userInfo.followingCount }}</div>
            <div class="stat-label">关注</div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 内容区域 -->
    <div class="content-section">
      <!-- 发帖列表 -->
      <div v-if="activeTab === 'posts'">
        <div class="section-header">
          <h2>
            <i class="ri-article-line"></i>
            {{ userInfo?.username }}的帖子 
            <span class="post-count">({{ userInfo?.total || 0 }})</span>
          </h2>
        </div>
        
        <div v-if="isPostsLoading && posts.length === 0" class="loading-spinner">
          <span class="custom-loader"></span> 加载中...
        </div>
        
        <div v-if="!isPostsLoading && posts.length === 0" class="empty-state">
          <i class="ri-article-line"></i>
          <p>该用户还没有发布任何帖子</p>
        </div>
        
        <div v-if="posts.length > 0" class="post-list">
          <div v-for="post in posts" 
               :key="post.id" 
               class="post-item">
            <div class="post-item-avatar-area">
              <img :src="post.avatar" alt="User Avatar" class="post-item-avatar"/>
            </div>
            <div class="post-item-details">
              <div class="post-item-title-line">
                <div class="title-left">
                  <a 
                    :href="`/post/${post.id}?page=1`"
                    class="post-item-title">{{ post.title }}</a>
                </div>
                <div class="post-tags">
                  <template v-if="post.tags && post.tags.length > 0">
                    <span v-for="(tagName, index) in post.tags" 
                          :key="index"
                          class="post-item-tag">
                      {{ tagName }}
                    </span>
                  </template>
                </div>
              </div>
              
              <div class="post-item-meta">
                <span class="meta-time">
                  <i class="ri-calendar-line"></i>{{ formatTimeAgo(post.created) }}
                </span>
                <span v-if="post.updated !== post.created" class="meta-time">
                  <i class="ri-time-line"></i>{{ formatTimeAgo(post.updated) }}
                </span>
                <div class="post-stats">
                  <span class="meta-stats like">
                    <i class="ri-thumb-up-line"></i>{{ post.likeCount || 0 }}
                  </span>
                  <span class="meta-stats dislike">
                    <i class="ri-thumb-down-line"></i>{{ post.dislikeCount || 0 }}
                  </span>
                  <span class="meta-stats comments">
                    <i class="ri-chat-1-line"></i>{{ post.commentCount || 0 }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <!-- 分页 -->
        <div v-if="userInfo && userInfo.total > userInfo.pageSize" class="pagination-section">
          <div class="pagination">
            <!-- 上一页 -->
            <button 
              @click="goToPage(currentPage - 1)"
              :disabled="currentPage <= 1 || isPostsLoading"
              class="pagination-btn prev-btn">
              <i class="ri-arrow-left-s-line"></i> 上一页
            </button>
            
            <!-- 页码按钮 -->
            <template v-for="page in visiblePages" :key="page">
              <button 
                v-if="page !== '...'"
                @click="goToPage(page)"
                :disabled="isPostsLoading"
                class="pagination-btn page-number"
                :class="{ active: page === currentPage }">
                <span v-if="isPostsLoading && page === currentPage" class="loading-spinner">⟳</span>
                <span v-else>{{ page }}</span>
              </button>
              <span v-else class="pagination-ellipsis">...</span>
            </template>
            
            <!-- 下一页 -->
            <button 
              @click="goToPage(currentPage + 1)"
              :disabled="currentPage >= totalPages || isPostsLoading"
              class="pagination-btn next-btn">
              下一页 <i class="ri-arrow-right-s-line"></i>
            </button>
          </div>
        </div>
      </div>

      <!-- 评论列表 -->
      <div v-if="activeTab === 'comments'">
        <div class="section-header">
          <h2>
            <i class="ri-chat-1-line"></i>
            {{ userInfo?.username }}的评论 
            <span class="post-count">({{ commentTotal || 0 }})</span>
          </h2>
        </div>
        
        <div v-if="isCommentsLoading && comments.length === 0" class="modern-loading">
          <div class="loading-dots">
            <div class="dot"></div>
            <div class="dot"></div>
            <div class="dot"></div>
          </div>
          <span class="loading-text">加载评论中...</span>
        </div>
        
        <div v-if="!isCommentsLoading && comments.length === 0" class="empty-state">
          <i class="ri-chat-1-line"></i>
          <p>该用户还没有发表任何评论</p>
        </div>
        
        <div v-if="comments.length > 0" class="post-list">
          <div v-for="comment in comments" 
               :key="comment.id" 
               class="post-item clickable-comment-card"
               @click="goToComment(comment)">
            <div class="post-item-avatar-area">
              <img :src="comment.avatar" alt="User Avatar" class="post-item-avatar"/>
            </div>
            <div class="post-item-details">
              <div class="post-item-title-line">
                <div class="title-left">
                  <div v-if="!comment.parentId" class="comment-context-inline">
                    <span class="comment-in-post">在 </span>
                    <span class="post-item-title comment-post-title">
                      {{ comment.postTitle === '未知帖子' ? '帖子已删除' : comment.postTitle }}
                    </span>
                    <span class="comment-in-post"> 中评论：</span>
                    <span class="comment-content-inline">
                      {{ comment.content }}
                    </span>
                  </div>
                  <div v-else class="comment-context-inline">
                    <span class="reply-indicator">
                      <i class="ri-reply-line"></i>
                      回复了评论：
                    </span>
                    <span class="comment-content-inline">
                      {{ comment.content }}
                    </span>
                  </div>
                </div>
              </div>
              
              <div class="post-item-meta">
                <span class="meta-time">
                  <i class="ri-calendar-line"></i>{{ formatTimeAgo(comment.created) }}
                </span>
                <span v-if="comment.updated !== comment.created" class="meta-time">
                  <i class="ri-time-line"></i>{{ formatTimeAgo(comment.updated) }}
                </span>
                <div class="post-stats">
                  <span class="meta-stats like">
                    <i class="ri-thumb-up-line"></i>{{ comment.likeCount || 0 }}
                  </span>
                  <span class="meta-stats dislike">
                    <i class="ri-thumb-down-line"></i>{{ comment.dislikeCount || 0 }}
                  </span>
                  <span class="meta-stats comments">
                    <i class="ri-chat-1-line"></i>{{ comment.replyCount || 0 }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <!-- 评论分页 -->
        <div v-if="commentTotal && commentTotal > commentPageSize" class="pagination-section">
          <div class="pagination">
            <!-- 上一页 -->
            <button 
              @click="goToCommentPage(commentCurrentPage - 1)"
              :disabled="commentCurrentPage <= 1 || isCommentsLoading"
              class="pagination-btn prev-btn">
              <i class="ri-arrow-left-s-line"></i> 上一页
            </button>
            
            <!-- 页码按钮 -->
            <template v-for="page in commentVisiblePages" :key="page">
              <button 
                v-if="page !== '...'"
                @click="goToCommentPage(page)"
                :disabled="isCommentsLoading"
                class="pagination-btn page-number"
                :class="{ active: page === commentCurrentPage }">
                <span v-if="isCommentsLoading && page === commentCurrentPage" class="loading-spinner">⟳</span>
                <span v-else>{{ page }}</span>
              </button>
              <span v-else class="pagination-ellipsis">...</span>
            </template>
            
            <!-- 下一页 -->
            <button 
              @click="goToCommentPage(commentCurrentPage + 1)"
              :disabled="commentCurrentPage >= commentTotalPages || isCommentsLoading"
              class="pagination-btn next-btn">
              下一页 <i class="ri-arrow-right-s-line"></i>
            </button>
          </div>
        </div>
      </div>

      <!-- 粉丝列表 -->
      <div v-if="activeTab === 'followers'">
        <div class="section-header">
          <h2>
            <i class="ri-group-line"></i>
            {{ userInfo?.username }}的粉丝 
            <span class="post-count">({{ followersTotal || 0 }})</span>
          </h2>
        </div>
        
        <div v-if="isFollowersLoading && followers.length === 0" class="modern-loading">
          <div class="loading-dots">
            <div class="dot"></div>
            <div class="dot"></div>
            <div class="dot"></div>
          </div>
          <span class="loading-text">加载粉丝列表中...</span>
        </div>
        
        <div v-if="!isFollowersLoading && followers.length === 0" class="empty-state">
          <i class="ri-group-line"></i>
          <p>该用户还没有粉丝</p>
        </div>
        
        <div v-if="followers.length > 0" class="post-list">
          <div v-for="follower in followers" 
               :key="follower.id" 
               class="post-item">
            <div class="post-item-avatar-area">
              <img :src="follower.avatar" alt="User Avatar" class="post-item-avatar"/>
            </div>
            <div class="post-item-details">
              <div class="post-item-title-line">
                <div class="title-left">
                  <a :href="`/user/${follower.id}`" class="post-item-title">{{ follower.username }}</a>
                </div>
                <div class="post-tags">
                  <span v-if="follower.groupId" class="post-item-tag">{{ follower.groupId }}</span>
                </div>
              </div>
              
              <div class="post-item-meta">
                <span class="meta-time">
                  <i class="ri-calendar-line"></i>{{ formatTimeAgo(follower.followTime) }}关注
                </span>
                <div class="post-stats">
                  <span class="meta-stats like">
                    <i class="ri-star-line"></i>{{ follower.experience || 0 }}
                  </span>
                  <span class="meta-stats dislike">
                    <i class="ri-group-line"></i>{{ follower.followerCount || 0 }}
                  </span>
                  <span class="meta-stats comments">
                    <i class="ri-user-follow-line"></i>{{ follower.followingCount || 0 }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <!-- 粉丝分页 -->
        <div v-if="followersTotal && followersTotal > followersPageSize" class="pagination-section">
          <div class="pagination">
            <!-- 上一页 -->
            <button 
              @click="goToFollowersPage(followersCurrentPage - 1)"
              :disabled="followersCurrentPage <= 1 || isFollowersLoading"
              class="pagination-btn prev-btn">
              <i class="ri-arrow-left-s-line"></i> 上一页
            </button>
            
            <!-- 页码按钮 -->
            <template v-for="page in followersVisiblePages" :key="page">
              <button 
                v-if="page !== '...'"
                @click="goToFollowersPage(page)"
                :disabled="isFollowersLoading"
                class="pagination-btn page-number"
                :class="{ active: page === followersCurrentPage }">
                <span v-if="isFollowersLoading && page === followersCurrentPage" class="loading-spinner">⟳</span>
                <span v-else>{{ page }}</span>
              </button>
              <span v-else class="pagination-ellipsis">...</span>
            </template>
            
            <!-- 下一页 -->
            <button 
              @click="goToFollowersPage(followersCurrentPage + 1)"
              :disabled="followersCurrentPage >= followersTotalPages || isFollowersLoading"
              class="pagination-btn next-btn">
              下一页 <i class="ri-arrow-right-s-line"></i>
            </button>
          </div>
        </div>
      </div>

      <!-- 关注列表 -->
      <div v-if="activeTab === 'following'">
        <div class="section-header">
          <h2>
            <i class="ri-user-follow-line"></i>
            {{ userInfo?.username }}的关注 
            <span class="post-count">({{ followingTotal || 0 }})</span>
          </h2>
        </div>
        
        <div v-if="isFollowingLoading && following.length === 0" class="modern-loading">
          <div class="loading-dots">
            <div class="dot"></div>
            <div class="dot"></div>
            <div class="dot"></div>
          </div>
          <span class="loading-text">加载关注列表中...</span>
        </div>
        
        <div v-if="!isFollowingLoading && following.length === 0" class="empty-state">
          <i class="ri-user-follow-line"></i>
          <p>该用户还没有关注任何人</p>
        </div>
        
        <div v-if="following.length > 0" class="post-list">
          <div v-for="followedUser in following" 
               :key="followedUser.id" 
               class="post-item">
            <div class="post-item-avatar-area">
              <img :src="followedUser.avatar" alt="User Avatar" class="post-item-avatar"/>
            </div>
            <div class="post-item-details">
              <div class="post-item-title-line">
                <div class="title-left">
                  <a :href="`/user/${followedUser.id}`" class="post-item-title">{{ followedUser.username }}</a>
                </div>
                <div class="post-tags">
                  <span v-if="followedUser.groupId" class="post-item-tag">{{ followedUser.groupId }}</span>
                </div>
              </div>
              
              <div class="post-item-meta">
                <span class="meta-time">
                  <i class="ri-calendar-line"></i>{{ formatTimeAgo(followedUser.followTime) }}关注
                </span>
                <div class="post-stats">
                  <span class="meta-stats like">
                    <i class="ri-star-line"></i>{{ followedUser.experience || 0 }}
                  </span>
                  <span class="meta-stats dislike">
                    <i class="ri-group-line"></i>{{ followedUser.followerCount || 0 }}
                  </span>
                  <span class="meta-stats comments">
                    <i class="ri-user-follow-line"></i>{{ followedUser.followingCount || 0 }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <!-- 关注分页 -->
        <div v-if="followingTotal && followingTotal > followingPageSize" class="pagination-section">
          <div class="pagination">
            <!-- 上一页 -->
            <button 
              @click="goToFollowingPage(followingCurrentPage - 1)"
              :disabled="followingCurrentPage <= 1 || isFollowingLoading"
              class="pagination-btn prev-btn">
              <i class="ri-arrow-left-s-line"></i> 上一页
            </button>
            
            <!-- 页码按钮 -->
            <template v-for="page in followingVisiblePages" :key="page">
              <button 
                v-if="page !== '...'"
                @click="goToFollowingPage(page)"
                :disabled="isFollowingLoading"
                class="pagination-btn page-number"
                :class="{ active: page === followingCurrentPage }">
                <span v-if="isFollowingLoading && page === followingCurrentPage" class="loading-spinner">⟳</span>
                <span v-else>{{ page }}</span>
              </button>
              <span v-else class="pagination-ellipsis">...</span>
            </template>
            
            <!-- 下一页 -->
            <button 
              @click="goToFollowingPage(followingCurrentPage + 1)"
              :disabled="followingCurrentPage >= followingTotalPages || isFollowingLoading"
              class="pagination-btn next-btn">
              下一页 <i class="ri-arrow-right-s-line"></i>
            </button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- Toast提示 -->
    <div v-if="error && error.includes('时时刻刻')" class="toast-message">
      <div class="toast-content">
        <i class="ri-heart-line"></i>
        {{ error }}
      </div>
    </div>
  </LayoutWithSidebar>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter, useCookie } from '#app'
import { useUserStore } from '~/stores/user'
import { API } from '~/utils/api'
import LayoutWithSidebar from '~/components/LayoutWithSidebar.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 响应式数据
const userInfo = ref(null)
const posts = ref([])
const comments = ref([])
const followers = ref([])
const following = ref([])
const error = ref(null)
const isPostsLoading = ref(false)
const isCommentsLoading = ref(false)
const isFollowersLoading = ref(false)
const isFollowingLoading = ref(false)
const isFollowLoading = ref(false)

// Tab切换
const activeTab = ref('posts')

// 分页相关
const currentPage = ref(1)

// 评论分页相关
const commentCurrentPage = ref(1)
const commentTotal = ref(0)
const commentPageSize = ref(15)

// 粉丝分页相关
const followersCurrentPage = ref(1)
const followersTotal = ref(0)
const followersPageSize = ref(15)

// 关注分页相关
const followingCurrentPage = ref(1)
const followingTotal = ref(0)
const followingPageSize = ref(15)

// 计算属性
const totalPages = computed(() => {
  if (!userInfo.value) return 1
  return Math.ceil(userInfo.value.total / userInfo.value.pageSize)
})

// 评论分页计算属性
const commentTotalPages = computed(() => {
  return Math.ceil(commentTotal.value / commentPageSize.value)
})

// 粉丝分页计算属性
const followersTotalPages = computed(() => {
  return Math.ceil(followersTotal.value / followersPageSize.value)
})

// 关注分页计算属性
const followingTotalPages = computed(() => {
  return Math.ceil(followingTotal.value / followingPageSize.value)
})

// 分页可见页码
const visiblePages = computed(() => {
  const total = totalPages.value
  const current = currentPage.value
  const pages = []
  
  if (total <= 7) {
    // 总页数少于等于7页，显示所有页码
    for (let i = 1; i <= total; i++) {
      pages.push(i)
    }
  } else {
    // 总页数大于7页，需要省略号
    if (current <= 4) {
      // 当前页在前面
      for (let i = 1; i <= 5; i++) {
        pages.push(i)
      }
      pages.push('...')
      pages.push(total)
    } else if (current >= total - 3) {
      // 当前页在后面
      pages.push(1)
      pages.push('...')
      for (let i = total - 4; i <= total; i++) {
        pages.push(i)
      }
    } else {
      // 当前页在中间
      pages.push(1)
      pages.push('...')
      for (let i = current - 1; i <= current + 1; i++) {
        pages.push(i)
      }
      pages.push('...')
      pages.push(total)
    }
  }
  
  return pages
})

// 评论分页可见页码
const commentVisiblePages = computed(() => {
  const total = commentTotalPages.value
  const current = commentCurrentPage.value
  const pages = []
  
  if (total <= 7) {
    // 总页数少于等于7页，显示所有页码
    for (let i = 1; i <= total; i++) {
      pages.push(i)
    }
  } else {
    // 总页数大于7页，需要省略号
    if (current <= 4) {
      // 当前页在前面
      for (let i = 1; i <= 5; i++) {
        pages.push(i)
      }
      pages.push('...')
      pages.push(total)
    } else if (current >= total - 3) {
      // 当前页在后面
      pages.push(1)
      pages.push('...')
      for (let i = total - 4; i <= total; i++) {
        pages.push(i)
      }
    } else {
      // 当前页在中间
      pages.push(1)
      pages.push('...')
      for (let i = current - 1; i <= current + 1; i++) {
        pages.push(i)
      }
      pages.push('...')
      pages.push(total)
    }
  }
  
  return pages
})

// 粉丝分页可见页码
const followersVisiblePages = computed(() => {
  const total = followersTotalPages.value
  const current = followersCurrentPage.value
  const pages = []
  
  if (total <= 7) {
    for (let i = 1; i <= total; i++) {
      pages.push(i)
    }
  } else {
    if (current <= 4) {
      for (let i = 1; i <= 5; i++) {
        pages.push(i)
      }
      pages.push('...')
      pages.push(total)
    } else if (current >= total - 3) {
      pages.push(1)
      pages.push('...')
      for (let i = total - 4; i <= total; i++) {
        pages.push(i)
      }
    } else {
      pages.push(1)
      pages.push('...')
      for (let i = current - 1; i <= current + 1; i++) {
        pages.push(i)
      }
      pages.push('...')
      pages.push(total)
    }
  }
  
  return pages
})

// 关注分页可见页码
const followingVisiblePages = computed(() => {
  const total = followingTotalPages.value
  const current = followingCurrentPage.value
  const pages = []
  
  if (total <= 7) {
    for (let i = 1; i <= total; i++) {
      pages.push(i)
    }
  } else {
    if (current <= 4) {
      for (let i = 1; i <= 5; i++) {
        pages.push(i)
      }
      pages.push('...')
      pages.push(total)
    } else if (current >= total - 3) {
      pages.push(1)
      pages.push('...')
      for (let i = total - 4; i <= total; i++) {
        pages.push(i)
      }
    } else {
      pages.push(1)
      pages.push('...')
      for (let i = current - 1; i <= current + 1; i++) {
        pages.push(i)
      }
      pages.push('...')
      pages.push(total)
    }
  }
  
  return pages
})

const isCurrentUser = computed(() => {
  return userStore.isLoggedIn && 
    userStore.user?.id && userInfo.value?.id &&
    String(userStore.user.id) === String(userInfo.value.id)
})

// API请求函数
const fetchUserProfile = async (userId, pageNo = 1) => {
  try {
    error.value = null
    isPostsLoading.value = true
    
    const result = await API.user.getUserInfo(userId, pageNo)
    
    if (result.code === 200) {
      userInfo.value = result.data
      posts.value = result.data.posts || []
      currentPage.value = pageNo
    } else {
      error.value = result.msg || '获取用户信息失败'
    }
  } catch (err) {
    error.value = '网络错误，请稍后重试'
    console.error('获取用户信息失败:', err)
  } finally {
    isPostsLoading.value = false
  }
}

// 关注/取消关注
const toggleFollow = async () => {
  if (!userStore.isLoggedIn) {
    router.push('/auth/login')
    return
  }
  
  // 检查是否是自己关注自己
  if (isCurrentUser.value) {
    // 显示特殊提示
    error.value = '你时时刻刻都在关注你自己~~'
    // 3秒后清除提示
    setTimeout(() => {
      error.value = null
    }, 3000)
    return
  }
  
  isFollowLoading.value = true
  
  try {
    const isCurrentlyFollowing = userInfo.value.isFollowing
    const originalFollowerCount = userInfo.value.followerCount
    
    // 乐观更新UI
    userInfo.value.isFollowing = !isCurrentlyFollowing
    userInfo.value.followerCount = isCurrentlyFollowing ? 
      Math.max(0, originalFollowerCount - 1) : 
      originalFollowerCount + 1
    
    // 乐观显示Toast
    if (process.client && window.$toast) {
      if (!isCurrentlyFollowing) {
        window.$toast.success(`关注 ${userInfo.value.username} 成功！`)
      } else {
        window.$toast.success(`已取消关注 ${userInfo.value.username}`)
      }
    }
    
    // API调用
    const result = isCurrentlyFollowing ? 
      await API.user.unfollow(userInfo.value.id) : 
      await API.user.follow(userInfo.value.id)
    
    if (result.code !== 200) {
      // 如果API失败，恢复原始状态
      userInfo.value.isFollowing = isCurrentlyFollowing
      userInfo.value.followerCount = originalFollowerCount
      
      if (process.client && window.$toast) {
        window.$toast.error(result.msg || '操作失败，请稍后重试')
      }
    }
  } catch (err) {
    console.error('关注操作失败:', err)
    
    // 恢复原始状态
    if (userInfo.value) {
      userInfo.value.isFollowing = !userInfo.value.isFollowing
      userInfo.value.followerCount = userInfo.value.isFollowing ? 
        userInfo.value.followerCount + 1 : 
        Math.max(0, userInfo.value.followerCount - 1)
    }
    
    if (process.client && window.$toast) {
      window.$toast.error('网络错误，请稍后重试')
    }
  } finally {
    isFollowLoading.value = false
  }
}

// Tab切换函数
const switchTab = async (tabName) => {
  if (activeTab.value === tabName) return
  
  activeTab.value = tabName
  
  if (tabName === 'comments' && comments.value.length === 0) {
    await fetchUserComments(route.params.id, 1)
  } else if (tabName === 'followers' && followers.value.length === 0) {
    await fetchUserFollowers(route.params.id, 1)
  } else if (tabName === 'following' && following.value.length === 0) {
    await fetchUserFollowing(route.params.id, 1)
  }
}

// 获取用户评论
const fetchUserComments = async (userId, pageNo = 1) => {
  try {
    isCommentsLoading.value = true
    error.value = null
    
    const result = await API.user.getUserComments(userId, pageNo)
    
    if (result.code === 200) {
      comments.value = result.data || []
      commentCurrentPage.value = pageNo
      // 从API响应中获取总数，如果没有则使用用户信息中的评论数
      commentTotal.value = result.total || userInfo.value?.commentCount || 0
    } else {
      error.value = result.msg || '获取评论失败'
    }
  } catch (err) {
    error.value = '网络错误，请稍后重试'
    console.error('获取用户评论失败:', err)
  } finally {
    isCommentsLoading.value = false
  }
}

// 获取用户粉丝
const fetchUserFollowers = async (userId, pageNo = 1) => {
  try {
    isFollowersLoading.value = true
    error.value = null
    
    const result = await API.user.getUserFollowers(userId, pageNo)
    
    if (result.code === 200) {
      followers.value = result.data?.records || []
      followersCurrentPage.value = pageNo
      followersTotal.value = result.data?.total || userInfo.value?.followerCount || 0
    } else {
      error.value = result.msg || '获取粉丝列表失败'
    }
  } catch (err) {
    error.value = '网络错误，请稍后重试'
    console.error('获取用户粉丝失败:', err)
  } finally {
    isFollowersLoading.value = false
  }
}

// 获取用户关注
const fetchUserFollowing = async (userId, pageNo = 1) => {
  try {
    isFollowingLoading.value = true
    error.value = null
    
    const result = await API.user.getUserFollowing(userId, pageNo)
    
    if (result.code === 200) {
      following.value = result.data?.records || []
      followingCurrentPage.value = pageNo
      followingTotal.value = result.data?.total || userInfo.value?.followingCount || 0
    } else {
      error.value = result.msg || '获取关注列表失败'
    }
  } catch (err) {
    error.value = '网络错误，请稍后重试'
    console.error('获取用户关注失败:', err)
  } finally {
    isFollowingLoading.value = false
  }
}

// 评论分页跳转
const goToCommentPage = async (page) => {
  if (page < 1 || page > commentTotalPages.value || isCommentsLoading.value) return
  
  try {
    await fetchUserComments(route.params.id, page)
    // 滚动到页面顶部
    if (process.client) {
      window.scrollTo({ top: 0, behavior: 'smooth' })
    }
  } catch (err) {
    console.error('评论分页跳转失败:', err)
    isCommentsLoading.value = false
  }
}

// 粉丝分页跳转
const goToFollowersPage = async (page) => {
  if (page < 1 || page > followersTotalPages.value || isFollowersLoading.value) return
  
  try {
    await fetchUserFollowers(route.params.id, page)
    // 滚动到页面顶部
    if (process.client) {
      window.scrollTo({ top: 0, behavior: 'smooth' })
    }
  } catch (err) {
    console.error('粉丝分页跳转失败:', err)
    isFollowersLoading.value = false
  }
}

// 关注分页跳转
const goToFollowingPage = async (page) => {
  if (page < 1 || page > followingTotalPages.value || isFollowingLoading.value) return
  
  try {
    await fetchUserFollowing(route.params.id, page)
    // 滚动到页面顶部
    if (process.client) {
      window.scrollTo({ top: 0, behavior: 'smooth' })
    }
  } catch (err) {
    console.error('关注分页跳转失败:', err)
    isFollowingLoading.value = false
  }
}

// 分页跳转
const goToPage = async (page) => {
  if (page < 1 || page > totalPages.value || isPostsLoading.value) return
  
  try {
    // 直接更新数据，而不是路由跳转
    await fetchUserProfile(route.params.id, page)
    
    // 更新URL但不触发页面重新加载
    await router.replace({
      path: `/user/${route.params.id}`,
      query: { page: page.toString() }
    })
    
    // 滚动到页面顶部
    if (process.client) {
      window.scrollTo({ top: 0, behavior: 'smooth' })
    }
  } catch (err) {
    console.error('分页跳转失败:', err)
    // 即使失败也要停止加载状态
    isPostsLoading.value = false
  }
}



// 跳转到评论位置
const goToComment = async (comment) => {
  // 立即显示loading动画
  if (process.client && window.showPageTransitionLoader) {
    window.showPageTransitionLoader()
  }
  
  try {
    // 调用API获取评论位置信息
    const result = await API.user.getCommentLocation(comment.id)
    
    if (result.code === 200) {
      const { postId, page, parentCommentId, commentId } = result.data
      
      // 构建跳转URL
      let targetUrl = `/post/${postId}?page=${page}`
      
      // 如果是二级评论，需要展开父评论并定位到具体评论
      if (parentCommentId) {
        targetUrl += `&expand=${parentCommentId}&highlight=${commentId}`
      } else {
        // 一级评论直接定位
        targetUrl += `&highlight=${commentId}`
      }
      
      // 使用现有的页面跳转方法
      if (process.client && window.navigateWithPageTransition) {
        window.navigateWithPageTransition(targetUrl)
      } else {
        window.location.href = targetUrl
      }
    } else {
      // 隐藏loading
      if (process.client && window.hidePageTransitionLoader) {
        window.hidePageTransitionLoader()
      }
      if (process.client && window.$toast) {
        window.$toast.error(result.msg || '获取评论位置失败')
      }
    }
  } catch (err) {
    console.error('跳转评论失败:', err)
    // 隐藏loading
    if (process.client && window.hidePageTransitionLoader) {
      window.hidePageTransitionLoader()
    }
    if (process.client && window.$toast) {
      window.$toast.error('网络错误，请稍后重试')
    }
  }
}

// 帖子详情跳转现在通过<a>标签的href实现刷新式加载

// 时间格式化函数
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

const formatDate = (timestamp) => {
  if (!timestamp) return ''
  return new Date(timestamp).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
}

// SSR数据获取 - API工具类会自动处理认证token
const { data: initialData } = await useAsyncData(
  `user-${route.params.id}`,
  async () => {
    const userId = route.params.id
    const pageNo = parseInt(route.query.page) || 1
    
    try {
      console.log('个人主页API调用 - 用户ID:', userId, '页码:', pageNo)
      
      const response = await API.user.getUserInfo(userId, pageNo)
      if (response.code === 200) {
        console.log('个人主页数据获取成功:', response.data?.username)
        return { userInfo: response.data, error: null }
      } else {
        console.error('个人主页API返回错误:', response.msg)
        return { userInfo: null, error: response.msg || '获取用户信息失败' }
      }
    } catch (err) {
      console.error('个人主页API调用失败:', err)
      return { userInfo: null, error: '获取用户信息失败' }
    }
  }
)

// 初始化数据
if (initialData.value?.userInfo) {
  userInfo.value = initialData.value.userInfo
  posts.value = initialData.value.userInfo.posts || []
  currentPage.value = parseInt(route.query.page) || 1
}

if (initialData.value?.error) {
  error.value = initialData.value.error
}

// 监听路由变化 - 只在初始加载和直接URL访问时触发
watch(() => route.query.page, (newPage, oldPage) => {
  const page = parseInt(newPage) || 1
  // 只有在页面初始化或直接修改URL时才重新获取数据
  if (page !== currentPage.value && oldPage !== undefined) {
    fetchUserProfile(route.params.id, page)
  }
}, { immediate: false })

// 设置页面标题
const title = computed(() => {
  return userInfo.value ? `${userInfo.value.username} 的主页` : '用户主页'
})

useHead({
  title
})
</script>

<style scoped>
.user-profile {
  background: white;
  border-radius: 16px;
  overflow: hidden;
  margin-bottom: 0;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(0, 0, 0, 0.04);
}

.user-header {
  padding: 1.5rem;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.user-avatar-section {
  flex-shrink: 0;
  position: relative;
}

.user-avatar {
  width: 100px;
  height: 100px;
  border-radius: 20px;
  object-fit: cover;
  border: 4px solid white;
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
  transition: transform 0.3s ease;
}

.user-avatar:hover {
  transform: scale(1.05);
}

.user-info-section {
  flex: 1;
  min-width: 0;
}

.username-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.75rem;
}

.username {
  font-size: 2rem;
  font-weight: 800;
  color: #1e293b;
  margin: 0;
  line-height: 1.1;
  background: linear-gradient(135deg, #1e293b 0%, #475569 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.follow-btn {
  padding: 0.75rem 1.5rem;
  font-size: 0.9rem;
  border-radius: 12px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.3);
}

.user-meta {
  display: flex;
  gap: 1.5rem;
  color: #64748b;
  font-size: 0.9rem;
  flex-wrap: wrap;
}

.user-group,
.join-date {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.user-group i {
  color: #f59e0b;
  font-size: 1rem;
}

.join-date i {
  color: #6b7280;
  font-size: 1rem;
}

.level-section {
  margin: 0.75rem 1.5rem;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #a855f7 100%);
  color: white;
  padding: 1rem;
  border-radius: 16px;
  box-shadow: 0 8px 25px rgba(99, 102, 241, 0.3);
  position: relative;
  overflow: hidden;
}

.level-section::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -50%;
  width: 100%;
  height: 100%;
  background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 70%);
  animation: shimmer 3s ease-in-out infinite;
}

@keyframes shimmer {
  0%, 100% { transform: translateX(-100%) translateY(-100%) rotate(0deg); }
  50% { transform: translateX(0%) translateY(0%) rotate(180deg); }
}

.level-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
  position: relative;
  z-index: 1;
}

.level-name {
  font-size: 1.25rem;
  font-weight: 700;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.level-number {
  font-size: 1rem;
  opacity: 0.9;
  background: rgba(255, 255, 255, 0.2);
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  backdrop-filter: blur(10px);
}

.experience-bar {
  background: rgba(255, 255, 255, 0.2);
  height: 8px;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 0.375rem;
  position: relative;
  z-index: 1;
}

.experience-progress {
  background: linear-gradient(90deg, #34d399, #10b981, #06d6a0);
  height: 100%;
  border-radius: 4px;
  transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 0 10px rgba(52, 211, 153, 0.5);
}

.experience-text {
  font-size: 0.9rem;
  opacity: 0.95;
  text-align: center;
  position: relative;
  z-index: 1;
  font-weight: 600;
}

.total-experience-info {
  margin-top: 0.75rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: relative;
  z-index: 1;
  gap: 1rem;
}

.total-exp,
.next-level-exp {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: rgba(255, 255, 255, 0.15);
  padding: 0.5rem 0.75rem;
  border-radius: 10px;
  backdrop-filter: blur(10px);
  flex: 1;
  transition: all 0.3s ease;
}

.total-exp:hover,
.next-level-exp:hover {
  background: rgba(255, 255, 255, 0.2);
  transform: translateY(-2px);
}

.exp-label {
  font-size: 0.75rem;
  opacity: 0.8;
  margin-bottom: 0.25rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.exp-value {
  font-size: 1.1rem;
  font-weight: 700;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

.stats-section {
  padding: 1rem;
  background: #f8fafc;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.75rem;
}

.stat-item {
  text-align: center;
  padding: 0.875rem 0.75rem;
  background: white;
  border-radius: 12px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid #e2e8f0;
  position: relative;
  overflow: hidden;
  cursor: pointer;
}

.stat-item::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 3px;
  background: linear-gradient(90deg, #3b82f6, #6366f1, #8b5cf6);
  transform: scaleX(0);
  transition: transform 0.3s ease;
}

.stat-item:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.15);
  border-color: #cbd5e1;
}

.stat-item:hover::before {
  transform: scaleX(1);
}

.stat-item.active {
  background: white;
  border-color: #3b82f6;
  box-shadow: 0 8px 25px rgba(59, 130, 246, 0.25);
  transform: translateY(-4px);
  border: 2px solid #3b82f6;
}

.stat-item.active::before {
  transform: scaleX(1);
  background: #3b82f6;
  height: 3px;
}

.stat-item.active .stat-number {
  background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  transform: scale(1.1);
}

.stat-item.active .stat-label {
  color: #3b82f6;
  font-weight: 700;
}

.stat-number {
  font-size: 2rem;
  font-weight: 800;
  background: linear-gradient(135deg, #2563eb 0%, #7c3aed 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 0.5rem;
  line-height: 1;
}

.stat-label {
  color: #64748b;
  font-size: 0.875rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.section-header {
  margin-bottom: 0;
  padding: 1rem;
  background: white;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(0, 0, 0, 0.04);
}

.section-header h2 {
  color: #1e293b;
  font-size: 1.5rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin: 0;
}

.section-header h2 i {
  color: #6366f1;
  font-size: 1.25rem;
}

.post-count {
  color: #64748b;
  font-size: 0.9rem;
  font-weight: 500;
  background: #f1f5f9;
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  margin-left: 0.5rem;
}

.empty-state {
  text-align: center;
  padding: 4rem 2rem;
  color: #64748b;
  background: white;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(0, 0, 0, 0.04);
}

.empty-state i {
  font-size: 4rem;
  margin-bottom: 1.5rem;
  color: #cbd5e1;
  background: linear-gradient(135deg, #cbd5e1 0%, #94a3b8 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.empty-state p {
  font-size: 1.1rem;
  font-weight: 500;
  margin: 0;
}

.post-list {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.post-item {
  display: flex;
  padding: 0.65rem 0.85rem;
  border-bottom: 1px solid var(--border-color, #f1f5f9);
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
  overflow: hidden;
}

.post-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  width: 3px;
  background: linear-gradient(to bottom, #3b82f6, #6366f1);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.post-item:hover {
  background: #f8fafc;
  transform: translateY(-1px);
}

.post-item:hover::before {
  opacity: 1;
}

.post-item:last-child {
  border-bottom: none;
}

.post-item-avatar-area {
  margin-right: 0.75rem;
  flex-shrink: 0;
}

.post-item-avatar {
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 6px;
  object-fit: cover;
  transition: transform 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.post-item-details {
  flex: 1;
  min-width: 0;
}

.post-item-title-line {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 0.35rem;
  width: 100%;
}

.title-left {
  flex: 1;
  min-width: 0;
  margin-right: 1rem;
}

.post-item-title {
  font-weight: 600;
  color: #1a202c;
  text-decoration: none;
  transition: color 0.15s;
  font-size: 0.9rem;
  line-height: 1.35;
  margin-bottom: 0.1rem;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

.post-item-title:hover {
  color: var(--primary-color, #2563eb);
}

.post-tags {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.post-item-tag {
  font-size: 0.65rem;
  padding: 0.05rem 0.3rem;
  border-radius: 3px;
  background-color: rgba(79, 70, 229, 0.08);
  color: #4f46e5;
  text-decoration: none;
  flex-shrink: 0;
  font-weight: 500;
  line-height: 1.5;
  transition: all 0.2s ease;
  border: 1px solid transparent;
  white-space: nowrap;
}

.post-item-tag:hover {
  background-color: rgba(79, 70, 229, 0.12);
  border-color: rgba(79, 70, 229, 0.2);
  transform: translateY(-1px);
}

.post-item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  font-size: 0.65rem;
  color: #64748b;
  margin-top: 0.1rem;
}

.meta-time {
  display: inline-flex;
  align-items: center;
  color: #64748b;
  font-size: 0.65rem;
}

.meta-time i {
  font-size: 0.75rem;
  margin-right: 0.25rem;
  opacity: 0.7;
}

.post-stats {
  display: flex;
  gap: 0.6rem;
  margin-left: auto;
}

.meta-stats {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  color: #64748b;
  font-size: 0.65rem;
}

.meta-stats.like i {
  color: #16a34a;
}

.meta-stats.dislike i {
  color: #dc2626;
}

.meta-stats.comments i {
  color: #2563eb;
}

.pagination-section {
  margin: 0;
  padding: 0;
  display: flex;
  justify-content: center;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 2px;
  margin: 0.5rem 0;
  padding: 0;
  background: transparent;
}

.pagination-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
  padding: 0 8px;
  background: #fff;
  border: 1px solid #dee2e6;
  color: #6c757d;
  font-weight: 400;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s ease;
  text-decoration: none;
  border-radius: 0;
}

.pagination-btn:first-child {
  border-top-left-radius: 4px;
  border-bottom-left-radius: 4px;
}

.pagination-btn:last-child {
  border-top-right-radius: 4px;
  border-bottom-right-radius: 4px;
}

.pagination-btn:not(:first-child) {
  border-left: none;
}

.pagination-btn:hover:not(:disabled):not(.active) {
  background-color: #e9ecef;
  border-color: #dee2e6;
  color: #495057;
}

.pagination-btn.active {
  background: #007bff;
  border-color: #007bff;
  color: white;
  font-weight: 500;
}

.pagination-btn:disabled {
  background-color: #fff;
  border-color: #dee2e6;
  color: #6c757d;
  opacity: 0.65;
  cursor: not-allowed;
}

.pagination-ellipsis {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
  padding: 0 8px;
  color: #6c757d;
  font-size: 14px;
  border: 1px solid #dee2e6;
  border-left: none;
  background: #fff;
}

.loading-spinner {
  animation: spin 1s linear infinite;
  display: inline-block;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.btn {
  padding: 0.75rem 1.5rem;
  border-radius: 8px;
  font-weight: 600;
  font-size: 0.9rem;
  transition: all 0.2s ease;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  border: none;
  text-decoration: none;
}

.btn-primary {
  background: #2563eb;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #1d4ed8;
}

.btn-outline {
  background: white;
  color: #475569;
  border: 1px solid #e2e8f0;
}

.btn-outline:hover:not(:disabled) {
  background: #f1f5f9;
  border-color: #cbd5e1;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.loading-spinner {
  text-align: center;
  padding: 2rem;
  color: #64748b;
}

.custom-loader {
  display: inline-block;
  width: 20px;
  height: 20px;
  border: 3px solid #f3f3f3;
  border-top: 3px solid #2563eb;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-right: 0.5rem;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.message {
  padding: 1rem;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.error-message {
  background: #fef2f2;
  color: #dc2626;
  border: 1px solid #fecaca;
}

.info-message {
  background: #f0f9ff;
  color: #0369a1;
  border: 1px solid #bae6fd;
}

/* Toast提示样式 */
.toast-message {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 1000;
  animation: slideInRight 0.3s ease-out, fadeOut 0.3s ease-in 2.7s;
}

.toast-content {
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  color: #0369a1;
  padding: 0.75rem 1rem;
  border-radius: 8px;
  border: 1px solid #bae6fd;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  font-weight: 500;
  max-width: 280px;
}

.toast-content i {
  color: #ec4899;
  font-size: 1rem;
}

@keyframes slideInRight {
  from {
    transform: translateX(100%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

@keyframes fadeOut {
  from {
    opacity: 1;
  }
  to {
    opacity: 0;
  }
}

/* 现代加载动画 */
.modern-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 3rem 2rem;
  color: #6b7280;
}

.loading-dots {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: linear-gradient(45deg, #667eea, #764ba2);
  animation: bounce 1.4s infinite both;
}

.dot:nth-child(1) { animation-delay: 0s; }
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0.8);
    opacity: 0.5;
  }
  40% {
    transform: scale(1.2);
    opacity: 1;
  }
}

.loading-text {
  font-size: 0.9rem;
  font-weight: 500;
  color: #6b7280;
}

/* 评论特定样式 */
.comment-context-inline {
  display: flex;
  align-items: baseline;
  gap: 0;
  flex-wrap: wrap;
  line-height: normal;
}

.comment-in-post {
  color: #6b7280;
  font-size: 0.875rem;
}

.reply-indicator {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  color: #3b82f6;
  font-size: 0.875rem;
  font-weight: 600;
}

.reply-indicator i {
  font-size: 0.875rem;
}

/* 评论中帖子标题 - 自适应宽度 */
.comment-post-title {
  display: inline !important;
  width: auto !important;
  max-width: none !important;
  flex: none !important;
  min-width: auto !important;
  flex-grow: 0 !important;
  flex-shrink: 1 !important;
}

.comment-content-inline {
  color: #8b5cf6;
  font-size: 0.875rem;
  font-weight: 600;
  margin-left: 0.25rem;
  display: inline;
}

.clickable-comment-card {
  cursor: pointer;
  transition: all 0.2s ease;
}

.clickable-comment-card:hover {
  background: rgba(139, 92, 246, 0.05);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}



.content-section {
  margin-bottom: 1rem;
}



@media (max-width: 768px) {
  .user-header {
    flex-direction: column;
    gap: 1rem;
    text-align: center;
    padding: 1.25rem;
  }
  
  .user-avatar {
    width: 80px;
    height: 80px;
    margin: 0 auto;
  }
  
  .username {
    font-size: 1.5rem;
  }
  
  .username-row {
    flex-direction: column;
    gap: 1rem;
    align-items: center;
  }
  
  .user-meta {
    justify-content: center;
    gap: 1rem;
  }
  
  .level-section {
    margin: 0.5rem 1rem;
    padding: 0.875rem;
  }
  
  .level-name {
    font-size: 1.1rem;
  }
  
  .total-experience-info {
    flex-direction: column;
    gap: 0.5rem;
    margin-top: 0.5rem;
  }
  
  .total-exp,
  .next-level-exp {
    padding: 0.4rem 0.6rem;
  }
  
  .exp-label {
    font-size: 0.7rem;
  }
  
  .exp-value {
    font-size: 1rem;
  }
  
  .stats-section {
    grid-template-columns: repeat(2, 1fr);
    padding: 0.875rem;
    gap: 0.5rem;
  }
  

  
  .stat-item {
    padding: 0.75rem 0.5rem;
  }
  
  .stat-number {
    font-size: 1.5rem;
  }
  
  .stat-label {
    font-size: 0.75rem;
  }
  
  .post-item {
    padding: 0.6rem 0.75rem;
  }
  
  .post-item-title-line {
    flex-direction: column;
    gap: 0.5rem;
  }
  
  .post-stats {
    margin-left: 0;
  }
  
  .post-item-meta {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }
  
  .section-header {
    padding: 0.875rem;
  }
  
  .section-header h2 {
    font-size: 1.25rem;
  }
  
  .pagination {
    gap: 1px;
    margin: 0.25rem 0;
  }
  
  .pagination-btn {
    min-width: 28px;
    height: 28px;
    padding: 0 6px;
    font-size: 13px;
  }
  
  .pagination-ellipsis {
    min-width: 28px;
    height: 28px;
    padding: 0 6px;
    font-size: 13px;
  }
}
</style> 