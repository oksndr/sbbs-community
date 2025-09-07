<template>
  <LayoutWithSidebar>
    <!-- Toast提示组件 -->
    <div class="toast-container" v-if="toast.show">
      <div class="toast-message" :class="[toast.type, {'toast-visible': toast.visible}]">
        <i :class="getToastIcon()"></i>
        {{ toast.message }}
      </div>
    </div>
    
    <div class="publish-page">
      <div class="publish-card">
        <div class="publish-header">
          <i class="ri-edit-line"></i>
          <h1>发布新帖子</h1>
        </div>
        
        <div v-if="!isLoggedIn" style="padding: 3rem 1rem; text-align: center;">
          <p style="margin-bottom: 1.5rem; font-size: 1.125rem; color: var(--text-secondary);">请先登录再发帖</p>
          <button @click="forceOpenLoginModal" class="btn btn-primary">
            <i class="ri-login-box-line"></i> 去登录
          </button>
        </div>
        
        <template v-else>
          <div class="publish-body">
            <div class="form-group">
              <label class="form-label" for="title">标题</label>
              <input type="text" id="title" class="form-input" v-model="postTitle" placeholder="请输入帖子标题" maxlength="100">
            </div>
            
            <div class="form-group">
              <label class="form-label">选择标签</label>
              <div class="tag-select">
                <div 
                  v-for="tag in tags" 
                  :key="tag.id" 
                  class="tag-item" 
                  :class="{ active: selectedTagIds.includes(tag.id) }"
                  @click="toggleTagSelection(tag.id)">
                  <i :class="getTagIcon(tag.id)"></i>
                  <span>{{ tag.name }}</span>
                </div>
              </div>
            </div>
            
            <div class="form-group">
              <label class="form-label">内容</label>
              

              <!-- 使用新的Markdown编辑器 -->
              <MarkdownEditor 
                v-model="markdownContent"
                placeholder="请输入帖子内容，支持Markdown格式..."
                height="600px"
                :enable-image-upload="true"
                @save="handleSave"
                @image-uploaded="handleImageUploaded"
                @focus="handleEditorFocus"
                @blur="handleEditorBlur"
                @upload-start="handleUploadStart"
                @upload-success="handleUploadSuccess"
                @upload-error="handleUploadError"
              />
              
              <!-- 编辑器功能提示 -->
              <div class="editor-tips">
                <i class="ri-drag-drop-line"></i>
                <span>💡 支持拖拽图片到编辑器中快速上传，也可以复制粘贴图片</span>
              </div>
            </div>
          </div>
          
          <div class="publish-footer">
            <button class="btn btn-outline" @click="goBack">
              <i class="ri-arrow-left-line"></i> 取消
            </button>
            <button class="btn btn-primary" @click="publishPost" :class="{ 'btn-loading': isPublishing }" :disabled="isPublishing">
              <template v-if="isPublishing">
                <i class="ri-loader-4-line spinning"></i>
                <span>发布中...</span>
              </template>
              <template v-else>
                <i class="ri-send-plane-line"></i>
                <span>发布帖子</span>
              </template>
            </button>
          </div>
        </template>
      </div>
    </div>
  </LayoutWithSidebar>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue';
import { computed } from 'vue';
import { useRouter } from '#app';
import { useUserStore } from '~/stores/user';
import { API } from '~/utils/api';
import LayoutWithSidebar from '~/components/LayoutWithSidebar.vue';
import MarkdownEditor from '~/components/MarkdownEditor.vue';
import pointsManager from '~/utils/points';
import { useRuntimeConfig } from '#app';

// 登录弹窗功能
const { forceOpenLoginModal } = useLoginModal()

const router = useRouter();
const userStore = useUserStore();

// 获取运行时配置中的API基础URL
const API_BASE_URL = useApiBaseUrl()

// 帖子信息
const postTitle = ref('');
const selectedTagIds = ref([]);
const markdownContent = ref('');
const markdownTextarea = ref(null);

// 标签相关
const tags = ref([]);

// 上传状态
const isUploading = ref(false);
const isPublishing = ref(false);
const imageInput = ref(null);

// 内容区块相关
const customBlockName = ref('');
const showCustomBlockInput = ref(false);
const customBlockInput = ref(null);
const customBlocks = ref([]);

// 拖拽相关
const isDragOver = ref(false);

// 计算属性
const isLoggedIn = computed(() => userStore.isLoggedIn);

