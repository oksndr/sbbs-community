<template>
  <div 
    class="markdown-editor-container"
    @dragover.prevent="handleDragOver"
    @drop.prevent="handleDrop"
  >
    <!-- md-editor-v3组件 -->
    <md-editor 
      v-model="content" 
      :language="language"
      :theme="theme"
      :preview-theme="previewTheme"
      :code-theme="codeTheme"
      :placeholder="placeholder"
      :toolbars="toolbars"
      :upload-imgs="enableImageUpload"
      :on-upload-img="handleImageUpload"
      @save="handleSave"
      @focus="handleFocus"
      @blur="handleBlur"
      :style="{ height: editorHeight }"
    />
    
    <!-- 上传进度覆盖层 -->
    <div v-if="isUploading" class="upload-progress-overlay">
      <div class="upload-content">
        <div class="upload-icon">
          <i class="ri-loader-4-line spinning"></i>
        </div>
        <p class="upload-title">{{ uploadMessage }}</p>
        <small class="upload-subtitle">{{ uploadSubtitle }}</small>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { API } from '~/utils/api'

// Props
const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: '请输入内容...'
  },
  height: {
    type: String,
    default: '500px'
  },
  theme: {
    type: String,
    default: 'light' // light, dark
  },
  previewTheme: {
    type: String,
    default: 'default' // default, github, vuepress, mk-cute, smart-blue, cyanosis
  },
  codeTheme: {
    type: String,
    default: 'atom' // atom, a11y, github, gradient, kimbie, paraiso, qtcreator, stackoverflow
  },
  language: {
    type: String,
    default: 'zh-CN' // zh-CN, en-US
  },
  enableImageUpload: {
    type: Boolean,
    default: true
  }
})

// Emits
const emit = defineEmits(['update:modelValue', 'save', 'focus', 'blur', 'image-uploaded', 'upload-start', 'upload-success', 'upload-error'])

// 添加自定义拖拽处理函数
const handleDragOver = (event) => {
  event.preventDefault()
  console.log('拖拽悬停中...')
}

const handleDrop = (event) => {
  event.preventDefault()
  console.log('检测到拖拽放下事件')
  
  const files = event.dataTransfer.files
  if (files && files.length > 0) {
    console.log('拖拽的文件:', files)
    const imageFiles = Array.from(files).filter(file => file.type.startsWith('image/'))
    console.log('过滤出的图片文件:', imageFiles)
    
    if (imageFiles.length > 0) {
      handleImageUpload(imageFiles, (urls) => {
        console.log('拖拽上传完成:', urls)
        // 手动插入图片到编辑器内容
        if (urls && urls.length > 0) {
          const imageMarkdown = urls.map(url => `![图片](${url})`).join('\n\n')
          const newContent = content.value + (content.value ? '\n\n' : '') + imageMarkdown
          content.value = newContent
          console.log('手动插入图片完成:', imageMarkdown)
        }
      })
    }
  }
}

// 内容双向绑定
const content = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

// 编辑器高度
const editorHeight = computed(() => props.height)

// 工具栏配置 - 保留常用功能
const toolbars = ref([
  'bold',
  'italic',
  'strikeThrough',
  '-',
  'title',
  'sub',
  'sup',
  'quote',
  'unorderedList',
  'orderedList',
  'task',
  '-',
  'codeRow',
  'code',
  'link',
  'image',
  'table',
  'mermaid',
  'katex',
  '-',
  'revoke',
  'next',
  'save',
  '-',
  'pageFullscreen',
  'fullscreen',
  'preview',
  'previewOnly',
  'htmlPreview',
  'catalog'
])

// 上传状态
const isUploading = ref(false)
const uploadMessage = ref('')
const uploadSubtitle = ref('')

// 图片上传处理
const handleImageUpload = async (files, callback) => {
  if (!props.enableImageUpload) {
    console.warn('图片上传功能已禁用')
    return
  }

  // 设置上传状态
  isUploading.value = true
  uploadMessage.value = `正在上传 ${files.length} 张图片...`
  uploadSubtitle.value = '上传完成后将自动插入到编辑器中'
  
  // 通知父组件开始上传
  emit('upload-start', files.length)

  try {
    const uploadPromises = files.map(async (file) => {
      console.log('开始上传图片:', file.name)
      
      const response = await API.upload.image(file)
      
      console.log('图片上传响应:', response)
      
      if (response.code === 200) {
        const imageUrl = response.data.url
        emit('image-uploaded', imageUrl)
        console.log('图片上传成功:', imageUrl)
        return imageUrl
      } else {
        throw new Error(response.msg || '上传失败')
      }
    })

    const urls = await Promise.all(uploadPromises)
    callback(urls)
    console.log('所有图片上传完成:', urls)
    
    // 通知父组件上传成功
    emit('upload-success', urls.length)
    
    // 显示成功状态
    uploadMessage.value = `成功上传 ${urls.length} 张图片！`
    uploadSubtitle.value = '图片已插入到编辑器中'
    
    // 短暂显示成功状态后隐藏
    setTimeout(() => {
      isUploading.value = false
    }, 1500)
  } catch (error) {
    console.error('图片上传失败:', error)
    callback([])
    
    // 通知父组件上传失败
    emit('upload-error', error.message)
    
    // 显示失败状态
    uploadMessage.value = '图片上传失败！'
    uploadSubtitle.value = error.message || '请重试'
    
    // 显示失败状态3秒后隐藏
    setTimeout(() => {
      isUploading.value = false
    }, 3000)
  } finally {
    // 不在这里直接隐藏，由成功/失败的回调来控制
  }
}


