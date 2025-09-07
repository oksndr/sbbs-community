<template>
  <LayoutWithSidebar>
    <!-- 阅读进度指示器 -->
    <div class="reading-progress" :style="{ width: readingProgress + '%' }"></div>
    
    <div class="post-detail-page">
      <!-- 非第一页的帖子导航提示 -->
      <div v-if="post && !showPostDetails" class="post-nav-hint">
        <div class="post-title-preview">
          <div class="title-row">
            <div class="title-meta-group">
              <h2 class="preview-title">{{ post.title }}</h2>
              <div class="post-meta-preview">
                <img :src="post.author.avatar || '/img/default-avatar.png'" :alt="post.author.username" class="author-avatar-small">
                <span class="author-name">{{ post.author.username }}</span>
                <span class="post-time-small">{{ formatDateTime(post.createdAt) }}</span>
              </div>
            </div>
            <button @click="handleBackToPost" class="back-to-post-btn">
              <i class="ri-arrow-left-line"></i>
              查看完整内容
            </button>
          </div>
        </div>
      </div>
      
      <!-- 帖子内容 - 只在第一页显示 -->
      <div v-if="post && showPostDetails" class="post-card">
        <div class="post-header">
          <!-- 标题区域 - 移到最上方 -->
          <div class="post-title-container">
            <h1 class="post-title">{{ post.title }}</h1>
            
            <!-- 标签显示 -->
            <div v-if="post.tags && post.tags.length > 0" class="post-tags">
              <a 
                v-for="(tag, index) in post.tags" 
                :key="tag.id || index" 
                :href="`/tag/${tag.id || tag}`" 
                class="post-detail-tag"
              >
                <span class="tag-icon">#</span>
                {{ tag.name || tag }}
              </a>
            </div>
            
            <!-- 临时显示假标签用于测试样式 -->
            <div v-else-if="!post.tags || post.tags.length === 0" class="post-tags">
              <a href="#" class="post-detail-tag">
                <span class="tag-icon">#</span>
                示例标签
              </a>
              <a href="#" class="post-detail-tag">
                <span class="tag-icon">#</span>
                测试标签
              </a>
            </div>
          </div>
          
          <!-- 作者信息区域 - 移到标题下方 -->
          <div class="post-author-info">
            <img :src="post.author.avatar || '/img/default-avatar.png'" :alt="post.author.username" class="post-author-avatar">
            <div>
              <a :href="`/user/${post.author.id}`" class="post-author-name">{{ post.author.username }}</a>
              <div class="post-meta">
                <div class="post-time">
                  <i class="ri-time-line"></i>
                  <span>{{ formatDateTime(post.createdAt) }}</span>
                </div>
                <div v-if="post.updatedAt && post.updatedAt !== post.createdAt" class="post-time">
                  <i class="ri-history-line"></i>
                  <span>{{ formatDateTime(post.updatedAt) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <div class="post-content">
          <!-- 使用md-editor-v3的预览组件替代复杂的自定义渲染 -->
          <MdPreview 
            :model-value="post.content || ''"
            :theme="'light'"
            :preview-theme="'default'"
            :code-theme="'atom'"
            class="custom-markdown-preview"
          />
        </div>
        
        <div class="post-actions">
          <div class="action-btn" :class="{ 'liked': isLiked }" @click="handleLike">
            <i class="ri-thumb-up-line"></i>
            <span>{{ post.likeCount || 0 }}</span>
          </div>
          
          <div class="action-btn" :class="{ 'disliked': isDisliked }" @click="handleDislike">
            <i class="ri-thumb-down-line"></i>
            <span>{{ post.dislikeCount || 0 }}</span>
          </div>
          
          <div class="action-btn" @click="scrollToComments">
            <i class="ri-message-3-line"></i>
            <span>{{ post.commentCount || 0 }}</span>
          </div>
          
          <div class="action-btn" @click="handleShare">
            <i class="ri-share-line"></i>
            <span>分享</span>
          </div>
          
          <!-- 只对帖子作者显示编辑删除按钮 -->
          <template v-if="isAuthor">
            <div 
              class="action-btn" 
              :class="{ 'loading': isNavigatingToEdit }" 
              @click="handleEdit">
              <template v-if="isNavigatingToEdit">
                <i class="ri-loader-4-line spinning"></i>
                <span>加载中...</span>
              </template>
              <template v-else>
              <i class="ri-edit-line"></i>
              <span>编辑</span>
              </template>
            </div>
            
            <div class="action-btn delete-btn" @click="handleDelete">
              <i class="ri-delete-bin-line"></i>
              <span>删除</span>
            </div>
          </template>
          
          <!-- 管理员删除按钮 -->
          <template v-if="isAdmin && !isAuthor">
            <div class="action-btn admin-delete-btn" @click="handleAdminDelete">
              <i class="ri-shield-cross-line"></i>
              <span>管理员删除</span>
            </div>
          </template>
        </div>
      </div>

      <!-- 评论区域 -->
      <div id="comments-section" class="comments-section">
        <div class="section-header">
          <h2>
            <i class="ri-message-3-line"></i>
            评论 ({{ totalComments }})
          </h2>
          
          <!-- 顶部分页导航 - 移到标题区域 -->
          <div v-if="totalPages > 1" class="header-pagination">
            <div class="modern-pagination">
              <!-- 上一页按钮 -->
              <button 
                @click="handlePreviousPage" 
                :disabled="!hasPreviousPage"
                class="pagination-btn prev-btn"
                :class="{ 'disabled': !hasPreviousPage }"
              >
                <i class="ri-arrow-left-line"></i>
                <span class="btn-text">上一页</span>
              </button>
              
              <!-- 首页按钮 -->
              <button 
                v-if="currentPage > 3"
                @click="handleGoToPage(1)"
                class="pagination-btn page-number"
                :class="{ 'active': currentPage === 1 }"
              >
                1
              </button>
              
              <!-- 首页省略号 -->
              <span v-if="currentPage > 4" class="pagination-ellipsis">
                <i class="ri-more-line"></i>
              </span>
              
              <!-- 页码按钮 -->
              <button 
                v-for="page in displayedPages" 
                :key="page"
                @click="handleGoToPage(page)"
                class="pagination-btn page-number"
                :class="{ 'active': currentPage === page }"
              >
                {{ page }}
              </button>
              
              <!-- 末页省略号 -->
              <span v-if="currentPage < totalPages - 3" class="pagination-ellipsis">
                <i class="ri-more-line"></i>
              </span>
              
              <!-- 末页按钮 -->
              <button 
                v-if="currentPage < totalPages - 2"
                @click="handleGoToPage(totalPages)"
                class="pagination-btn page-number"
                :class="{ 'active': currentPage === totalPages }"
              >
                {{ totalPages }}
              </button>
              
              <!-- 下一页按钮 -->
              <button 
                @click="handleNextPage" 
                :disabled="!hasNextPage"
                class="pagination-btn next-btn"
                :class="{ 'disabled': !hasNextPage }"
              >
                <span class="btn-text">下一页</span>
                <i class="ri-arrow-right-line"></i>
              </button>
            </div>
          </div>
        </div>

        <!-- 评论发布区 -->
        <div v-if="userStore.isInitialized && isLoggedIn" class="comment-form">
          <div class="comment-avatar">
            <img :src="userInfo.avatar || '/img/default-avatar.png'" :alt="userInfo.username" class="avatar">
          </div>
          <div 
            class="comment-input-area"
            :class="{ 'drag-over': isDragOver, 'uploading': isImageUploading }"
            @dragover="handleDragOver"
            @dragenter="handleDragEnter"
            @dragleave="handleDragLeave"
            @drop="handleDrop"
          >


            
            <div
              ref="commentEditable"
              class="comment-editable"
              contenteditable="true"
              :placeholder="isCommentSubmitting ? '正在发布...' : (isDragOver ? '放开鼠标上传图片...' : '发表你的评论...')"
              @input="handleCommentInput"
              @keydown="handleCommentKeydownExtended"
              @paste="handleCommentPaste"
              @focus="updateActiveInputType"
              @click="activeInputType = 'main'"
            ></div>
            
            <!-- 拖拽上传提示覆盖层 -->
            <div v-if="isDragOver" class="drag-overlay">
              <div class="drag-icon">
                <i class="ri-upload-cloud-line"></i>
              </div>
              <p>放开鼠标上传图片</p>
              <small>支持 JPG、PNG、GIF 格式，最大 5MB</small>
            </div>
            
            <!-- 上传进度提示 -->
            <div v-if="isImageUploading" class="upload-progress-overlay">
              <div class="upload-icon">
                <i class="ri-loader-4-line spinning"></i>
              </div>
              <p>图片上传中...</p>
              <small>上传完成后将自动插入到评论中</small>
            </div>
            
            <div class="comment-tips enhanced-toolbar">
              <!-- 统一工具栏 - 提示文本、工具按钮、发布按钮在同一排 -->
              <div class="unified-toolbar">
                <span class="tips-text">
                  <i class="ri-information-line"></i>
                  支持@提及、拖拽上传图片，Ctrl+Enter发布
                </span>
                
                <!-- 工具按钮组 -->
                <div class="toolbar-section">
                  <!-- Markdown快捷按钮 -->
                  <div class="toolbar-group">
                    <button class="toolbar-btn" title="粗体" @click="insertText('**', '**')">
                      <i class="ri-bold"></i>
                    </button>
                    <button class="toolbar-btn" title="斜体" @click="insertText('*', '*')">
                      <i class="ri-italic"></i>
                    </button>
                    <button class="toolbar-btn" title="代码" @click="insertText('`', '`')">
                      <i class="ri-code-line"></i>
                    </button>
                    <button class="toolbar-btn" title="删除线" @click="insertText('~~', '~~')">
                      <i class="ri-strikethrough"></i>
                    </button>
                  </div>
                  
                  <div class="toolbar-divider"></div>
                  
                  <!-- 链接和引用 -->
                  <div class="toolbar-group">
                    <button class="toolbar-btn" title="链接" @click="insertText('[', '](url)')">
                      <i class="ri-link"></i>
                    </button>
                    <button class="toolbar-btn" title="引用" @click="insertText('> ', '')">
                      <i class="ri-double-quotes-l"></i>
                    </button>
                    <button class="toolbar-btn" title="无序列表" @click="insertText('- ', '')">
                      <i class="ri-list-unordered"></i>
                    </button>
                    <button class="toolbar-btn" title="有序列表" @click="insertText('1. ', '')">
                      <i class="ri-list-ordered"></i>
                    </button>
                  </div>
                  
                  <div class="toolbar-divider"></div>
                  
                  <!-- 表情选择器 -->
                  <div class="toolbar-group">
                    <button 
                      class="toolbar-btn emoji-btn" 
                      title="表情" 
                      @click="showEmojiPicker = !showEmojiPicker"
                      :class="{ 'active': showEmojiPicker }"
                    >
                      <i class="ri-emotion-line"></i>
                    </button>
                  </div>
                </div>
                
                <button 
                  @click="submitComment" 
                  :disabled="isCommentSubmitting || !commentContent.trim() || isImageUploading"
                  class="publish-comment-button"
                >
                  <span v-if="isCommentSubmitting">⏳ 发布中...</span>
                  <span v-else-if="isImageUploading">🖼️ 上传中...</span>
                  <span v-else>📝 发布</span>
                </button>
              </div>
              
              <!-- 表情选择器面板 -->
              <div v-if="showEmojiPicker" class="emoji-picker">
                <div class="emoji-grid">
                  <button 
                    v-for="emoji in commonEmojis" 
                    :key="emoji"
                    class="emoji-item"
                    @click="addEmoji(emoji)"
                    :title="emoji"
                  >
                    {{ emoji }}
                  </button>
                </div>
              </div>
            </div>
            
            <!-- 主评论用户搜索下拉框 -->
            <div v-if="showMainUserSearch" class="user-search-dropdown" :class="{ visible: showMainUserSearch }">
              <div v-if="isSearchingUsers" class="user-search-loading">
                <i class="ri-loader-4-line rotating"></i>
                搜索中...
              </div>
              <div v-else-if="searchUsers.length === 0" class="user-search-empty">
                没有找到用户
              </div>
              <div 
                v-else 
                v-for="(user, index) in searchUsers" 
                :key="user.id"
                :class="{ active: index === userSearchIndex }"
                class="user-search-item"
                @click="selectUser(user)"
                @mouseenter="userSearchIndex = index"
              >
                <img 
                  :src="user.avatar || '/img/default-avatar.png'" 
                  :alt="user.username"
                  class="user-search-avatar"
                >
                <div class="user-info">
                  <div class="user-search-name">{{ user.username }}</div>
                  <div v-if="user.nickname" class="user-nickname">{{ user.nickname }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 未初始化状态 -->
        <div v-else-if="!userStore.isInitialized" class="initializing-comments" style="padding: 1.5rem; text-align: center; color: #666;">
          <i class="ri-loader-4-line rotating"></i>
          初始化中...
        </div>

        <!-- 登录提示 -->
        <div v-else class="login-to-comment">
          <p>
            <button @click="forceOpenLoginModal" class="login-link">登录</button> 后查看评论
          </p>
        </div>

        <!-- 评论列表 -->
        <div v-if="userStore.isInitialized && isLoggedIn" class="comments-wrapper">
          <div v-if="isCommentsLoading" class="comments-loading">
            <i class="ri-loader-4-line rotating"></i>
            正在加载评论...
          </div>

          <div v-else-if="processedComments.length === 0" class="no-comments">
            <div class="empty-icon">
              <i class="ri-message-3-line"></i>
            </div>
            <p>还没有评论，来发表第一条评论吧！</p>
          </div>

          <div v-else class="comments-list">
            <div 
              v-for="comment in processedComments" 
              :key="`comment-${comment.id}`" 
              class="comment-item"
              :id="`comment-${comment.id}`"
              :data-comment-id="comment.id"
            >
              <!-- 评论内容 -->
              <div class="comment-author">
                <div class="comment-avatar">
                  <img 
                    :src="comment.author.avatar || '/img/default-avatar.png'" 
                    :alt="comment.author.username"
                    class="avatar"
                  >
                </div>
                <div class="comment-info">
                  <div class="comment-name-time">
                    <a 
                      :href="`/user/${comment.author.id}`" 
                      class="comment-name"
                    >
                      {{ comment.author.username }}
                    </a>
                    <span class="comment-time">{{ formatDateTime(comment.createdAt) }}</span>
                  </div>
                  
                  <div class="comment-text" v-html="comment.processedContent"></div>
                  
                  <!-- 评论操作 -->
                  <div class="comment-actions">
                    <button 
                      @click="handleLikeComment(comment.id)"
                      :class="{ 'liked': comment.isLiked }"
                      class="comment-action-btn like-btn"
                      :disabled="!isLoggedIn"
                    >
                      <i class="ri-thumb-up-line"></i>
                      <span>{{ comment.likeCount || 0 }}</span>
                    </button>
                    
                    <button 
                      @click="handleDislikeComment(comment.id)"
                      :class="{ 'disliked': comment.isDisliked }"
                      class="comment-action-btn dislike-btn"
                      :disabled="!isLoggedIn"
                    >
                      <i class="ri-thumb-down-line"></i>
                      <span>{{ comment.dislikeCount || 0 }}</span>
                    </button>
                    
                    <button 
                      @click="toggleReply(comment.id)"
                      class="comment-action-btn reply-btn"
                      :disabled="!isLoggedIn"
                    >
                      <i class="ri-reply-line"></i>
                      回复
                    </button>
                    
                    <button 
                      v-if="comment.replyCount > 0"
                      @click="loadReplies(comment.id)"
                      class="comment-action-btn view-all-replies-btn"
                      :disabled="comment.loadingReplies"
                    >
                      <i v-if="comment.loadingReplies" class="ri-loader-4-line rotating"></i>
                      <i v-else-if="comment.showReplies" class="ri-arrow-up-line"></i>
                      <i v-else class="ri-arrow-down-line"></i>
                      {{ comment.showReplies ? '折叠' : '展开' }} {{ comment.replyCount }} 条回复
                    </button>
                  </div>
                </div>
              </div>

              <!-- 回复输入框 - 添加@功能支持 -->
              <div v-if="activeReplyId === comment.id" class="reply-form">
                <div class="comment-input-area reply-comment-input-area">
                  
                  <div
                    class="comment-editable"
                    contenteditable="true"
                    :placeholder="isReplySubmitting ? '正在发布...' : '回复 @' + comment.author.username + '...'"
                    @input="handleCommentInput"
                    @keydown="handleCommentKeydownExtended"
                    @paste="handleCommentPaste"
                    @focus="updateActiveInputType"
                    @click="activeInputType = 'reply'"
                  ></div>
                  
                  <div class="comment-tips reply-comment-tips">
                    <span>
                      <i class="ri-information-line"></i>
                      支持 @提及用户，Ctrl+Enter 快速发布
                    </span>
                    <div class="reply-actions">
                      <button 
                        class="emoji-btn-reply" 
                        title="表情" 
                        @click="showReplyEmojiPicker = !showReplyEmojiPicker"
                        :class="{ 'active': showReplyEmojiPicker }"
                      >
                        <i class="ri-emotion-line"></i>
                      </button>
                      <button @click="cancelReply" class="cancel-btn">取消</button>
                      <button 
                        @click="submitReply(comment.id)" 
                        :disabled="isReplySubmitting || !replyContent.trim()"
                        class="submit-btn"
                      >
                        <i v-if="isReplySubmitting" class="ri-loader-4-line rotating"></i>
                        <i v-else class="ri-send-plane-line"></i>
                        {{ isReplySubmitting ? '发布中...' : '发布' }}
                      </button>
                    </div>
                  </div>
                  
                  <!-- 一级评论回复用户搜索下拉框 -->
                  <div v-if="showReplyUserSearch" class="user-search-dropdown" :class="{ visible: showReplyUserSearch }">
                    <div v-if="isSearchingUsers" class="user-search-loading">
                      <i class="ri-loader-4-line rotating"></i>
                      搜索中...
                    </div>
                    <div v-else-if="searchUsers.length === 0" class="user-search-empty">
                      没有找到用户
                    </div>
                    <div 
                      v-else 
                      v-for="(user, index) in searchUsers" 
                      :key="user.id"
                      :class="{ active: index === userSearchIndex }"
                      class="user-search-item"
                      @click="selectUserExtended(user)"
                      @mouseenter="userSearchIndex = index"
                    >
                      <img 
                        :src="user.avatar || '/img/default-avatar.png'" 
                        :alt="user.username"
                        class="user-search-avatar"
                      >
                      <div class="user-info">
                        <div class="user-search-name">{{ user.username }}</div>
                        <div v-if="user.nickname" class="user-nickname">{{ user.nickname }}</div>
                      </div>
                    </div>
                  </div>
                  
                  <!-- 一级回复表情选择器面板 -->
                  <div v-if="showReplyEmojiPicker" class="emoji-picker reply-emoji-picker">
                    <div class="emoji-grid">
                      <button 
                        v-for="emoji in commonEmojis" 
                        :key="emoji"
                        class="emoji-item"
                        @click="addReplyEmoji(emoji)"
                        :title="emoji"
                      >
                        {{ emoji }}
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 回复列表 -->
              <div v-if="comment.showReplies && comment.replies.length > 0" class="replies-list">
                <div 
                  v-for="reply in comment.replies" 
                  :key="reply.id" 
                  class="reply-item"
                  :id="`reply-${reply.id}`"
                >
                  <div class="reply-content">
                    <div class="reply-header">
                      <img 
                        :src="reply.author.avatar || '/img/default-avatar.png'" 
                        :alt="reply.author.username"
                        class="reply-avatar"
                      >
                      <div class="reply-meta">
                        <a 
                          :href="`/user/${reply.author.id}`" 
                          class="reply-author"
                        >
                          {{ reply.author.username }}
                        </a>
                        <span v-if="reply.replyToUsername" class="reply-to">
                          回复 <a :href="`/user/${reply.replyToId}`">@{{ reply.replyToUsername }}</a>
                        </span>
                        <span class="reply-time">{{ formatDateTime(reply.createdAt) }}</span>
                      </div>
                    </div>
                    
                    <div class="reply-text" v-html="highlightMentions(reply.content, true)"></div>
                    
                    <!-- 回复操作 -->
                    <div class="reply-actions">
                      <button 
                        @click="handleLikeComment(reply.id)"
                        :class="{ 'liked': reply.isLiked }"
                        class="action-btn like-btn"
                        :disabled="!isLoggedIn"
                      >
                        <i class="ri-thumb-up-line"></i>
                        <span>{{ reply.likeCount || 0 }}</span>
                      </button>
                      
                      <button 
                        @click="handleDislikeComment(reply.id)"
                        :class="{ 'disliked': reply.isDisliked }"
                        class="action-btn dislike-btn"
                        :disabled="!isLoggedIn"
                      >
                        <i class="ri-thumb-down-line"></i>
                        <span>{{ reply.dislikeCount || 0 }}</span>
                      </button>
                      
                      <button 
                        @click="toggleReplyToReply(comment.id, reply.id, reply.author.username)"
                        class="action-btn reply-btn"
                        :disabled="!isLoggedIn"
                      >
                        <i class="ri-reply-line"></i>
                        回复
                      </button>
                    </div>
                  </div>

                  <!-- 回复的回复输入框 -->
                  <div v-if="activeReplyToReplyId === reply.id" class="reply-to-reply-form">
                    <!-- 简化的输入区域结构 -->
                    <div class="comment-input-area reply-comment-input-area">
                      
                      <div
                        ref="replyTextarea"
                        class="comment-editable"
                        contenteditable="true"
                        :placeholder="isReplySubmitting ? '正在发布...' : '回复 @' + reply.author.username + '...'"
                        @input="handleCommentInput"
                        @keydown="handleCommentKeydownExtended"
                        @paste="handleCommentPaste"
                        @focus="updateActiveInputType"
                        @click="activeInputType = 'secondary'"
                      ></div>
                      
                      <div class="comment-tips reply-comment-tips">
                        <span>
                          <i class="ri-information-line"></i>
                          支持 @提及用户，Ctrl+Enter 快速发布
                        </span>
                        <div class="reply-actions">
                          <button 
                            class="emoji-btn-reply" 
                            title="表情" 
                            @click="showSecondaryEmojiPicker = !showSecondaryEmojiPicker"
                            :class="{ 'active': showSecondaryEmojiPicker }"
                          >
                            <i class="ri-emotion-line"></i>
                          </button>
                          <button @click="cancelReplyToReply" class="cancel-btn">取消</button>
                          <button 
                            @click="submitReply(comment.id)" 
                            :disabled="isReplySubmitting || !replyContent.trim()"
                            class="submit-btn"
                          >
                            <i v-if="isReplySubmitting" class="ri-loader-4-line rotating"></i>
                            <i v-else class="ri-send-plane-line"></i>
                            {{ isReplySubmitting ? '发布中...' : '发布' }}
                          </button>
                        </div>
                      </div>
                      
                      <!-- 二级评论回复用户搜索下拉框 -->
                      <div v-if="showSecondaryUserSearch" class="user-search-dropdown" :class="{ visible: showSecondaryUserSearch }">
                        <div v-if="isSearchingUsers" class="user-search-loading">
                          <i class="ri-loader-4-line rotating"></i>
                          搜索中...
                        </div>
                        <div v-else-if="searchUsers.length === 0" class="user-search-empty">
                          没有找到用户
                        </div>
                        <div 
                          v-else 
                          v-for="(user, index) in searchUsers" 
                          :key="user.id"
                          :class="{ active: index === userSearchIndex }"
                          class="user-search-item"
                          @click="selectUserExtended(user)"
                          @mouseenter="userSearchIndex = index"
                        >
                          <img 
                            :src="user.avatar || '/img/default-avatar.png'" 
                            :alt="user.username"
                            class="user-search-avatar"
                          >
                          <div class="user-info">
                            <div class="user-search-name">{{ user.username }}</div>
                            <div v-if="user.nickname" class="user-nickname">{{ user.nickname }}</div>
                          </div>
                        </div>
                      </div>
                      
                      <!-- 二级回复表情选择器面板 -->
                      <div v-if="showSecondaryEmojiPicker" class="emoji-picker reply-emoji-picker">
                        <div class="emoji-grid">
                          <button 
                            v-for="emoji in commonEmojis" 
                            :key="emoji"
                            class="emoji-item"
                            @click="addSecondaryEmoji(emoji)"
                            :title="emoji"
                          >
                            {{ emoji }}
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部分页导航 -->
        <div v-if="totalPages > 1" class="pagination-container bottom-pagination">
          <div class="modern-pagination">
            <!-- 上一页按钮 -->
            <button 
              @click="handlePreviousPage" 
              :disabled="!hasPreviousPage"
              class="pagination-btn prev-btn"
              :class="{ 'disabled': !hasPreviousPage }"
            >
              <i class="ri-arrow-left-line"></i>
              <span class="btn-text">上一页</span>
            </button>
            
            <!-- 首页按钮 -->
            <button 
              v-if="currentPage > 3"
              @click="handleGoToPage(1)"
              class="pagination-btn page-number"
              :class="{ 'active': currentPage === 1 }"
            >
              1
            </button>
            
            <!-- 首页省略号 -->
            <span v-if="currentPage > 4" class="pagination-ellipsis">
              <i class="ri-more-line"></i>
            </span>
            
            <!-- 页码按钮 -->
            <button 
              v-for="page in displayedPages" 
              :key="page"
              @click="handleGoToPage(page)"
              class="pagination-btn page-number"
              :class="{ 'active': currentPage === page }"
            >
              {{ page }}
            </button>
            
            <!-- 末页省略号 -->
            <span v-if="currentPage < totalPages - 3" class="pagination-ellipsis">
              <i class="ri-more-line"></i>
            </span>
            
            <!-- 末页按钮 -->
            <button 
              v-if="currentPage < totalPages - 2"
              @click="handleGoToPage(totalPages)"
              class="pagination-btn page-number"
              :class="{ 'active': currentPage === totalPages }"
            >
              {{ totalPages }}
            </button>
            
            <!-- 下一页按钮 -->
            <button 
              @click="handleNextPage" 
              :disabled="!hasNextPage"
              class="pagination-btn next-btn"
              :class="{ 'disabled': !hasNextPage }"
            >
              <span class="btn-text">下一页</span>
              <i class="ri-arrow-right-line"></i>
            </button>
          </div>
        </div>
      </div>

      <!-- 通知组件 -->
      <div v-if="showNotification" class="notification" :class="notificationType">
        <i :class="notificationIcon"></i>
        <span>{{ notificationMessage }}</span>
        <button @click="showNotification = false" class="close-btn">
          <i class="ri-close-line"></i>
        </button>
      </div>

      <!-- 图片预览模态框 -->
      <div v-if="previewImageUrl" class="image-preview-modal show" @click="closeImagePreview">
        <div class="image-preview-content" @click.stop>
          <button class="image-preview-close" @click="closeImagePreview">
            <i class="ri-close-line"></i>
          </button>
          <img :src="previewImageUrl" class="image-preview-img" alt="预览图片">
        </div>
      </div>
    </div>
    
    <!-- 通知组件 -->
    <div v-if="showNotification" :class="['notification', notificationType]">
      <div class="notification-content">
        <i :class="notificationIcon"></i>
        {{ notificationMessage }}
      </div>
    </div>
  </LayoutWithSidebar>

  <!-- 图片全屏预览模态框 -->
  <div v-if="imageModalVisible" class="image-modal" @click="closeImageModal">
    <div class="image-modal-content" @click.stop>
      <img :src="imageModalSrc" class="modal-image" alt="全屏预览" />
      <button class="image-modal-close" @click="closeImageModal">
        <i class="ri-close-line"></i>
      </button>
      <div class="image-modal-controls">
        <button class="image-control-btn" @click.stop="downloadImage">
          <i class="ri-download-line"></i>
          <span>下载</span>
        </button>
        <button class="image-control-btn" @click.stop="copyImageUrl">
          <i class="ri-link"></i>
          <span>复制链接</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { 
  ref, watch, nextTick, onMounted, onUnmounted,
  computed, useAsyncData, useNuxtApp,
  useRouter, useRoute
} from '#imports'
import LayoutWithSidebar from '~/components/LayoutWithSidebar.vue'
import { useUserStore } from '~/stores/user'
import { usePostStore } from '~/stores/post'
import { API } from '~/utils/api'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'

// 导入CSS文件
import '~/assets/css/post-detail.css'
import '~/assets/css/comment-section.css'
import '~/assets/css/reply.css'
import '~/assets/css/markdown.css'
import '~/assets/css/utilities.css'

// 导入组合式函数
import { usePostDetail } from '~/composables/post/usePostDetail'
import { useComments } from '~/composables/post/useComments'
import { useUserMention } from '~/composables/post/useUserMention'
// import { useReplyMention } from '~/composables/post/useReplyMention' // 不再使用
import { useImagePreview } from '~/composables/post/useImagePreview'
import { useFormatters } from '~/composables/post/useFormatters'

// 登录弹窗功能
const { forceOpenLoginModal } = useLoginModal()

const route = useRoute()
const router = useRouter()
const postStore = usePostStore()
const userStore = useUserStore()

// Reactive parameters for data fetching
const postIdForAsyncData = computed(() => route.params.id)
const currentPageForAsyncData = computed(() => parseInt(route.query.page || '1'))

// 添加开发环境检测
const isDev = computed(() => process.dev || process.env.NODE_ENV === 'development')

// 帮助函数: 从客户端获取cookie值
const getCookieValue = (name) => {
  if (process.client) {
    const match = document.cookie.match(new RegExp('(^| )' + name + '=([^;]+)'))
    return match ? match[2] : null
  }
  return null
}

// 帮助函数: 从cookie字符串中获取值 (用于SSR)
const getCookieValueFromString = (cookieString, name) => {
  const match = cookieString.match(new RegExp('(^| )' + name + '=([^;]+)'))
  return match ? match[2] : null
}

// 使用帖子详情逻辑
const { 
  postId, post, isLoading, error, isLoggedIn, userInfo, isAuthor, 
  isLiked, isDisliked, renderedContent, fetchPost, 
  handleLike, handleDislike, handleShare, handleEdit, handleDelete, scrollToComments 
} = usePostDetail()

// 管理员权限检查
const isAdmin = computed(() => {
  return isLoggedIn.value && userInfo.value && userInfo.value.role === '管理员'
})

// 通知功能 - 需要在useComments之前定义
const showNotification = ref(false)
const notificationMessage = ref('')
const notificationType = ref('success') // 'success', 'error', 'warning'

// 编辑按钮加载状态
const isNavigatingToEdit = ref(false)

// 监听全局编辑状态
if (process.client) {
  const checkEditingState = () => {
    isNavigatingToEdit.value = !!window._isNavigatingToEdit
  }
  
  // 初始检查
  checkEditingState()
  
  // 定期检查状态变化
  const intervalId = setInterval(checkEditingState, 100)
  
  // 清理定时器的函数
  const cleanup = () => {
    clearInterval(intervalId)
  }
  
  // 当页面销毁时清理
  if (typeof window !== 'undefined') {
    window.addEventListener('beforeunload', cleanup)
  }
}

const notificationIcon = computed(() => {
  switch (notificationType.value) {
    case 'success': return 'ri-check-line'
    case 'error': return 'ri-close-line' 
    case 'warning': return 'ri-error-warning-line'
    default: return 'ri-information-line'
  }
})

// 显示通知的函数
const showNotify = (message, type = 'success') => {
  notificationMessage.value = message
  notificationType.value = type
  showNotification.value = true
  
  // 3秒后自动隐藏
  setTimeout(() => {
    showNotification.value = false
  }, 3000)
}

// 管理员删除帖子
const handleAdminDelete = async () => {
  if (!isAdmin.value || !post.value) return
  
  const confirmMessage = `确定要作为管理员删除这篇帖子吗？\n\n帖子标题：${post.value.title}\n作者：${post.value.author?.username}\n\n删除后无法恢复。`
  
  if (confirm(confirmMessage)) {
    try {
      // 获取token
      const userStore = useUserStore()
      const token = userStore.token
      
      if (!token) {
        showNotify('请先登录', 'error')
        return
      }
      
      // 调用删除API
      const response = await fetch(`${API_BASE_URL}/v2/post/${post.value.id}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      })
      
      const result = await response.json()
      
      if (result.code === 200) {
        showNotify('帖子删除成功', 'success')
        
        // 延迟跳转，让用户看到成功提示
        setTimeout(() => {
          if (process.client && window.navigateWithPageTransition) {
            window.navigateWithPageTransition('/')
          } else {
            navigateTo('/')
          }
        }, 1500)
      } else {
        showNotify(result.msg || '删除失败', 'error')
      }
    } catch (error) {
      console.error('管理员删除帖子失败:', error)
      showNotify('删除失败，请稍后重试', 'error')
    }
  }
}

// 使用图片预览功能
const { previewImageUrl, openImagePreview, closeImagePreview, bindImageClickEvents } = useImagePreview()

// 图片模态框功能
const imageModalVisible = ref(false)
const imageModalSrc = ref('')

const openImageModal = (src) => {
  imageModalSrc.value = src
  imageModalVisible.value = true
  // 阻止body滚动
  if (process.client) {
    document.body.style.overflow = 'hidden'
  }
}

const closeImageModal = () => {
  imageModalVisible.value = false
  imageModalSrc.value = ''
  // 恢复body滚动
  if (process.client) {
    document.body.style.overflow = ''
  }
}

const downloadImage = async () => {
  if (!imageModalSrc.value) return
  
  // 防止重复通知
  let notificationShown = false
  
  const showNotification = (message, type) => {
    if (!notificationShown) {
      notificationShown = true
      showNotify(message, type)
    }
  }
  
  try {
    const response = await fetch(imageModalSrc.value)
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    
    const link = document.createElement('a')
    link.href = url
    link.download = `image_${Date.now()}.${blob.type.split('/')[1] || 'jpg'}`
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    
    window.URL.revokeObjectURL(url)
    showNotification('图片下载成功', 'success')
  } catch (error) {
    console.error('下载图片失败:', error)
    showNotification('下载失败，请检查网络连接', 'error')
  }
}

const copyImageUrl = async () => {
  if (!imageModalSrc.value) return
  
  // 防止重复通知的标志
  let notificationShown = false
  
  const showSuccessNotification = () => {
    if (!notificationShown) {
      notificationShown = true
      // 临时禁用全局toast系统，只使用我们的通知
      const originalToast = window.$toast
      window.$toast = null
      
      showNotify('图片链接已复制', 'success')
      
      // 1秒后恢复全局toast系统
      setTimeout(() => {
        window.$toast = originalToast
      }, 1000)
    }
  }
  
  const showErrorNotification = (message = '复制失败，请手动复制') => {
    if (!notificationShown) {
      notificationShown = true
      // 临时禁用全局toast系统
      const originalToast = window.$toast
      window.$toast = null
      
      showNotify(message, 'error')
      
      setTimeout(() => {
        window.$toast = originalToast
      }, 1000)
    }
  }
  
  try {
    // 优先使用现代的 clipboard API
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(imageModalSrc.value)
      showSuccessNotification()
      return
    }
    
    // 如果不支持现代API，使用备用方案
    const textArea = document.createElement('textarea')
    textArea.value = imageModalSrc.value
    textArea.style.position = 'fixed'
    textArea.style.left = '-999999px'
    textArea.style.top = '-999999px'
    document.body.appendChild(textArea)
    textArea.focus()
    textArea.select()
    
    try {
      const successful = document.execCommand('copy')
      if (successful) {
        showSuccessNotification()
      } else {
        showErrorNotification()
      }
    } catch (err) {
      console.error('复制失败:', err)
      showErrorNotification()
    } finally {
      document.body.removeChild(textArea)
    }
  } catch (error) {
    console.error('复制操作失败:', error)
    showErrorNotification()
  }
}

// 将openImageModal函数添加到全局，供innerHTML中的onclick使用
if (process.client) {
  window.openImageModal = openImageModal
}

// 为每个评论创建处理后的内容计算属性
const processedComments = computed(() => {
  const currentComments = comments.value
  if (!currentComments || currentComments.length === 0) return []
  
  return currentComments.map(comment => ({
    ...comment,
    processedContent: highlightMentions(comment.content || '', false)
  }))
})



// 监听帖子内容变化，重新绑定图片点击事件
watch(renderedContent, () => {
  if (renderedContent.value) {
    nextTick(() => {
      bindImageClickEvents()
    })
  }
}, { immediate: true })

// 使用评论相关逻辑
const useCommentsResult = useComments(postIdForAsyncData) // Pass reactive postId
const { 
  comments: originalComments, // 重命名以避免冲突 
  localComments, commentContent, isCommentSubmitting, isCommentsLoading, 
  activeReplyId, replyContent, isReplySubmitting, 
  activeReplyToReplyId, replyToUsername, replyToId, replyTextarea, commentEditable,
  currentPage, totalPages, hasNextPage, hasPreviousPage, totalComments, pageSize,
  isFirstPage, showPostDetails,
  fetchComments,
  submitComment, toggleReply, cancelReply, 
  submitReply, toggleReplyToReply, cancelReplyToReply, handleLikeComment, 
  handleDislikeComment, loadReplies, highlightMentions,
  isDragOver, isImageUploading, handleDragOver, handleDragEnter, handleDragLeave, 
  handleDrop, uploadImageToComment
} = useCommentsResult

// 创建直接引用commentsData的计算属性，确保页面始终能访问到最新的评论数据
const comments = computed(() => {
  // 如果客户端获取的本地评论不为空，优先使用
  if (localComments.value && localComments.value.length > 0) {
    return localComments.value
  }
  
  // 否则使用SSR获取的数据
  if (commentsData.value?.comments && commentsData.value.comments.length > 0) {
    return commentsData.value.comments
  }
  
  // 最后使用本地评论（包括空数组）
  return localComments.value || []
})

// --- SSR Data Fetching ---
// Fetch Post Data with smart caching
// 在全局作用域定义cookie引用
const authCookie = useCookie('Authorization', { default: () => null })
const tokenCookie = useCookie('token', { default: () => null })

// 根据用户登录状态决定缓存策略
const getPostCacheKey = () => {
  // 使用一致的登录状态检测
  const isLoggedIn = !!(authCookie.value || tokenCookie.value)
  const postId = postIdForAsyncData.value
  
  // 为不同状态的用户使用不同的缓存key
  if (isLoggedIn) {
    return `post-detail-auth-${postId}`
  } else {
    return `post-detail-anon-${postId}`
  }
}

const { pending: postDataPending, error: postDataError } = await useAsyncData(
  getPostCacheKey(),
  async () => {
    if (postIdForAsyncData.value) {
      // 检查用户登录状态
      const isLoggedIn = !!(authCookie.value || tokenCookie.value)
      
      console.log('获取帖子详情，用户状态:', isLoggedIn ? '已登录' : '未登录')
      
      await fetchPost(postIdForAsyncData.value) // Explicitly pass ID, or ensure composable uses it
    }
    // The composable's `post`, `isLoading`, `error` refs should be hydrated.
    // Return a minimal object if useAsyncData needs a return for hydration state,
    // otherwise, rely on composable's state being directly usable.
    return { 
      id: post.value?.id, 
      title: post.value?.title,
      success: true
    }
  },
  { 
    watch: [postIdForAsyncData], // Re-fetch when postId changes
    // 为所有用户启用客户端缓存，避免水合不一致
    server: true,
    client: true,
    // 但为未登录用户启用更长的缓存时间
    default: () => ({ id: null, title: null, success: false })
  }
)
// Sync loading/error state if composable's state isn't directly managed by useAsyncData's pending/error
// For example, if fetchPost doesn't set isLoading immediately in a way useAsyncData understands for SSR pending.
// This might require more complex synchronization or refactoring composable.
// For now, we assume fetchPost updates isLoading/error correctly.


// Fetch Comments Data - 只为已登录用户获取
const { pending: commentsDataPending, error: commentsDataError, data: commentsData } = await useAsyncData(
  `post-comments-${postIdForAsyncData.value}-p${currentPageForAsyncData.value}`,
  async () => {
    // 检查登录状态，未登录用户直接返回空数据
    const isLoggedIn = !!(authCookie.value || tokenCookie.value)
      
    if (!isLoggedIn || !postIdForAsyncData.value) {
      console.log('用户未登录或无效帖子ID，跳过评论数据获取')
      return { comments: [], pagination: { current: 1, total: 0, pages: 1 } }
    }
    
    try {
      // 使用API模块获取评论
      const data = await API.comments.getComments(postIdForAsyncData.value, currentPageForAsyncData.value)
      
      if (data.code === 200 && data.data) {
        // 将API返回的评论数据格式化
        const formattedComments = data.data.comments.map(comment => {
          // 确保数值类型正确
          const likeCount = parseInt(comment.likeCount || '0')
          const dislikeCount = parseInt(comment.dislikeCount || '0')
          const replyCount = parseInt(comment.replyCount || '0')
          
          return {
            ...comment,
            author: {
              id: comment.userId,
              username: comment.username,
              avatar: comment.avatar
            },
            createdAt: comment.created,
            updatedAt: comment.updated,
            likeCount,
            dislikeCount,
            replyCount,
            // 确保点赞状态正确
            isLiked: !!comment.isLiked,
            isDisliked: !!comment.isDisliked,
            showReplies: false,
            loadingReplies: false,
            replies: []
          }
        })
        
        return {
          comments: formattedComments,
          pagination: {
            current: data.data.current,
            total: data.data.total,
            pages: data.data.pages,
            hasNext: data.data.hasNext,
            hasPrevious: data.data.hasPrevious
          }
        }
      }
      
      // 处理401错误（未登录）或其他错误
      return { comments: [], pagination: { current: 1, total: 0, pages: 1 } }
    } catch (error) {
      console.error('评论获取失败:', error)
      return { comments: [], pagination: { current: 1, total: 0, pages: 1 } }
    }
  },
  { 
    watch: [postIdForAsyncData, currentPageForAsyncData],
    default: () => ({ comments: [], pagination: { current: 1, total: 0, pages: 1 } }),
    // 为所有用户启用客户端缓存，避免水合不一致
    server: true,
    client: true
  }
)

// 同步处理useAsyncData数据到组件状态
watch(() => commentsData.value, (newData) => {
  if (newData) {
    // 同步评论数据
    if (newData.comments) {
      // 确保评论数据也经过Markdown处理
      localComments.value = newData.comments.map(comment => ({
        ...comment,
        // 不在这里处理内容，让processedComments处理
      }))
    }
    
    // 同步分页数据
    if (newData.pagination) {
      const { current, total, pages, hasNext, hasPrevious } = newData.pagination
      
      currentPage.value = current
      totalComments.value = total
      totalPages.value = pages
      hasNextPage.value = hasNext
      hasPreviousPage.value = hasPrevious
    }
    
    // 更新加载状态
    isCommentsLoading.value = false
  }
}, { immediate: true })

// 确保加载状态正确反映
watch(() => commentsDataPending.value, (isPending) => {
  isCommentsLoading.value = isPending
})

// 使用用户提及相关逻辑
const { 
  showUserSearch, searchUsers, userSearchIndex, isSearchingUsers, 
  handleContentEditableInput, handleCommentKeydown, handleBackspace, 
  handleReplyBackspace, selectUser 
} = useUserMention(commentContent, commentEditable)

// 追踪当前活动的输入框类型
const activeInputType = ref('main') // 'main', 'reply', 'secondary'

// 简单的文本插入函数 - 只用于主评论框
const insertText = (prefix, suffix) => {
  console.log('🔧 insertText 被调用:', { prefix, suffix })
  
  const textarea = commentEditable.value
  if (!textarea) {
    console.log('🔧 找不到主评论框')
    return
  }
  
  // 确保输入框获得焦点
  textarea.focus()
  
  // 保存选择状态
  const selection = window.getSelection()
  if (selection.rangeCount === 0) {
    console.log('🔧 没有选择，在末尾插入')
    // 如果没有选择，在末尾插入
    const text = textarea.textContent || ''
    const newText = text + prefix + suffix
    textarea.textContent = newText
    
    // 设置光标在中间
    const range = document.createRange()
    const textNode = textarea.firstChild || textarea.appendChild(document.createTextNode(''))
    const cursorPos = text.length + prefix.length
    range.setStart(textNode, cursorPos)
    range.setEnd(textNode, cursorPos)
    selection.removeAllRanges()
    selection.addRange(range)
    
    // 更新内容变量
    commentContent.value = newText
    return
  }
  
  const range = selection.getRangeAt(0)
  
  // 检查选择是否在评论框内
  if (!textarea.contains(range.commonAncestorContainer)) {
    console.log('🔧 选择不在评论框内')
    return
  }
  
  // 获取选中的文本
  const selectedText = range.toString()
  console.log('🔧 选中的文本:', selectedText)
  
  // 使用execCommand插入文本
  const textToInsert = prefix + selectedText + suffix
  
  // 如果有选中文本，先删除
  if (selectedText.length > 0) {
    document.execCommand('delete', false)
  }
  
  // 插入新文本
  document.execCommand('insertText', false, textToInsert)
  
  // 如果没有选中文本，调整光标位置到中间
  if (selectedText.length === 0) {
    // 获取当前光标位置
    const newSelection = window.getSelection()
    if (newSelection.rangeCount > 0) {
      const newRange = newSelection.getRangeAt(0)
      if (newRange.startContainer.nodeType === Node.TEXT_NODE) {
        const textNode = newRange.startContainer
        const currentPos = newRange.startOffset
        const targetPos = currentPos - suffix.length
        
        if (targetPos >= 0) {
          const finalRange = document.createRange()
          finalRange.setStart(textNode, targetPos)
          finalRange.setEnd(textNode, targetPos)
          newSelection.removeAllRanges()
          newSelection.addRange(finalRange)
        }
      }
    }
  }
  
  // 更新内容变量
  commentContent.value = textarea.textContent || ''
  
  // 保持焦点
  textarea.focus()
}

// 插入表情的函数
const addEmoji = (emoji) => {
  const textarea = commentEditable.value
  if (!textarea) {
    return
  }
  
  // 确保输入框获得焦点
  textarea.focus()
  
  // 在当前光标位置插入表情
  const text = textarea.textContent || ''
  const newText = text + emoji
  textarea.textContent = newText
  
  // 移动光标到末尾
  const range = document.createRange()
  const textNode = textarea.firstChild || textarea.appendChild(document.createTextNode(''))
  range.setStart(textNode, newText.length)
  range.setEnd(textNode, newText.length)
  const selection = window.getSelection()
  selection.removeAllRanges()
  selection.addRange(range)
  
  // 更新内容变量
  commentContent.value = newText
  
  // 关闭表情选择器
  showEmojiPicker.value = false
  
  // 保持焦点
  textarea.focus()
}

// Markdown 工具栏相关状态
const showEmojiPicker = ref(false)
const showReplyEmojiPicker = ref(false)
const showSecondaryEmojiPicker = ref(false)

// 常用表情列表
const commonEmojis = ref([
  '😀', '😃', '😄', '😁', '😆', '😅', '😂', '🤣', '😊', '😇',
  '🙂', '🙃', '😉', '😌', '😍', '🥰', '😘', '😗', '😙', '😚',
  '😋', '😛', '😝', '😜', '🤪', '🤨', '🧐', '🤓', '😎', '🤩',
  '🥳', '😏', '😒', '😞', '😔', '😟', '😕', '🙁', '☹️', '😣',
  '😖', '😫', '😩', '🥺', '😢', '😭', '😤', '😠', '😡', '🤬',
  '🤯', '😳', '🥵', '🥶', '😱', '😨', '😰', '😥', '😓', '🤗',
  '🤔', '🤭', '🤫', '🤥', '😶', '😐', '😑', '😬', '🙄', '😯',
  '😦', '😧', '😮', '😲', '🥱', '😴', '🤤', '😪', '😵', '🤐',
  '🥴', '🤢', '🤮', '🤧', '😷', '🤒', '🤕', '🤑', '🤠', '😈',
  '👍', '👎', '👌', '🤝', '👏', '🙌', '🤟', '✌️', '🤞', '🤘'
])

// 智能调整表情选择器位置
const adjustEmojiPickerPosition = (pickerElement, buttonElement) => {
  if (!pickerElement || !buttonElement) return
  
  const buttonRect = buttonElement.getBoundingClientRect()
  const screenWidth = window.innerWidth
  const screenHeight = window.innerHeight
  
  // 计算理想的右侧位置
  let left = buttonRect.right + 10
  let top = buttonRect.top
  
  // 如果右边空间不够，显示在左边
  if (left + 240 > screenWidth - 20) {
    left = buttonRect.left - 250
  }
  
  // 如果下方空间不够，向上调整
  if (top + 300 > screenHeight - 20) {
    top = Math.max(20, screenHeight - 320)
  }
  
  pickerElement.style.left = `${left}px`
  pickerElement.style.top = `${top}px`
}

// 为主评论区表情选择器定位
const positionEmojiPicker = (pickerSelector) => {
  const picker = document.querySelector(pickerSelector)
  const button = document.querySelector('.emoji-btn')
  adjustEmojiPickerPosition(picker, button)
}

// 回复表情插入函数
const addReplyEmoji = (emoji) => {
  const activeForm = document.querySelector('.reply-form .comment-editable')
  if (!activeForm) return
  
  activeForm.focus()
  const text = activeForm.textContent || ''
  const newText = text + emoji
  activeForm.textContent = newText
  
  // 移动光标到末尾
  const range = document.createRange()
  const textNode = activeForm.firstChild || activeForm.appendChild(document.createTextNode(''))
  range.setStart(textNode, newText.length)
  range.setEnd(textNode, newText.length)
  const selection = window.getSelection()
  selection.removeAllRanges()
  selection.addRange(range)
  
  // 更新内容变量
  replyContent.value = newText
  
  // 关闭表情选择器
  showReplyEmojiPicker.value = false
  
  activeForm.focus()
}

// 二级回复表情插入函数
const addSecondaryEmoji = (emoji) => {
  const activeForm = document.querySelector('.reply-to-reply-form .comment-editable')
  if (!activeForm) return
  
  activeForm.focus()
  const text = activeForm.textContent || ''
  const newText = text + emoji
  activeForm.textContent = newText
  
  // 移动光标到末尾
  const range = document.createRange()
  const textNode = activeForm.firstChild || activeForm.appendChild(document.createTextNode(''))
  range.setStart(textNode, newText.length)
  range.setEnd(textNode, newText.length)
  const selection = window.getSelection()
  selection.removeAllRanges()
  selection.addRange(range)
  
  // 更新内容变量
  replyContent.value = newText
  
  // 关闭表情选择器
  showSecondaryEmojiPicker.value = false
  
  activeForm.focus()
}

// 关闭表情选择器的函数
const closeEmojiPickers = () => {
  showEmojiPicker.value = false
  showReplyEmojiPicker.value = false
  showSecondaryEmojiPicker.value = false
}

// 监听用户搜索事件和滚动事件
onMounted(() => {
  if (typeof window !== 'undefined') {
    // 监听搜索激活事件
    window.addEventListener('userSearchActivated', () => {
      console.log('🔍 收到userSearchActivated事件，强制显示搜索框')
      forceShowUserSearch()
    })
    
    // 监听滚动事件，重新定位搜索框
    window.addEventListener('scroll', handleScrollForSearch, { passive: true })
    
    // 监听窗口大小变化
    window.addEventListener('resize', handleScrollForSearch, { passive: true })
    
    // 监听点击事件，关闭表情选择器
    document.addEventListener('click', (event) => {
      // 如果点击的不是表情按钮或表情选择器内部，则关闭所有表情选择器
      if (!event.target.closest('.emoji-btn, .emoji-btn-reply, .emoji-picker')) {
        closeEmojiPickers()
      }
    })
  }
})

onUnmounted(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('userSearchActivated', forceShowUserSearch)
    window.removeEventListener('scroll', handleScrollForSearch)
    window.removeEventListener('resize', handleScrollForSearch)
  }
})

// 监听表情选择器显示状态，调整位置
watch([showEmojiPicker, showReplyEmojiPicker, showSecondaryEmojiPicker], () => {
  nextTick(() => {
    if (showEmojiPicker.value) {
      const picker = document.querySelector('.emoji-picker')
      const button = document.querySelector('.emoji-btn')
      adjustEmojiPickerPosition(picker, button)
    }
    if (showReplyEmojiPicker.value) {
      const picker = document.querySelector('.reply-form .reply-emoji-picker')
      const button = document.querySelector('.reply-form .emoji-btn-reply')
      adjustEmojiPickerPosition(picker, button)
    }
    if (showSecondaryEmojiPicker.value) {
      const picker = document.querySelector('.reply-to-reply-form .reply-emoji-picker')
      const button = document.querySelector('.reply-to-reply-form .emoji-btn-reply')
      adjustEmojiPickerPosition(picker, button)
    }
  })
})

// 滚动时重新定位搜索框
const handleScrollForSearch = () => {
  // 如果搜索框可见，重新定位
  if (showUserSearch.value) {
    console.log('🔍 滚动事件：重新定位搜索框')
    forceShowUserSearch()
  }
}

// 更新活动输入框类型的函数
const updateActiveInputType = (event) => {
  const replyToReplyForm = event.target.closest('.reply-to-reply-form')
  const replyForm = event.target.closest('.reply-form')
  const commentForm = event.target.closest('.comment-form')
  
  if (replyToReplyForm) {
    activeInputType.value = 'secondary'
  } else if (replyForm) {
    activeInputType.value = 'reply'
  } else {
    activeInputType.value = 'main'
  }
  console.log('🔍 updateActiveInputType:', {
    activeInputType: activeInputType.value,
    replyToReplyForm: !!replyToReplyForm,
    replyForm: !!replyForm,
    commentForm: !!commentForm,
    target: event.target.className
  })
  
  // 检查焦点事件的键盘是否已按下@键
  setTimeout(() => {
    const text = event.target.textContent || ''
    if (text.includes('@')) {
      console.log('🔍 焦点事件: 检测到@符号，显示搜索框')
      showUserSearch.value = true
      forceShowUserSearch()
    }
  }, 10)
}

// 为不同位置的搜索框创建独立状态
const showMainUserSearch = computed(() => {
  const result = showUserSearch.value && activeInputType.value === 'main'
  console.log('🔍 showMainUserSearch:', { showUserSearch: showUserSearch.value, activeInputType: activeInputType.value, result })
  return result
})

const showReplyUserSearch = computed(() => {
  const result = showUserSearch.value && activeInputType.value === 'reply'
  console.log('🔍 showReplyUserSearch:', { showUserSearch: showUserSearch.value, activeInputType: activeInputType.value, result })
  return result
})

const showSecondaryUserSearch = computed(() => {
  const result = showUserSearch.value && activeInputType.value === 'secondary'
  console.log('🔍 showSecondaryUserSearch:', { showUserSearch: showUserSearch.value, activeInputType: activeInputType.value, result })
  return result
})

// 注释掉二级评论的独立@功能，直接使用一级评论的逻辑
// const { 
//   showReplyUserSearch, replySearchUsers, replyUserSearchIndex, isSearchingReplyUsers,
//   handleReplyContentEditableInput, handleReplyCommentKeydown, 
//   handleReplyBackspace: handleReplyMentionBackspace, selectReplyUser 
// } = useReplyMention(replyContent, replyTextarea)

// 为模板创建所需的函数别名
const handleCommentInput = (event) => {
  // 更新活动输入框类型
  if (event.target.closest('.reply-to-reply-form')) {
    activeInputType.value = 'secondary'
    replyContent.value = event.target.textContent || ''
  } else if (event.target.closest('.reply-form')) {
    activeInputType.value = 'reply'
    replyContent.value = event.target.textContent || ''
  } else {
    activeInputType.value = 'main'
    // 主评论发布框的处理
    commentContent.value = event.target.textContent || ''
  }
  
  console.log('🔍 handleCommentInput activeInputType:', activeInputType.value)
  
  // 调用@功能处理
  handleContentEditableInput(event)
}

const handleCommentPaste = (event) => {
  event.preventDefault()
  const text = (event.clipboardData || window.clipboardData).getData('text/plain')
  document.execCommand('insertText', false, text)
  
  // 判断是回复（一级或二级）还是主评论
  if (event.target.closest('.reply-to-reply-form') || event.target.closest('.reply-form')) {
    replyContent.value = event.target.textContent || ''
  } else {
    commentContent.value = event.target.textContent || ''
  }
}

const handleReplyInput = (event) => {
  replyContent.value = event.target.textContent || ''
}

const handleReplyKeydown = (event) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    if (activeReplyId.value) {
      submitReply(activeReplyId.value)
    }
  }
  if (event.key === 'Escape') {
    cancelReply()
  }
}

// 修改handleCommentKeydown来处理二级评论
const originalHandleCommentKeydown = handleCommentKeydown
const handleCommentKeydownExtended = (event) => {
  // 更新活动输入框类型
  if (event.target.closest('.reply-to-reply-form')) {
    activeInputType.value = 'secondary'
  } else if (event.target.closest('.reply-form')) {
    activeInputType.value = 'reply'
  } else {
    activeInputType.value = 'main'
  }
  
  // 如果是二级评论输入框或一级评论回复输入框，处理回复相关的逻辑
  if (event.target.closest('.reply-to-reply-form') || event.target.closest('.reply-form')) {
    
    // 处理退格键和删除键的特殊逻辑
    if (event.key === 'Backspace' || event.key === 'Delete') {
      // 延迟检查，等待删除操作完成
      setTimeout(() => {
        const text = event.target.textContent || ''
        
        // 如果删除后内容为空，隐藏搜索框
        if (!text || text.trim() === '') {
          console.log('🗑️ 回复框删除后内容为空，隐藏搜索框')
          showUserSearch.value = false
          searchUsers.value = []
          return
        }
        
        // 检查删除后是否仍有@符号需要触发搜索
        const selection = window.getSelection()
        if (selection.rangeCount > 0) {
          const range = selection.getRangeAt(0)
          const caretPos = range.startOffset || 0
          const lastAtPos = text.lastIndexOf('@', caretPos)
          
          if (lastAtPos !== -1 && lastAtPos < caretPos) {
            const textAfterAt = text.substring(lastAtPos + 1, caretPos)
            // 如果@后面没有空格，触发搜索
            if (!textAfterAt.includes(' ')) {
                             console.log('🔍 退格后重新触发回复框搜索:', textAfterAt)
               showUserSearch.value = true
               // 创建模拟的input事件来触发搜索
               const inputEvent = new Event('input', { bubbles: true })
               Object.defineProperty(inputEvent, 'target', {
                 value: event.target,
                 writable: false
               })
               // 调用handleCommentInput来处理搜索
               handleCommentInput(inputEvent)
            }
          }
        }
      }, 10)
    }
    
    // 如果用户搜索框显示，处理用户选择逻辑
    if (showUserSearch.value) {
      if (event.key === 'ArrowDown') {
        event.preventDefault()
        userSearchIndex.value = (userSearchIndex.value + 1) % searchUsers.value.length
        return
      } else if (event.key === 'ArrowUp') {
        event.preventDefault()
        userSearchIndex.value = (userSearchIndex.value - 1 + searchUsers.value.length) % searchUsers.value.length
        return
      } else if (event.key === 'Enter' || event.key === 'Tab') {
        event.preventDefault()
        if (searchUsers.value.length > 0) {
          selectUserExtended(searchUsers.value[userSearchIndex.value])
        }
        return
      } else if (event.key === 'Escape') {
        event.preventDefault()
        showUserSearch.value = false
        searchUsers.value = []
        return
      }
    }
    
    // 处理回复快捷键
    if (event.key === 'Enter' && event.ctrlKey) {
      event.preventDefault()
      if (activeReplyToReplyId.value) {
        // 二级评论：找到父评论ID
        const parentCommentId = comments.value.find(c => 
          c.replies && c.replies.some(r => r.id === activeReplyToReplyId.value)
        )?.id
        if (parentCommentId) {
          submitReply(parentCommentId)
        }
      } else if (activeReplyId.value) {
        // 一级评论回复
        submitReply(activeReplyId.value)
      }
      return
    }
    
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      if (activeReplyToReplyId.value) {
        // 二级评论：找到父评论ID
        const parentCommentId = comments.value.find(c => 
          c.replies && c.replies.some(r => r.id === activeReplyToReplyId.value)
        )?.id
        if (parentCommentId) {
          submitReply(parentCommentId)
        }
      } else if (activeReplyId.value) {
        // 一级评论回复
        submitReply(activeReplyId.value)
      }
      return
    }
    
    if (event.key === 'Escape') {
      if (activeReplyToReplyId.value) {
        cancelReplyToReply()
      } else if (activeReplyId.value) {
        cancelReply()
      }
      return
    }
    
  } // 添加缺失的大括号来闭合 if 语句块
  
  // 其他键盘事件让原始函数处理（比如@键检测等）
  originalHandleCommentKeydown(event)
  return
}

// Markdown 工具栏相关函数
const insertCommentMarkdown = (prefix, suffix) => {
  console.log('🔧 insertCommentMarkdown 被调用:', { prefix, suffix, activeInputType: activeInputType.value })
  
  // 获取当前活动的输入框
  let currentInput = null
  
  // 先从按钮元素判断
  const activeElement = document.activeElement
  console.log('🔧 当前活动元素:', activeElement.tagName, activeElement.className)
  
  // 如果是按钮，获取它所在的工具栏
  if (activeElement.tagName === 'BUTTON' || activeElement.tagName === 'I') {
    let button = activeElement
    if (activeElement.tagName === 'I') {
      button = activeElement.closest('button')
    }
    
    if (button) {
      // 获取按钮所在的工具栏
      const toolbar = button.closest('.simple-toolbar, .comment-markdown-toolbar')
      
      if (toolbar) {
        // 找到工具栏所在的输入区域
        const inputArea = toolbar.closest('.comment-input-area')
        
        if (inputArea) {
          // 从输入区域找到contenteditable元素
          const editableDiv = inputArea.querySelector('.comment-editable')
          
          if (editableDiv) {
            currentInput = editableDiv
            console.log('🔧 从按钮找到了输入框:', editableDiv.className)
          }
        }
      }
    }
  }
  
  // 如果没有找到输入框，尝试通过activeInputType查找
  if (!currentInput) {
    if (activeInputType.value === 'main') {
      currentInput = commentEditable.value
      console.log('🔧 使用主评论框')
    } else if (activeInputType.value === 'reply') {
      // 查找可见的回复框
      const replyForms = document.querySelectorAll('.reply-form .comment-editable')
      for (let i = 0; i < replyForms.length; i++) {
        if (replyForms[i].offsetParent !== null) {
          currentInput = replyForms[i]
          console.log('🔧 找到回复框:', i)
          break
        }
      }
    } else if (activeInputType.value === 'secondary') {
      // 查找可见的二级回复框
      const secondaryForms = document.querySelectorAll('.reply-to-reply-form .comment-editable')
      for (let i = 0; i < secondaryForms.length; i++) {
        if (secondaryForms[i].offsetParent !== null) {
          currentInput = secondaryForms[i]
          console.log('🔧 找到二级回复框:', i)
          break
        }
      }
    }
  }
  
  console.log('🔧 当前输入框:', currentInput)
  console.log('🔧 输入框内容:', currentInput?.textContent)
  
  if (!currentInput) {
    console.log('🔧 没有找到活动的输入框')
    return
  }
  
  // 确保输入框获得焦点
  currentInput.focus()
  
  // 获取当前选择
  const selection = window.getSelection()
  console.log('🔧 选择数量:', selection.rangeCount)
  
  if (selection.rangeCount === 0) {
    console.log('🔧 没有选择，在末尾插入')
    // 如果没有选择，在末尾插入
    const text = currentInput.textContent || ''
    console.log('🔧 原始文本:', `"${text}"`)
    const newText = text + prefix + suffix
    console.log('🔧 新文本:', `"${newText}"`)
    currentInput.textContent = newText
    
    // 设置光标在中间
    const range = document.createRange()
    const textNode = currentInput.firstChild || currentInput.appendChild(document.createTextNode(''))
    const cursorPos = text.length + prefix.length
    console.log('🔧 光标位置:', cursorPos)
    range.setStart(textNode, cursorPos)
    range.setEnd(textNode, cursorPos)
    selection.removeAllRanges()
    selection.addRange(range)
    
    // 更新内容变量
    if (activeInputType.value === 'main') {
      commentContent.value = newText
    } else {
      replyContent.value = newText
    }
    console.log('🔧 完成末尾插入')
    return
  }
  
  const range = selection.getRangeAt(0)
  console.log('🔧 选择范围:', {
    startContainer: range.startContainer.nodeName,
    startOffset: range.startOffset,
    endContainer: range.endContainer.nodeName,
    endOffset: range.endOffset,
    commonAncestor: range.commonAncestorContainer.nodeName
  })
  
  // 检查选择是否在我们的输入框内
  if (!currentInput.contains(range.commonAncestorContainer)) {
    console.log('🔧 选择不在当前输入框内')
    return
  }
  
  // 获取选中的文本
  const selectedText = range.toString()
  console.log('🔧 选中的文本:', `"${selectedText}"`)
  console.log('🔧 选中文本长度:', selectedText.length)
  
  // 保存当前位置信息
  const beforeInsertion = {
    startContainer: range.startContainer,
    startOffset: range.startOffset,
    endContainer: range.endContainer,
    endOffset: range.endOffset,
    textContent: currentInput.textContent
  }
  console.log('🔧 插入前状态:', beforeInsertion)
  
  // 使用execCommand插入文本
  const textToInsert = prefix + selectedText + suffix
  console.log('🔧 要插入的文本:', `"${textToInsert}"`)
  
  // 如果有选中文本，先删除
  if (selectedText.length > 0) {
    console.log('🔧 删除选中文本')
    document.execCommand('delete', false)
  }
  
  // 插入新文本
  console.log('🔧 插入新文本')
  document.execCommand('insertText', false, textToInsert)
  
  // 检查插入后的状态
  const afterInsertion = {
    textContent: currentInput.textContent,
    selectionRangeCount: selection.rangeCount
  }
  console.log('🔧 插入后状态:', afterInsertion)
  
  // 如果没有选中文本，调整光标位置到中间
  if (selectedText.length === 0) {
    console.log('🔧 调整光标位置到中间')
    // 获取当前光标位置
    const newSelection = window.getSelection()
    console.log('🔧 新选择数量:', newSelection.rangeCount)
    
    if (newSelection.rangeCount > 0) {
      const newRange = newSelection.getRangeAt(0)
      console.log('🔧 新选择范围:', {
        startContainer: newRange.startContainer.nodeName,
        startOffset: newRange.startOffset,
        nodeType: newRange.startContainer.nodeType
      })
      
      if (newRange.startContainer.nodeType === Node.TEXT_NODE) {
        const textNode = newRange.startContainer
        const currentPos = newRange.startOffset
        const targetPos = currentPos - suffix.length
        
        console.log('🔧 光标位置调整:', {
          当前位置: currentPos,
          目标位置: targetPos,
          后缀长度: suffix.length
        })
        
        if (targetPos >= 0) {
          const finalRange = document.createRange()
          finalRange.setStart(textNode, targetPos)
          finalRange.setEnd(textNode, targetPos)
          newSelection.removeAllRanges()
          newSelection.addRange(finalRange)
          console.log('🔧 光标位置已调整')
        } else {
          console.log('🔧 目标位置无效')
        }
      } else {
        console.log('🔧 不是文本节点，无法调整')
      }
    } else {
      console.log('🔧 没有新选择，无法调整')
    }
  }
  
  // 更新对应的内容变量
  const updatedText = currentInput.textContent || ''
  console.log('🔧 最终文本:', `"${updatedText}"`)
  
  if (activeInputType.value === 'main') {
    commentContent.value = updatedText
  } else {
    replyContent.value = updatedText
  }
  
  // 保持焦点
  currentInput.focus()
  console.log('🔧 insertCommentMarkdown 完成')
}

// 切换表情选择器显示状态
const toggleEmojiPicker = () => {
  showEmojiPicker.value = !showEmojiPicker.value
  
  if (showEmojiPicker.value) {
    nextTick(() => {
      positionEmojiPicker('.emoji-picker')
    })
  }
}

// 插入表情
const insertEmoji = (emoji) => {
  console.log('😀 insertEmoji 被调用:', emoji)
  
  // 获取当前活动的输入框
  let currentInput = null
  
  if (activeInputType.value === 'main') {
    currentInput = commentEditable.value
  } else {
    const activeElement = document.activeElement
    if (activeElement && activeElement.contentEditable === 'true') {
      currentInput = activeElement
    }
  }
  
  if (!currentInput) {
    console.log('😀 没有找到活动的输入框')
    return
  }
  
  // 确保输入框获得焦点
  currentInput.focus()
  
  // 使用 document.execCommand 插入表情
  document.execCommand('insertText', false, emoji)
  
  // 更新对应的内容变量
  const updatedText = currentInput.textContent || ''
  
  if (activeInputType.value === 'main') {
    commentContent.value = updatedText
  } else {
    replyContent.value = updatedText
  }
  
  // 隐藏表情选择器
  showEmojiPicker.value = false
  
  // 保持焦点
  currentInput.focus()
  
  console.log('😀 表情插入完成:', updatedText)
}

// 一级评论的处理继续执行原始函数
// originalHandleCommentKeydown(event) - 这行代码已经在上面的函数中处理了

// 重写selectUser函数来处理二级评论
const originalSelectUser = selectUser
// 全局函数：强制显示当前活动框的搜索下拉框
const forceShowUserSearch = () => {
  const currentInputType = activeInputType.value
  console.log('🔍 强制显示搜索框，当前输入框类型:', currentInputType)

  // 先设置搜索状态为true
  showUserSearch.value = true
  
  // 延迟执行以确保DOM已更新
  setTimeout(() => {
    try {
      // 主动查找搜索框
      let dropdownSelector = '.user-search-dropdown'
      
      if (currentInputType === 'secondary') {
        dropdownSelector = '.reply-to-reply-form .user-search-dropdown'
      } else if (currentInputType === 'reply') {
        dropdownSelector = '.reply-form .user-search-dropdown'
      } else {
        dropdownSelector = '.comment-form .user-search-dropdown'
      }
      
      // 首先隐藏所有搜索框
      document.querySelectorAll('.user-search-dropdown').forEach(el => {
        el.classList.remove('visible')
      })
      
      // 显示当前活动输入框对应的搜索框
      const dropdown = document.querySelector(dropdownSelector)
      if (dropdown) {
        dropdown.classList.add('visible')
        console.log('🔍 已强制显示搜索框:', dropdownSelector)
      } else {
        console.log('🔍 未找到搜索框元素:', dropdownSelector)
      }
    } catch (error) {
      console.error('🔍 强制显示搜索框失败:', error)
    }
  }, 100)
}

const selectUserExtended = (user) => {
  // 检查当前活动的元素是否是回复输入框（一级或二级）
  const activeElement = document.activeElement
  if (activeElement && (activeElement.closest('.reply-to-reply-form') || activeElement.closest('.reply-form'))) {
    // 回复逻辑（一级或二级评论）
    
    const text = replyContent.value || ''
    const atPos = text.lastIndexOf('@')
    
    if (atPos !== -1) {
      // 替换@及其后的文本为@username
      const beforeAt = text.substring(0, atPos)
      const newText = beforeAt + '@' + user.username + ' '
      replyContent.value = newText
      
      // 更新输入框内容
      if (activeElement) {
        activeElement.textContent = newText
        
        // 设置光标位置到@username之后
        nextTick(() => {
          const range = document.createRange()
          const sel = window.getSelection()
          
          if (activeElement.firstChild) {
            range.setStartAfter(activeElement.firstChild)
            range.collapse(true)
            sel.removeAllRanges()
            sel.addRange(range)
          }
        })
      }
      
      // 隐藏搜索框
      showUserSearch.value = false
      searchUsers.value = []
    }
  } else {
    // 一级评论逻辑
    originalSelectUser(user)
  }
}

const handleReplyPaste = (event) => {
  event.preventDefault()
  const text = (event.clipboardData || window.clipboardData).getData('text/plain')
  document.execCommand('insertText', false, text)
  replyContent.value = event.target.textContent || ''
}

// 使用格式化工具
const { formatDateTime, formatTimeAgo } = useFormatters()

// 删除之前的自定义函数，现在直接使用一级评论的逻辑

// 保留原有的简单输入处理函数以备用
const handleReplyToReplyInput = (event) => {
  replyContent.value = event.target.textContent || ''
}

const handleReplyToReplyKeydown = (event) => {
  // 处理快捷键
  if (event.key === 'Enter' && event.ctrlKey) {
    event.preventDefault()
    if (activeReplyToReplyId.value) {
      // 找到父评论ID
      const parentCommentId = comments.value.find(c => 
        c.replies && c.replies.some(r => r.id === activeReplyToReplyId.value)
      )?.id
      if (parentCommentId) {
        submitReply(parentCommentId)
      }
    }
    return
  }
  
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    if (activeReplyToReplyId.value) {
      // 找到父评论ID
      const parentCommentId = comments.value.find(c => 
        c.replies && c.replies.some(r => r.id === activeReplyToReplyId.value)
      )?.id
      if (parentCommentId) {
        submitReply(parentCommentId)
      }
    }
    return
  }
  
  if (event.key === 'Escape') {
    cancelReplyToReply()
    return
  }
}

const handleReplyToReplyPaste = (event) => {
  event.preventDefault()
  const text = (event.clipboardData || window.clipboardData).getData('text/plain')
  document.execCommand('insertText', false, text)
  replyContent.value = event.target.textContent || ''
}

// 分页导航函数
const previousPage = () => {
  if (hasPreviousPage.value) {
    const prevPage = currentPage.value - 1
    router.push({
      query: { 
        ...route.query,
        page: prevPage 
      }
    })
  }
}

const nextPage = () => {
  if (hasNextPage.value) {
    const nextPage = currentPage.value + 1
    router.push({
      query: { 
        ...route.query,
        page: nextPage 
      }
    })
  }
}

// 计算要显示的页码 - 优化后的算法
const displayedPages = computed(() => {
  const maxDisplayed = 5; // 最多显示的页码数
  const totalPagesValue = totalPages.value;
  const currentPageValue = currentPage.value;
  
  if (totalPagesValue <= maxDisplayed + 2) {
    // 如果总页数较少，直接显示所有页码（除了首末页，它们单独显示）
    const start = currentPageValue > 3 ? 2 : 1;
    const end = currentPageValue < totalPagesValue - 2 ? totalPagesValue - 1 : totalPagesValue;
    return Array.from({ length: end - start + 1 }, (_, i) => start + i);
  }
  
  // 计算中心显示区域
  const half = Math.floor(maxDisplayed / 2);
  let start = Math.max(currentPageValue - half, 2); // 从第2页开始，因为第1页单独显示
  let end = Math.min(start + maxDisplayed - 1, totalPagesValue - 1); // 到倒数第2页结束，因为最后一页单独显示
  
  // 调整起始位置，确保始终显示maxDisplayed个页码
  if (end - start + 1 < maxDisplayed) {
    start = Math.max(end - maxDisplayed + 1, 2);
  }
  
  // 如果当前页在前3页，从第1页开始显示
  if (currentPageValue <= 3) {
    start = 1;
    end = Math.min(maxDisplayed, totalPagesValue);
  }
  
  // 如果当前页在最后3页，显示到最后一页
  if (currentPageValue > totalPagesValue - 3) {
    end = totalPagesValue;
    start = Math.max(totalPagesValue - maxDisplayed + 1, 1);
  }
  
  return Array.from({ length: end - start + 1 }, (_, i) => start + i);
});

// 记录评论区域的高度
const commentsListRef = ref(null);
const commentAreaHeight = ref(300); // 默认最小高度

// 阅读进度
const readingProgress = ref(0)
const showBackToTop = ref(false)

// 计算阅读进度
const updateReadingProgress = () => {
  if (process.client) {
    const winHeight = window.innerHeight
    const docHeight = document.documentElement.scrollHeight
    const scrollTop = window.pageYOffset || document.documentElement.scrollTop
    const maxScroll = docHeight - winHeight
    
    if (maxScroll > 0) {
      readingProgress.value = Math.min((scrollTop / maxScroll) * 100, 100)
    } else {
      readingProgress.value = 100
    }
    
    // 控制回到顶部按钮显示
    showBackToTop.value = scrollTop > 300
  }
}

// 页面滚动函数
const scrollToTop = () => {
  if (process.client) { // Guard client-side specific code
    window.scrollTo({
      top: 0,
      behavior: 'smooth'
    });
  }
};

// 使用router导航，避免直接使用window.location
const handleGoToPage = (page) => {
  if (process.client) {
    if (page >= 1 && page <= totalPages.value && page !== currentPage.value) {
      // 设置跳过动画标志，然后刷新页面
      window._skipNextTransition = true;
      const url = new URL(window.location.href)
      url.searchParams.set('page', page.toString())
      window.location.href = url.toString()
    }
  }
};

// 前一页 - 使用页面刷新导航但跳过动画
const handlePreviousPage = () => {
  if (process.client) {
    if (hasPreviousPage.value) {
      const prevPage = currentPage.value - 1;
      // 设置跳过动画标志，然后刷新页面
      window._skipNextTransition = true;
      const url = new URL(window.location.href)
      url.searchParams.set('page', prevPage.toString())
      window.location.href = url.toString()
    }
  }
};

// 后一页 - 使用页面刷新导航但跳过动画
const handleNextPage = () => {
  if (process.client) {
    if (hasNextPage.value) {
      const nextPage = currentPage.value + 1;
      // 设置跳过动画标志，然后刷新页面
      window._skipNextTransition = true;
      const url = new URL(window.location.href)
      url.searchParams.set('page', nextPage.toString())
      window.location.href = url.toString()
    }
  }
};

// 处理返回帖子详情 - 使用页面刷新导航
const handleBackToPost = () => {
  if (process.client) {
    const url = new URL(window.location.href)
    url.searchParams.set('page', '1')
    window.location.href = url.toString()
  }
};

// 监听评论数据变化 - 简化逻辑
watch(() => comments.value, () => {
  if (process.client) { // Guard DOM related operations
    nextTick(() => {
      if (commentsListRef.value) {
        commentAreaHeight.value = commentsListRef.value.offsetHeight;
      }
      
      // 检查是否需要高亮显示评论
      handleCommentHighlight()
    });
  }
}, { deep: true });

// 在组件挂载时初始化 - Data fetching is now done by useAsyncData
onMounted(() => {
  // 移除重复的用户初始化 - 已在app.vue中统一处理
  
  // 检查是否需要重新获取评论数据
  if (process.client && comments.value.length === 0 && totalComments.value === 0) {
    // 如果有token，尝试重新获取评论
    const token = localStorage.getItem('token') || getCookieValue('Authorization')
    if (token) {
      console.log('客户端重新获取评论数据')
      refreshCommentsData()
    }
  }
  
  // 添加滚动监听器来更新阅读进度
  if (process.client) {
    window.addEventListener('scroll', updateReadingProgress)
    updateReadingProgress() // 初始计算
    
    // 检查是否需要高亮显示评论
    handleCommentHighlight()
  }

  // 在组件挂载后设置全局函数
  // 复制代码函数
  window.copyCode = function(button) {
    const codeBlock = button.closest('.code-block-container').querySelector('code')
    const text = codeBlock.textContent
    
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text).then(() => {
        const originalText = button.innerHTML
        button.innerHTML = '<i class="ri-check-line"></i> 已复制'
        button.style.background = '#22c55e'
        
        setTimeout(() => {
          button.innerHTML = originalText
          button.style.background = '#2d3748'
        }, 2000)
      }).catch(err => {
        console.error('复制失败:', err)
        window.fallbackCopyTextToClipboard(text)
      })
    } else {
      window.fallbackCopyTextToClipboard(text)
    }
  }

  // 兼容性复制方法
  window.fallbackCopyTextToClipboard = function(text) {
    const textArea = document.createElement('textarea')
    textArea.value = text
    document.body.appendChild(textArea)
    textArea.focus()
    textArea.select()
    
    try {
      const successful = document.execCommand('copy')
      if (successful) {
        if (window.$toast) {
          window.$toast.success('代码已复制到剪贴板')
        }
      } else {
        if (window.$toast) {
          window.$toast.error('复制失败，请手动复制')
        }
      }
    } catch (err) {
      console.error('复制失败:', err)
      if (window.$toast) {
        window.$toast.error('复制失败，请手动复制')
      }
    }
    
    document.body.removeChild(textArea)
  }

  // 图片预览功能
  window.previewImage = function(src) {
    const overlay = document.createElement('div')
    overlay.style.cssText = `
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0,0,0,0.8);
      display: flex;
      justify-content: center;
      align-items: center;
      z-index: 10000;
      cursor: pointer;
    `
    
    const img = document.createElement('img')
    img.src = src
    img.style.cssText = `
      max-width: 90%;
      max-height: 90%;
      border-radius: 8px;
      box-shadow: 0 8px 32px rgba(0,0,0,0.3);
    `
    
    overlay.appendChild(img)
    document.body.appendChild(overlay)
    
    overlay.addEventListener('click', () => {
      document.body.removeChild(overlay)
    })
  }

})

// 刷新评论数据的函数
const refreshCommentsData = async () => {
  try {
    const url = `${API_BASE_URL}/v3/getComments?postId=${route.params.id}&pageNum=${currentPage.value}`
    const token = localStorage.getItem('token') || getCookieValue('Authorization')
    
    const headers = {
      'Content-Type': 'application/json'
    }
    
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
    
    const response = await fetch(url, { headers })
    const data = await response.json()
    
    if (data.code === 200 && data.data) {
      // 格式化评论数据
      const formattedComments = data.data.comments.map(comment => {
        const likeCount = parseInt(comment.likeCount || '0')
        const dislikeCount = parseInt(comment.dislikeCount || '0')
        const replyCount = parseInt(comment.replyCount || '0')
        
        return {
          ...comment,
          author: {
            id: comment.userId,
            username: comment.username,
            avatar: comment.avatar
          },
          createdAt: comment.created,
          updatedAt: comment.updated,
          likeCount,
          dislikeCount,
          replyCount,
          // 确保点赞状态正确
          isLiked: !!comment.isLiked,
          isDisliked: !!comment.isDisliked,
          showReplies: false,
          loadingReplies: false,
          replies: []
        }
      })
      
      // 先清空，然后再设置，确保响应式系统检测到变化
      localComments.value = []
      await nextTick()
      
      // 更新本地状态
      localComments.value = formattedComments
      currentPage.value = data.data.current
      totalComments.value = data.data.total
      totalPages.value = data.data.pages
      hasNextPage.value = data.data.hasNext
      hasPreviousPage.value = data.data.hasPrevious
      isCommentsLoading.value = false
      
      console.log('客户端评论数据获取成功:', formattedComments.length, '条评论')
      console.log('localComments更新后:', localComments.value.length)
      
      // 强制触发下一次tick以确保UI更新
      await nextTick()
    }
  } catch (error) {
    console.error('客户端获取评论失败:', error)
  }
}

// 处理评论高亮显示和自动展开
const handleCommentHighlight = () => {
  if (!process.client) return
  
  const urlParams = new URLSearchParams(window.location.search)
  const highlightCommentId = urlParams.get('highlight')
  const expandCommentId = urlParams.get('expand')
  
  // 处理自动展开父评论
  if (expandCommentId) {
    console.log('🔽 需要自动展开评论:', expandCommentId)
    
    nextTick(() => {
      const expandComment = () => {
        // 查找对应的父评论
        const parentComment = comments.value.find(c => c.id == expandCommentId)
        
        if (parentComment && parentComment.replyCount > 0) {
          console.log('🔽 找到父评论，开始自动展开')
          
          // 自动加载回复
          loadReplies(parentComment.id)
          
          // 移除URL中的expand参数
          const newUrl = new URL(window.location.href)
          newUrl.searchParams.delete('expand')
          window.history.replaceState({}, '', newUrl.toString())
        } else {
          console.log('🔽 未找到父评论或无回复，500ms后重试')
          setTimeout(expandComment, 500)
        }
      }
      
      setTimeout(expandComment, 500)
    })
  }
  
  // 处理评论高亮
  if (highlightCommentId) {
    console.log('🎯 需要高亮显示评论:', highlightCommentId)
    
    // 等待评论列表渲染完成后再高亮
    nextTick(() => {
      const highlightComment = () => {
        // 先查找主评论
        let commentElement = document.querySelector(`[data-comment-id="${highlightCommentId}"]`)
        
        // 如果主评论中没找到，查找回复
        if (!commentElement) {
          commentElement = document.querySelector(`#reply-${highlightCommentId}`)
        }
        
        if (commentElement) {
          console.log('🎯 找到评论元素，开始高亮显示')
          
          // 添加高亮样式
          commentElement.classList.add('comment-highlight')
          
          // 滚动到评论位置
          commentElement.scrollIntoView({ 
            behavior: 'smooth', 
            block: 'center' 
          })
          
          // 3秒后移除高亮
          setTimeout(() => {
            commentElement.classList.remove('comment-highlight')
            
            // 移除URL中的highlight参数
            const newUrl = new URL(window.location.href)
            newUrl.searchParams.delete('highlight')
            window.history.replaceState({}, '', newUrl.toString())
          }, 3000)
        } else {
          console.log('🎯 未找到评论元素，500ms后重试')
          // 如果元素还没有渲染，等待500ms后重试
          setTimeout(highlightComment, 500)
        }
      }
      
      // 延迟执行，确保DOM已完全渲染
      // 如果有展开操作，等待更长时间让回复加载完成
      const delay = expandCommentId ? 1500 : 500
      setTimeout(highlightComment, delay)
    })
  }
}