// 添加toast状态
const toast = ref({
  show: false,
  visible: false, // 控制实际可见性
  message: '',
  type: 'info' // 可选: info, success, warning, error
});

// 显示toast提示，带渐入渐出效果
const showToast = (message, type = 'info', duration = 3000) => {
  // 如果已经有toast在显示，先清除之前的
  if (toast.value.show) {
    clearTimeout(toast.value.timer);
  }
  
  // 设置内容
  toast.value.message = message;
  toast.value.type = type;
  toast.value.show = true;
  
  // 使用nextTick确保DOM更新后再添加visible类触发动画
  nextTick(() => {
    // 短暂延迟以确保DOM已更新
    setTimeout(() => {
      toast.value.visible = true;
    }, 10);
  });
  
  // 自动关闭（先淡出再隐藏）
  toast.value.timer = setTimeout(() => {
    // 先淡出
    toast.value.visible = false;
    
    // 等待动画完成后移除DOM
    setTimeout(() => {
      toast.value.show = false;
    }, 300); // 和CSS动画时长一致
  }, duration);
};

// 获取toast图标
const getToastIcon = () => {
  const icons = {
    info: 'ri-information-line',
    success: 'ri-check-line',
    warning: 'ri-error-warning-line',
    error: 'ri-close-circle-line'
  };
  return icons[toast.value.type] || icons.info;
};

// 获取所有标签
const fetchTags = async () => {
  try {
    const response = await API.tags.getAllTags();
    if (response.code === 200) {
      tags.value = response.data || [];
    }
  } catch (error) {
    console.error('获取标签失败:', error);
  }
};

// 标签选择切换
const toggleTagSelection = (tagId) => {
  const index = selectedTagIds.value.indexOf(tagId);
  if (index !== -1) {
    // 如果已选中，则移除
    selectedTagIds.value.splice(index, 1);
  } else {
    // 如果未选中且选择数量少于3个，则添加
    if (selectedTagIds.value.length < 3) {
      selectedTagIds.value.push(tagId);
    } else {
      // 如果已经选择了3个，显示toast提示
      showToast('最多只能选择3个标签', 'warning');
    }
  }
};

// 获取标签图标
const getTagIcon = (tagId) => {
  const tagIcons = {
    1: 'ri-code-s-slash-line',
    2: 'ri-terminal-box-line',
    3: 'ri-question-line',
    4: 'ri-discuss-line',
    5: 'ri-lightbulb-line',
    'default': 'ri-hashtag'
  };
  return tagIcons[tagId] || tagIcons.default;
};

// 插入Markdown格式
const insertMarkdown = (prefix, suffix) => {
  if (!markdownTextarea.value) return;
  
  const textarea = markdownTextarea.value;
  const start = textarea.selectionStart;
  const end = textarea.selectionEnd;
  const selectedText = markdownContent.value.substring(start, end);
  
  const beforeText = markdownContent.value.substring(0, start);
  const afterText = markdownContent.value.substring(end);
  
  markdownContent.value = beforeText + prefix + selectedText + suffix + afterText;
  
  // 重新设置光标位置，保持滚动位置不变
  nextTick(() => {
    // 保存当前滚动位置
    const scrollTop = textarea.scrollTop;
    
    textarea.focus();
    
    if (selectedText.length > 0) {
      textarea.selectionStart = start + prefix.length;
      textarea.selectionEnd = start + prefix.length + selectedText.length;
    } else {
      textarea.selectionStart = textarea.selectionEnd = start + prefix.length;
    }
    
    // 恢复滚动位置，防止自动滚动到底部
    textarea.scrollTop = scrollTop;
  });
};

// 处理拖拽放下事件
const handleDrop = (event) => {
  isDragOver.value = false;
  
  const files = event.dataTransfer.files;
  if (files.length > 0) {
    const file = files[0];
    
    // 检查是否为图片
    if (!file.type.startsWith('image/')) {
      showToast('请上传图片文件', 'warning');
      return;
    }
    
    // 检查文件大小
    if (file.size > 5 * 1024 * 1024) {
      showToast('图片大小不能超过5MB', 'warning');
      return;
    }
    
    // 上传图片
    uploadImageFile(file);
  }
};

