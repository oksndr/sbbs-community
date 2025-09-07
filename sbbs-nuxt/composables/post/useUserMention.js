import { ref, watch, nextTick } from 'vue'
import { API } from '~/utils/api'

/**
 * 用户提及和搜索相关的组合式函数
 * @param {Ref} commentContent - 评论内容引用
 * @param {Ref} commentEditable - 评论输入框的引用
 */
export function useUserMention(commentContent, commentEditable) {
  // @功能相关变量
  const showUserSearch = ref(false)
  const searchUsers = ref([])
  const userSearchIndex = ref(0)
  const isSearchingUsers = ref(false)
  const searchTimer = ref(null)
  const atPosition = ref(-1)
  const lastCaretPosition = ref(0)
  
  // 添加标志位防止循环处理
  const isSelectingUser = ref(false)

  // 获取token的辅助函数
  const getToken = () => {
    if (process.client) {
      return localStorage.getItem('token') || '';
    }
    return '';
  }

  // 处理contenteditable输入
  const handleContentEditableInput = (e) => {
    // 如果正在选择用户，跳过处理避免冲突
    if (isSelectingUser.value) {
      console.log('🎯 跳过输入处理，正在选择用户')
      return
    }
    
    const text = e.target.textContent || ''
    
    // 如果文本为空，直接隐藏搜索框
    if (!text || text.trim() === '') {
      console.log('📝 输入框为空，隐藏搜索框')
      showUserSearch.value = false
      searchUsers.value = []
      atPosition.value = -1
      if (searchTimer.value) {
        clearTimeout(searchTimer.value)
        searchTimer.value = null
      }
      return
    }
    
    // 简化方法：使用正则直接匹配文本末尾的@提及
    // 这样避免了复杂的光标位置计算
    const endMentionMatch = text.match(/@([a-zA-Z0-9_\u4e00-\u9fa5]*)$/)
    
    console.log('输入检测:', { 
      text: text.substring(Math.max(0, text.length - 30)), // 只显示最后30个字符
      endMentionMatch: endMentionMatch ? endMentionMatch[0] : null,
      shouldTrigger: endMentionMatch !== null
    })
    
    if (endMentionMatch) {
      const searchQuery = endMentionMatch[1] || '' // 获取@后面的内容
      const mentionStart = text.lastIndexOf(endMentionMatch[0])
      
      console.log('准备搜索用户:', searchQuery)
      
      atPosition.value = mentionStart
      
      // 清除之前的定时器
      if (searchTimer.value) {
        clearTimeout(searchTimer.value)
      }
      
      // 如果是刚输入@符号，立即显示搜索框
      if (searchQuery === '') {
        showUserSearch.value = true
        searchUsers.value = []
        isSearchingUsers.value = true
        searchForUsers('')
        return
      }
      
      // 设置新的定时器，延迟搜索
      searchTimer.value = setTimeout(() => {
        searchForUsers(searchQuery)
      }, 300)
    } else {
      console.log('🔍 文本末尾没有@提及，隐藏搜索框')
      showUserSearch.value = false
      searchUsers.value = []
      atPosition.value = -1
    }
  }

  // 定位搜索下拉框 - 智能定位版本
  const positionSearchDropdown = () => {
    if (!showUserSearch.value) return
    
    // 延迟执行，确保搜索框已渲染
    setTimeout(() => {
      try {
        // 找到当前活动的输入框
        const activeElement = document.activeElement
        if (!activeElement || activeElement.contentEditable !== 'true') {
          console.log('🔍 没有找到活动的可编辑元素')
          return
        }
        
        console.log('🔍 活动元素类名:', activeElement.className)
        
        // 确定搜索框的选择器
        let dropdownSelector = '.user-search-dropdown'
        if (activeElement.closest('.reply-to-reply-form')) {
          dropdownSelector = '.reply-to-reply-form .user-search-dropdown'
        } else if (activeElement.closest('.reply-form')) {
          dropdownSelector = '.reply-form .user-search-dropdown'
        } else if (activeElement.closest('.comment-form')) {
          dropdownSelector = '.comment-form .user-search-dropdown'
        }
        
        console.log('🔍 使用选择器:', dropdownSelector)
        
        // 查找对应的搜索框
        const dropdown = document.querySelector(dropdownSelector)
        if (!dropdown || getComputedStyle(dropdown).display === 'none') {
          console.log('🔍 未找到可见的搜索框:', dropdownSelector)
          return
        }
        
        // 获取位置信息
        const inputRect = activeElement.getBoundingClientRect()
        const viewportHeight = window.innerHeight
        
        // 计算下拉框的理想尺寸
        const dropdownWidth = Math.max(200, Math.min(320, inputRect.width))
        const dropdownHeight = 200 // 最大高度
        
        // 检查下方空间是否足够
        const spaceBelow = viewportHeight - inputRect.bottom
        const showBelow = spaceBelow >= (dropdownHeight + 28) // 24px偏移 + 4px边距
        
        // 移除现有的方向类名
        dropdown.classList.remove('dropdown-top', 'dropdown-bottom')
        
        // 设置水平位置（确保不超出视口边界）
        const left = Math.max(10, Math.min(
          inputRect.left,
          window.innerWidth - dropdownWidth - 10
        ))
        
        if (showBelow) {
          // 向下显示
          dropdown.classList.add('dropdown-bottom')
          dropdown.style.top = `${inputRect.bottom}px`
        } else {
          // 向上显示
          dropdown.classList.add('dropdown-top')
          dropdown.style.top = `${inputRect.top}px`
        }
        
        // 应用位置和尺寸
        dropdown.style.left = `${left}px`
        dropdown.style.width = `${dropdownWidth}px`
        
        // 确保搜索框可见
        dropdown.classList.add('visible')
        
        console.log('🔍 搜索框已定位:', {
          inputRect,
          viewportHeight,
          spaceBelow,
          showBelow,
          dropdownWidth,
          left,
          dropdown
        })
      } catch (error) {
        console.error('🔍 定位搜索框失败:', error)
      }
    }, 10) // 短延迟确保DOM已更新
  }

  // 搜索用户
  const searchForUsers = async (query) => {
    isSearchingUsers.value = true
    console.log('🔍 开始搜索用户:', query)
    
    try {
      // 获取token
      const token = getToken()
      console.log('🔍 Token状态:', token ? '有token' : '无token')
      
      // 使用API服务搜索用户
      const data = await API.user.search(query)
      console.log('🔍 搜索用户API响应:', data)
      
      if (data.code === 200 && Array.isArray(data.data)) {
        searchUsers.value = data.data.slice(0, 5) // 最多显示5个结果
        userSearchIndex.value = 0
        showUserSearch.value = searchUsers.value.length > 0
        console.log('🔍 找到用户数量:', searchUsers.value.length, '用户列表:', searchUsers.value)
        console.log('🔍 showUserSearch状态:', showUserSearch.value)
        
        // 强制触发DOM更新并设置位置
        await nextTick()
        positionSearchDropdown()
      } else {
        searchUsers.value = []
        showUserSearch.value = false
        console.log('🔍 未找到用户，响应代码:', data.code, '消息:', data.msg)
      }
    } catch (error) {
      console.error('❌ 搜索用户失败:', error)
      searchUsers.value = []
      showUserSearch.value = false
    } finally {
      isSearchingUsers.value = false
    }
  }

  // 选择用户
  const selectUser = (user) => {
    if (!commentEditable.value) {
      console.error('❌ commentEditable不存在')
      return
    }
    
    // 设置标志位防止循环处理
    isSelectingUser.value = true
    
    console.log('🎯 选择用户开始:', {
      user: user.username,
      atPosition: atPosition.value,
      lastCaretPosition: lastCaretPosition.value
    })
    
    const text = commentEditable.value.textContent || ''
    
    // 重新查找最新的@提及位置（文本末尾的@提及）
    const endMentionMatch = text.match(/@([a-zA-Z0-9_\u4e00-\u9fa5]*)$/)
    let actualAtPosition = atPosition.value
    
    if (endMentionMatch) {
      // 如果文本末尾有@提及，使用最新的位置
      actualAtPosition = text.lastIndexOf(endMentionMatch[0])
      console.log('🎯 发现文本末尾的@提及，使用最新位置:', actualAtPosition)
    }
    
    // 找到@符号后的查询文本结束位置
    const atStart = actualAtPosition + 1 // @符号后的第一个字符
    let searchEndPos = atStart
    
    // 查找@符号后没有空格的文本结束位置
    for (let i = atStart; i < text.length; i++) {
      if (/\s/.test(text[i])) {
        break
      }
      searchEndPos = i + 1
    }
    
    const beforeAt = text.substring(0, actualAtPosition)
    const afterAt = text.substring(searchEndPos)
    
    console.log('🎯 文本分析:', {
      originalText: text,
      beforeAt: beforeAt,
      afterAt: afterAt,
      originalAtPosition: atPosition.value,
      actualAtPosition: actualAtPosition,
      atStart: atStart,
      searchEndPos: searchEndPos
    })
    
    // 构建新文本：beforeAt + @用户名 + 空格 + afterAt
    const newText = beforeAt + '@' + user.username + ' ' + afterAt
    console.log('🎯 构建新文本:', newText)
    
    // 计算新光标位置（在@用户名和空格之后）
    const newCaretPosition = beforeAt.length + 1 + user.username.length + 1
    console.log('🎯 计算新光标位置:', newCaretPosition)
    
    // 立即清理搜索状态，防止后续处理
    showUserSearch.value = false
    searchUsers.value = []
    atPosition.value = -1
    userSearchIndex.value = 0
    
    // 更新contenteditable内容
    commentEditable.value.textContent = newText
    
    // 更新响应式变量
    commentContent.value = newText
    
    // 使用nextTick确保DOM更新后设置光标
    nextTick(() => {
      try {
        // 确保元素仍然存在且有焦点
        if (!commentEditable.value) {
          console.error('❌ nextTick后commentEditable不存在')
          return
        }
        
        // 先获取焦点
        commentEditable.value.focus()
        
        // 获取selection和range
        const selection = window.getSelection()
        if (!selection) {
          console.error('❌ 无法获取selection')
          return
        }
        
        // 清除现有选区
        selection.removeAllRanges()
        
        // 创建新的range
        const range = document.createRange()
        
        // 获取文本节点
        let textNode = commentEditable.value.firstChild
        
        // 如果没有文本节点或不是文本节点，创建一个
        if (!textNode || textNode.nodeType !== Node.TEXT_NODE) {
          textNode = document.createTextNode(newText)
          commentEditable.value.innerHTML = ''
          commentEditable.value.appendChild(textNode)
        }
        
        // 确保光标位置不超过文本长度
        const actualTextLength = textNode.textContent.length
        const finalCaretPosition = Math.min(newCaretPosition, actualTextLength)
        
        console.log('🎯 最终光标设置:', {
          actualTextLength,
          newCaretPosition,
          finalCaretPosition,
          textNodeContent: textNode.textContent
        })
        
        // 设置range位置
        range.setStart(textNode, finalCaretPosition)
        range.setEnd(textNode, finalCaretPosition)
        
        // 应用选区
        selection.addRange(range)
        
        console.log('🎯 光标设置成功')
        
      } catch (error) {
        console.error('❌ 设置光标失败:', error)
        // 备用方案：只确保获得焦点
        try {
          commentEditable.value.focus()
        } catch (focusError) {
          console.error('❌ 设置焦点也失败:', focusError)
        }
      }
    })
    
    console.log('🎯 用户选择完成')
    
    // 延迟清除标志位，确保处理完成
    setTimeout(() => {
      isSelectingUser.value = false
      console.log('🎯 清除选择用户标志位')
    }, 200)
  }

  // 处理键盘事件
  const handleCommentKeydown = (e) => {
    console.log('🎯 键盘事件:', e.key)
    
    // 如果按下删除键或退格键，需要检查删除后的内容
    if (e.key === 'Backspace' || e.key === 'Delete') {
      // 延迟检查，等待删除操作完成
      setTimeout(() => {
        if (!commentEditable.value) return
        
        const text = commentEditable.value.textContent || ''
        
        // 如果删除后内容为空，隐藏搜索框
        if (!text || text.trim() === '') {
          console.log('🗑️ 删除后内容为空，隐藏搜索框')
          showUserSearch.value = false
          searchUsers.value = []
          atPosition.value = -1
          if (searchTimer.value) {
            clearTimeout(searchTimer.value)
            searchTimer.value = null
          }
        }
      }, 10)
    }
    
    // 如果按下@键，立即显示搜索框
    if (e.key === '@') {
      console.log('🎯 检测到@键被按下')
      
      // 延迟执行，等待@字符实际输入到文本中
      setTimeout(() => {
        if (!commentEditable.value) {
          console.log('❌ commentEditable为空')
          return
        }
        
        const selection = window.getSelection()
        if (!selection.rangeCount) {
          console.log('❌ 没有选区')
          return
        }
        
        const range = selection.getRangeAt(0)
        const caretPos = range.startOffset
        lastCaretPosition.value = caretPos
        
        const text = commentEditable.value.textContent
        const lastAtPos = text.lastIndexOf('@', caretPos)
        
        console.log('🎯 @键处理:', {
          text,
          caretPos,
          lastAtPos,
          找到At: lastAtPos !== -1 && lastAtPos < caretPos
        })
        
        if (lastAtPos !== -1 && lastAtPos < caretPos) {
          atPosition.value = lastAtPos
          showUserSearch.value = true
          searchUsers.value = []
          isSearchingUsers.value = true
          
          console.log('🎯 立即触发空查询搜索')
          // 立即触发搜索，获取初始用户列表
          searchForUsers('')
          
          // 延迟一点定位，等待搜索框显示
          setTimeout(() => {
            positionSearchDropdown()
            
            // 通知外部确保搜索框显示 - 添加自定义事件
            if (typeof window !== 'undefined') {
              const event = new CustomEvent('userSearchActivated', { detail: { query: '' } })
              window.dispatchEvent(event)
            }
          }, 50)
        }
      }, 10)
    }
    
    if (showUserSearch.value) {
      console.log('🎯 用户搜索框已显示，处理导航键')
      
      if (e.key === 'ArrowDown') {
        e.preventDefault()
        userSearchIndex.value = (userSearchIndex.value + 1) % searchUsers.value.length
        console.log('🎯 向下选择，当前索引:', userSearchIndex.value)
      } else if (e.key === 'ArrowUp') {
        e.preventDefault()
        userSearchIndex.value = (userSearchIndex.value - 1 + searchUsers.value.length) % searchUsers.value.length
        console.log('🎯 向上选择，当前索引:', userSearchIndex.value)
      } else if (e.key === 'Enter' || e.key === 'Tab') {
        e.preventDefault()
        if (searchUsers.value.length > 0) {
          console.log('🎯 Enter/Tab键选择用户:', {
            selectedUser: searchUsers.value[userSearchIndex.value],
            userIndex: userSearchIndex.value,
            totalUsers: searchUsers.value.length
          })
          selectUser(searchUsers.value[userSearchIndex.value])
        } else {
          console.log('❌ Enter/Tab键但没有可选用户')
        }
      } else if (e.key === 'Escape') {
        e.preventDefault()
        console.log('🎯 ESC键关闭搜索')
        showUserSearch.value = false
        searchUsers.value = []
        atPosition.value = -1
      }
    }
  }

  // 处理退格键
  const handleBackspace = () => {
    // 如果正在显示用户搜索，不做特殊处理
    if (showUserSearch.value) return
    
    // 获取当前光标位置
    const selection = window.getSelection()
    if (!selection.rangeCount) return
    
    const range = selection.getRangeAt(0)
    const caretPos = range.startOffset
    
    // 检查是否在删除@用户名
    const text = commentEditable.value.textContent
    const beforeCaret = text.substring(0, caretPos)
    
    // 如果光标前是一个完整的@用户名标记，则整体删除
    const mentionMatch = beforeCaret.match(/@([a-zA-Z0-9_\u4e00-\u9fa5]+)$/g)
    if (mentionMatch) {
      const mentionText = mentionMatch[0]
      const startPos = caretPos - mentionText.length
      
      // 创建一个新的范围来删除整个@用户名
      const deleteRange = document.createRange()
      deleteRange.setStart(commentEditable.value.firstChild, startPos)
      deleteRange.setEnd(commentEditable.value.firstChild, caretPos)
      deleteRange.deleteContents()
      
      // 防止默认的退格键行为
      event.preventDefault()
    }
  }

  // 处理回复输入框的退格键
  const handleReplyBackspace = (e, replyTextarea, replyContent, replyToUsername) => {
    // 检查是否在删除@用户名
    const text = replyContent.value
    const caretPos = replyTextarea.selectionStart
    
    // 如果光标前是一个完整的@用户名标记，则整体删除
    const beforeCaret = text.substring(0, caretPos)
    const mentionMatch = beforeCaret.match(/@([a-zA-Z0-9_\u4e00-\u9fa5]+)$/g)
    
    // 检查是否位于回复前缀的位置
    const replyPrefix = '回复 '
    const colonPos = beforeCaret.lastIndexOf(' : ')
    
    if (caretPos > 0 && caretPos <= replyPrefix.length + replyToUsername.length + 3 && 
        text.startsWith(replyPrefix)) {
      // 如果尝试删除回复前缀，阻止删除
      e.preventDefault()
      return
    }
    
    if (mentionMatch) {
      const mentionText = mentionMatch[0]
      const startPos = caretPos - mentionText.length
      
      // 删除整个@用户名
      replyContent.value = text.substring(0, startPos) + text.substring(caretPos)
      
      // 设置新的光标位置
      nextTick(() => {
        replyTextarea.selectionStart = startPos
        replyTextarea.selectionEnd = startPos
      })
      
      // 防止默认的退格键行为
      e.preventDefault()
    }
  }

  return {
    showUserSearch,
    searchUsers,
    userSearchIndex,
    isSearchingUsers,
    handleContentEditableInput,
    handleCommentKeydown,
    handleBackspace,
    handleReplyBackspace,
    selectUser
  }
} 