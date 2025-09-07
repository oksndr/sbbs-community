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
          <h1>编辑帖子</h1>
        </div>
        
        <!-- 加载状态 -->
        <template v-if="isLoading">
          <div class="loading-state">
            <i class="ri-loader-4-line spinning"></i>
            <span>正在加载帖子数据...</span>
          </div>
        </template>
        
        <!-- 错误状态 -->
        <template v-else-if="error">
          <div class="error-state">
            <i class="ri-error-warning-line"></i>
            <h3>加载失败</h3>
            <p>{{ error }}</p>
            <button class="btn btn-primary" @click="loadPostData">
              <i class="ri-refresh-line"></i> 重试
            </button>
          </div>
        </template>
        
        <!-- 编辑表单 -->
        <template v-else>
          <div class="publish-body">
            <div class="form-group">
              <label class="form-label" for="title">标题</label>
              <input 
                type="text" 
                id="title" 
                class="form-input" 
                v-model="postTitle" 
                placeholder="请输入帖子标题" 
                maxlength="100">
              <div class="input-hint">
                <span class="char-count">{{ postTitle.length }}/100</span>
              </div>
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
                placeholder="请输入内容，支持Markdown格式..."
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
            <button class="btn btn-primary" @click="updatePost" :class="{ 'btn-loading': isUpdating }" :disabled="isUpdating">
              <template v-if="isUpdating">
                <i class="ri-loader-4-line spinning"></i>
                <span>更新中...</span>
              </template>
              <template v-else>
                <i class="ri-save-line"></i>
                <span>保存修改</span>
              </template>
            </button>
          </div>
        </template>
      </div>
    </div>
  </LayoutWithSidebar>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue';
import { useRoute, useRouter } from '#imports';
import { useUserStore } from '~/stores/user';
import { API } from '~/utils/api';
import LayoutWithSidebar from '~/components/LayoutWithSidebar.vue';
import MarkdownEditor from '~/components/MarkdownEditor.vue';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

// 获取API基础URL
const API_BASE_URL = useApiBaseUrl()

// 帖子ID
const postId = route.params.id;

// 帖子信息
const postTitle = ref('');
const selectedTagIds = ref([]);
const markdownContent = ref('');
const markdownTextarea = ref(null);

// 标签相关
const tags = ref([]);

// 状态管理
const isLoading = ref(true);
const isUpdating = ref(false);
const error = ref(null);

// 上传状态
const isUploading = ref(false);
const imageInput = ref(null);

// 拖拽相关
const isDragOver = ref(false);

// 内容区块相关
const customBlockName = ref('');
const showCustomBlockInput = ref(false);
const customBlockInput = ref(null);
const customBlocks = ref([]);

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

// 简单的消息提示函数 - 使用Toast替代alert
const showMessage = (message, type = 'info') => {
  showToast(message, type);
};

// 检查登录状态
const checkAuth = () => {
  if (!userStore.isLoggedIn) {
    router.push('/auth/login');
    return false;
  }
  return true;
};

// 处理标签匹配
const processTagSelection = (post) => {
  if (!post || !tags.value.length) return;
  
  if (post.tags && Array.isArray(post.tags)) {
    // 如果是字符串数组，根据名称找到对应的ID
    selectedTagIds.value = [];
    post.tags.forEach(tagName => {
      const tag = tags.value.find(t => t.name === tagName);
      if (tag) {
        selectedTagIds.value.push(tag.id);
      }
    });
  } else if (post.tagIdsStringAlias) {
    // 如果有tagIdsStringAlias字段，直接使用
    selectedTagIds.value = post.tagIdsStringAlias.split(',').map(id => parseInt(id));
  }
  
  console.log('标签处理完成:', selectedTagIds.value);
};

// 获取标签列表
const fetchTags = async () => {
  try {
    const response = await API.tags.getTags();
    if (response.code === 200 && response.data) {
      tags.value = response.data;
      
      // 如果帖子数据已经加载，处理标签选择
      if (postTitle.value) {
        // 使用当前加载的帖子数据重新处理标签
        const currentPost = {
          tags: lastLoadedPost.value?.tags,
          tagIdsStringAlias: lastLoadedPost.value?.tagIdsStringAlias
        };
        processTagSelection(currentPost);
      }
    }
  } catch (error) {
    console.error('获取标签失败:', error);
  }
};