// 修改图片上传函数，提取通用上传逻辑
const uploadImage = (event) => {
  const file = event.target.files[0];
  if (!file) return;
  
  // 检查文件类型
  if (!['image/jpeg', 'image/png', 'image/gif'].includes(file.type)) {
    showToast('请上传JPG、PNG或GIF格式的图片', 'warning');
    return;
  }
  
  // 检查文件大小 (5MB限制)
  if (file.size > 5 * 1024 * 1024) {
    showToast('图片大小不能超过5MB', 'warning');
    return;
  }
  
  // 上传图片
  uploadImageFile(file);
  
  // 重置input，确保可以上传相同的文件
  if (imageInput.value) {
    imageInput.value.value = '';
  }
};

// 新增通用上传图片文件函数
const uploadImageFile = async (file) => {
  if (isUploading.value) return;
  
  isUploading.value = true;
  
  try {
    // 创建FormData对象
    const formData = new FormData();
    formData.append('image', file);
    
    // 调用API上传图片
    const response = await fetch(`${API_BASE_URL}/v1/image/upload`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      },
      body: formData
    });
    
    const result = await response.json();
    
    if (result.code === 200 && result.data && result.data.url) {
      // 上传成功，获取图片URL
      const imageUrl = result.data.url;
      
      // 在编辑器中插入图片
      const imageText = `![图片](${imageUrl})`;
      
      // 在光标位置插入，或者添加到末尾
      if (markdownTextarea.value) {
        const textarea = markdownTextarea.value;
        const start = textarea.selectionStart;
        const beforeText = markdownContent.value.substring(0, start);
        const afterText = markdownContent.value.substring(start);
        
        markdownContent.value = beforeText + imageText + afterText;
        
        // 设置光标位置到图片后，保持滚动位置不变
        nextTick(() => {
          // 保存当前滚动位置
          const scrollTop = textarea.scrollTop;
          
          textarea.focus();
          textarea.selectionStart = textarea.selectionEnd = start + imageText.length;
          
          // 恢复滚动位置，防止自动滚动
          textarea.scrollTop = scrollTop;
        });
      } else {
        markdownContent.value += (markdownContent.value ? '\n\n' : '') + imageText;
      }
      
      // 显示成功提示
      showToast('图片上传成功', 'success');
    } else {
      // 上传失败
      showToast(`图片上传失败: ${result.msg || '未知错误'}`, 'error');
    }
  } catch (error) {
    console.error('上传图片失败:', error);
    showToast('上传图片失败，请稍后重试', 'error');
  } finally {
    isUploading.value = false;
  }
};

// 强制重置上传状态
const forceResetUploadStatus = () => {
  isUploading.value = false;
  
  // 重置文件输入框
  if (imageInput.value) {
    imageInput.value.value = '';
  }
};

// 插入内容区块
const insertContentBlock = (blockName) => {
  const blockText = `**[${blockName}]**\n`;
  
  // 在当前光标位置插入区块标记，或者添加到末尾
  if (markdownTextarea.value) {
    const textarea = markdownTextarea.value;
    const start = textarea.selectionStart;
    const beforeText = markdownContent.value.substring(0, start);
    const afterText = markdownContent.value.substring(start);
    
    // 检查前后是否需要添加换行符
    const needPrefixNewline = beforeText.length > 0 && !beforeText.endsWith('\n');
    const needSuffixNewline = afterText.length > 0 && !afterText.startsWith('\n');
    
    const finalBlockText = 
      (needPrefixNewline ? '\n' : '') + 
      blockText + 
      (needSuffixNewline ? '\n' : '');
    
    markdownContent.value = beforeText + finalBlockText + afterText;
    
    // 设置光标位置到区块后，保持滚动位置不变
    nextTick(() => {
      // 保存当前滚动位置
      const scrollTop = textarea.scrollTop;
      
      textarea.focus();
      const newPosition = start + finalBlockText.length;
      textarea.selectionStart = textarea.selectionEnd = newPosition;
      
      // 恢复滚动位置，防止自动滚动
      textarea.scrollTop = scrollTop;
    });
  } else {
    // 添加到末尾，前面确保有换行符
    const prefix = markdownContent.value ? 
      (markdownContent.value.endsWith('\n') ? '' : '\n') : '';
    markdownContent.value += prefix + blockText;
  }
};

