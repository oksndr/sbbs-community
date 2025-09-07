import { ref, computed, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { usePostStore } from '~/stores/post'
import { useUserStore } from '~/stores/user'
import pointsManager from '~/utils/points'
import { useMarkdownIt } from '~/composables/post/useMarkdownIt'

// 帮助函数: 从客户端获取cookie值
const getCookieValue = (name) => {
  if (process.client) {
    const match = document.cookie.match(new RegExp('(^| )' + name + '=([^;]+)'))
    return match ? match[2] : null
  }
  return null
}

// 帮助函数: 获取认证token
const getToken = () => {
  if (process.client) {
    return localStorage.getItem('token') || getCookieValue('Authorization')?.replace('Bearer ', '')
  }
  return null
}

// 使用评论功能的Hook
export function useComments(postId, initialComments = []) {
  // 获取API基础URL
  const API_BASE_URL = useApiBaseUrl()

  const router = useRouter()
  const route = useRoute()
  const postStore = usePostStore()
  const userStore = useUserStore()

  // 用户数据
  const isLoggedIn = computed(() => userStore.isLoggedIn)
  const userInfo = computed(() => userStore.user || {})

  // 评论相关数据
  const localComments = ref(initialComments) // 本地存储的评论列表
  const comments = computed(() => localComments.value) // 使用本地列表
  const commentContent = ref('')
  const isCommentSubmitting = ref(false)
  const isCommentsLoading = ref(false)

  // 分页相关
  const currentPage = ref(1)
  const totalPages = ref(1)
  const hasNextPage = ref(false)
  const hasPreviousPage = ref(false)
  const totalComments = ref(0)
  const pageSize = ref(15)

  // 获取当前页码
  const pageFromRoute = computed(() => {
    const page = parseInt(route.query.page) || 1
    return page
  })

  // 是否在第一页
  const isFirstPage = computed(() => pageFromRoute.value === 1)

  // 显示详情内容
  const showPostDetails = computed(() => pageFromRoute.value === 1)

  // 回复相关数据
  const activeReplyId = ref(null)
  const replyContent = ref('')
  const isReplySubmitting = ref(false)
  const activeReplyToReplyId = ref(null)
  const replyToUsername = ref('')
  const replyToId = ref(null)
  const replyTextarea = ref(null)

  // contenteditable 引用
  const commentEditable = ref(null)
  
  // 拖拽上传相关状态
  const isDragOver = ref(false)
  const isImageUploading = ref(false)
  
  // 拖拽上传功能
  const handleDragOver = (event) => {
    event.preventDefault()
    event.stopPropagation()
  }
  
  const handleDragEnter = (event) => {
    event.preventDefault()
    event.stopPropagation()
    isDragOver.value = true
  }
  
  const handleDragLeave = (event) => {
    event.preventDefault()
    event.stopPropagation()
    // 确保真正离开了评论输入框区域
    const rect = event.currentTarget.getBoundingClientRect()
    const x = event.clientX
    const y = event.clientY
    
    if (x < rect.left || x > rect.right || y < rect.top || y > rect.bottom) {
      isDragOver.value = false
    }
  }
  
  const handleDrop = async (event) => {
    event.preventDefault()
    event.stopPropagation()
    isDragOver.value = false
    
    const files = event.dataTransfer.files
    if (files.length > 0) {
      const file = files[0]
      
      // 检查是否为图片
      if (!file.type.startsWith('image/')) {
        if (showNotify) {
          showNotify('请上传图片文件', 'warning')
        } else {
          alert('请上传图片文件')
        }
        return
      }
      
      // 检查文件大小 (降低到3MB限制)
      if (file.size > 3 * 1024 * 1024) {
        if (showNotify) {
          showNotify('图片大小不能超过3MB，请压缩后再上传', 'warning')
        } else {
          alert('图片大小不能超过3MB，请压缩后再上传')
        }
        return
      }
      
      // 上传图片
      await uploadImageToComment(file)
    }
  }
  
  // 上传图片到评论
  const uploadImageToComment = async (file) => {
    if (isImageUploading.value) return
    
    isImageUploading.value = true
    
    try {
      // 创建FormData
      const formData = new FormData()
      formData.append('image', file)
      
      // 获取token
      const token = getCookieValue('token') || (process.client ? localStorage.getItem('token') : null)
      
      // 调用图片上传API
      const response = await fetch(`${API_BASE_URL}/v1/image/upload`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`
        },
        body: formData
      })
      
      const result = await response.json()
      
      if (result.code === 200 && result.data && result.data.url) {
        const imageUrl = result.data.url
        
        // 在评论内容中插入图片markdown语法
        const imageMarkdown = `![图片](${imageUrl})`
        
        // 在评论输入框中插入图片markdown
        if (commentEditable.value) {
          try {
            // 检查当前选择是否在commentEditable元素内
            const selection = window.getSelection()
            const isSelectionInCommentBox = selection.rangeCount > 0 && 
              commentEditable.value.contains(selection.getRangeAt(0).commonAncestorContainer)
            
            if (isSelectionInCommentBox) {
              const range = selection.getRangeAt(0)
              
              // 创建文本节点
              const textNode = document.createTextNode(imageMarkdown)
              range.insertNode(textNode)
              
              // 将光标移动到插入文本的末尾
              range.setStartAfter(textNode)
              range.setEndAfter(textNode)
              selection.removeAllRanges()
              selection.addRange(range)
              
              // 更新评论内容
              commentContent.value = commentEditable.value.textContent || commentEditable.value.innerText
            } else {
              // 如果选区不在评论框内，或没有选区，追加到评论框末尾
              const currentText = commentEditable.value.textContent || commentEditable.value.innerText || ''
              const newText = currentText + (currentText ? '\n\n' : '') + imageMarkdown
              commentEditable.value.textContent = newText
              commentContent.value = newText
              
              // 设置光标到末尾
              const range = document.createRange()
              const selection = window.getSelection()
              range.selectNodeContents(commentEditable.value)
              range.collapse(false)
              selection.removeAllRanges()
              selection.addRange(range)
            }
            
            // 聚焦回输入框
            commentEditable.value.focus()
          } catch (error) {
            console.warn('插入图片时出错，使用备用方案:', error)
            // 备用方案：直接添加到评论框内容末尾
            const currentText = commentEditable.value.textContent || commentEditable.value.innerText || ''
            const newText = currentText + (currentText ? '\n\n' : '') + imageMarkdown
            commentEditable.value.textContent = newText
            commentContent.value = newText
          }
        } else {
          // 如果commentEditable不存在，只更新commentContent
          commentContent.value += (commentContent.value ? '\n\n' : '') + imageMarkdown
        }
        
        if (showNotify) {
          showNotify('图片上传成功', 'success')
        }
      } else {
        if (showNotify) {
          showNotify(`图片上传失败: ${result.msg || '未知错误'}`, 'error')
        } else {
          alert(`图片上传失败: ${result.msg || '未知错误'}`)
        }
      }
    } catch (error) {
      console.error('上传图片失败:', error)
      if (showNotify) {
        showNotify('上传图片失败，请稍后重试', 'error')
      } else {
        alert('上传图片失败，请稍后重试')
      }
    } finally {
      isImageUploading.value = false
    }
  }

  // 检查并处理评论高亮
  const handleCommentHighlight = () => {
    if (!process.client) return
    
    const urlParams = new URLSearchParams(window.location.search)
    const highlightId = urlParams.get('highlight')
    const highlightType = urlParams.get('highlightType')
    const jumpMessage = urlParams.get('jumpMessage')
    
    if (highlightId && (highlightType === 'comment' || highlightType === 'reply')) {
      // 延迟执行，确保DOM已渲染
      setTimeout(() => {
        let targetElement = null
        let successMessage = ''
        
        if (highlightType === 'comment') {
          targetElement = document.querySelector(`[data-comment-id="${highlightId}"]`)
          // 使用传递过来的消息，如果没有则使用默认消息
          successMessage = jumpMessage ? decodeURIComponent(jumpMessage) : '评论发布成功！'
        } else if (highlightType === 'reply') {
          targetElement = document.querySelector(`#reply-${highlightId}`)
          successMessage = jumpMessage ? decodeURIComponent(jumpMessage) : '回复发布成功！'
        }
        
        if (targetElement) {
          // 显示Toast提示 - 已禁用，使用新的通知系统
          // if (window.$toast) {
          //   const isSpecial = successMessage.includes('积分奖励')
          //   window.$toast.success(successMessage, {
          //     duration: 3000,
          //     special: isSpecial
          //   })
          // }
          
          // 滚动到评论并高亮
          targetElement.scrollIntoView({ behavior: 'smooth', block: 'center' })
          
          // 添加高亮效果
          targetElement.style.background = 'rgba(34, 197, 94, 0.1)'
          targetElement.style.border = '2px solid rgba(34, 197, 94, 0.3)'
          targetElement.style.borderRadius = '8px'
          targetElement.style.transition = 'all 0.3s ease'
          
          // 3秒后移除高亮
          setTimeout(() => {
            targetElement.style.background = ''
            targetElement.style.border = ''
            targetElement.style.borderRadius = ''
          }, 3000)
          
          // 清理URL参数
          const newUrl = new URL(window.location.href)
          newUrl.searchParams.delete('highlight')
          newUrl.searchParams.delete('highlightType')
          newUrl.searchParams.delete('jumpMessage')
          window.history.replaceState({}, '', newUrl.toString())
        }
      }, 500) // 延迟500ms确保页面渲染完成
    }
  }

  // 获取评论 - 使用分页
  const fetchComments = async () => {
    if (!postId.value) return
    
    isCommentsLoading.value = true
    
    try {
      // 获取评论列表
      const url = `${API_BASE_URL}/v3/getComments?postId=${postId.value}&pageNum=${pageFromRoute.value}`
      
      // 添加认证头
      const headers = {
        'Content-Type': 'application/json'
      };
      
      const token = getToken()
      if (token) {
        headers['Authorization'] = `Bearer ${token}`
      }
      
      const response = await fetch(url, { headers })
      const data = await response.json()
      
      if (data.code === 200 && data.data) {
        
        // 将API返回的评论数据格式化并保存到本地
        const formattedComments = data.data.comments.map(comment => ({
          ...comment,
          author: {
            id: comment.userId,
            username: comment.username,
            avatar: comment.avatar
          },
          createdAt: comment.created,
          updatedAt: comment.updated,
          // 确保数值类型正确
          likeCount: parseInt(comment.likeCount || '0'),
          dislikeCount: parseInt(comment.dislikeCount || '0'),
          replyCount: parseInt(comment.replyCount || '0'),
          // 确保点赞状态正确
          isLiked: !!comment.isLiked,
          isDisliked: !!comment.isDisliked,
          // 添加UI状态属性
          showReplies: false,
          loadingReplies: false,
          replies: []
        }))
        
        // 更新本地评论列表
        localComments.value = formattedComments
        
        // 更新分页信息
        currentPage.value = data.data.current
        totalPages.value = data.data.pages
        hasNextPage.value = data.data.hasNext
        hasPreviousPage.value = data.data.hasPrevious
        totalComments.value = data.data.total
        pageSize.value = data.data.size
        
        // 检查是否需要高亮评论
        nextTick(() => {
          handleCommentHighlight()
        })
      } else {
        localComments.value = []
      }
    } catch (err) {
      localComments.value = []
    } finally {
      isCommentsLoading.value = false
    }
  }

  // 切换页码
  const changePage = (page) => {
    if (page < 1 || page > totalPages.value) return
    
    // 更新路由，触发页面刷新
    router.push({
      query: { 
        ...route.query,
        page: page 
      }
    })
  }

  // 下一页
  const nextPage = () => {
    if (hasNextPage.value) {
      changePage(currentPage.value + 1)
    }
  }

  // 上一页
  const previousPage = () => {
    if (hasPreviousPage.value) {
      changePage(currentPage.value - 1)
    }
  }

  // 提交评论
  const submitComment = async () => {
    if (!isLoggedIn.value || !commentContent.value.trim() || isCommentSubmitting.value) return
    
    isCommentSubmitting.value = true
    
    try {
      // 智能trim处理：检查是否以@mention结尾
      let content = commentContent.value;
      if (content) {
        // 先去除前导空格
        content = content.replace(/^\s+/, '');
        
        // 检查是否以@mention格式结尾（@用户名 ）
        const endsWithMention = /@\w+\s*$/.test(content);
        
        if (endsWithMention) {
          // 如果以@mention结尾，只保留一个尾部空格
          content = content.replace(/\s+$/, ' ');
        } else {
          // 否则正常去除尾部空格
          content = content.replace(/\s+$/, '');
        }
      }
      
      // 清空输入框 - 确保在这里清空
      commentContent.value = ''
      
      // 清空可编辑div的内容
      if (commentEditable.value) {
        commentEditable.value.textContent = ''
      }
      
      // 调用API发布评论
      const result = await postStore.addComment(postId.value, content)
      
      if (result.success) {
        const { comment, commentId, page, shouldAddToCurrentPage } = result
        
        // 判断是否在当前页显示新评论
        if (shouldAddToCurrentPage && pageFromRoute.value === 1) {
          // 在当前页显示新评论，立即显示成功提示（包括积分奖励） - 已禁用，使用新通知系统
          // if (process.client && window.$toast) {
          //   let finalMessage = '评论发布成功！'
          //   if (window.pointsManager) {
          //     const pointsResult = window.pointsManager.addPoints('comment', 5, '评论奖励')
          //     if (pointsResult.added) {
          //       finalMessage = `评论发布成功！ 🎉 +${pointsResult.points}积分奖励！`
          //     }
          //   }
          //   
          //   window.$toast.success(finalMessage, {
          //     duration: 3000,
          //     special: window.pointsManager?.lastResult?.added || false
          //   })
          // }
          
          // 使用toast系统显示评论成功 - 评论每次都加5积分
          if (process.client && window.$toast) {
            window.$toast.success('评论成功，加 5 积分')
          }
          // 在当前页显示新评论（第一页且API建议可以显示）
          localComments.value.push(comment)
          
          // 更新评论总数
          totalComments.value = totalComments.value + 1
          
          // 滚动到新评论
          nextTick(() => {
            const newCommentElement = document.querySelector(`[data-comment-id="${commentId}"]`)
            if (newCommentElement) {
              newCommentElement.scrollIntoView({ behavior: 'smooth', block: 'center' })
              
              // 添加高亮效果
              newCommentElement.style.background = 'rgba(34, 197, 94, 0.1)'
              newCommentElement.style.border = '2px solid rgba(34, 197, 94, 0.3)'
              newCommentElement.style.borderRadius = '8px'
              newCommentElement.style.transition = 'all 0.3s ease'
              
              // 3秒后移除高亮
              setTimeout(() => {
                newCommentElement.style.background = ''
                newCommentElement.style.border = ''
                newCommentElement.style.borderRadius = ''
              }, 3000)
            } else {
              // 如果找不到新评论元素，滚动到评论区底部
              const commentsSection = document.getElementById('comments-section')
              if (commentsSection) {
                commentsSection.scrollIntoView({ behavior: 'smooth', block: 'end' })
              }
            }
          })
                 } else if (page && commentId && page !== pageFromRoute.value) {
           // 评论被添加到其他页面，需要跳转
           // 在跳转前奖励积分并记录消息
           let jumpMessage = '评论发布成功！'
           // 禁用pointsManager调用，避免重复toast
           // if (window.pointsManager) {
           //   const pointsResult = window.pointsManager.addPoints('comment', 5, '评论奖励')
           //   if (pointsResult.added) {
           //     jumpMessage = `评论发布成功！ 🎉 +${pointsResult.points}积分奖励！`
           //   }
           // }
           
           const targetUrl = new URL(window.location.href)
           targetUrl.searchParams.set('page', page.toString())
           targetUrl.searchParams.set('highlight', commentId.toString())
           targetUrl.searchParams.set('highlightType', 'comment') // 标记高亮类型
           targetUrl.searchParams.set('jumpMessage', encodeURIComponent(jumpMessage)) // 传递消息
           
           // 立即跳转，消息将在高亮时显示
           if (process.client && window.navigateWithPageTransition) {
             window.navigateWithPageTransition(targetUrl.toString());
           } else {
             window.location.href = targetUrl.toString();
           }
        } else {
          // 当前页面显示新评论（非第一页），沿用第一页的逻辑
          if (process.client && window.$toast) {
            window.$toast.success('评论成功，加 5 积分')
          }
          
          // 直接在当前页显示新评论，无需重新请求API
          localComments.value.push(comment)
          
          // 更新评论总数
          totalComments.value = totalComments.value + 1
          
          // 滚动到新评论并高亮
          nextTick(() => {
            const newCommentElement = document.querySelector(`[data-comment-id="${commentId}"]`)
            if (newCommentElement) {
              newCommentElement.scrollIntoView({ behavior: 'smooth', block: 'center' })
              
              // 添加高亮效果
              newCommentElement.style.background = 'rgba(34, 197, 94, 0.1)'
              newCommentElement.style.border = '2px solid rgba(34, 197, 94, 0.3)'
              newCommentElement.style.borderRadius = '8px'
              newCommentElement.style.transition = 'all 0.3s ease'
              
              // 3秒后移除高亮
              setTimeout(() => {
                newCommentElement.style.background = ''
                newCommentElement.style.border = ''
                newCommentElement.style.borderRadius = ''
              }, 3000)
            } else {
              // 如果找不到新评论元素，滚动到评论区底部
              const commentsSection = document.getElementById('comments-section')
              if (commentsSection) {
                commentsSection.scrollIntoView({ behavior: 'smooth', block: 'end' })
              }
            }
          })
        }
      }
    } catch (err) {
      alert('评论发布过程中发生错误，请稍后再试')
    } finally {
      isCommentSubmitting.value = false
    }
  }

  // 切换回复状态
  const toggleReply = (commentId) => {
    if (!isLoggedIn.value) {
      router.push('/auth/login')
      return
    }
    
    // 如果点击的是当前已激活的评论，则关闭回复框
    if (activeReplyId.value === commentId && !activeReplyToReplyId.value) {
      activeReplyId.value = null
      replyContent.value = ''
      return
    }
    
    // 先重置所有回复状态
    activeReplyToReplyId.value = null
    replyToUsername.value = ''
    replyToId.value = null
    
    // 然后设置当前评论的回复状态
    activeReplyId.value = commentId
    replyContent.value = ''
    
    // 聚焦到回复输入框
    nextTick(() => {
      const textarea = document.querySelector(`.reply-form textarea`)
      if (textarea) {
        textarea.focus()
      }
    })
  }

  // 取消回复
  const cancelReply = () => {
    // 完全重置所有回复状态
    activeReplyId.value = null
    activeReplyToReplyId.value = null
    replyToUsername.value = ''
    replyToId.value = null
    replyContent.value = ''
  }

  // 提交回复
  const submitReply = async (commentId) => {
    if (!isLoggedIn.value || !replyContent.value.trim() || isReplySubmitting.value) return
    
    isReplySubmitting.value = true
    
    try {
      // 智能trim处理：检查是否以@mention结尾
      let content = replyContent.value;
      if (content) {
        // 先去除前导空格
        content = content.replace(/^\s+/, '');
        
        // 检查是否以@mention格式结尾（@用户名 ）
        const endsWithMention = /@\w+\s*$/.test(content);
        
        if (endsWithMention) {
          // 如果以@mention结尾，只保留一个尾部空格
          content = content.replace(/\s+$/, ' ');
        } else {
          // 否则正常去除尾部空格
          content = content.replace(/\s+$/, '');
        }
      }
      
      // 确保回复二级评论时内容包含正确的前缀格式
      if (replyToId.value && !content.startsWith(`回复 ${replyToUsername.value} : `)) {
        content = `回复 ${replyToUsername.value} : ${content}`
      }
      
      // 构建回复数据
      const replyData = {
        content: content,
        parentid: commentId, // 使用parentid而不是parentId
        postId: postId.value // 确保包含postId
      }
      
      if (replyToId.value) {
        replyData.replyToId = replyToId.value
      }
      
      // 清空输入框
      replyContent.value = ''
      
      // 先保存当前textarea引用，因为在API调用后可能会被清除
      const currentTextarea = replyTextarea.value
      if (currentTextarea) {
        currentTextarea.value = ''
      }
      
      // 获取当前用户信息，用于后续查找自己发的回复
      const currentUserId = userInfo.value?.id
      const currentUsername = userInfo.value?.username
      
      // 调用 store 中的 addReply 方法
      const result = await postStore.addReply(commentId, content, replyToId.value)
      
      if (result.success) {
        // 找到目标评论
        const comment = comments.value.find(c => c.id === commentId)
        
        if (comment) {
          // 更新回复计数
          if (parseInt(comment.replyCount) >= 0) {
            comment.replyCount = (parseInt(comment.replyCount) + 1).toString()
          }
          
          // 确保回复列表会被展开（在重新加载之前设置）
          comment.showReplies = true
          
          // 重新加载回复列表以显示新回复（强制重新加载）
          await loadReplies(commentId, true)
          
          // 显示成功提示（包括积分奖励） - 已禁用，使用新通知系统
          // if (process.client && window.$toast) {
          //   let finalMessage = '回复发表成功！'
          //   if (window.pointsManager) {
          //     const pointsResult = window.pointsManager.addPoints('comment', 5, '评论奖励')
          //     if (pointsResult.added) {
          //       finalMessage = `回复发表成功！ 🎉 +${pointsResult.points}积分奖励！`
          //     }
          //   }
          //   
          //   window.$toast.success(finalMessage, {
          //     duration: 3000,
          //     special: window.pointsManager?.lastResult?.added || false
          //   })
          // }
          
          // 使用toast系统显示回复成功
          if (process.client && window.$toast) {
            window.$toast.success('回复成功')
          }
          
          // 等待DOM更新后滚动到新回复位置
          await nextTick()
          
          // 增加延时，确保回复列表完全渲染
          setTimeout(() => {
            if (process.client) {
              // 找到该评论的回复区域
              const commentElement = document.querySelector(`[data-comment-id="${commentId}"]`)
              if (commentElement) {
                // 查找回复列表（使用正确的类名）
                const replyList = commentElement.querySelector('.replies-list')
                if (replyList) {
                  
                                                        // 查找当前用户最新发布的回复
                   const currentComment = comments.value.find(c => c.id === commentId)
                   if (currentComment && currentComment.replies && currentComment.replies.length > 0) {
                     // 找到当前用户发的所有回复，按时间排序取最新的
                     const myReplies = currentComment.replies.filter(reply => 
                       reply.author.id === currentUserId || reply.author.username === currentUsername
                     )
                     
                     if (myReplies.length > 0) {
                       // 取最新的回复（通常是最后一个，因为API返回是按时间排序的）
                       const latestMyReply = myReplies[myReplies.length - 1]
                       const newReplyElement = document.querySelector(`#reply-${latestMyReply.id}`)
                       
                       if (newReplyElement) {
                         // 滚动到新回复
                         newReplyElement.scrollIntoView({ 
                           behavior: 'smooth', 
                           block: 'center' 
                         })
                         
                         // 添加高亮效果
                         newReplyElement.style.background = 'rgba(59, 130, 246, 0.15)'
                         newReplyElement.style.border = '2px solid rgba(59, 130, 246, 0.3)'
                         newReplyElement.style.borderRadius = '8px'
                         newReplyElement.style.transition = 'all 0.3s ease'
                         
                         // 3秒后移除高亮
                         setTimeout(() => {
                           newReplyElement.style.background = ''
                           newReplyElement.style.border = ''
                           newReplyElement.style.borderRadius = ''
                         }, 3000)
                       } else {
                         // 备用方案：滚动到回复列表底部
                         replyList.scrollIntoView({ 
                           behavior: 'smooth', 
                           block: 'end' 
                         })
                       }
                     } else {
                       replyList.scrollIntoView({ 
                         behavior: 'smooth', 
                         block: 'end' 
                       })
                     }
                   } else {
                     // 最终备用方案：滚动到回复列表底部
                     replyList.scrollIntoView({ 
                       behavior: 'smooth', 
                       block: 'end' 
                     })
                   }
                } else {
                  // 如果找不到回复列表，滚动到评论区域
                  commentElement.scrollIntoView({ 
                    behavior: 'smooth', 
                    block: 'center' 
                  })
                }
              }
            }
          }, 800) // 增加延时到800ms，确保DOM完全更新
        }
        
        // 重置表单
        cancelReply()
      }
    } catch (err) {
      // 静默处理错误
    } finally {
      isReplySubmitting.value = false
    }
  }

  // 切换二级回复状态
  const toggleReplyToReply = (commentId, replyId, username) => {
    if (!isLoggedIn.value) {
      router.push('/auth/login')
      return
    }
    
    // 如果已经在回复相同的评论，则取消回复状态
    if (activeReplyToReplyId.value === replyId) {
      activeReplyToReplyId.value = null
      replyToId.value = null
      replyToUsername.value = ''
      replyContent.value = ''
      return
    }
    
    // 完全重置所有回复状态
    activeReplyId.value = null
    activeReplyToReplyId.value = null
    replyToUsername.value = ''
    replyToId.value = null
    replyContent.value = ''
    
    // 设置二级回复状态 - 只设置二级回复相关的状态
    activeReplyToReplyId.value = replyId  // 设置回复ID
    replyToUsername.value = username
    replyToId.value = replyId
    replyContent.value = ''  // 清空内容，不预填充
    
    // 聚焦到回复输入框 - 使用nextTick确保DOM已更新
    nextTick(() => {
      // 查找当前二级回复输入框
      const replyInput = document.querySelector('.reply-to-reply-form .reply-input')
      if (replyInput) {
        replyInput.focus()
      }
    })
  }

  // 取消回复二级评论
  const cancelReplyToReply = () => {
    // 完全重置所有回复状态，包括一级评论的激活ID
    activeReplyId.value = null
    activeReplyToReplyId.value = null
    replyToUsername.value = ''
    replyToId.value = null
    replyContent.value = ''
  }

  // 点赞评论
  const handleLikeComment = async (commentId) => {
    if (!isLoggedIn.value) {
      router.push('/auth/login')
      return
    }
    
    try {
      // 查找评论（包括一级评论和二级评论）
      let comment = null
      let parentComment = null
      
      // 先在一级评论中查找
      comment = localComments.value.find(c => c.id === commentId)
      
      // 如果没找到，在二级评论中查找
      if (!comment) {
        for (const c of localComments.value) {
          if (c.replies && c.replies.length > 0) {
            const reply = c.replies.find(r => r.id === commentId)
            if (reply) {
              comment = reply
              parentComment = c
              break
            }
          }
        }
      }
      
      if (!comment) {
        console.error('未找到评论，ID:', commentId)
        return
      }
      
      // 保存原始状态，用于在API失败时恢复
      const wasLiked = comment.isLiked
      const wasDisliked = comment.isDisliked
      const originalLikeCount = comment.likeCount || 0
      const originalDislikeCount = comment.dislikeCount || 0
      
      // 获取token
      const token = localStorage.getItem('token') || getCookieValue('Authorization')
      if (!token) {
        if (showNotify) {
          showNotify('登录状态失效，请重新登录', 'error')
        }
        return
      }
      
      const headers = {
        'Authorization': `Bearer ${token}`
      }
      
      let success = false
      
      if (wasLiked) {
        // 当前已点赞，执行取消点赞
        // 先更新UI
        comment.isLiked = false
        comment.likeCount = Math.max(0, originalLikeCount - 1)
        
        // 乐观显示成功提示 - 已禁用，使用新通知系统
        // if (process.client && window.$toast) {
        //   window.$toast.success('已取消点赞')
        // }
        
        try {
          const response = await fetch(`${API_BASE_URL}/v4/comment/cancelLike/${commentId}`, { headers })
          const data = await response.json()
          
          if (data.code === 200) {
            success = true
          } else {
            throw new Error(data.msg || '取消点赞失败')
          }
        } catch (error) {
          // 恢复UI状态
          comment.isLiked = wasLiked
          comment.likeCount = originalLikeCount
          if (showNotify) {
            showNotify('取消点赞失败', 'error')
          }
        }
      } else {
        // 当前未点赞，执行点赞
        // 先更新UI
        comment.isLiked = true
        comment.likeCount = originalLikeCount + 1
        
        // 如果之前点过踩，同时取消点踩
        if (wasDisliked) {
          comment.isDisliked = false
          comment.dislikeCount = Math.max(0, originalDislikeCount - 1)
        }
        
        // 显示合并的点赞提示和积分奖励 - 已禁用，使用新通知系统
        // if (process.client && window.$toast) {
        //   let finalMessage = '点赞成功'
        //   let isSpecial = false
        //   
        //   if (window.pointsManager) {
        //     const pointsResult = window.pointsManager.addPoints('like', 1, '点赞奖励')
        //     if (pointsResult.added) {
        //       finalMessage = `点赞成功！ 🎉 +${pointsResult.points}积分奖励！`
        //       isSpecial = true
        //     }
        //   }
        //   
        //   window.$toast.success(finalMessage, {
        //     duration: isSpecial ? 3000 : 2000,
        //     special: isSpecial
        //   })
        // }
        
        try {
          // 如果之前点过踩，先取消点踩
          if (wasDisliked) {
            const cancelDislikeResponse = await fetch(`${API_BASE_URL}/v4/comment/cancelDislike/${commentId}`, { headers })
            const cancelDislikeData = await cancelDislikeResponse.json()
          }
          
          // 执行点赞
          const response = await fetch(`${API_BASE_URL}/v4/comment/like/${commentId}`, { headers })
          const data = await response.json()
          
          if (data.code === 200) {
            success = true
            // 不再显示提示，因为已经在UI更新时显示过了
          } else if (data.code === 3) {
            // 已经点过赞了，自动执行取消点赞
            const cancelResponse = await fetch(`${API_BASE_URL}/v4/comment/cancelLike/${commentId}`, { headers })
            const cancelData = await cancelResponse.json()
            
            if (cancelData.code === 200) {
              // 更新UI为取消点赞状态
              comment.isLiked = false
              comment.likeCount = Math.max(0, originalLikeCount - 1)
              success = true
              // 乐观显示提示（在UI更新时已显示）
            } else {
              throw new Error(cancelData.msg || '取消点赞失败')
            }
          } else {
            throw new Error(data.msg || '点赞失败')
          }
        } catch (error) {
          // 恢复UI状态
          comment.isLiked = wasLiked
          comment.isDisliked = wasDisliked
          comment.likeCount = originalLikeCount
          comment.dislikeCount = originalDislikeCount
          if (showNotify) {
            showNotify('点赞失败', 'error')
          }
        }
      }
    } catch (err) {
      if (showNotify) {
        showNotify('操作失败，请稍后重试', 'error')
      }
    }
  }
    
  // 点踩评论
  const handleDislikeComment = async (commentId) => {
    if (!isLoggedIn.value) {
      router.push('/auth/login')
      return
    }
    
    try {
      // 查找评论（包括一级评论和二级评论）
      let comment = null
      let parentComment = null
      
      // 先在一级评论中查找
      comment = localComments.value.find(c => c.id === commentId)
      
      // 如果没找到，在二级评论中查找
      if (!comment) {
        for (const c of localComments.value) {
          if (c.replies && c.replies.length > 0) {
            const reply = c.replies.find(r => r.id === commentId)
            if (reply) {
              comment = reply
              parentComment = c
              break
            }
          }
        }
      }
      
      if (!comment) {
        console.error('未找到评论，ID:', commentId)
        return
      }
      
      // 保存原始状态，用于在API失败时恢复
      const wasLiked = comment.isLiked
      const wasDisliked = comment.isDisliked
      const originalLikeCount = comment.likeCount || 0
      const originalDislikeCount = comment.dislikeCount || 0
      
      // 获取token
      const token = localStorage.getItem('token') || getCookieValue('Authorization')
      if (!token) {
        if (showNotify) {
          showNotify('登录状态失效，请重新登录', 'error')
        }
        return
      }
      
      const headers = {
        'Authorization': `Bearer ${token}`
      }
      
      let success = false
      
      if (wasDisliked) {
        // 当前已点踩，执行取消点踩
        // 先更新UI
        comment.isDisliked = false
        comment.dislikeCount = Math.max(0, originalDislikeCount - 1)
        
        // 乐观显示成功提示 - 已禁用，使用新通知系统
        // if (process.client && window.$toast) {
        //   window.$toast.success('已取消点踩')
        // }
        
        try {
          const response = await fetch(`${API_BASE_URL}/v4/comment/cancelDislike/${commentId}`, { headers })
          const data = await response.json()
          
          if (data.code === 200) {
            success = true
          } else {
            throw new Error(data.msg || '取消点踩失败')
          }
        } catch (error) {
          // 恢复UI状态
          comment.isDisliked = wasDisliked
          comment.dislikeCount = originalDislikeCount
          if (showNotify) {
            showNotify('取消点踩失败', 'error')
          }
        }
      } else {
        // 当前未点踩，执行点踩
        // 先更新UI
        comment.isDisliked = true
        comment.dislikeCount = originalDislikeCount + 1
        
        // 如果之前点过赞，同时取消点赞
        if (wasLiked) {
          comment.isLiked = false
          comment.likeCount = Math.max(0, originalLikeCount - 1)
        }
        
        // 乐观显示点踩成功提示 - 已禁用，使用新通知系统
        // if (process.client && window.$toast) {
        //   window.$toast.info('点踩成功')
        // }
        
        try {
          // 如果之前点过赞，先取消点赞
          if (wasLiked) {
            const cancelLikeResponse = await fetch(`${API_BASE_URL}/v4/comment/cancelLike/${commentId}`, { headers })
            const cancelLikeData = await cancelLikeResponse.json()
          }
          
          // 执行点踩
          const response = await fetch(`${API_BASE_URL}/v4/comment/dislike/${commentId}`, { headers })
          const data = await response.json()
          
          if (data.code === 200) {
            success = true
            if (showNotify) {
              showNotify('点踩成功', 'success')
            }
          } else if (data.code === 5) {
            // 已经点过踩了，自动执行取消点踩
            const cancelResponse = await fetch(`${API_BASE_URL}/v4/comment/cancelDislike/${commentId}`, { headers })
            const cancelData = await cancelResponse.json()
            
            if (cancelData.code === 200) {
              // 更新UI为取消点踩状态
              comment.isDisliked = false
              comment.dislikeCount = Math.max(0, originalDislikeCount - 1)
              success = true
              // 乐观显示提示（在UI更新时已显示）
            } else {
              throw new Error(cancelData.msg || '取消点踩失败')
            }
          } else {
            throw new Error(data.msg || '点踩失败')
          }
        } catch (error) {
          // 恢复UI状态
          comment.isLiked = wasLiked
          comment.isDisliked = wasDisliked
          comment.likeCount = originalLikeCount
          comment.dislikeCount = originalDislikeCount
          if (showNotify) {
            showNotify('点踩失败', 'error')
          }
        }
      }
    } catch (err) {
      if (showNotify) {
        showNotify('操作失败，请稍后重试', 'error')
      }
    }
  }

  // 加载二级评论
  const loadReplies = async (commentId, forceReload = false) => {
    // 找到评论
    const comment = comments.value.find(c => c.id === commentId)
    if (!comment) {
      return
    }
    
    // 如果已经显示回复且不是强制重新加载，则折叠回复
    if (comment.showReplies && !comment.loadingReplies && !forceReload) {
      comment.showReplies = false
      return
    }
    
    // 如果有缓存的回复数据且不是强制重新加载，直接显示缓存数据
    if (comment.replies && comment.replies.length > 0 && !forceReload) {
      comment.showReplies = true
      return
    }
    
    // 标记为加载中
    comment.loadingReplies = true
    
    try {
      // 调用API获取回复
      const url = `${API_BASE_URL}/v3/comment/${commentId}/replies`
      
      // 添加认证头
      const headers = {
        'Content-Type': 'application/json'
      };
      
      const token = getToken()
      if (token) {
        headers['Authorization'] = `Bearer ${token}`
      }
      
      const response = await fetch(url, { headers })
      const data = await response.json()
      
      if (data.code === 200 && data.data) {
        // 格式化回复数据
        const formattedReplies = data.data.map(reply => ({
          ...reply,
          author: {
            id: reply.userId,
            username: reply.username,
            avatar: reply.avatar
          },
          createdAt: reply.created,
          updatedAt: reply.updated,
          // 确保数值类型正确
          likeCount: parseInt(reply.likeCount || '0'),
          dislikeCount: parseInt(reply.dislikeCount || '0'),
          // 确保点赞状态正确
          isLiked: !!reply.isLiked,
          isDisliked: !!reply.isDisliked
        }))
        
        // 更新评论的回复列表
        comment.replies = formattedReplies
        comment.showReplies = true
      } else {
        comment.replies = []
      }
    } catch (err) {
      comment.replies = []
    } finally {
      comment.loadingReplies = false
    }
  }

  // 高亮@用户名和回复内容 - 支持一级评论的markdown解析
  const highlightMentions = (text, isReply = false) => {
    if (!text) return ''
    
    let processedText = text

    // 对于一级评论（非回复），支持markdown解析
    if (!isReply) {
      try {
        // 使用自定义markdown-it配置，禁用锚点，保留@提及
        const { md } = useMarkdownIt({
          disableAnchor: true,
          typographer: false // 禁用印刷格式，避免干扰@提及
        })
        
        // 先清理Vue模板相关的注释（这些是拖拽组件产生的）
        processedText = processedText.replace(/<!--[\s\S]*?-->/g, '')
        
        // 自定义@用户名提及处理
        const defaultRender = md.renderer.rules.text || function(tokens, idx) {
          return tokens[idx].content
        }
        
        md.renderer.rules.text = function(tokens, idx) {
          let content = tokens[idx].content
          // 处理@用户名提及
          content = content.replace(
            /(^|[^a-zA-Z0-9_\u4e00-\u9fa5])@([a-zA-Z0-9_\u4e00-\u9fa5]+)(?=[^a-zA-Z0-9_\u4e00-\u9fa5]|$)/g, 
            '$1<span class="mention-tag">@$2</span>'
          )
          return content
        }
        
        // 使用markdown-it解析
        return md.render(processedText)
      } catch (error) {
        console.error('Markdown解析错误:', error)
        // 降级为原有的纯文本处理
      }
    }
    
    // 对于二级评论或markdown解析失败的情况，使用原有的纯文本处理逻辑
    
    // 首先清理掉所有HTML标签，只保留纯文本内容
    let cleanText = processedText
    
    // 移除所有HTML标签，包括图片标签、script标签等
    cleanText = cleanText.replace(/<[^>]*>/g, '')
    
    // 移除markdown图片语法
    cleanText = cleanText.replace(/!\[([^\]]*)\]\([^)]+\)/g, '$1')
    
    // 移除HTML实体
    cleanText = cleanText.replace(/&[a-zA-Z]+;/g, ' ')
    
    // 移除多余的换行符和制表符
    cleanText = cleanText.replace(/[\r\n\t]/g, ' ')
    
    // 清理多余的空白符，将多个连续空格合并为一个
    cleanText = cleanText.replace(/\s+/g, ' ').trim()
    
    // 如果清理后内容为空，返回空字符串
    if (!cleanText) return ''
    
    // 限制文本长度，防止过长内容
    if (cleanText.length > 500) {
      cleanText = cleanText.substring(0, 500) + '...'
    }
    
    // 1. 只在二级评论中高亮"回复 用户名 :"格式，但后面必须有内容
    if (isReply) {
      cleanText = cleanText.replace(
        /^回复\s+([a-zA-Z0-9_\u4e00-\u9fa5]+)\s*:\s*(.+)/g, 
        '<span class="reply-mention">回复 <span class="mention-tag">$1</span> :</span> $2'
      )
    }
    
    // 2. 高亮独立的@用户名，使用前瞻断言确保@用户名的完整性
    cleanText = cleanText.replace(
      /(^|[^a-zA-Z0-9_\u4e00-\u9fa5])@([a-zA-Z0-9_\u4e00-\u9fa5]+)(?=[^a-zA-Z0-9_\u4e00-\u9fa5]|$)/g, 
      '$1<span class="mention-tag">@$2</span>'
    )
    
    return cleanText
  }

  // 监听路由变化，获取评论
  watch(() => route.query.page, (newPage) => {
    fetchComments()
  })

  return {
    comments,
    localComments,
    commentContent,
    isCommentSubmitting,
    isCommentsLoading,
    activeReplyId,
    replyContent,
    isReplySubmitting,
    activeReplyToReplyId,
    replyToUsername,
    replyToId,
    replyTextarea,
    commentEditable,
    isLoggedIn,
    userInfo,
    currentPage,
    totalPages,
    hasNextPage,
    hasPreviousPage,
    totalComments,
    pageSize,
    isFirstPage,
    showPostDetails,
    fetchComments,
    changePage,
    nextPage,
    previousPage,
    submitComment,
    toggleReply,
    cancelReply,
    submitReply,
    toggleReplyToReply,
    cancelReplyToReply,
    handleLikeComment,
    handleDislikeComment,
    loadReplies,
    highlightMentions,
    handleCommentHighlight,
    isDragOver,
    isImageUploading,
    handleDragOver,
    handleDragEnter,
    handleDragLeave,
    handleDrop,
    uploadImageToComment
  }
} 