// 处理回复输入框的退格键的包装函数
const handleSpecificReplyBackspace = (e) => {
  handleReplyBackspace(e, replyTextarea.value, replyContent, replyToUsername.value)
}

// 是否有任何回复表单处于激活状态
const isAnyReplyFormActive = computed(() => {
  return activeReplyId.value !== null || activeReplyToReplyId.value !== null
})

// 页面元数据
definePageMeta({
  layout: 'default'
})

useHead({
  title: computed(() => post.value ? `${post.value.title} - SBBS社区` : 'SBBS社区'),
  meta: [
    {
      name: 'description',
      content: computed(() => post.value ? post.value.content.substring(0, 150) : 'SBBS社区帖子详情')
    }
  ]
})

// 登录成功后刷新页面，恢复滚动位置
if (process.client) {
  // 检查是否有保存的滚动位置（登录刷新后）
  const savedScrollPosition = sessionStorage.getItem('loginScrollPosition')
  if (savedScrollPosition) {
    const scrollTop = parseInt(savedScrollPosition)
    // 等待页面加载完成后恢复滚动位置
    setTimeout(() => {
      window.scrollTo({
        top: scrollTop,
        behavior: 'smooth'
      })
      // 清除保存的位置
      sessionStorage.removeItem('loginScrollPosition')
      console.log('✅ 已恢复登录前的滚动位置')
    }, 500)
  }
}

