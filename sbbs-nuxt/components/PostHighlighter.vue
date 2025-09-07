<template>
  <div class="highlighter-container">
    <!-- 在首页显示新发布的帖子提示 -->
    <div v-if="isVisible" class="highlight-notification" :class="{ 'fade-out': isFading }">
      <i class="ri-check-line"></i>
      <span>帖子发布成功！<strong>{{ postTitle }}</strong></span>
      <button class="close-btn" @click="hide">
        <i class="ri-close-line"></i>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, provide } from 'vue';
import { useRoute } from '#app';

const route = useRoute();
const isVisible = ref(false);
const isFading = ref(false);
const postTitle = ref('');
const shouldHighlight = ref(false);
let hideTimer = null;

onMounted(() => {
  // 检查是否需要显示高亮通知（从发布页面跳转来）
  if (route.query.highlight === 'new' && process.client) {
    // 从localStorage获取最新发布的帖子标题
    const savedTitle = localStorage.getItem('sbbs-latest-post-title');
    const savedTime = localStorage.getItem('sbbs-latest-post-time');
    
    if (savedTitle && savedTime) {
      // 检查发布时间是否在最近5分钟内
      const publishTime = parseInt(savedTime);
      const currentTime = new Date().getTime();
      const fiveMinutes = 5 * 60 * 1000;
      
      if (currentTime - publishTime < fiveMinutes) {
        postTitle.value = savedTitle;
        shouldHighlight.value = true;
        
        // 将高亮状态设置为全局变量，确保其他组件可以访问
        window.SBBS_LATEST_POST_TITLE = savedTitle;
        window.SBBS_SHOULD_HIGHLIGHT_POST = true;
        
        // 显示通知
        setTimeout(() => {
          isVisible.value = true;
          
          // 5秒后自动隐藏通知，但保持高亮效果7秒
          hideTimer = setTimeout(hide, 5000);
          
          // 7秒后取消高亮效果
          setTimeout(() => {
            shouldHighlight.value = false;
            window.SBBS_SHOULD_HIGHLIGHT_POST = false;
          }, 7000);
        }, 500); // 延迟显示，等待页面加载
      }
    }
  }
});

// 提供高亮检查函数给其他组件
const checkHighlight = (title) => {
  if (process.client) {
    // 优先使用全局变量，因为它在页面刷新后仍然存在
    if (window.SBBS_SHOULD_HIGHLIGHT_POST && window.SBBS_LATEST_POST_TITLE === title) {
      return true;
    }
    return shouldHighlight.value && postTitle.value === title;
  }
  return false;
};

// 将高亮检查函数提供给后代组件
provide('highlightCheck', checkHighlight);

onBeforeUnmount(() => {
  // 清除定时器
  if (hideTimer) {
    clearTimeout(hideTimer);
  }
});

// 隐藏通知
const hide = () => {
  isFading.value = true;
  setTimeout(() => {
    isVisible.value = false;
  }, 300); // 等待淡出动画完成
};

// 导出函数供父组件调用
defineExpose({
  hide,
  checkHighlight
});
</script>

<style scoped>
.highlighter-container {
  position: fixed;
  top: 20px;
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  z-index: 9999;
  pointer-events: none;
}

.highlight-notification {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background-color: #ecfdf5;
  border-left: 4px solid #10b981;
  color: #065f46;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  font-size: 14px;
  font-weight: 500;
  max-width: 80%;
  pointer-events: auto;
  animation: slide-down 0.3s ease;
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.highlight-notification.fade-out {
  opacity: 0;
  transform: translateY(-20px);
}

.highlight-notification i {
  font-size: 18px;
  margin-right: 12px;
  color: #10b981;
}

.highlight-notification strong {
  font-weight: 600;
  margin: 0 4px;
}

.close-btn {
  background: none;
  border: none;
  color: #065f46;
  margin-left: 12px;
  padding: 4px;
  cursor: pointer;
  opacity: 0.7;
  transition: opacity 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  opacity: 1;
}

@keyframes slide-down {
  from {
    transform: translateY(-20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}
</style> 