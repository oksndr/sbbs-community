import { ref, onMounted, onUnmounted, nextTick } from 'vue'

/**
 * 图片预览相关的组合式函数
 */
export function useImagePreview() {
  // 图片预览相关
  const previewImageUrl = ref(null)

  // 打开图片预览
  const openImagePreview = (url) => {
    previewImageUrl.value = url
    // 禁止滚动
    document.body.style.overflow = 'hidden'
  }

  // 关闭图片预览
  const closeImagePreview = () => {
    previewImageUrl.value = null
    // 恢复滚动
    document.body.style.overflow = ''
  }

  // 绑定帖子内容中的图片点击事件
  const bindImageClickEvents = () => {
    nextTick(() => {
      // 查找所有帖子内容中的图片
      const contentImages = document.querySelectorAll('.post-content img, .markdown-body img, .content-image')
      
      contentImages.forEach(img => {
        // 移除之前的事件监听器（避免重复绑定）
        img.removeEventListener('click', handleImageClick)
        // 添加点击事件
        img.addEventListener('click', handleImageClick)
        // 添加样式指示图片可点击
        img.style.cursor = 'pointer'
      })
    })
  }

  // 处理图片点击事件
  const handleImageClick = (event) => {
    event.preventDefault()
    event.stopPropagation()
    const imageUrl = event.target.src
    if (imageUrl) {
      openImagePreview(imageUrl)
    }
  }

  // 设置全局预览函数
  onMounted(() => {
    window.previewImage = openImagePreview
    
    // 初始绑定图片点击事件
    bindImageClickEvents()
    
    // 添加键盘事件监听，按ESC关闭预览
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && previewImageUrl.value) {
        closeImagePreview()
      }
    }
    
    window.addEventListener('keydown', handleKeyDown)
    
    // 组件卸载时移除事件监听
    onUnmounted(() => {
      window.removeEventListener('keydown', handleKeyDown)
      delete window.previewImage
      
      // 清理图片点击事件
      const contentImages = document.querySelectorAll('.post-content img, .markdown-body img, .content-image')
      contentImages.forEach(img => {
        img.removeEventListener('click', handleImageClick)
      })
    })
  })

  return {
    previewImageUrl,
    openImagePreview,
    closeImagePreview,
    bindImageClickEvents
  }
} 