// 组件卸载时清理事件监听器
onUnmounted(() => {
  if (process.client) {
    window.removeEventListener('scroll', updateReadingProgress)
  }
})

// 获取API基础URL
const API_BASE_URL = useApiBaseUrl()

</script>

<style scoped>
/* Vue组件专用样式 - 强制缩短分隔符间距 */
:deep(.content-section-divider) {
  margin: 1rem 0 0.5rem 0 !important;
  padding: 0 !important;
  display: block !important;
  background-color: transparent !important;
  border: none !important;
  border-radius: 0 !important;
  border-bottom: 1px solid #3a9c77 !important;
  width: 100% !important;
}

:deep(.content-section-divider .section-title) {
  font-size: 1.1rem !important;
  font-weight: 600 !important;
  color: #3a9c77 !important;
  margin: 0 !important;
  padding: 0 0 0.25rem 0 !important;
  line-height: 1.5 !important;
  letter-spacing: 0.02em !important;
  display: inline-block !important;
}

:deep(.content-section-divider + *) {
  margin-top: 0 !important;
  padding-top: 0 !important;
}

:deep(.content-section-divider + p) {
  margin-top: 0 !important;
  padding-top: 0 !important;
}

:deep(p + .content-section-divider) {
  margin-top: 1rem !important;
}

