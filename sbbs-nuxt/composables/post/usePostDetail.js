/* 移除之前添加的所有修改，恢复到原始状态 */
/* 如果您需要保留原始的JavaScript，请提供原始文件内容 */

import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { usePostStore } from '~/stores/post'
import { useUserStore } from '~/stores/user'
import { useMarkdownIt } from '~/composables/post/useMarkdownIt'
import { useLoginModal } from '~/composables/useLoginModal'

// 帮助函数: 从客户端获取cookie值
const getCookieValue = (name) => {
  if (process.client) {
    const match = document.cookie.match(new RegExp('(^| )' + name + '=([^;]+)'))
    return match ? match[2] : null
  }
  return null
}

// 检查并处理今日首次点赞积分奖励
const checkDailyLikeReward = () => {
  if (!process.client) return false
  
  const today = new Date().toDateString()
  const lastLikeDate = localStorage.getItem('lastLikeDate')
  
  if (lastLikeDate !== today) {
    // 今日首次点赞，记录日期
    localStorage.setItem('lastLikeDate', today)
    return true // 返回true表示获得了积分奖励
  }
  
  return false // 返回false表示今日已经获得过积分
}

/**
 * 帖子详情相关的组合式函数
 */
export function usePostDetail() {
  const router = useRouter()
  const route = useRoute()
  const postStore = usePostStore()
  const userStore = useUserStore()
  
  // 防抖标识
  let isLikeProcessing = false
  let isDislikeProcessing = false

  // 响应式数据
  const postId = computed(() => route.params.id)
  const post = computed(() => postStore.currentPost)
  const isLoading = ref(false)
  const error = ref(null)

  // 用户相关
  const isLoggedIn = computed(() => userStore.isLoggedIn)
  const userInfo = computed(() => userStore.user || {})
  const isAuthor = computed(() => {
    const loggedIn = isLoggedIn.value
    const postData = post.value
    const currentUser = userInfo.value
    
    console.log('🔍 检查帖子作者权限:', {
      loggedIn,
      currentUserId: currentUser?.id,
      currentUserUsername: currentUser?.username,
      postAuthorId: postData?.author?.id,
      postAuthorUsername: postData?.author?.username,
      postData: postData
    })
    
    if (!loggedIn || !postData) {
      console.log('❌ 权限检查失败: 用户未登录或帖子数据缺失')
      return false
    }
    
    if (!currentUser) {
      console.log('⏳ 权限检查等待: 用户信息加载中')
      return false // 用户信息加载中，暂时返回false
    }
    
    // 通过ID或用户名比较
    const isOwner = (
      (currentUser.id && postData.author?.id && currentUser.id === postData.author.id) ||
      (currentUser.username && postData.author?.username && currentUser.username === postData.author.username)
    )
    
    console.log('✅ 权限检查结果:', isOwner)
    return isOwner
  })

  // 点赞相关
  const isLiked = computed(() => postStore.isLiked)
  const isDisliked = computed(() => postStore.isDisliked)

  // 渲染后的内容
  const renderedContent = computed(() => {
    if (!post.value?.content) return ''
    
    try {
      let content = post.value.content
      
      console.log('🎯 原始内容:', content)
      
      // 使用我们的可复用markdown-it组件
      const { renderMarkdown } = useMarkdownIt()
      
      // 预处理内容，先处理分区标题
      content = content.replace(/\*\*\[([^\]]+)\]\*\*/g, (match, sectionName) => {
        const sectionHeader = `<div class="content-section-header">
          <div class="section-icon"></div>
          <h3 class="section-title">${sectionName}</h3>
        </div>`
        
        return sectionHeader
      })
      
      console.log('🎯 预处理后内容:', content)
      
      // 直接使用markdown-it渲染器，不尝试自己处理加粗和其他格式
      let html = renderMarkdown(content)
      
      console.log('🎯 markdown-it解析后:', html)
      
      // 增强段落样式
      html = html.replace(/<p>/g, '<p class="content-paragraph">')
      
      return html
    } catch (error) {
      console.error('渲染内容失败:', error)
      return post.value.content || ''
    }
  })

  // 获取帖子详情
  const fetchPost = async (id = null) => {
    const targetId = id || postId.value
    if (!targetId) return
    
    isLoading.value = true
    error.value = null
    
    try {
      console.log('🔍 开始加载帖子详情，ID:', targetId)
      
      const result = await postStore.fetchPostById(targetId)
      
      if (result.success) {
        console.log('🔍 帖子详情加载成功:', result.post)
      } else {
        error.value = result.error || '获取帖子详情失败'
        console.error('❌ 获取帖子详情失败:', error.value)
      }
    } catch (err) {
      error.value = err.message || '获取帖子详情失败'
      console.error('❌ 获取帖子详情失败:', err)
    } finally {
      isLoading.value = false
    }
  }

  // 导入登录弹窗功能
  const { requireLoginForAction } = useLoginModal()
  
  // 点赞帖子
  const handleLike = async () => {
    // 防抖处理
    if (isLikeProcessing) return
    isLikeProcessing = true
    
    const canProceed = await requireLoginForAction('点赞')
    if (!canProceed) {
      isLikeProcessing = false
      return
    }
    
    if (!post.value) {
      isLikeProcessing = false
      return
    }
    
    try {
      // 执行点赞操作
      const result = await postStore.likePost(post.value.id, !isLiked.value)
      if (!result.success) {
        console.error('点赞失败:', result.error)
      }
    } catch (error) {
      console.error('点赞失败:', error)
    } finally {
      // 延迟重置防抖标识，避免过快点击
      setTimeout(() => {
        isLikeProcessing = false
      }, 200)
    }
  }

  // 点踩帖子
  const handleDislike = async () => {
    // 防抖处理
    if (isDislikeProcessing) return
    isDislikeProcessing = true
    
    const canProceed = await requireLoginForAction('点踩')
    if (!canProceed) {
      isDislikeProcessing = false
      return
    }
    
    if (!post.value) {
      isDislikeProcessing = false
      return
    }
    
    try {
      // 执行点踩操作
      const result = await postStore.dislikePost(post.value.id, !isDisliked.value)
      if (!result.success) {
        console.error('点踩失败:', result.error)
      }
    } catch (error) {
      console.error('点踩失败:', error)
    } finally {
      // 延迟重置防抖标识，避免过快点击
      setTimeout(() => {
        isDislikeProcessing = false
      }, 200)
    }
  }

  // 分享帖子
  const handleShare = () => {
    if (!post.value) return
    
    const url = `${window.location.origin}/post/${post.value.id}`
    
    if (navigator.share) {
      navigator.share({
        title: post.value.title,
        text: post.value.title,
        url: url
      }).catch(err => {
        console.log('分享失败:', err)
        copyToClipboard(url)
      })
    } else {
      copyToClipboard(url)
    }
  }

  // 复制到剪贴板
  const copyToClipboard = (text) => {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text).then(() => {
        alert('链接已复制到剪贴板')
      }).catch(err => {
        console.error('复制失败:', err)
        fallbackCopyTextToClipboard(text)
      })
    } else {
      fallbackCopyTextToClipboard(text)
    }
  }

  // 兼容性复制方法
  const fallbackCopyTextToClipboard = (text) => {
    const textArea = document.createElement('textarea')
    textArea.value = text
    document.body.appendChild(textArea)
    textArea.focus()
    textArea.select()
    
    try {
      document.execCommand('copy')
      alert('链接已复制到剪贴板')
    } catch (err) {
      console.error('复制失败:', err)
      alert('复制失败，请手动复制链接')
    }
    
    document.body.removeChild(textArea)
  }

  // 编辑帖子
  const handleEdit = async () => {
    if (!isAuthor.value || !post.value) return
    
    // 设置编辑状态
    if (process.client) {
      window._isNavigatingToEdit = true;
    }
    
    try {
    if (process.client) {
      window._skipNextTransition = true;
    }
      
      // 短暂延迟让用户看到加载状态
      await new Promise(resolve => setTimeout(resolve, 300))
      
      const url = `/post/edit/${post.value.id}`;
      await router.push(url);
    } catch (error) {
      console.error('导航到编辑页面失败:', error)
    } finally {
      // 重置加载状态
      if (process.client) {
        setTimeout(() => {
          window._isNavigatingToEdit = false;
        }, 500)
      }
    }
  }

  // 删除帖子
  const handleDelete = async () => {
    if (!isAuthor.value || !post.value) return
    
    if (confirm('确定要删除这篇帖子吗？删除后无法恢复。')) {
      try {
        const result = await postStore.deletePost(post.value.id)
        if (result.success) {
          alert('帖子删除成功')
          
          if (process.client && window.navigateWithPageTransition) {
            window.navigateWithPageTransition('/');
          } else {
            router.push('/');
          }
        } else {
          alert(result.error || '删除失败')
        }
      } catch (error) {
        console.error('删除失败:', error)
        alert('删除失败，请稍后重试')
      }
    }
  }

  // 滚动到评论区
  const scrollToComments = () => {
    const commentsElement = document.getElementById('comments-section')
    if (commentsElement) {
      commentsElement.scrollIntoView({ behavior: 'smooth' })
    }
  }

  // 注意：路由变化已由页面中的useAsyncData处理，这里不需要重复监听

  return {
    // 响应式数据
    postId,
    post,
    isLoading,
    error,
    
    // 用户相关
    isLoggedIn,
    userInfo,
    isAuthor,
    
    // 点赞相关
    isLiked,
    isDisliked,
    
    // 计算属性
    renderedContent,
    
    // 方法
    fetchPost,
    handleLike,
    handleDislike,
    handleShare,
    handleEdit,
    handleDelete,
    scrollToComments
  }
}