// 添加自定义区块
const addCustomBlock = () => {
  if (!customBlockName.value.trim()) return;
  
  // 添加到自定义区块列表
  customBlocks.value.push(customBlockName.value.trim());
  
  // 重置并隐藏输入框
  customBlockName.value = '';
  showCustomBlockInput.value = false;
  
  // 保存自定义区块到localStorage
  if (process.client) {
    localStorage.setItem('sbbs-custom-blocks', JSON.stringify(customBlocks.value));
  }
};

// 取消添加自定义区块
const cancelCustomBlock = () => {
  customBlockName.value = '';
  showCustomBlockInput.value = false;
};

// 聚焦自定义区块输入框
const focusCustomBlockInput = () => {
  nextTick(() => {
    if (customBlockInput.value) {
      customBlockInput.value.focus();
    }
  });
};

// 修改发布帖子函数，添加高亮参数
const publishPost = async () => {
  if (!isLoggedIn.value) {
    showToast('请先登录', 'warning');
    router.push('/auth/login');
    return;
  }
  
  if (!postTitle.value.trim()) {
    showToast('请输入帖子标题', 'warning');
    return;
  }
  
  if (selectedTagIds.value.length === 0) {
    showToast('请至少选择一个标签', 'warning');
    return;
  }
  
  if (!markdownContent.value.trim()) {
    showToast('请输入帖子内容', 'warning');
    return;
  }
  
  isPublishing.value = true;
  
  try {
    // 先设置加载状态，再进行异步操作避免闪烁
    await nextTick() // 确保UI更新
    
    // 构建帖子数据 - 直接使用原始markdown内容
    const postData = {
      title: postTitle.value.trim(),
      content: markdownContent.value, // 直接使用编辑器内容，不做额外处理
      tagIds: selectedTagIds.value,
    };
    
    // 调用V2版本的API发布帖子
    const response = await fetch(`${API_BASE_URL}/v2/publish`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      },
      body: JSON.stringify(postData)
    });
    
    const result = await response.json();
    
    if (result.code === 200) {
      // 发布成功，检查积分奖励并显示合并消息
      const pointsResult = pointsManager.addPoints('post');
      if (pointsResult.awarded) {
        showToast(`帖子发布成功！ 🎉 ${pointsResult.message}`, 'success');
      } else {
        showToast('帖子发布成功！', 'success');
      }
      
      // 清除草稿
      if (process.client) {
        localStorage.removeItem('sbbs-post-draft');
      }
      
      // 获取返回的postId并立即跳转，使用强制刷新避免加载动画闪烁
      const postId = result.data?.postId;
      if (postId) {
        // 使用window.location.href强制刷新跳转，避免Vue路由过渡闪烁
        if (process.client) {
          window.location.href = `/post/${postId}?page=1`;
        } else {
          router.push(`/post/${postId}?page=1`);
        }
      } else {
        // 如果没有返回postId，则跳转到首页
        if (process.client) {
          window.location.href = '/?highlight=new';
        } else {
          router.push({ path: '/', query: { highlight: 'new' } });
        }
      }
      
      // 成功时不在这里关闭加载状态，在跳转时会自然结束
      return;
    } else {
      // 显示错误提示
      showToast(`发布失败: ${result.msg || '未知错误'}`, 'error');
      isPublishing.value = false; // 只在失败时立即关闭加载状态
    }
  } catch (error) {
    console.error('发布帖子失败:', error);
    showToast('发布失败，请稍后重试', 'error');
    isPublishing.value = false; // 只在错误时立即关闭加载状态
  }
};

// 返回上一页
const goBack = () => {
  if (markdownContent.value.trim()) {
    if (confirm('放弃编辑？未发布的内容将保存为草稿。')) {
      router.back();
    }
  } else {
    router.back();
  }
};

// 处理编辑器焦点
const handleEditorFocus = () => {
  // 新的markdown编辑器焦点处理
};

// 处理编辑器失焦
const handleEditorBlur = () => {
  // 新的markdown编辑器失焦处理
};

// 处理保存快捷键 (Ctrl+S)
const handleSave = (content) => {
  markdownContent.value = content;
  showToast('内容已保存到编辑器', 'success', 1000);
};

// 处理图片上传成功
const handleImageUploaded = (imageUrl) => {
  // 图片上传成功的回调，由upload-success统一处理
};

// 处理上传开始
const handleUploadStart = (fileCount) => {
  showToast(`📤 正在上传 ${fileCount} 张图片，请稍候...`, 'info', 8000);
};