:deep(.markdown-body p) {
  margin: 0 !important;
  padding: 0 !important;
}

:deep(.markdown-body p + p) {
  margin-top: 0.1em !important;
}

:deep(.markdown-body br) {
  line-height: 0.5 !important;
  margin: 0 !important;
  padding: 0 !important;
}

:deep(.content-section-divider + br) {
  display: none !important;
}

:deep(br + .content-section-divider) {
  margin-top: 0 !important;
}

/* 通用样式继续保持 */

/* 阅读进度指示器 */
.reading-progress {
  position: fixed;
  top: 0;
  left: 0;
  height: 3px;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6, #06b6d4);
  z-index: 9999;
  transition: width 0.1s ease-out;
  border-radius: 0 3px 3px 0;
  box-shadow: 0 2px 4px rgba(59, 130, 246, 0.3);
}

/* 回到顶部按钮 */
.back-to-top-btn {
  position: fixed;
  bottom: 2rem;
  right: 2rem;
  width: 3rem;
  height: 3rem;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  color: white;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.25rem;
  box-shadow: 0 4px 15px rgba(59, 130, 246, 0.4);
  transition: all 0.3s ease;
  z-index: 1000;
}

.back-to-top-btn:hover {
  transform: translateY(-3px) scale(1.1);
  box-shadow: 0 8px 25px rgba(59, 130, 246, 0.6);
}

