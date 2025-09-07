import { ref, watch, nextTick } from 'vue'
import { API } from '~/utils/api'

/**
 * 二级评论的用户提及和搜索相关的组合式函数
 * @param {Ref} replyContent - 回复内容引用
 * @param {Ref} replyTextarea - 回复输入框的引用
 */
export function useReplyMention(replyContent, replyTextarea) {
  console.log('🔥 useReplyMention 初始化', { replyContent, replyTextarea })
  
  // @功能相关变量
  const showReplyUserSearch = ref(false)
  const replySearchUsers = ref([])
  const replyUserSearchIndex = ref(0)
  const isSearchingReplyUsers = ref(false)
  const replySearchTimer = ref(null)
  const replyAtPosition = ref(-1)
  const replyLastCaretPosition = ref(0)
  
  // 添加标志位防止循环处理
  const isSelectingReplyUser = ref(false)

  // 获取token的辅助函数
  const getToken = () => {
    if (process.client) {
      return localStorage.getItem('token') || '';
    }
    return '';
  }

  // 处理二级评论contenteditable输入
  const handleReplyContentEditableInput = (e) => {
    console.log('🔥 handleReplyContentEditableInput 被调用', e)
    
    // 如果正在选择用户，跳过处理避免冲突
    if (isSelectingReplyUser.value) {
      console.log('🎯 跳过回复输入处理，正在选择用户')
      return
    }
    
    // 获取输入内容
    replyContent.value = e.target.innerHTML
    
    const text = e.target.textContent || ''
    
    // 如果文本为空，直接隐藏搜索框
    if (!text || text.trim() === '') {
      console.log('📝 回复输入框为空，隐藏搜索框')
      showReplyUserSearch.value = false
      replySearchUsers.value = []
      replyAtPosition.value = -1
      if (replySearchTimer.value) {
        clearTimeout(replySearchTimer.value)
        replySearchTimer.value = null
      }
      return
    }
    
    // 检查是否有@符号
    const selection = window.getSelection()
    const range = selection.getRangeAt(0)
    const caretPos = range.startOffset
    
    // 更新lastCaretPosition到当前位置
    replyLastCaretPosition.value = caretPos
    
    const lastAtPos = text.lastIndexOf('@', caretPos)
    
    console.log('回复输入检测:', { 
      text, 
      caretPos, 
      lastAtPos, 
      hasAt: lastAtPos !== -1,
      textAfterAt: lastAtPos !== -1 ? text.substring(lastAtPos + 1, caretPos) : ''
    })
    
    if (lastAtPos !== -1 && lastAtPos < caretPos) {
      // 确保@后面没有空格，否则认为是一个完整的@提及
      const textAfterAt = text.substring(lastAtPos + 1, caretPos)
      const hasSpace = /\s/.test(textAfterAt)
      
      console.log('回复@检测:', { textAfterAt, hasSpace })
      
      if (!hasSpace) {
        const searchQuery = textAfterAt.trim()
        
        console.log('准备搜索回复用户:', searchQuery)
        
        replyAtPosition.value = lastAtPos
        
        // 如果是刚输入@符号，立即显示搜索框
        if (textAfterAt === '') {
          showReplyUserSearch.value = true
          replySearchUsers.value = []
          isSearchingReplyUsers.value = true
        }
        
        // 清除之前的定时器
        if (replySearchTimer.value) {
          clearTimeout(replySearchTimer.value)
        }
        
        // 设置新的定时器，延迟搜索
        replySearchTimer.value = setTimeout(() => {
          searchForReplyUsers(searchQuery)
        }, 300)
      } else {
        console.log('🔍 回复@后有空格，隐藏搜索框')
        showReplyUserSearch.value = false
        replySearchUsers.value = []
        replyAtPosition.value = -1
      }
    } else {
      console.log('🔍 回复没有找到@符号，隐藏搜索框')
      showReplyUserSearch.value = false
      replySearchUsers.value = []
      replyAtPosition.value = -1
    }
  }

  // 搜索用户
  const searchForReplyUsers = async (query) => {
    isSearchingReplyUsers.value = true
    console.log('🔍 开始搜索回复用户:', query)
    
    try {
      // 获取token
      const token = getToken()
      console.log('🔍 回复Token状态:', token ? '有token' : '无token')
      
      // 使用API服务搜索用户
      const data = await API.user.search(query)
      console.log('🔍 搜索回复用户API响应:', data)
      
      if (data.code === 200 && Array.isArray(data.data)) {
        replySearchUsers.value = data.data.slice(0, 5) // 最多显示5个结果
        replyUserSearchIndex.value = 0
        showReplyUserSearch.value = replySearchUsers.value.length > 0
        console.log('🔍 找到回复用户数量:', replySearchUsers.value.length, '用户列表:', replySearchUsers.value)
        console.log('🔍 回复用户搜索框状态:', showReplyUserSearch.value)
        
        // 强制触发DOM更新
        await nextTick()
        console.log('🔍 回复DOM更新完成，检查下拉框元素存在')
        const dropdown = document.querySelector('.reply-user-search-dropdown')
        console.log('🔍 回复下拉框元素:', dropdown)
      } else {
        replySearchUsers.value = []
        showReplyUserSearch.value = false
        console.log('🔍 未找到回复用户，响应代码:', data.code, '消息:', data.msg)
      }
    } catch (error) {
      console.error('❌ 搜索回复用户失败:', error)
      replySearchUsers.value = []
      showReplyUserSearch.value = false
    } finally {
      isSearchingReplyUsers.value = false
    }
  }

  // 选择用户
  const selectReplyUser = (user) => {
    if (!replyTextarea.value) {
      console.error('❌ replyTextarea不存在')
      return
    }
    
    // 设置标志位防止循环处理
    isSelectingReplyUser.value = true
    
    console.log('🎯 选择回复用户开始:', {
      user: user.username,
      atPosition: replyAtPosition.value,
      lastCaretPosition: replyLastCaretPosition.value
    })
    
    const text = replyTextarea.value.textContent || ''
    
    // 找到@符号后的查询文本结束位置
    const atStart = replyAtPosition.value + 1 // @符号后的第一个字符
    let searchEndPos = atStart
    
    // 查找@符号后没有空格的文本结束位置
    for (let i = atStart; i < text.length; i++) {
      if (/\s/.test(text[i])) {
        break
      }
      searchEndPos = i + 1
    }
    
    const beforeAt = text.substring(0, replyAtPosition.value)
    const afterAt = text.substring(searchEndPos)
    
    console.log('🎯 回复文本分析:', {
      originalText: text,
      beforeAt: beforeAt,
      afterAt: afterAt,
      atPosition: replyAtPosition.value,
      atStart: atStart,
      searchEndPos: searchEndPos
    })
    
    // 构建新文本：beforeAt + @用户名 + 空格 + afterAt
    const newText = beforeAt + '@' + user.username + ' ' + afterAt
    console.log('🎯 构建新回复文本:', newText)
    
    // 计算新光标位置（在@用户名和空格之后）
    const newCaretPosition = beforeAt.length + 1 + user.username.length + 1
    console.log('🎯 计算新回复光标位置:', newCaretPosition)
    
    // 立即清理搜索状态，防止后续处理
    showReplyUserSearch.value = false
    replySearchUsers.value = []
    replyAtPosition.value = -1
    replyUserSearchIndex.value = 0
    
    // 更新contenteditable内容
    replyTextarea.value.textContent = newText
    
    // 更新响应式变量
    replyContent.value = newText
    
    // 使用nextTick确保DOM更新后设置光标
    nextTick(() => {
      try {
        // 确保元素仍然存在且有焦点
        if (!replyTextarea.value) {
          console.error('❌ nextTick后replyTextarea不存在')
          return
        }
        
        // 先获取焦点
        replyTextarea.value.focus()
        
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
        let textNode = replyTextarea.value.firstChild
        
        // 如果没有文本节点或不是文本节点，创建一个
        if (!textNode || textNode.nodeType !== Node.TEXT_NODE) {
          textNode = document.createTextNode(newText)
          replyTextarea.value.innerHTML = ''
          replyTextarea.value.appendChild(textNode)
        }
        
        // 确保光标位置不超过文本长度
        const actualTextLength = textNode.textContent.length
        const finalCaretPosition = Math.min(newCaretPosition, actualTextLength)
        
        console.log('🎯 最终回复光标设置:', {
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
        
        console.log('🎯 回复光标设置成功')
        
      } catch (error) {
        console.error('❌ 设置回复光标失败:', error)
        // 备用方案：只确保获得焦点
        try {
          replyTextarea.value.focus()
        } catch (focusError) {
          console.error('❌ 设置回复焦点也失败:', focusError)
        }
      }
    })
    
    console.log('🎯 回复用户选择完成')
    
    // 延迟清除标志位，确保处理完成
    setTimeout(() => {
      isSelectingReplyUser.value = false
      console.log('🎯 清除选择回复用户标志位')
    }, 200)
  }

  // 处理键盘事件
  const handleReplyCommentKeydown = (e) => {
    console.log('🔥 handleReplyCommentKeydown 被调用:', e.key)
    
    // 如果按下删除键或退格键，需要检查删除后的内容
    if (e.key === 'Backspace' || e.key === 'Delete') {
      // 延迟检查，等待删除操作完成
      setTimeout(() => {
        if (!replyTextarea.value) return
        
        const text = replyTextarea.value.textContent || ''
        
        // 如果删除后内容为空，隐藏搜索框
        if (!text || text.trim() === '') {
          console.log('🗑️ 删除后回复内容为空，隐藏搜索框')
          showReplyUserSearch.value = false
          replySearchUsers.value = []
          replyAtPosition.value = -1
          if (replySearchTimer.value) {
            clearTimeout(replySearchTimer.value)
            replySearchTimer.value = null
          }
        }
      }, 10)
    }
    
    // 如果按下@键，立即显示搜索框
    if (e.key === '@') {
      console.log('🎯 检测到回复@键被按下')
      
      // 延迟执行，等待@字符实际输入到文本中
      setTimeout(() => {
        if (!replyTextarea.value) {
          console.log('❌ replyTextarea为空')
          return
        }
        
        const selection = window.getSelection()
        if (!selection.rangeCount) {
          console.log('❌ 没有选区')
          return
        }
        
        const range = selection.getRangeAt(0)
        const caretPos = range.startOffset
        replyLastCaretPosition.value = caretPos
        
        const text = replyTextarea.value.textContent
        const lastAtPos = text.lastIndexOf('@', caretPos)
        
        console.log('🎯 回复@键处理:', {
          text,
          caretPos,
          lastAtPos,
          找到At: lastAtPos !== -1 && lastAtPos < caretPos
        })
        
        if (lastAtPos !== -1 && lastAtPos < caretPos) {
          replyAtPosition.value = lastAtPos
          showReplyUserSearch.value = true
          replySearchUsers.value = []
          isSearchingReplyUsers.value = true
          
          console.log('🎯 立即触发回复空查询搜索')
          // 立即触发搜索，获取初始用户列表
          searchForReplyUsers('')
        }
      }, 10)
    }
    
    if (showReplyUserSearch.value) {
      console.log('🎯 回复用户搜索框已显示，处理导航键')
      
      if (e.key === 'ArrowDown') {
        e.preventDefault()
        replyUserSearchIndex.value = (replyUserSearchIndex.value + 1) % replySearchUsers.value.length
        console.log('🎯 向下选择，当前索引:', replyUserSearchIndex.value)
      } else if (e.key === 'ArrowUp') {
        e.preventDefault()
        replyUserSearchIndex.value = (replyUserSearchIndex.value - 1 + replySearchUsers.value.length) % replySearchUsers.value.length
        console.log('🎯 向上选择，当前索引:', replyUserSearchIndex.value)
      } else if (e.key === 'Enter' || e.key === 'Tab') {
        e.preventDefault()
        if (replySearchUsers.value.length > 0) {
          console.log('🎯 Enter/Tab键选择回复用户:', {
            selectedUser: replySearchUsers.value[replyUserSearchIndex.value],
            userIndex: replyUserSearchIndex.value,
            totalUsers: replySearchUsers.value.length
          })
          selectReplyUser(replySearchUsers.value[replyUserSearchIndex.value])
        } else {
          console.log('❌ Enter/Tab键但没有可选回复用户')
        }
      } else if (e.key === 'Escape') {
        e.preventDefault()
        console.log('🎯 ESC键关闭回复搜索')
        showReplyUserSearch.value = false
        replySearchUsers.value = []
        replyAtPosition.value = -1
      }
    }
  }

  // 处理退格键
  const handleReplyBackspace = () => {
    // 如果正在显示用户搜索，不做特殊处理
    if (showReplyUserSearch.value) return
    
    // 获取当前光标位置
    const selection = window.getSelection()
    if (!selection.rangeCount) return
    
    const range = selection.getRangeAt(0)
    const caretPos = range.startOffset
    
    // 检查是否在删除@用户名
    const text = replyTextarea.value.textContent
    const beforeCaret = text.substring(0, caretPos)
    
    // 如果光标前是一个完整的@用户名标记，则整体删除
    const mentionMatch = beforeCaret.match(/@([a-zA-Z0-9_\u4e00-\u9fa5]+)$/g)
    if (mentionMatch) {
      const mentionText = mentionMatch[0]
      const startPos = caretPos - mentionText.length
      
      // 创建一个新的范围来删除整个@用户名
      const deleteRange = document.createRange()
      deleteRange.setStart(replyTextarea.value.firstChild, startPos)
      deleteRange.setEnd(replyTextarea.value.firstChild, caretPos)
      deleteRange.deleteContents()
      
      // 防止默认的退格键行为
      event.preventDefault()
    }
  }

  return {
    showReplyUserSearch,
    replySearchUsers,
    replyUserSearchIndex,
    isSearchingReplyUsers,
    handleReplyContentEditableInput,
    handleReplyCommentKeydown,
    handleReplyBackspace,
    selectReplyUser
  }
} 