// 处理上传成功
const handleUploadSuccess = (fileCount) => {
  showToast(`🎉 成功上传 ${fileCount} 张图片！`, 'success', 4000);
};

// 处理上传失败
const handleUploadError = (error) => {
  showToast(`💥 图片上传失败: ${error}`, 'error', 8000);
};

// 原始的编辑器焦点处理 (保留用于兼容)
const handleOldEditorFocus = () => {
  // 确保光标在第一次点击时位于开头位置
  if (markdownTextarea.value) {
    // 延迟执行以覆盖浏览器默认行为
    setTimeout(() => {
      // 如果编辑器内容为空，确保光标在开头
      if (!markdownContent.value.trim()) {
        markdownTextarea.value.selectionStart = 0;
        markdownTextarea.value.selectionEnd = 0;
      }
    }, 0);
  }
};

// 组件挂载
onMounted(async () => {
  // 移除重复的用户初始化 - 已在app.vue中统一处理
  
  // 如果没有登录，提示并跳转
  if (!userStore.isLoggedIn) {
    setTimeout(() => {
      if (window.$toast) {
        window.$toast.warning('请先登录再发帖');
      }
      
      if (process.client && window.navigateWithPageTransition) {
        window.navigateWithPageTransition('/auth/login');
      } else {
        router.push('/auth/login');
      }
    }, 500);
    return;
  }
  
  // 获取标签列表
  await fetchTags();
  
  // 从localStorage加载草稿
  if (process.client) {
    const savedContent = localStorage.getItem('sbbs-post-draft');
    if (savedContent) {
      // 确保没有多余的前导空白或换行符
      markdownContent.value = savedContent.replace(/^\s+/, '');
    }
    
    // 加载自定义区块
    try {
      const savedBlocks = JSON.parse(localStorage.getItem('sbbs-custom-blocks') || '[]');
      customBlocks.value = savedBlocks;
    } catch (error) {
      console.error('加载自定义区块失败:', error);
    }
    
    // 设置自动保存
    const autoSaveInterval = setInterval(() => {
      if (markdownContent.value) {
        localStorage.setItem('sbbs-post-draft', markdownContent.value);
      }
    }, 30000);
    
    // 组件卸载时清除定时器
    onBeforeUnmount(() => {
      clearInterval(autoSaveInterval);
    });
  }
});

// 页面元数据
definePageMeta({
  layout: 'default'
});

useHead({
  title: '发布帖子 - SBBS社区',
  meta: [
    { name: 'description', content: 'SBBS社区 - 发布新帖子' }
  ]
});
</script>

<style scoped>
.publish-page {
  width: 100%;
  /* 网格已经限制了列宽，这里不需要额外限制 */
}

.publish-card {
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0,0,0,0.05);
  overflow: hidden;
  margin-bottom: 1.5rem;
}

.publish-header {
  padding: 1rem;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
}

.publish-header h1 {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0;
  color: #111;
}

.publish-header i {
  margin-right: 0.5rem;
  color: var(--primary-color);
  font-size: 1.25rem;
}

.publish-body {
  padding: 1.5rem;
}

.form-group {
  margin-bottom: 1.5rem;
}

.form-label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  font-size: 0.9375rem;
  color: #111;
}

.form-input {
  width: 100%;
  padding: 0.4rem 0.75rem; /* 减小垂直内边距 */
  border: 1px solid var(--border-color);
  border-radius: 6px;
  font-size: 0.9375rem;
  transition: all 0.15s;
  line-height: 1.4; /* 调整行高 */
}

.form-input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.tag-select {
  display: flex;
  flex-wrap: nowrap; /* 强制在一行显示 */
  gap: 0.375rem; /* 减小间距 */
  margin-top: 0.5rem;
  width: 100%; /* 充分利用可用宽度 */
}

.tag-item {
  display: inline-flex;
  align-items: center;
  padding: 0.25rem 0.5rem; /* 减小内边距 */
  border-radius: 4px; /* 减小圆角 */
  font-size: 0.8125rem; /* 略微减小字体 */
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  border: 1px solid var(--border-color);
  flex: 1; /* 让标签平分可用空间 */
  justify-content: center; /* 内容居中 */
  text-align: center;
  white-space: nowrap;
  min-width: 0; /* 允许收缩 */
}

.tag-item:hover {
  background-color: var(--hover-color);
}

.tag-item.active {
  background-color: #eef2ff;
  color: #4f46e5;
  border-color: #c7d2fe;
}