.back-to-top-btn:active {
  transform: translateY(-1px) scale(1.05);
}

/* 过渡动画 */
.fade-enter-active, .fade-leave-active {
  transition: all 0.3s ease;
}

.fade-enter-from, .fade-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.8);
}

/* 精美的骨架屏样式 */
.post-skeleton {
  background: white;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 1.5rem;
}

.skeleton-header {
  display: flex;
  align-items: center;
  margin-bottom: 1rem;
}

.skeleton-avatar {
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 6px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
  margin-right: 0.75rem;
}

.skeleton-author-info {
  flex: 1;
}

.skeleton-line {
  height: 0.875rem;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
  border-radius: 4px;
  margin-bottom: 0.5rem;
}

.skeleton-name {
  width: 8rem;
}

.skeleton-meta {
  width: 12rem;
}

.skeleton-title {
  height: 1.5rem;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
  border-radius: 4px;
  margin-bottom: 1rem;
  width: 80%;
}

.skeleton-tags {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.skeleton-tag {
  width: 4rem;
  height: 1.5rem;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
  border-radius: 3px;
}

.skeleton-content {
  margin-bottom: 1rem;
}

.skeleton-line-short {
  width: 60%;
}

.skeleton-actions {
  display: flex;
  gap: 0.5rem;
  padding-top: 0.5rem;
  border-top: 1px solid #f0f0f0;
}

.skeleton-action-btn {
  width: 4rem;
  height: 2rem;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
  border-radius: 4px;
}

/* 评论骨架屏 */
.comments-skeleton {
  padding: 1rem;
}

.comment-skeleton {
  display: flex;
  margin-bottom: 1.5rem;
  padding: 0.75rem;
  background: #f9fafb;
  border-radius: 6px;
}

.skeleton-comment-avatar {
  width: 1.75rem;
  height: 1.75rem;
  border-radius: 4px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
  margin-right: 0.5rem;
  flex-shrink: 0;
}

.skeleton-comment-content {
  flex: 1;
}

.skeleton-comment-name {
  width: 6rem;
  height: 0.875rem;
  margin-bottom: 0.5rem;
}

.skeleton-comment-text {
  height: 0.875rem;
  margin-bottom: 0.25rem;
}

@keyframes skeleton-loading {
  0% {
    background-position: -200% 0;
  }
  100% {
    background-position: 200% 0;
  }
}

/* 精美的空状态 */
.empty-state {
  text-align: center;
  padding: 3rem 2rem;
}

.empty-state .empty-icon {
  font-size: 3rem;
  color: #cbd5e1;
  margin-bottom: 1rem;
  opacity: 0.8;
}

.empty-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: #374151;
  margin-bottom: 0.5rem;
  margin: 0 0 0.5rem 0;
}

