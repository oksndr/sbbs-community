import { defineStore } from 'pinia'
import { useUserStore } from './user'
import { API } from '../utils/api'

export const usePostStore = defineStore('post', {
  state: () => ({
    posts: [],
    currentPost: null,
    comments: [],
    isLoading: false,
    error: null,
    totalPosts: 0,
    currentPage: 1,
    pageSize: 15,
    lastId: null,
    lastUpdated: null,
    isLoadMoreDisabled: false,
    filters: {
      tagId: null,
      search: '',
      sort: 'newest' // newest, popular, etc.
    },
    isLiked: false,
    isDisliked: false,
    commentsLastId: null
  }),
  
  getters: {
    formattedPosts: (state) => {
      return state.posts.map(post => ({
        ...post,
        createdAt: new Date(post.createTime).toLocaleString('zh-CN'),
        excerpt: post.content.substring(0, 150) + (post.content.length > 150 ? '...' : '')
      }))
    },
    
    hasMorePosts: (state) => {
      return state.posts.length > 0 && !state.isLoadMoreDisabled
    }
  },
  
  actions: {
    // 清空评论列表和重置评论分页状态
    clearComments() {
      this.comments = []
      this.commentsLastId = null
      return true
    },
    
    // 获取帖子列表
    async fetchPosts(page = 1, pageSize = 15, filters = {}) {
      this.isLoading = true
      this.error = null
      
      // 合并过滤条件
      const mergedFilters = { ...this.filters, ...filters }
      this.filters = mergedFilters
      this.currentPage = page
      this.pageSize = pageSize
      
      try {
        // 构建请求参数
        const params = {
          pageSize: pageSize
        };
        
        // 如果不是第一页，使用lastId和lastUpdated进行分页
        if (page > 1 && this.lastId) {
          params.lastId = this.lastId;
          if (this.lastUpdated) {
            params.lastUpdated = this.lastUpdated;
          }
        }
        
        // 添加标签过滤
        if (mergedFilters.tagId) {
          params.tagId = mergedFilters.tagId;
        }
        
        // 添加搜索过滤
        if (mergedFilters.search) {
          params.search = mergedFilters.search;
        }
        
        // 使用API服务获取帖子列表
        const data = await API.posts.getList(params);
        
        if (data.code !== 200) {
          throw new Error(data.msg || '获取帖子失败');
        }
        
        // 处理第一页和加载更多的情况
        if (page === 1) {
          this.posts = data.data.list;
        } else {
          // 过滤掉可能重复的帖子
          const newPosts = data.data.list.filter(
            newPost => !this.posts.some(existingPost => existingPost.id === newPost.id)
          );
          this.posts = [...this.posts, ...newPosts];
        }
        
        // 更新分页信息
        if (data.data.list && data.data.list.length > 0) {
          this.lastId = data.data.lastId;
          this.lastUpdated = data.data.lastUpdated;
        }
        
        if (data.data.list.length < pageSize) {
          this.isLoadMoreDisabled = true; // 没有更多数据
        } else {
          this.isLoadMoreDisabled = false;
        }
        
        return { success: true, posts: data.data.list };
      } catch (error) {
        this.error = error.message || '获取帖子失败，请稍后重试';
        return { success: false, error: this.error };
      } finally {
        this.isLoading = false;
      }
    },
    
    // 获取单个帖子详情
    async fetchPostById(id) {
      this.isLoading = true;
      this.error = null;
      
      // 只有在切换到不同帖子时才清空评论，避免页面闪烁
      if (this.currentPost?.id !== parseInt(id)) {
        this.comments = []; // 只在切换帖子时清除评论
      }
      
      try {
        // 使用API服务获取帖子详情
        const data = await API.posts.getDetail(id);
        
        if (data.code !== 200) {
          throw new Error(data.msg || '获取帖子详情失败');
        }
        
        // 从响应中提取帖子数据和点赞状态
        const postData = data.data.post;
        
        // 处理标签数据
        let tags = [];
        if (postData.tags) {
          // 如果标签是数组
          if (Array.isArray(postData.tags)) {
            tags = postData.tags.map(tag => {
              // 如果标签是对象
              if (typeof tag === 'object' && tag !== null) {
                return {
                  id: tag.id || tag.tagId || '',
                  name: tag.name || tag.tagName || ''
                };
              } 
              // 如果标签是字符串
              else if (typeof tag === 'string') {
                return {
                  id: tag,
                  name: tag
                };
              }
              return tag;
            });
          } 
          // 如果标签是字符串，可能是逗号分隔的标签
          else if (typeof postData.tags === 'string') {
            tags = postData.tags.split(',').map(tag => ({
              id: tag.trim(),
              name: tag.trim()
            }));
          }
        }
        
        // 转换帖子数据结构以匹配页面组件期望的格式
        this.currentPost = {
          ...postData,
          author: {
            id: postData.userId,
            username: postData.username,
            avatar: postData.avatar
          },
          createdAt: postData.created,
          updatedAt: postData.updated,
          // 使用处理后的标签
          tags: tags
        };
        
        // 添加点赞状态到 store 中，供组件使用
        this.isLiked = data.data.liked || false;
        this.isDisliked = data.data.disLiked || false;
        
        return { 
          success: true, 
          post: this.currentPost,
          liked: this.isLiked,
          disliked: this.isDisliked
        };
      } catch (error) {
        this.error = error.message || '获取帖子详情失败，请稍后重试';
        // 只在出错时清空数据
        this.currentPost = null;
        return { success: false, error: this.error };
      } finally {
        this.isLoading = false;
      }
    },
    
    // 创建新帖子
    async createPost(postData) {
      const userStore = useUserStore();
      
      if (!userStore.isLoggedIn) {
        return { success: false, error: '用户未登录' };
      }
      
      this.isLoading = true;
      this.error = null;
      
      try {
        // 使用API服务创建帖子
        const data = await API.posts.create(postData);
        
        if (data.code !== 200) {
          throw new Error(data.msg || '创建帖子失败');
        }
        
        return { success: true, post: data.data };
      } catch (error) {
        this.error = error.message || '创建帖子失败，请稍后重试';
        return { success: false, error: this.error };
      } finally {
        this.isLoading = false;
      }
    },
    
    // 更新帖子
    async updatePost(id, postData) {
      const userStore = useUserStore();
      
      if (!userStore.isLoggedIn) {
        return { success: false, error: '用户未登录' };
      }
      
      this.isLoading = true;
      this.error = null;
      
      try {
        // 使用API服务更新帖子
        const data = await API.posts.update(id, postData);
        
        if (data.code !== 200) {
          throw new Error(data.msg || '更新帖子失败');
        }
        
        // 更新当前查看的帖子（如果有）
        if (this.currentPost && this.currentPost.id === id) {
          this.currentPost = data.data;
        }
        
        return { success: true, post: data.data };
      } catch (error) {
        this.error = error.message || '更新帖子失败，请稍后重试';
        return { success: false, error: this.error };
      } finally {
        this.isLoading = false;
      }
    },
    
    // 删除帖子
    async deletePost(id) {
      const userStore = useUserStore();
      
      if (!userStore.isLoggedIn) {
        return { success: false, error: '用户未登录' };
      }
      
      this.isLoading = true;
      this.error = null;
      
      try {
        // 使用API服务删除帖子
        const data = await API.posts.delete(id);
        
        if (data.code !== 200) {
          throw new Error(data.msg || '删除帖子失败');
        }
        
        // 从列表中移除帖子
        this.posts = this.posts.filter(post => post.id !== id);
        
        // 清空当前帖子（如果是被删除的帖子）
        if (this.currentPost && this.currentPost.id === id) {
          this.currentPost = null;
        }
        
        return { success: true };
      } catch (error) {
        this.error = error.message || '删除帖子失败，请稍后重试';
        return { success: false, error: this.error };
      } finally {
        this.isLoading = false;
      }
    },
    
    // 获取帖子评论
    async fetchComments(postId, loadMore = false) {
      if (!loadMore) {
        // 如果不是加载更多，则重置评论状态
        this.comments = [];
        this.commentsLastId = null;
      }
      
      this.error = null;
      
      try {
        // 构建请求参数
        const params = {
          postId,
          limit: 10 // 每次加载10条评论
        };
        
        // 如果是加载更多，则使用lastId进行分页
        if (loadMore && this.commentsLastId) {
          params.lastId = this.commentsLastId;
        }
        
        console.log('获取评论，参数:', params);
        
        // 使用API服务获取评论
        let url = `${API.comments.getListUrl(postId)}?limit=${params.limit}`;
        if (loadMore && this.commentsLastId) {
          url += `&lastId=${this.commentsLastId}`;
        }
        
        const response = await fetch(url);
        const data = await response.json();
        
        console.log('评论API响应:', data);
        
        if (data.code !== 200) {
          throw new Error(data.msg || '获取评论失败');
        }
        
        // 从响应中提取评论数据
        const commentsData = data.data.comments || [];
        
        // 转换评论数据结构以匹配页面组件期望的格式
        const formattedComments = commentsData.map(comment => ({
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
        }));
        
        console.log('格式化后的评论:', formattedComments);
        
        // 更新评论列表
        if (loadMore) {
          // 过滤掉可能重复的评论
          const newComments = formattedComments.filter(
            newComment => !this.comments.some(existingComment => existingComment.id === newComment.id)
          );
          this.comments = [...this.comments, ...newComments];
        } else {
          this.comments = formattedComments;
        }
        
        // 更新lastId，用于下次加载更多
        if (formattedComments.length > 0) {
          this.commentsLastId = formattedComments[formattedComments.length - 1].id;
        }
        
        // 判断是否还有更多评论
        const hasMoreComments = formattedComments.length >= params.limit;
        
        console.log('评论加载完成，lastId:', this.commentsLastId, '是否有更多:', hasMoreComments);
        
        return { 
          success: true, 
          comments: this.comments, 
          hasMoreComments,
          lastId: this.commentsLastId
        };
      } catch (error) {
        console.error('获取评论失败:', error);
        this.error = error.message || '获取评论失败，请稍后重试';
        return { success: false, error: this.error };
      }
    },
    
    // 添加评论
    async addComment(postId, content) {
      const userStore = useUserStore();
      if (!userStore.isLoggedIn) {
        return { success: false, error: '用户未登录', comment: null };
      }
      
      this.error = null;
      
      try {
        const commentDataToApi = { content };
        // API返回格式为 {code: 200, msg: "评论发布成功", data: {commentId: 78, page: 2}}
        const responseFromApi = await API.comments.add(postId, commentDataToApi);
        
        console.log('API响应原始数据:', responseFromApi);
        
        // 检查code是否为200，表示成功
        if (responseFromApi.code === 200) {
          // 从API响应中提取commentId和page信息
          const { commentId, page } = responseFromApi.data || {};
          
          // 构造新评论对象（包含真实的API返回ID）
          const newComment = {
            id: commentId || 'temp-' + Date.now(), // 使用API返回的真实ID
            content: content,
            author: {
              id: userStore.user.id,
              username: userStore.user.username,
              avatar: userStore.user.avatar || '/img/default-avatar.png'
            },
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            likeCount: 0,
            dislikeCount: 0,
            replyCount: 0,
            isLiked: false,
            isDisliked: false,
            showReplies: false,
            loadingReplies: false,
            replies: []
          };
          
          // 显示积分奖励 - 已禁用，由页面层面处理
          // pointsManager.showPointsReward('comment');
          
          return { 
            success: true, 
            comment: newComment,
            commentId, // 返回API提供的评论ID
            page, // 返回API提供的页码信息
            shouldAddToCurrentPage: !page || page === 1 // 判断是否应该添加到当前页
          };
        } else {
          throw new Error(responseFromApi.msg || '发表评论失败');
        }
      } catch (err) {
        console.error('添加评论失败:', err);
        this.error = err.message || '发表评论失败';
        return { success: false, error: err.message || '发表评论失败', comment: null };
      }
    },
    
    // 获取评论回复
    async fetchReplies(commentId) {
      this.error = null;
      
      try {
        // 找到对应的评论
        const comment = this.comments.find(c => c.id === commentId);
        if (!comment) {
          throw new Error('评论不存在');
        }
        
        // 标记为加载中
        comment.loadingReplies = true;
        
        // 使用API服务获取回复
        const response = await fetch(API.comments.getRepliesUrl(commentId));
        const data = await response.json();
        
        if (data.code !== 200) {
          throw new Error(data.msg || '获取回复失败');
        }
        
        // 从响应中提取回复数据
        const repliesData = data.data || [];
        
        // 转换回复数据结构以匹配页面组件期望的格式
        const formattedReplies = repliesData.map(reply => ({
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
          isDisliked: !!reply.isDisliked,
          // 如果有回复对象，添加replyTo属性
          replyTo: reply.replyToId ? {
            id: reply.replyToId,
            username: reply.replyToUsername
          } : null
        }));
        
        // 更新评论的回复列表
        comment.replies = formattedReplies;
        comment.showReplies = true;
        
        return { 
          success: true, 
          replies: formattedReplies
        };
      } catch (error) {
        this.error = error.message || '获取回复失败，请稍后重试';
        return { success: false, error: this.error };
      } finally {
        // 无论成功失败，都取消加载状态
        const comment = this.comments.find(c => c.id === commentId);
        if (comment) {
          comment.loadingReplies = false;
        }
      }
    },
    
    // 添加回复
    async addReply(commentId, content, replyToId = null) {
      const userStore = useUserStore();
      
      if (!userStore.isLoggedIn) {
        return { success: false, error: '用户未登录' };
      }

      // 智能trim处理：检查是否以@mention结尾
      let processedContent = content;
      if (processedContent) {
        // 先去除前导空格
        processedContent = processedContent.replace(/^\s+/, '');
        
        // 检查是否以@mention格式结尾（@用户名 ）
        const endsWithMention = /@\w+\s*$/.test(processedContent);
        
        if (endsWithMention) {
          // 如果以@mention结尾，只保留一个尾部空格
          processedContent = processedContent.replace(/\s+$/, ' ');
        } else {
          // 否则正常去除尾部空格
          processedContent = processedContent.replace(/\s+$/, '');
        }
      }

      if (!processedContent || !processedContent.trim()) {
        return { success: false, error: '回复内容不能为空' };
      }
      
      this.error = null;
      
      try {
        // 构建回复数据，按照用户提供的API格式
        const replyData = {
          postId: this.currentPost?.id?.toString() || '', // 确保postId是字符串
          parentid: commentId.toString(), // 使用parentid而不是parentId
          content: processedContent
        };
        
        console.log('🔄 发送回复请求:', {
          commentId: commentId,
          data: replyData
        });
        
        // 使用API服务添加回复
        const data = await API.comments.addReply(commentId, replyData);
        
        console.log('🔄 回复API响应:', data);
        
        if (data.code !== 200) {
          throw new Error(data.msg || '发表回复失败');
        }
        
        console.log('✅ 回复API调用成功');
        
        return { 
          success: true, 
          message: '回复发表成功'
        };
      } catch (error) {
        console.error('❌ 发表回复失败:', error);
        this.error = error.message || '发表回复失败';
        return { success: false, error: this.error };
      }
    },
    
    // 点赞帖子
    async likePost(postId, isLike) {
      const userStore = useUserStore();
      
      if (!userStore.isLoggedIn) {
        return { success: false, error: '用户未登录' };
      }
      
      try {
        // 更新本地状态（乐观更新）
        const wasLiked = this.isLiked;
        const wasDisliked = this.isDisliked;
        
        if (isLike) {
          // 点赞 - 立即更新UI
          this.isLiked = true;
          if (this.currentPost) {
            this.currentPost.likeCount = (this.currentPost.likeCount || 0) + 1;
          }
          
          // 如果之前点过踩，取消点踩
          if (wasDisliked) {
            this.isDisliked = false;
            if (this.currentPost) {
              this.currentPost.dislikeCount = Math.max(0, (this.currentPost.dislikeCount || 0) - 1);
            }
            // 先取消点踩
            await API.postInteractions.cancelDislike(postId);
          }
          
          // 发送点赞请求
          const data = await API.postInteractions.like(postId);
          
          if (data.code !== 200) {
            // 恢复状态
            this.isLiked = wasLiked;
            this.isDisliked = wasDisliked;
            if (this.currentPost) {
              this.currentPost.likeCount = Math.max(0, (this.currentPost.likeCount || 0) - 1);
              if (wasDisliked) {
                this.currentPost.dislikeCount = (this.currentPost.dislikeCount || 0) + 1;
              }
            }
            throw new Error(data.msg || '点赞失败');
          }
        } else {
          // 取消点赞 - 立即更新UI
          this.isLiked = false;
          if (this.currentPost) {
            this.currentPost.likeCount = Math.max(0, (this.currentPost.likeCount || 0) - 1);
          }
          
          // 发送取消点赞请求
          const data = await API.postInteractions.cancelLike(postId);
          
          if (data.code !== 200) {
            // 恢复状态
            this.isLiked = wasLiked;
            if (this.currentPost) {
              this.currentPost.likeCount = (this.currentPost.likeCount || 0) + 1;
            }
            throw new Error(data.msg || '取消点赞失败');
          }
        }
        
        return { success: true };
      } catch (error) {
        // 发生错误时恢复状态
        this.error = error.message || '操作失败，请稍后重试';
        return { success: false, error: this.error };
      }
    },
    
    // 点踩帖子
    async dislikePost(postId, isDislike) {
      const userStore = useUserStore();
      
      if (!userStore.isLoggedIn) {
        return { success: false, error: '用户未登录' };
      }
      
      try {
        // 更新本地状态（乐观更新）
        const wasLiked = this.isLiked;
        const wasDisliked = this.isDisliked;
        
        if (isDislike) {
          // 点踩 - 立即更新UI
          this.isDisliked = true;
          if (this.currentPost) {
            this.currentPost.dislikeCount = (this.currentPost.dislikeCount || 0) + 1;
          }
          
          
          // 如果之前点过赞，取消点赞
          if (wasLiked) {
            this.isLiked = false;
            if (this.currentPost) {
              this.currentPost.likeCount = Math.max(0, (this.currentPost.likeCount || 0) - 1);
            }
            // 先取消点赞
            await API.postInteractions.cancelLike(postId);
          }
          
          // 发送点踩请求
          const data = await API.postInteractions.dislike(postId);
          
          if (data.code !== 200) {
            // 恢复状态
            this.isLiked = wasLiked;
            this.isDisliked = wasDisliked;
            if (this.currentPost) {
              this.currentPost.dislikeCount = Math.max(0, (this.currentPost.dislikeCount || 0) - 1);
              if (wasLiked) {
                this.currentPost.likeCount = (this.currentPost.likeCount || 0) + 1;
              }
            }
            throw new Error(data.msg || '点踩失败');
          }
        } else {
          // 取消点踩 - 立即更新UI
          this.isDisliked = false;
          if (this.currentPost) {
            this.currentPost.dislikeCount = Math.max(0, (this.currentPost.dislikeCount || 0) - 1);
          }
          
          
          // 发送取消点踩请求
          const data = await API.postInteractions.cancelDislike(postId);
          
          if (data.code !== 200) {
            // 恢复状态
            this.isDisliked = wasDisliked;
            if (this.currentPost) {
              this.currentPost.dislikeCount = (this.currentPost.dislikeCount || 0) + 1;
            }
            throw new Error(data.msg || '取消点踩失败');
          }
        }
        
        return { success: true };
      } catch (error) {
        // 发生错误时恢复状态
        this.error = error.message || '操作失败，请稍后重试';
        return { success: false, error: this.error };
      }
    },
    
    // 点赞评论
    async likeComment(commentId, isLike) {
      const userStore = useUserStore();
      
      if (!userStore.isLoggedIn) {
        return { success: false, error: '用户未登录' };
      }
      
      try {
        // 查找评论
        const commentIndex = this.comments.findIndex(c => c.id === commentId);
        if (commentIndex === -1) {
          return { success: false, error: '未找到评论' };
        }
        
        const comment = this.comments[commentIndex];
        const wasLiked = comment.isLiked;
        const wasDisliked = comment.isDisliked;
        
        // 已经在组件中处理了UI更新，这里不再重复更新
        // 只执行API调用
        if (isLike) {
          // 如果之前点过踩，取消点踩
          if (wasDisliked) {
            // 先取消点踩
            await API.comments.cancelDislike(commentId);
          }
          
          // 发送点赞请求
          const data = await API.comments.like(commentId);
          
          if (data.code !== 200) {
            throw new Error(data.msg || '点赞失败');
          }
        } else {
          // 发送取消点赞请求
          const data = await API.comments.cancelLike(commentId);
          
          if (data.code !== 200) {
            throw new Error(data.msg || '取消点赞失败');
          }
        }
        
        return { success: true };
      } catch (error) {
        // 发生错误时记录错误信息
        this.error = error.message || '操作失败，请稍后重试';
        return { success: false, error: this.error };
      }
    },
    
    // 点踩评论
    async dislikeComment(commentId, isDislike) {
      const userStore = useUserStore();
      
      if (!userStore.isLoggedIn) {
        return { success: false, error: '用户未登录' };
      }
      
      try {
        // 查找评论
        const commentIndex = this.comments.findIndex(c => c.id === commentId);
        if (commentIndex === -1) {
          return { success: false, error: '未找到评论' };
        }
        
        const comment = this.comments[commentIndex];
        const wasLiked = comment.isLiked;
        const wasDisliked = comment.isDisliked;
        
        // 已经在组件中处理了UI更新，这里不再重复更新
        // 只执行API调用
        if (isDislike) {
          // 如果之前点过赞，取消点赞
          if (wasLiked) {
            // 先取消点赞
            await API.comments.cancelLike(commentId);
          }
          
          // 发送点踩请求
          const data = await API.comments.dislike(commentId);
          
          if (data.code !== 200) {
            throw new Error(data.msg || '点踩失败');
          }
        } else {
          // 发送取消点踩请求
          const data = await API.comments.cancelDislike(commentId);
          
          if (data.code !== 200) {
            throw new Error(data.msg || '取消点踩失败');
          }
        }
        
        return { success: true };
      } catch (error) {
        // 发生错误时记录错误信息
        this.error = error.message || '操作失败，请稍后重试';
        return { success: false, error: this.error };
      }
    },
    
    // 获取新评论（增量加载）
    async fetchNewComments(postId, lastCommentId) {
      this.error = null;
      
      try {
        // 构建请求参数
        const params = {
          postId,
          limit: 10, // 每次加载10条评论
          afterId: lastCommentId // 获取指定ID之后的评论
        };
        
        console.log('获取新评论，参数:', params);
        
        // 使用API服务获取新评论
        let url = `${API.comments.getListUrl(postId)}?limit=${params.limit}`;
        if (lastCommentId) {
          url += `&afterId=${lastCommentId}`;
        }
        
        const response = await fetch(url);
        const data = await response.json();
        
        console.log('新评论API响应:', data);
        
        if (data.code !== 200) {
          throw new Error(data.msg || '获取新评论失败');
        }
        
        // 从响应中提取评论数据
        const commentsData = data.data.comments || [];
        
        // 转换评论数据结构以匹配页面组件期望的格式
        const formattedComments = commentsData.map(comment => ({
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
        }));
        
        console.log('格式化后的新评论:', formattedComments);
        
        return { 
          success: true, 
          newComments: formattedComments
        };
      } catch (error) {
        console.error('获取新评论失败:', error);
        this.error = error.message || '获取新评论失败，请稍后重试';
        return { success: false, error: this.error };
      }
    },
    
    // 获取新回复（增量加载）
    async fetchNewReplies(commentId, lastReplyId) {
      this.error = null;
      
      try {
        // 构建请求参数
        const params = {
          commentId,
          afterId: lastReplyId // 获取指定ID之后的回复
        };
        
        console.log('获取新回复，参数:', params);
        
        // 使用API服务获取新回复
        let url = `${API.comments.getRepliesUrl(commentId)}?limit=${params.limit}`;
        if (lastReplyId) {
          url += `&afterId=${lastReplyId}`;
        }
        
        const response = await fetch(url);
        const data = await response.json();
        
        console.log('新回复API响应:', data);
        
        if (data.code !== 200) {
          throw new Error(data.msg || '获取新回复失败');
        }
        
        // 从响应中提取回复数据
        const repliesData = data.data || [];
        
        // 转换回复数据结构以匹配页面组件期望的格式
        const formattedReplies = repliesData.map(reply => ({
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
          isDisliked: !!reply.isDisliked,
          // 如果有回复对象，添加replyTo属性
          replyTo: reply.replyToId ? {
            id: reply.replyToId,
            username: reply.replyToUsername
          } : null
        }));
        
        console.log('格式化后的新回复:', formattedReplies);
        
        return { 
          success: true, 
          newReplies: formattedReplies
        };
      } catch (error) {
        console.error('获取新回复失败:', error);
        this.error = error.message || '获取新回复失败，请稍后重试';
        return { success: false, error: this.error };
      }
    },
    
    // 获取最新评论（瀑布式加载）
    async fetchLatestComments(postId) {
      this.error = null;
      
      try {
        // 构建请求参数
        const params = {
          postId,
          limit: 10 // 每次加载10条评论
        };
        
        console.log('获取最新评论，参数:', params);
        
        // 使用API服务获取最新评论
        let url = `${API.comments.getListUrl(postId)}?limit=${params.limit}&sort=newest`;
        
        const response = await fetch(url);
        const data = await response.json();
        
        console.log('最新评论API响应:', data);
        
        if (data.code !== 200) {
          throw new Error(data.msg || '获取最新评论失败');
        }
        
        // 从响应中提取评论数据
        const commentsData = data.data.comments || [];
        
        // 转换评论数据结构以匹配页面组件期望的格式
        const formattedComments = commentsData.map(comment => ({
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
        }));
        
        console.log('格式化后的最新评论:', formattedComments);
        
        return { 
          success: true, 
          comments: formattedComments
        };
      } catch (error) {
        console.error('获取最新评论失败:', error);
        this.error = error.message || '获取最新评论失败，请稍后重试';
        return { success: false, error: this.error };
      }
    }
  }
}) 