// 存储最后加载的帖子数据，用于标签处理
const lastLoadedPost = ref(null);

// 加载帖子数据
const loadPostData = async () => {
  if (!checkAuth()) return;
  
  isLoading.value = true;
  error.value = null;
  
  try {
    // 获取帖子详情
    const postResponse = await API.posts.getPostById(postId);
    
    if (postResponse.code === 200 && postResponse.data) {
      // v2 API返回的数据结构: { post: {}, liked: bool, disLiked: bool }
      const responseData = postResponse.data;
      const post = responseData.post || responseData; // 兼容两种数据结构
      
      console.log('🔍 权限检查:', {
        postUserId: post.userId,
        postUserIdType: typeof post.userId,
        currentUserId: userStore.user?.id,
        currentUserIdType: typeof userStore.user?.id,
        userStoreUser: userStore.user
      });
      
      // 检查是否是作者 - 支持字符串和数字类型比较
      const postUserId = String(post.userId);
      const currentUserId = String(userStore.user?.id || '');
      
      if (postUserId !== currentUserId) {
        error.value = `您没有权限编辑此帖子 (帖子作者ID: ${postUserId}, 当前用户ID: ${currentUserId})`;
        return;
      }
      
      // 填充表单数据
      postTitle.value = post.title;
      markdownContent.value = post.content;
      
      // 保存帖子数据用于后续处理
      lastLoadedPost.value = post;
      
      // 如果标签数据已经加载，立即处理标签选择
      if (tags.value.length > 0) {
        processTagSelection(post);
      }
      
      console.log('帖子数据加载成功:', post);
    } else {
      error.value = postResponse.msg || '获取帖子数据失败';
    }
  } catch (err) {
    console.error('加载帖子数据失败:', err);
    error.value = '网络错误，请稍后重试';
  } finally {
    isLoading.value = false;
  }
};

// 切换标签选择
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
      // 如果已经选择了3个，显示提示
      showMessage('最多只能选择3个标签', 'warning');
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

// 处理编辑器焦点
const handleEditorFocus = () => {
  // 可以在这里添加焦点处理逻辑
};

// 处理编辑器失焦
const handleEditorBlur = () => {
  // 可以在这里添加失焦处理逻辑
};