.empty-description {
  color: #6b7280;
  font-size: 0.875rem;
  margin: 0;
}

/* 危险按钮样式 */
.action-btn-danger {
  color: #dc2626 !important;
  border-color: #dc2626 !important;
}

.action-btn-danger:hover {
  background-color: #fef2f2 !important;
  color: #b91c1c !important;
  border-color: #b91c1c !important;
}

.action-btn-danger:active {
  background-color: #fee2e2 !important;
}

/* 操作按钮增强效果 */
.action-btn {
  position: relative;
  overflow: hidden;
}

.action-ripple {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 8px;
  height: 8px;
  background: rgba(59, 130, 246, 0.3);
  border-radius: 50%;
  transform: translate(-50%, -50%) scale(0);
  animation: ripple-effect 0.6s ease-out;
}

@keyframes ripple-effect {
  to {
    transform: translate(-50%, -50%) scale(4);
    opacity: 0;
  }
}

/* 工具提示 */
.action-tooltip {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.8);
  color: white;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  white-space: nowrap;
  opacity: 0;
  visibility: hidden;
  transition: all 0.2s ease;
  pointer-events: none;
  z-index: 1000;
  margin-bottom: 0.25rem;
}

.action-tooltip::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  border: 4px solid transparent;
  border-top-color: rgba(0, 0, 0, 0.8);
}