// 保存事件
const handleSave = (value) => {
  emit('save', value)
}

// 焦点事件
const handleFocus = () => {
  emit('focus')
}

// 失焦事件
const handleBlur = () => {
  emit('blur')
}

// 主题适配 - 根据系统主题自动切换
const detectSystemTheme = () => {
  if (typeof window !== 'undefined') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  }
  return 'light'
}

// 如果需要动态主题，可以监听系统主题变化
onMounted(() => {
  if (typeof window !== 'undefined' && props.theme === 'auto') {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    mediaQuery.addEventListener('change', (e) => {
      // 这里可以emit事件让父组件知道主题变化
    })
  }
})
</script>

<style scoped>
.markdown-editor-container {
  position: relative;
  border: 1px solid var(--border-color, #ddd);
  border-radius: 8px;
  overflow: hidden;
  background: var(--bg-color, #fff);
}

/* 自定义编辑器样式 */
:deep(.md-editor) {
  --md-color: var(--text-color, #333);
  --md-hover-color: var(--primary-color, #2563eb);
  --md-bk-color: var(--bg-color, #fff);
  --md-bk-color-outstand: var(--bg-secondary, #f8f9fa);
  --md-bk-hover-color: var(--bg-hover, #f0f0f0);
  --md-border-color: var(--border-color, #ddd);
  --md-border-hover-color: var(--border-hover, #bbb);
}

/* 工具栏分两行显示的优雅解决方案 */
:deep(.md-editor-toolbar-wrapper) {
  min-height: auto !important;
}

:deep(.md-editor-toolbar) {
  flex-wrap: wrap !important;
  min-height: 58px !important; /* 两行工具栏的高度 */
  align-content: flex-start !important;
  padding: 4px 8px !important;
  gap: 2px !important;
}

:deep(.md-editor-toolbar-left) {
  flex-wrap: wrap !important;
  max-width: 100% !important;
}

:deep(.md-editor-toolbar-right) {
  flex-wrap: wrap !important;
  margin-left: auto !important;
}

/* 调整工具栏项目的间距，使其更紧凑 */
:deep(.md-editor-toolbar-item) {
  margin: 1px 1px !important;
  flex-shrink: 0 !important;
}

/* 调整分隔符 */
:deep(.md-editor-divider) {
  margin: 1px 2px !important;
}

/* 继续md-editor的CSS变量 */
:deep(.md-editor) {
  --md-border-active-color: var(--primary-color, #2563eb);
  --md-modal-mask: rgba(0, 0, 0, 0.5);
  --md-scrollbar-bg-color: var(--scrollbar-bg, #f0f0f0);
  --md-scrollbar-thumb-color: var(--scrollbar-thumb, #c1c1c1);
  --md-scrollbar-thumb-hover-color: var(--scrollbar-thumb-hover, #a1a1a1);
  --md-scrollbar-thumb-active-color: var(--scrollbar-thumb-active, #909090);
}

/* 暗色模式适配 */
:deep(.md-editor.md-editor-dark) {
  --md-color: var(--text-color-dark, #e5e5e5);
  --md-bk-color: var(--bg-color-dark, #1f2937);
  --md-bk-color-outstand: var(--bg-secondary-dark, #374151);
  --md-border-color: var(--border-color-dark, #4b5563);
}

/* md-editor预览区域的markdown样式 */
:deep(.md-editor-preview) {
  padding: 1rem !important;
  font-size: 0.95rem;
  line-height: 1.6;
}

/* 编辑器预览区域的图片尺寸控制 */
:deep(.md-editor-preview img) {
  max-width: min(400px, 80%) !important;
  width: auto !important;
  height: auto !important;
  display: block !important;
  margin: 0.8rem auto !important;
  border-radius: 8px !important;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1) !important;
  transition: all 0.3s ease !important;
  cursor: pointer !important;
  background-color: #fff !important;
  box-sizing: content-box !important;
}

:deep(.md-editor-preview img:hover) {
  transform: scale(1.01) !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12) !important;
}

/* 列表基础样式 */
:deep(.md-editor-preview ul),
:deep(.md-editor-preview ol) {
  padding-left: 1.8em !important;
  margin: 0.8em 0 !important;
  list-style: initial !important;
}

:deep(.md-editor-preview ul) {
  list-style-type: disc !important;
}

:deep(.md-editor-preview ol) {
  list-style-type: decimal !important;
}

/* 列表项样式优化 */
:deep(.md-editor-preview li) {
  margin-bottom: 0.4em !important;
  list-style: inherit !important;
  line-height: 1.5 !important;
  color: #333 !important;
  padding-left: 0.3em !important;
}

:deep(.md-editor-preview li+li) {
  margin-top: 0.4em !important;
}

/* 优化marker样式 */
:deep(.md-editor-preview ul li::marker) {
  color: #3b82f6 !important;
  font-size: 0.9em !important;
}

:deep(.md-editor-preview ol li::marker) {
  color: #059669 !important;
  font-weight: 600 !important;
}

/* 嵌套列表样式优化 */
:deep(.md-editor-preview ul ul) {
  list-style-type: circle !important;
  padding-left: 1.5em !important;
  margin: 0.3em 0 !important;
}

:deep(.md-editor-preview ul ul ul) {
  list-style-type: square !important;
}

:deep(.md-editor-preview ol ol) {
  list-style-type: lower-alpha !important;
  padding-left: 1.5em !important;
  margin: 0.3em 0 !important;
}

:deep(.md-editor-preview ol ol ol) {
  list-style-type: lower-roman !important;
}

/* 嵌套列表marker颜色 */
:deep(.md-editor-preview ul ul li::marker) {
  color: #6366f1 !important;
}

:deep(.md-editor-preview ul ul ul li::marker) {
  color: #8b5cf6 !important;
}

:deep(.md-editor-preview ol ol li::marker) {
  color: #0891b2 !important;
}

:deep(.md-editor-preview ol ol ol li::marker) {
  color: #c2410c !important;
}

/* 标题样式优化 */
:deep(.md-editor-preview h1),
:deep(.md-editor-preview h2),
:deep(.md-editor-preview h3),
:deep(.md-editor-preview h4),
:deep(.md-editor-preview h5),
:deep(.md-editor-preview h6) {
  margin-top: 1.2em !important;
  margin-bottom: 0.6em !important;
  font-weight: 600 !important;
  line-height: 1.25 !important;
  color: #1a1a1a !important;
}

/* 第一个标题或紧跟在内容开头的标题减少上边距 */
:deep(.md-editor-preview h1:first-child),
:deep(.md-editor-preview h2:first-child),
:deep(.md-editor-preview h3:first-child),
:deep(.md-editor-preview h4:first-child),
:deep(.md-editor-preview h5:first-child),
:deep(.md-editor-preview h6:first-child) {
  margin-top: 0.3em !important;
}

:deep(.md-editor-preview h1) {
  font-size: 1.75rem !important;
}

:deep(.md-editor-preview h2) {
  font-size: 1.5rem !important;
  padding-bottom: 0.25em !important;
  border-bottom: 1px solid #f3f4f6 !important;
}

:deep(.md-editor-preview h3) {
  font-size: 1.25rem !important;
}

:deep(.md-editor-preview h4) {
  font-size: 1.1rem !important;
}

:deep(.md-editor-preview h5) {
  font-size: 1rem !important;
}

:deep(.md-editor-preview h6) {
  font-size: 0.9rem !important;
  color: #6b7280 !important;
}


/* 上传进度覆盖层 */
.upload-progress-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.3s ease-in-out;
}

.upload-content {
  text-align: center;
  padding: 2rem;
}

.upload-icon {
  margin-bottom: 1rem;
}

.upload-icon i {
  font-size: 3rem;
  color: #3b82f6;
}

.upload-icon i.spinning {
  animation: spin 1s linear infinite;
}

.upload-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 0.5rem 0;
}

.upload-subtitle {
  font-size: 0.875rem;
  color: #6b7280;
  margin: 0;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* 响应式调整 */
@media (max-width: 768px) {
  .markdown-editor-container {
    border-radius: 4px;
  }
  
  :deep(.md-editor) {
    --md-editor-height: 400px;
  }
  
  :deep(.md-editor-preview ul),
  :deep(.md-editor-preview ol) {
    padding-left: 1.5em !important;
  }
  
  .upload-content {
    padding: 1.5rem;
  }
  
  .upload-icon i {
    font-size: 2.5rem;
  }
  
  .upload-title {
    font-size: 1rem;
  }
}
</style>