// 处理保存快捷键 (Ctrl+S)
const handleSave = (content) => {
  markdownContent.value = content;
  showMessage('内容已保存到编辑器', 'success');
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

// 图片上传处理
const uploadImage = (event) => {
  const file = event.target.files[0];
  if (!file) return;
  
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
  
  // 清空input，这样相同文件也能再次选择
  if (imageInput.value) {
    imageInput.value.value = '';
  }
};

// 通用图片上传函数
const uploadImageFile = async (file) => {
  if (isUploading.value) return;
  
  isUploading.value = true;
  
  try {
    const response = await API.upload.image(file);
    
    if (response.code === 200 && response.data) {
      const imageUrl = response.data.url;
      
      // 将图片URL插入到markdown内容中
      insertImageToMarkdown(imageUrl);
      
      showToast('图片上传成功', 'success');
    } else {
      showToast(response.msg || '图片上传失败', 'error');
    }
  } catch (error) {
    console.error('图片上传失败:', error);
    showToast('图片上传失败，请稍后重试', 'error');
  } finally {
    isUploading.value = false;
  }
};

// 将图片插入到markdown内容中
const insertImageToMarkdown = (imageUrl) => {
  const imageMarkdown = `![图片](${imageUrl})\n`;
  
  if (markdownTextarea.value) {
    const textarea = markdownTextarea.value;
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    
    const beforeText = markdownContent.value.substring(0, start);
    const afterText = markdownContent.value.substring(end);
    
    markdownContent.value = beforeText + imageMarkdown + afterText;
    
    // 设置光标位置到插入内容之后，保持滚动位置不变
    nextTick(() => {
      // 保存当前滚动位置
      const scrollTop = textarea.scrollTop;
      
      textarea.focus();
      const newPosition = start + imageMarkdown.length;
      textarea.selectionStart = textarea.selectionEnd = newPosition;
      
      // 恢复滚动位置，防止自动滚动
      textarea.scrollTop = scrollTop;
    });
  } else {
    // 如果没有焦点位置，就追加到末尾
    markdownContent.value += '\n' + imageMarkdown;
  }
};

// 强制重置上传状态
const forceResetUploadStatus = () => {
  isUploading.value = false;
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

// 更新帖子
const updatePost = async () => {
  if (!checkAuth()) return;
  
  // 验证表单
  if (!postTitle.value.trim()) {
    showMessage('请输入帖子标题', 'error');
    return;
  }
  
  if (!markdownContent.value.trim()) {
    showMessage('请输入帖子内容', 'error');
    return;
  }
  
  if (selectedTagIds.value.length === 0) {
    showMessage('请至少选择一个标签', 'error');
    return;
  }
  
  isUpdating.value = true;
  
  try {
    // 先设置加载状态，再进行异步操作避免闪烁
    await nextTick() // 确保UI更新
    
    // 使用通用的markdown标准化函数
    const { autoAddParagraphBreaks, standardizeMarkdownContent } = await import('~/composables/post/useMarkdownIt.js')
    // 先自动添加段落分隔，再进行标准化处理
    const paragraphProcessedContent = autoAddParagraphBreaks(markdownContent.value)
    const processedContent = standardizeMarkdownContent(paragraphProcessedContent)
    
    console.log('编辑页面 - 原始内容:', markdownContent.value)
    console.log('编辑页面 - 段落处理后:', paragraphProcessedContent)
    console.log('编辑页面 - 最终处理内容:', processedContent)
    
    const response = await API.posts.updatePost(postId, {
      title: postTitle.value.trim(),
      content: processedContent,
      tagIds: selectedTagIds.value
    });
    
    if (response.code === 200) {
      showMessage('帖子更新成功', 'success');
      
      // 使用强制刷新跳转，避免Vue路由过渡导致的加载动画闪烁
      if (process.client) {
        window.location.href = `/post/${postId}`;
      } else {
        router.push(`/post/${postId}`);
      }
      
      // 成功时不关闭加载状态，页面跳转会自然结束
      return;
    } else {
      showMessage(response.msg || '更新失败', 'error');
      isUpdating.value = false; // 只在失败时立即关闭加载状态
    }
  } catch (error) {
    console.error('更新帖子失败:', error);
    showMessage('网络错误，请稍后重试', 'error');
    isUpdating.value = false; // 只在错误时立即关闭加载状态
  }
};

// 返回上一页 - 直接刷新返回原文
const goBack = () => {
  // 直接跳转到帖子详情页面并强制刷新
  const postUrl = `/post/${postId}`;
  if (process.client) {
    window.location.href = postUrl;
  } else {
    router.push(postUrl);
  }
};

// 页面初始化
onMounted(async () => {
  // 移除重复的用户初始化 - 已在app.vue中统一处理
  
  if (!checkAuth()) return;
  
  console.log('当前用户信息:', userStore.user);
  
  // 加载自定义区块
  if (process.client) {
    try {
      const savedBlocks = JSON.parse(localStorage.getItem('sbbs-custom-blocks') || '[]');
      customBlocks.value = savedBlocks;
    } catch (error) {
      console.error('加载自定义区块失败:', error);
    }
  }
  
  // 并行加载数据
  await Promise.all([
    loadPostData(),
    fetchTags()
  ]);
});
</script>

<style scoped>
.publish-page {
  width: 100%;
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

.input-hint {
  display: flex;
  justify-content: flex-end;
  margin-top: 0.25rem;
}

.char-count {
  font-size: 0.75rem;
  color: #6b7280;
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
  white-space: pre-wrap;
  overflow-wrap: break-word;
  word-break: normal;
  tab-size: 2;
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

/* 加载和错误状态样式 */
.loading-state,
.error-state {
  text-align: center;
  padding: 3rem 1rem;
}

.loading-state i,
.error-state i {
  font-size: 3rem;
  color: #6b7280;
  margin-bottom: 1rem;
}

.error-state h3 {
  color: #dc2626;
  margin-bottom: 0.5rem;
}

.error-state p {
  color: #6b7280;
  margin-bottom: 1.5rem;
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
</style> 