.action-btn:hover .action-tooltip {
  opacity: 1;
  visibility: visible;
}

/* 更多按钮特殊效果 */
.more-btn {
  transition: all 0.3s ease;
}

.more-btn:hover {
  transform: rotate(90deg);
}

/* 帖子标题增强 */
.post-title {
  background: linear-gradient(135deg, #1a202c, #2d3748);
  -webkit-background-clip: text;
  background-clip: text;
  position: relative;
  margin-bottom: 0.75rem;
  color: #1a202c; /* 确保emoji和文字正常显示颜色 */
}

/* 标题容器样式调整 */
.post-title-container {
  margin-bottom: 0.5rem;
}

/* 帖子头部样式调整 - 缩小下padding */
.post-header {
  padding-bottom: 0 !important;
}

/* 帖子内容样式调整 - 缩小上padding */
.post-content {
  padding-top: 0.5rem !important;
}

/* 作者信息区域样式调整 */
.post-author-info {
  padding-top: 0.5rem;
  border-top: 1px solid #f3f4f6;
}

/* 作者头像悬停效果 */
.post-author-avatar {
  transition: all 0.3s ease;
  position: relative;
}

.post-author-avatar:hover {
  transform: translateY(-2px) scale(1.05);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
}

.post-author-avatar::before {
  content: '';
  position: absolute;
  top: -2px;
  left: -2px;
  right: -2px;
  bottom: -2px;
  background: linear-gradient(45deg, #3b82f6, #8b5cf6);
  border-radius: inherit;
  z-index: -1;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.post-author-avatar:hover::before {
  opacity: 1;
}

/* 分页样式 - 与评论区保持一致 */
.pagination-container {
  margin: 0.5rem 0; /* 减小margin */
  padding: 0.5rem;
  display: flex;
  justify-content: center;
  background-color: transparent;
  border-radius: 0;
  box-shadow: none;
}

/* 顶部分页样式 */
.top-pagination {
  margin-bottom: 0.75rem; /* 减小margin */
  border-bottom: none;
}

/* 底部分页样式 */
.bottom-pagination {
  margin-top: 0.75rem; /* 减小margin */
  border-top: none;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 0.35rem; /* 减小间距，与评论区一致 */
  width: auto;
  max-width: none;
}

.page-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 28px; /* 减小尺寸 */
  height: 28px;
  background-color: white;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-weight: 500;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 0.7rem; /* 减小字体 */
}

.page-btn:hover:not(:disabled) {
  background-color: #f1f5f9; /* 与comment按钮一致 */
  border-color: #cbd5e0;
  color: #475569;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-btn i {
  font-size: 0.75rem; /* 减小图标 */
}

/* 页码数字按钮样式 */
.page-numbers {
  display: flex;
  align-items: center;
  gap: 0.35rem; /* 减小间距 */
}

.page-number-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 28px; /* 减小尺寸 */
  height: 28px;
  padding: 0;
  background-color: white;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-weight: 500;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 0.7rem; /* 减小字体 */
}

.page-number-btn:hover {
  background-color: #f1f5f9; /* 与其他按钮一致 */
  border-color: #cbd5e0;
  color: #475569;
}

.page-number-btn.active {
  background-color: #3b82f6;
  border-color: #3b82f6;
  color: white;
}

/* 简化顶部导航样式 - 与整体风格一致 */
.simplified-header {
  padding: 0.75rem 1rem; /* 减小padding */
  background-color: white;
  border: 1px solid var(--border-color, #e5e7eb); /* 添加边框 */
  border-radius: 8px;
  margin-bottom: 1rem;
  box-shadow: none; /* 移除阴影 */
}

.simplified-header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.75rem; /* 减小间距 */
}

.simplified-header .page-title {
  font-size: 1.1rem; /* 减小字体 */
  font-weight: 600;
  color: #1a202c;
  margin: 0;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.back-to-post-btn {
  display: inline-flex;
  align-items: center;
  padding: 0.35rem 0.6rem; /* 减小padding */
  border: 1px solid #e2e8f0;
  background-color: white;
  color: #3b82f6;
  border-radius: 4px; /* 减小圆角 */
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 0.75rem; /* 减小字体 */
  text-decoration: none;
}

.back-to-post-btn:hover {
  background-color: #f0f7ff;
  border-color: #3b82f6;
}

.back-to-post-btn i {
  margin-right: 0.35rem; /* 减小间距 */
  font-size: 0.8rem; /* 减小图标 */
}

/* 媒体查询适配移动设备 */
@media (max-width: 640px) {
  .pagination {
    gap: 0.25rem; /* 进一步减小间距 */
  }
  
  .page-btn, .page-number-btn {
    min-width: 26px; /* 移动端更小 */
    height: 26px;
    font-size: 0.65rem;
  }

  .simplified-header {
    padding: 0.65rem 0.85rem;
  }

  .simplified-header-content {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }
  
  .simplified-header .page-title {
    margin-bottom: 0;
    font-size: 1rem;
  }
  
  .back-to-post-btn {
    align-self: flex-start;
    padding: 0.3rem 0.5rem;
    font-size: 0.7rem;
  }
  
  .action-tooltip {
    display: none; /* 移动端隐藏工具提示 */
  }
  
  .empty-state {
    padding: 2rem 1rem;
  }
  
  .post-skeleton {
    padding: 0.75rem;
  }
}

/* 评论区容器样式 */
.comments-wrapper {
  position: relative;
  min-height: 200px; /* 减小最小高度 */
}

/* 评论高亮样式 */
.comment-highlight {
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.1), rgba(139, 92, 246, 0.1));
  border: 2px solid rgba(59, 130, 246, 0.3);
  border-radius: 8px;
  animation: highlightPulse 2s ease-in-out;
  transition: all 0.3s ease;
}

@keyframes highlightPulse {
  0% {
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.2), rgba(139, 92, 246, 0.2));
    border-color: rgba(59, 130, 246, 0.5);
    transform: scale(1.02);
  }
  50% {
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.15), rgba(139, 92, 246, 0.15));
    border-color: rgba(59, 130, 246, 0.4);
    transform: scale(1.01);
  }
  100% {
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.1), rgba(139, 92, 246, 0.1));
    border-color: rgba(59, 130, 246, 0.3);
    transform: scale(1);
  }
}

/* 通知组件样式 - 使用更好看的样式 */
.notification {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 9999;
  animation: slideInRight 0.3s ease-out, fadeOut 0.3s ease-in 2.7s;
}

.notification .notification-content {
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

.notification.success .notification-content {
  background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%);
  color: #166534;
  border-color: #bbf7d0;
}

.notification.error .notification-content {
  background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%);
  color: #991b1b;
  border-color: #fecaca;
}

.notification.warning .notification-content {
  background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);
  color: #92400e;
  border-color: #fed7aa;
}

.notification i {
  font-size: 1rem;
  flex-shrink: 0;
}

.notification.success i {
  color: #22c55e;
}

.notification.error i {
  color: #ef4444;
}