.tag-item i {
  margin-right: 0.25rem; /* 减小图标间距 */
  font-size: 0.875rem; /* 减小图标尺寸 */
}


.publish-footer {
  padding: 1rem;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid var(--border-color);
  background-color: #f9fafb;
}

.btn {
  display: inline-flex;
  align-items: center;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  font-weight: 500;
  font-size: 0.875rem;
  cursor: pointer;
  transition: all 0.15s;
  text-decoration: none;
}

.btn i {
  margin-right: 0.375rem;
}

.btn-primary {
  background-color: var(--primary-color);
  color: white;
  border: 1px solid transparent;
}

.btn-primary:hover {
  background-color: #2563eb;
}

.btn-outline {
  background-color: transparent;
  border: 1px solid var(--border-color);
  color: var(--text-color);
}

.btn-outline:hover {
  background-color: var(--hover-color);
}

.btn + .btn {
  margin-left: 0.75rem;
}

.btn-loading {
  position: relative;
  color: transparent !important;
  pointer-events: none;
  display: inline-flex;
  justify-content: center;
  align-items: center;
}

.btn-loading i {
  color: white !important;
  position: absolute;
  margin: 0 !important;
}

.btn-primary.btn-loading {
  background-color: #4a90e2;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.spinning {
  animation: spin 1s linear infinite;
}

/* 内容区块选择器样式 */
.content-block-selector {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background-color: #f9fafb;
}

.block-selector-label {
  font-weight: 500;
  font-size: 14px;
  color: #4b5563;
  margin-right: 6px;
}

.block-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.block-option {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 4px;
  border: 1px solid #d1d5db;
  background-color: white;
  color: #4b5563;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.block-option:hover {
  background-color: #f3f4f6;
  border-color: #b0b9c4;
}

.block-option i {
  margin-right: 5px;
  font-size: 16px;
  color: #4b5563;
}

/* 自定义区块输入框样式 */
.custom-block-input {
  display: flex;
  align-items: center;
  padding: 0;
  border-radius: 4px;
  border: 1px solid #d1d5db;
  overflow: hidden;
  background-color: white;
  transition: all 0.2s;
}

.custom-block-text {
  width: 120px;
  padding: 6px 8px;
  border: none;
  font-size: 14px;
  outline: none;
}