.notification.warning i {
  color: #f59e0b;
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

/* 文章详情页标签样式 - 专门针对.post-card内的标签 */
.post-card .post-tags .post-item-tag {
  display: inline-flex;
  align-items: center;
  padding: 0.25rem 0.6rem;
  margin-right: 0.5rem;
  margin-bottom: 0.35rem;
  background-color: rgba(249, 250, 251, 0.95);
  color: #111827;
  border-radius: 4px;
  font-size: 0.85rem;
  font-weight: 500;
  text-decoration: none;
  transition: all 0.2s ease;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  letter-spacing: 0.01em;
  position: relative;
  overflow: hidden;
}

/* 使用更和谐的色调，增强对比度 */
.post-card .post-tags .post-item-tag:nth-of-type(3n+1) {
  background-color: rgba(224, 242, 254, 0.95);
  border-color: #7dd3fc;
  color: #0369a1;
}

.post-card .post-tags .post-item-tag:nth-of-type(3n+2) {
  background-color: rgba(237, 233, 254, 0.95);
  border-color: #a78bfa;
  color: #5b21b6;
}

.post-card .post-tags .post-item-tag:nth-of-type(3n+3) {
  background-color: rgba(236, 252, 203, 0.95);
  border-color: #a3e635;
  color: #3f6212;
}

.post-card .post-tags .post-item-tag i {
  margin-right: 0.3rem;
  font-size: 0.85rem;
  transition: all 0.2s ease;
}

.post-card .post-tags .post-item-tag:hover {
  transform: translateY(-1px);
  box-shadow: 0 3px 5px rgba(0, 0, 0, 0.12);
}

.post-card .post-tags .post-item-tag:nth-of-type(3n+1):hover {
  background-color: rgba(224, 242, 254, 1);
  border-color: #38bdf8;
}

.post-card .post-tags .post-item-tag:nth-of-type(3n+2):hover {
  background-color: rgba(237, 233, 254, 1);
  border-color: #8b5cf6;
}

.post-card .post-tags .post-item-tag:nth-of-type(3n+3):hover {
  background-color: rgba(236, 252, 203, 1);
  border-color: #84cc16;
}

.post-card .post-tags .post-item-tag:hover i {
  transform: scale(1.1);
}

/* 添加微妙的标签闪光效果 */
.post-card .post-tags .post-item-tag::after {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: linear-gradient(
    to bottom right,
    rgba(255, 255, 255, 0) 0%,
    rgba(255, 255, 255, 0.1) 77%,
    rgba(255, 255, 255, 0.3) 92%,
    rgba(255, 255, 255, 0) 100%
  );
  transform: rotate(-45deg);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.post-card .post-tags .post-item-tag:hover::after {
  opacity: 1;
  animation: shine 1.5s ease;
}

@keyframes shine {
  0% {
    transform: translateX(-100%) rotate(-45deg);
  }
  100% {
    transform: translateX(100%) rotate(-45deg);
  }
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .post-card .post-tags .post-item-tag {
    background-color: rgba(30, 41, 59, 0.95);
    color: #f8fafc;
    border-color: #475569;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
  }
  
  .post-card .post-tags .post-item-tag:nth-of-type(3n+1) {
    background-color: rgba(3, 105, 161, 0.4);
    border-color: #0ea5e9;
    color: #e0f2fe;
  }
  
  .post-card .post-tags .post-item-tag:nth-of-type(3n+2) {
    background-color: rgba(91, 33, 182, 0.4);
    border-color: #8b5cf6;
    color: #ede9fe;
  }
  
  .post-card .post-tags .post-item-tag:nth-of-type(3n+3) {
    background-color: rgba(63, 98, 18, 0.4);
    border-color: #84cc16;
    color: #ecfccb;
  }
  
  .post-card .post-tags .post-item-tag i {
    color: #cbd5e1;
  }
  
  .post-card .post-tags .post-item-tag:nth-of-type(3n+1) i {
    color: #7dd3fc;
  }
  
  .post-card .post-tags .post-item-tag:nth-of-type(3n+2) i {
    color: #c4b5fd;
  }
  
  .post-card .post-tags .post-item-tag:nth-of-type(3n+3) i {
    color: #bef264;
  }
  
  .post-card .post-tags .post-item-tag:hover {
    box-shadow: 0 3px 5px rgba(0, 0, 0, 0.3);
  }
  
  .post-card .post-tags .post-item-tag:nth-of-type(3n+1):hover {
    background-color: rgba(3, 105, 161, 0.5);
    border-color: #38bdf8;
  }
  
  .post-card .post-tags .post-item-tag:nth-of-type(3n+2):hover {
    background-color: rgba(91, 33, 182, 0.5);
    border-color: #a78bfa;
  }
  
  .post-card .post-tags .post-item-tag:nth-of-type(3n+3):hover {
    background-color: rgba(63, 98, 18, 0.5);
    border-color: #a3e635;
  }
  
  .post-card .post-tags .post-item-tag::after {
    background: linear-gradient(
      to bottom right,
      rgba(255, 255, 255, 0) 0%,
      rgba(255, 255, 255, 0.05) 77%,
      rgba(255, 255, 255, 0.15) 92%,
      rgba(255, 255, 255, 0) 100%
    );
  }
}

.post-card .post-tags .post-item-tag .tag-icon {
  margin-right: 0.3rem;
  font-size: 0.95rem;
  font-weight: 600;
  transition: all 0.2s ease;
}

.post-card .post-tags .post-item-tag:nth-of-type(3n+1) .tag-icon {
  color: #0369a1;
}

.post-card .post-tags .post-item-tag:nth-of-type(3n+2) .tag-icon {
  color: #6d28d9;
}

.post-card .post-tags .post-item-tag:nth-of-type(3n+3) .tag-icon {
  color: #4d7c0f;
}

.post-card .post-tags .post-item-tag:hover .tag-icon {
  transform: scale(1.1);
}

/* 强制缩短分隔符间距 - 最高优先级 */
.content-section-divider {
  margin: 1rem 0 0.5rem 0 !important;
  padding: 0 !important;
  display: block !important;
  background-color: transparent !important;
  border: none !important;
  border-radius: 0 !important;
  border-bottom: 1px solid #3a9c77 !important;
  width: 100% !important;
}

.content-section-divider + p {
  margin-top: 0 !important;
  padding-top: 0 !important;
}

.content-section-divider + * {
  margin-top: 0 !important;
  padding-top: 0 !important;
}

/* 强制设置所有段落的间距 */
.post-content .markdown-body p {
  margin: 0 0 24px 0 !important;      /* 段落间距：扩大到24px */
  padding: 0 !important;
  line-height: 1.5 !important;        /* 行距：缩小到1.5 */
  font-size: 15px !important;         /* 字体大小：15px */
}

/* 段落之间的基本间距 */
.post-content .markdown-body p + p {
  margin-top: 0 !important;           /* 移除额外的上边距，使用bottom margin */
}

/* 分隔符前的段落底部间距也要缩短 */
.post-content .markdown-body p + .content-section-divider {
  margin-top: 1rem !important;
}

/* 超高优先级覆盖其他CSS文件中的规则 */
.post-detail-page .post-content .markdown-body .content-section-divider {
  margin: 1rem 0 0.5rem 0 !important;
  padding: 0 !important;
  width: 100% !important;
  border-bottom: 1px solid #3a9c77 !important;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .post-detail-page .post-content .markdown-body .content-section-divider {
    margin: 1rem 0 0.5rem 0 !important;
    padding: 0 !important;
  }
}

/* MdPreview组件图片尺寸覆盖 - 解决图片过大问题 */
:deep(.md-editor-preview img),
:deep(.custom-markdown-preview img),
:deep(.md-editor-preview .md-editor-preview-wrapper img),
:deep(.md-preview-wrapper img),
:deep(.md-zoom) {
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

:deep(.md-editor-preview img:hover),
:deep(.custom-markdown-preview img:hover),
:deep(.md-zoom:hover) {
  transform: scale(1.01) !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12) !important;
}

/* 移动端图片适配 */
@media (max-width: 768px) {
  :deep(.md-editor-preview img),
  :deep(.custom-markdown-preview img),
  :deep(.md-zoom) {
    max-width: min(350px, 90%) !important;
    margin: 0.6rem auto !important;
  }
}

/* 最高优先级 - 强制缩短分隔符间距 */
.post-detail-page .post-card .post-content .markdown-body .content-section-divider,
.post-detail-page .post-content .markdown-body .content-section-divider,
.post-content .markdown-body .content-section-divider,
.markdown-body .content-section-divider,
.content-section-divider {
  margin: 1rem 0 0.5rem 0 !important;
  padding: 0 !important;
  display: block !important;
  background-color: transparent !important;
  border: none !important;
  border-radius: 0 !important;
  border-bottom: 1px solid #3a9c77 !important;
  width: 100% !important;
}

/* 分隔符后的所有元素 */
.post-detail-page .post-card .post-content .markdown-body .content-section-divider + *,
.post-detail-page .post-content .markdown-body .content-section-divider + *,
.post-content .markdown-body .content-section-divider + *,
.markdown-body .content-section-divider + *,
.content-section-divider + * {
  margin-top: 0 !important;
  padding-top: 0 !important;
}

/* 分隔符前的段落 */
.post-detail-page .post-card .post-content .markdown-body p + .content-section-divider,
.post-detail-page .post-content .markdown-body p + .content-section-divider,
.post-content .markdown-body p + .content-section-divider,
.markdown-body p + .content-section-divider,
p + .content-section-divider {
  margin-top: 1rem !important;
}

/* 评论高亮样式 */
.comment-highlight {
  background-color: rgba(59, 130, 246, 0.1) !important;
  border: 2px solid #3b82f6 !important;
  border-radius: 8px !important;
  animation: highlightPulse 2s ease-in-out !important;
  position: relative !important;
}

.comment-highlight::before {
  content: "📍";
  position: absolute;
  top: -10px;
  left: -10px;
  background: #3b82f6;
  color: white;
  border-radius: 50%;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  z-index: 10;
  animation: bounce 2s ease-in-out infinite;
}

@keyframes highlightPulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.4);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(59, 130, 246, 0.1);
  }
}

@keyframes bounce {
  0%, 20%, 50%, 80%, 100% {
    transform: translateY(0);
  }
  40% {
    transform: translateY(-5px);
  }
  60% {
    transform: translateY(-3px);
  }
}

/* 回复高亮样式 */
.reply-item.comment-highlight {
  background-color: rgba(34, 197, 94, 0.1) !important;
  border: 2px solid #22c55e !important;
}

.reply-item.comment-highlight::before {
  background: #22c55e;
}

/* 增强型工具栏样式 */
.enhanced-toolbar {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 0 0.75rem;
  margin: 0 !important;
  background: #f8fafc;
  border-top: none;
  border-radius: 0 0 8px 8px;
}

/* 重写comment-tips的margin */
.comment-tips.enhanced-toolbar {
  margin-top: 0.25rem !important;
  margin-bottom: 0 !important;
}

/* 统一工具栏 - 单行布局 */
.unified-toolbar {
  display: flex;
  align-items: center;
  gap: 1rem;
  justify-content: space-between;
}

.toolbar-section {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: nowrap;
}

.toolbar-group {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  background: white;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 12px;
  color: #4b5563;
  flex-shrink: 0;
}

.toolbar-btn:hover {
  background: #f3f4f6;
  border-color: #9ca3af;
  color: #374151;
  transform: translateY(-1px);
}

.toolbar-btn:active {
  transform: translateY(0);
  background: #e5e7eb;
}

.toolbar-btn.active {
  background: #3b82f6;
  border-color: #3b82f6;
  color: white;
}

.toolbar-btn.active:hover {
  background: #2563eb;
  border-color: #2563eb;
}

.toolbar-divider {
  width: 1px;
  height: 20px;
  background: #d1d5db;
  margin: 0 0.25rem;
  flex-shrink: 0;
}

.tips-text {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.8rem;
  color: #6b7280;
  white-space: nowrap;
  flex-shrink: 0;
}

.tips-text i {
  font-size: 0.9rem;
  color: #9ca3af;
}

/* 表情选择器样式 */
.emoji-picker {
  position: fixed;
  z-index: 9999;
  background: white;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  padding: 0.75rem;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  width: 240px;
  max-height: none;
  overflow-x: hidden;
  overflow-y: visible;
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 0.4rem;
  max-width: 100%;
  overflow-x: hidden;
}

/* 回复表情选择器的网格调整 */
.reply-emoji-picker .emoji-grid {
  grid-template-columns: repeat(8, 1fr);
  gap: 0.4rem;
  overflow-x: hidden;
  max-width: 100%;
}

.reply-emoji-picker .emoji-item {
  width: 22px;
  height: 22px;
  font-size: 15px;
  min-width: 0;
  flex-shrink: 1;
}

.emoji-item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  font-size: 15px;
  transition: all 0.2s ease;
  min-width: 0;
  flex-shrink: 1;
}

.emoji-item:hover {
  background: #f3f4f6;
  border-color: #d1d5db;
  transform: scale(1.1);
}

.emoji-item:active {
  transform: scale(0.95);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .enhanced-toolbar {
    padding: 0.5rem;
    gap: 0.5rem;
  }
  
  .unified-toolbar {
    flex-direction: column;
    align-items: stretch;
    gap: 0.75rem;
  }
  
  .toolbar-section {
    gap: 0.25rem;
    justify-content: center;
  }
  
  .toolbar-btn {
    width: 24px;
    height: 24px;
    font-size: 11px;
  }
  
  .toolbar-divider {
    height: 16px;
    margin: 0 0.15rem;
  }
  
  .tips-text {
    font-size: 0.75rem;
    justify-content: center;
  }
  
  .emoji-grid {
    grid-template-columns: repeat(8, 1fr);
    gap: 0.25rem;
  }
  
  .emoji-item {
    width: 28px;
    height: 28px;
    font-size: 16px;
  }
}

/* 回复表情按钮样式 */
.emoji-btn-reply {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  background: white;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 12px;
  color: #4b5563;
  margin-right: 0.5rem;
}

.emoji-btn-reply:hover {
  background: #f3f4f6;
  border-color: #9ca3af;
  color: #374151;
  transform: translateY(-1px);
}

.emoji-btn-reply:active {
  transform: translateY(0);
  background: #e5e7eb;
}

.emoji-btn-reply.active {
  background: #3b82f6;
  border-color: #3b82f6;
  color: white;
}

.emoji-btn-reply.active:hover {
  background: #2563eb;
  border-color: #2563eb;
}

/* 回复表情选择器样式 */
.reply-emoji-picker {
  position: fixed;
  z-index: 9999;
  width: 240px;
  margin-top: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  background: white;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  padding: 0.75rem;
  max-height: none;
  overflow-x: hidden;
  overflow-y: visible;
}

/* 移除了show-left类，现在使用JavaScript动态定位 */

/* 为回复表单添加相对定位 */
.reply-form .comment-input-area,
.reply-to-reply-form .comment-input-area {
  position: relative;
}

/* 移动端表情选择器调整 */
@media (max-width: 768px) {
  .emoji-picker,
  .reply-emoji-picker {
    position: fixed;
    top: 50%;
    left: 50%;
    right: auto;
    transform: translate(-50%, -50%);
    width: 240px;
    max-height: 300px;
    box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
    border: 2px solid #3b82f6;
  }
  
  /* 移动端添加遮罩 */
  .emoji-picker::before,
  .reply-emoji-picker::before {
    content: '';
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.3);
    z-index: -1;
  }
}

/* 编辑按钮加载状态样式 */
.action-btn.loading {
  background: #f9fafb !important;
  border-color: #e5e7eb !important;
  color: #6b7280 !important;
  cursor: not-allowed !important;
  transform: none !important;
  opacity: 0.8;
  position: relative;
  overflow: hidden;
}

.action-btn.loading:hover {
  background: #f9fafb !important;
  border-color: #e5e7eb !important;
  color: #6b7280 !important;
  transform: none !important;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1) !important;
}

.action-btn.loading::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.6), transparent);
  animation: shimmer 1.5s infinite;
}

.action-btn .spinning {
  animation: spin 1s linear infinite;
}

@keyframes shimmer {
  0% { left: -100%; }
  100% { left: 100%; }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 帖子详情页标签样式优化 */
.post-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin: 0.75rem 0 1rem 0;
  padding: 0;
}

.post-detail-tag {
  display: inline-flex;
  align-items: center;
  padding: 0.25rem 0.6rem;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  text-decoration: none;
  font-size: 0.8125rem;
  font-weight: 500;
  color: #475569;
  transition: all 0.2s ease;
  position: relative;
  overflow: hidden;
}

.post-detail-tag::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.4), transparent);
  transition: left 0.6s;
}

.post-detail-tag:hover::before {
  left: 100%;
}

.post-detail-tag:hover {
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  color: white;
  border-color: #3b82f6;
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(59, 130, 246, 0.25);
}

.post-detail-tag .tag-icon {
  margin-right: 0.4rem;
  font-size: 0.875rem;
  font-weight: 600;
  transition: all 0.2s ease;
  color: #64748b;
}

.post-detail-tag:hover .tag-icon {
  color: white;
  transform: scale(1.1);
}

/* 不同位置标签的特殊效果 */
.post-detail-tag:nth-child(3n+1):hover {
  background: linear-gradient(135deg, #f59e0b, #f97316);
  border-color: #f59e0b;
  box-shadow: 0 8px 25px rgba(245, 158, 11, 0.25);
}

.post-detail-tag:nth-child(3n+2):hover {
  background: linear-gradient(135deg, #10b981, #06b6d4);
  border-color: #10b981;
  box-shadow: 0 8px 25px rgba(16, 185, 129, 0.25);
}

.post-detail-tag:nth-child(3n+3):hover {
  background: linear-gradient(135deg, #8b5cf6, #a855f7);
  border-color: #8b5cf6;
  box-shadow: 0 8px 25px rgba(139, 92, 246, 0.25);
}

/* 移动端适配 */
@media (max-width: 768px) {
  .post-tags {
    gap: 0.5rem;
    margin: 0.75rem 0;
  }
  
  .post-detail-tag {
    padding: 0.2rem 0.5rem;
    font-size: 0.75rem;
    border-radius: 3px;
  }
  
  .post-detail-tag .tag-icon {
    margin-right: 0.3rem;
    font-size: 0.8125rem;
  }
}


/* 修复标题和标签容器的对齐 */
.post-title-container {
  margin-bottom: 1rem;
}

.post-title-container .post-title {
  margin: 0 0 0.5rem 0 !important;
  padding: 0 !important;
}

.post-title-container .post-tags {
  margin: 0 !important;
}

</style>