.custom-block-add,
.custom-block-cancel {
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  padding: 6px 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.custom-block-add {
  color: #10b981;
}

.custom-block-add:hover {
  background-color: rgba(16, 185, 129, 0.1);
}

.custom-block-cancel {
  color: #ef4444;
}

.custom-block-cancel:hover {
  background-color: rgba(239, 68, 68, 0.1);
}

/* 自定义已保存区块样式 */
.custom-saved-block {
  background-color: #f0f7ff;
  border-color: #93c5fd;
}

.custom-saved-block i {
  color: #3b82f6;
}

.custom-saved-block:hover {
  background-color: #e0edff;
  border-color: #60a5fa;
}

/* 图片上传容器样式 */
.image-upload-container {
  margin-bottom: 10px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background-color: #f9fafb;
  display: flex;
  flex-direction: column;
  transition: all 0.2s ease;
}

.image-upload-container.is-dragover {
  border-color: #60a5fa;
  background-color: rgba(59, 130, 246, 0.05);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.image-upload-container.is-uploading {
  border-color: #409eff;
  background-color: #ecf5ff;
}

.image-upload-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.image-upload-title {
  font-size: 14px;
  font-weight: 500;
  color: #4b5563;
  display: flex;
  align-items: center;
}

.image-upload-title i {
  margin-right: 6px;
  color: #6b7280;
}

.image-upload-btn {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 4px;
  background-color: #f3f4f6;
  border: 1px solid #d1d5db;
  color: #4b5563;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.image-upload-btn:hover {
  background-color: #e5e7eb;
}

.image-upload-btn i {
  margin-right: 6px;
}

.image-upload-input {
  display: none;
}

.drag-upload-area {
  margin-top: 8px;
  padding: 20px 0;
  border: 2px dashed #d1d5db;
  border-radius: 4px;
  text-align: center;
  transition: all 0.2s;
}

.image-upload-container.is-dragover .drag-upload-area {
  border-color: #60a5fa;
  background-color: rgba(59, 130, 246, 0.05);
}

.drag-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  color: #6b7280;
}

.drag-inner i {
  font-size: 24px;
  margin-bottom: 8px;
  color: #9ca3af;
}

.drag-inner p {
  margin: 0 0 4px 0;
  font-size: 14px;
  font-weight: 500;
}

.drag-inner span {
  font-size: 12px;
  color: #9ca3af;
}

.upload-progress {
  margin-top: 12px;
  padding: 10px;
  background-color: #409eff;
  color: white;
  border-radius: 4px;
  font-weight: 500;
  font-size: 13px;
  display: flex;
  align-items: center;
  box-shadow: 0 2px 5px rgba(64, 158, 255, 0.3);
}

.upload-progress i.spinning {
  margin-right: 10px;
  font-size: 18px;
}

.progress-title {
  font-weight: 600;
}

.progress-sub {
  font-size: 12px;
  opacity: 0.9;
  margin-top: 3px;
}

.image-cancel-btn {
  margin-left: auto;
  background: none;
  border: none;
  color: white;
  cursor: pointer;
  opacity: 0.8;
  transition: opacity 0.15s;
}

.image-upload-btn.disabled {
  opacity: 0.6;
  cursor: not-allowed;
  background-color: #f0f0f0;
}

/* 编辑器功能提示 */
.editor-tips {
  margin-top: 0.5rem;
  padding: 0.75rem 1rem;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 0.875rem;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.editor-tips i {
  color: #3b82f6;
  font-size: 1rem;
}

.editor-tips span {
  line-height: 1.4;
}

/* Markdown编辑器样式 */
.markdown-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
  padding: 0.5rem;
  background-color: #f3f4f6;
  border: 1px solid #e5e7eb;
  border-bottom: none;
  border-top-left-radius: 6px;
  border-top-right-radius: 6px;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border-radius: 4px;
  border: none;
  background: none;
  color: #4b5563;
  cursor: pointer;
  transition: all 0.15s;
}

.toolbar-btn:hover {
  background-color: #e5e7eb;
  color: #1f2937;
}

.markdown-editor {
  border: 1px solid #e5e7eb;
  border-top: none;
  border-bottom-left-radius: 6px;
  border-bottom-right-radius: 6px;
  overflow: hidden;
}

.markdown-textarea {
  width: 100%;
  min-height: 300px;
  padding: 1rem;
  font-family: monospace;
  font-size: 0.9375rem;
  line-height: 1.6;
  border: none;
  outline: none;
  resize: vertical;
  white-space: pre-wrap; /* 确保空白符正确处理 */
  overflow-wrap: break-word; /* 确保长单词自动换行 */
  word-break: normal;
  tab-size: 2; /* 设置Tab键宽度 */
}

.markdown-preview-hint {
  display: flex;
  align-items: center;
  margin-top: 0.5rem;
  font-size: 0.75rem;
  color: #6b7280;
}

.markdown-preview-hint i {
  margin-right: 0.375rem;
  font-size: 0.875rem;
}

/* 响应式调整 */
@media (max-width: 768px) {
  
  .publish-body {
    padding: 1rem;
  }
  
  .block-options {
    width: 100%;
    margin-top: 8px;
  }
  
  .block-selector-label {
    width: 100%;
  }
  
  .markdown-toolbar {
    padding: 0.25rem;
  }
}

/* Toast提示样式 */
.toast-container {
  position: fixed;
  top: 20px;
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  z-index: 9999;
  pointer-events: none;
}

.toast-message {
  display: flex;
  align-items: center;
  padding: 10px 16px;
  background-color: white;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  font-size: 14px;
  max-width: 80%;
  transform: translateY(-20px);
  opacity: 0;
  transition: all 0.3s ease;
}

.toast-message.toast-visible {
  transform: translateY(0);
  opacity: 1;
}

.toast-message i {
  margin-right: 8px;
  font-size: 16px;
}

.toast-message.info {
  background-color: #e6f7ff;
  border-left: 4px solid #1890ff;
  color: #0c63e4;
}

.toast-message.success {
  background-color: #f6ffed;
  border-left: 4px solid #52c41a;
  color: #389e0d;
}

.toast-message.warning {
  background-color: #fffbe6;
  border-left: 4px solid #faad14;
  color: #d48806;
}

.toast-message.error {
  background-color: #fff2f0;
  border-left: 4px solid #ff4d4f;
  color: #cf1322;
}
</style> 