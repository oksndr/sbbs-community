// 获取存储的token
const getToken = () => {
  if (process.client) {
    // 客户端环境：从localStorage获取
    let token = localStorage.getItem('token');

    // 如果localStorage没有，尝试从cookie获取
    if (!token) {
      const cookieMatch = document.cookie.match(/(?:^|;\s*)Authorization=([^;]*)/);
      token = cookieMatch ? cookieMatch[1] : null;

      // 如果cookie中的token有Bearer前缀，去掉它
      if (token && token.startsWith('Bearer ')) {
        token = token.substring(7);
      }
    }

    return token || '';
  } else {
    // 服务端环境：更可靠的token获取方式
    try {
      // 方法1：尝试从useNuxtApp获取
      const nuxtApp = useNuxtApp?.();
      if (nuxtApp?.ssrContext?.event?.node?.req?.headers?.cookie) {
        const cookieString = nuxtApp.ssrContext.event.node.req.headers.cookie;

        // 尝试从Authorization cookie获取
        const authMatch = cookieString.match(/Authorization=([^;]*)/);
        if (authMatch) {
          let token = decodeURIComponent(authMatch[1]);
          if (token.startsWith('Bearer ')) {
            token = token.substring(7);
          }
          console.log('SSR获取到Authorization token:', token ? '已找到' : '未找到');
          return token;
        }

        // 尝试从token cookie获取
        const tokenMatch = cookieString.match(/token=([^;]*)/);
        if (tokenMatch) {
          const token = decodeURIComponent(tokenMatch[1]);
          console.log('SSR获取到token cookie:', token ? '已找到' : '未找到');
          return token;
        }
      }

      // 方法2：如果上面的方法失败，尝试直接从useCookie获取
      if (typeof useCookie !== 'undefined') {
        const authCookie = useCookie('Authorization', { default: () => null });
        if (authCookie.value) {
          let token = authCookie.value;
          if (token.startsWith('Bearer ')) {
            token = token.substring(7);
          }
          console.log('SSR从authCookie获取到token:', token ? '已找到' : '未找到');
          return token;
        }

        const tokenCookie = useCookie('token', { default: () => null });
        if (tokenCookie.value) {
          console.log('SSR从tokenCookie获取到token:', tokenCookie.value ? '已找到' : '未找到');
          return tokenCookie.value;
        }
      }

      console.log('SSR未找到任何token');
    } catch (error) {
      console.error('SSR获取token失败:', error);
    }

    return '';
  }
};

// SBBS社区API封装

// 获取API基础URL的函数 - 修复SSR问题
const getApiBaseUrl = () => {
  // 在服务端环境：根据开发/生产环境使用不同地址
  if (process.server || typeof window === 'undefined') {
    // 在服务端，根据环境变量确定API地址
    const isDev = process.env.NODE_ENV === 'development';
    return isDev
      ? (process.env.SBBS_DEV_API_URL || 'http://localhost:12367')
      : (process.env.SBBS_API_URL || 'http://' +
            'example:port');
  } else {
    // 在客户端环境：使用代理路径
    return '/api';
  }
};

// 动态获取API基础URL的函数（保持向后兼容）
const getAPI_BASE_URL = () => {
  return getApiBaseUrl();
};

// API请求封装
export const API = {
  // 帖子相关接口
  posts: {
    /**
     * 获取帖子列表
     * @param {Object} params - 查询参数
     * @param {number} params.pageSize - 每页数量
     * @param {number} params.lastId - 上一页最后一条记录的ID
     * @param {string} params.lastUpdated - 上一页最后一条记录的更新时间
     * @param {number} params.tagId - 标签ID
     * @returns {Promise} - 返回帖子列表的Promise
     */
    async getList(params = {}) {
      let url = `${getAPI_BASE_URL()}/v2/list?pageSize=${params.pageSize || 15}`;

      if (params.lastId) {
        url += `&lastId=${params.lastId}`;
      }

      if (params.lastUpdated) {
        url += `&lastUpdated=${encodeURIComponent(params.lastUpdated)}`;
      }

      if (params.tagId !== null && params.tagId !== undefined) {
        url += `&tagId=${params.tagId}`;
      }

      // 添加token认证头
      const headers = {};
      const token = getToken();

      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      const response = await fetch(url, {
        headers
      });
      return await response.json();
    },

    /**
     * 获取帖子详情
     * @param {number} id - 帖子ID
     * @returns {Promise} - 返回帖子详情的Promise
     */
    async getDetail(id) {
      const headers = {};
      const token = getToken();

      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      const response = await fetch(`${getAPI_BASE_URL()}/v2/post/${id}`, {
        headers
      });
      return await response.json();
    },

    /**
     * 获取帖子详情（别名方法）
     * @param {number} id - 帖子ID
     * @returns {Promise} - 返回帖子详情的Promise
     */
    async getPostById(id) {
      return this.getDetail(id);
    },

    /**
     * 创建新帖子
     * @param {Object} postData - 帖子数据
     * @returns {Promise} - 返回创建结果的Promise
     */
    async create(postData) {
      const response = await fetch(`${getAPI_BASE_URL()}/v1/posts`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${getToken()}`
        },
        body: JSON.stringify(postData)
      });
      return await response.json();
    },

    /**
     * 更新帖子
     * @param {number} id - 帖子ID
     * @param {Object} postData - 帖子数据
     * @returns {Promise} - 返回更新结果的Promise
     */
    async update(id, postData) {
      const response = await fetch(`${getAPI_BASE_URL()}/v2/my/post/${id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${getToken()}`
        },
        body: JSON.stringify(postData)
      });
      return await response.json();
    },

    /**
     * 更新帖子（别名方法）
     * @param {number} id - 帖子ID
     * @param {Object} postData - 帖子数据
     * @returns {Promise} - 返回更新结果的Promise
     */
    async updatePost(id, postData) {
      return this.update(id, postData);
    },

    /**
     * 删除帖子
     * @param {number} id - 帖子ID
     * @returns {Promise} - 返回删除结果的Promise
     */
    async delete(id) {
      const response = await fetch(`${getAPI_BASE_URL()}/v2/my/post/${id}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${getToken()}`
        }
      });
      return await response.json();
    }
  },

  // 用户相关接口
  user: {
    /**
     * 用户登录
     * @param {Object} credentials - 登录凭据
     * @param {string} credentials.username - 用户名
     * @param {string} credentials.password - 密码
     * @returns {Promise} - 返回登录结果的Promise
     */
    async login(credentials) {
      const response = await fetch(`${getAPI_BASE_URL()}/v1/auth/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(credentials)
      });
      return await response.json();
    },

    /**
     * 用户注册
     * @param {Object} userData - 用户数据
     * @returns {Promise} - 返回注册结果的Promise
     */
    async register(userData) {
      const response = await fetch(`${getAPI_BASE_URL()}/v1/auth/register`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(userData)
      });
      return await response.json();
    },

    /**
     * 获取用户信息
     * @param {number} id - 用户ID
     * @returns {Promise} - 返回用户信息的Promise
     */
    async getProfile(id) {
      const response = await fetch(`${getAPI_BASE_URL()}/v1/users/${id}`);
      return await response.json();
    },

    /**
     * 更新用户信息
     * @param {Object} userData - 用户数据
     * @returns {Promise} - 返回更新结果的Promise
     */
    async updateProfile(userData) {
      const response = await fetch(`${getAPI_BASE_URL()}/v1/users/profile`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${getToken()}`
        },
        body: JSON.stringify(userData)
      });
      return await response.json();
    },

    /**
     * 搜索用户
     * @param {string} keyword - 搜索关键词
     * @returns {Promise} - 返回搜索结果的Promise
     */
    async search(keyword) {
      const response = await fetch(`${getAPI_BASE_URL()}/users/search?keyword=${encodeURIComponent(keyword)}`, {
        headers: {
          'Authorization': `Bearer ${getToken()}`
        }
      });
      return await response.json();
    },

    /**
     * 获取用户详细信息（包含帖子列表）
     * @param {number} userId - 用户ID
     * @param {number} pageNo - 页码，默认为1
     * @returns {Promise} - 返回用户信息的Promise
     */
    async getUserInfo(userId, pageNo = 1) {
      const headers = {};
      const token = getToken();

      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      const response = await fetch(`${getAPI_BASE_URL()}/v2/user/${userId}?pageNo=${pageNo}`, {
        headers
      });
      return await response.json();
    },

    /**
     * 关注用户
     * @param {number} userId - 用户ID
     * @returns {Promise} - 返回关注结果的Promise
     */
    async follow(userId) {
      const response = await fetch(`${getAPI_BASE_URL()}/follow/user/${userId}`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${getToken()}`
        }
      });
      return await response.json();
    },

    /**
     * 取消关注用户
     * @param {number} userId - 用户ID
     * @returns {Promise} - 返回取消关注结果的Promise
     */
    async unfollow(userId) {
      const response = await fetch(`${getAPI_BASE_URL()}/follow/user/${userId}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${getToken()}`
        }
      });
      return await response.json();
    },

    /**
     * 获取用户评论列表
     * @param {number} userId - 用户ID
     * @param {number} pageNum - 页码
     * @returns {Promise} - 返回用户评论列表的Promise
     */
    async getUserComments(userId, pageNum = 1) {
      const headers = {};
      const token = getToken();

      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      const response = await fetch(`${getAPI_BASE_URL()}/v3/user/${userId}/comments?pageNum=${pageNum}`, {
        headers
      });
      return await response.json();
    },

    /**
     * 获取用户粉丝列表
     * @param {number} userId - 用户ID
     * @param {number} pageNum - 页码
     * @returns {Promise} - 返回用户粉丝列表的Promise
     */
    async getUserFollowers(userId, pageNum = 1) {
      const headers = {};
      const token = getToken();

      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      const response = await fetch(`${getAPI_BASE_URL()}/follow/followerList/${userId}/pageDetail?pageNum=${pageNum}`, {
        headers
      });
      return await response.json();
    },

    /**
     * 获取用户关注列表
     * @param {number} userId - 用户ID
     * @param {number} pageNum - 页码
     * @returns {Promise} - 返回用户关注列表的Promise
     */
    async getUserFollowing(userId, pageNum = 1) {
      const headers = {};
      const token = getToken();

      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      const response = await fetch(`${getAPI_BASE_URL()}/follow/followingList/${userId}/pageDetail?pageNum=${pageNum}`, {
        headers
      });
      return await response.json();
    },

    /**
     * 获取评论位置信息
     * @param {number} commentId - 评论ID
     * @returns {Promise} - 返回评论位置信息的Promise
     */
    async getCommentLocation(commentId) {
      const headers = {};
      const token = getToken();

      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      const response = await fetch(`${getAPI_BASE_URL()}/v3/location/${commentId}`, {
        headers
      });
      return await response.json();
    },

    /**
     * 验证token并获取当前用户信息
     * @returns {Promise} - 返回验证结果和用户信息的Promise
     */
    async validateToken() {
      const token = getToken();
      if (!token) {
        return { code: 401, msg: '未找到token' };
      }

      const response = await fetch(`${getAPI_BASE_URL()}/v1/validateToken`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({})
      });
      return await response.json();
    }
  },

  // 标签相关接口
  tags: {
    /**
     * 获取所有标签
     * @returns {Promise} - 返回标签列表的Promise
     */
    async getAllTags() {
      const response = await fetch(`${getAPI_BASE_URL()}/tags`);
      return await response.json();
    },

    /**
     * 获取标签列表（别名方法）
     * @returns {Promise} - 返回标签列表的Promise
     */
    async getTags() {
      return this.getAllTags();
    }
  },

  // 统计相关接口
  stats: {
    /**
     * 获取社区概览统计
     * @returns {Promise} - 返回社区统计的Promise
     */
    async getOverview() {
      const response = await fetch(`${getAPI_BASE_URL()}/v1/stats/overview`, {
        headers: {
          'Authorization': `Bearer ${getToken()}`
        }
      });
      return await response.json();
    }
  },

  // 评论相关接口
  comments: {
    /**
     * 获取帖子评论（支持分页）
     * @param {number} postId - 帖子ID
     * @param {number} pageNum - 页码，默认为1
     * @returns {Promise} - 返回评论列表的Promise
     */
    async getComments(postId, pageNum = 1) {
      const headers = {};
      const token = getToken();

      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      const response = await fetch(`${getAPI_BASE_URL()}/v3/getComments?postId=${postId}&pageNum=${pageNum}`, {
        headers
      });
      return await response.json();
    },

    /**
     * 获取帖子评论
     * @param {number} postId - 帖子ID
     * @returns {Promise} - 返回评论列表的Promise
     */
    async getByPostId(postId) {
      const response = await fetch(`${getAPI_BASE_URL()}/v3/getComments?postId=${postId}`);
      return await response.json();
    },

    /**
     * 获取评论的回复
     * @param {number} commentId - 评论ID
     * @returns {Promise} - 返回回复列表的Promise
     */
    async getReplies(commentId) {
      const response = await fetch(`${getAPI_BASE_URL()}/v3/comment/${commentId}/replies`);
      return await response.json();
    },

    /**
     * 添加评论
     * @param {number} postId - 帖子ID
     * @param {Object} commentData - 评论数据
     * @returns {Promise} - 返回添加结果的Promise，包含commentId和page信息
     */
    async add(postId, commentData) {
      const data = {
        ...commentData,
        postId
      };
      const response = await fetch(`${getAPI_BASE_URL()}/v3/comment`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${getToken()}`
        },
        body: JSON.stringify(data)
      });
      return await response.json();
    },

    /**
     * 添加评论回复
     * @param {number} commentId - 评论ID
     * @param {Object} replyData - 回复数据
     * @returns {Promise} - 返回添加结果的Promise
     */
    async addReply(commentId, replyData) {
      // 确保参数命名符合API要求
      const data = {
        ...replyData,
        parentid: replyData.parentid || commentId,
        postId: replyData.postId
      };

      const response = await fetch(`${getAPI_BASE_URL()}/v3/comment`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${getToken()}`
        },
        body: JSON.stringify(data)
      });
      return await response.json();
    },

    /**
     * 点赞评论
     * @param {number} commentId - 评论ID
     * @returns {Promise} - 返回点赞结果的Promise
     */
    async like(commentId) {
      const response = await fetch(`${getAPI_BASE_URL()}/v4/comment/like/${commentId}`, {
        headers: {
          'Authorization': `Bearer ${getToken()}`
        }
      });
      return await response.json();
    },

    /**
     * 取消点赞评论
     * @param {number} commentId - 评论ID
     * @returns {Promise} - 返回取消点赞结果的Promise
     */
    async cancelLike(commentId) {
      const response = await fetch(`${getAPI_BASE_URL()}/v4/comment/cancelLike/${commentId}`, {
        headers: {
          'Authorization': `Bearer ${getToken()}`
        }
      });
      return await response.json();
    },

    /**
     * 点踩评论
     * @param {number} commentId - 评论ID
     * @returns {Promise} - 返回点踩结果的Promise
     */
    async dislike(commentId) {
      const response = await fetch(`${getAPI_BASE_URL()}/v4/comment/dislike/${commentId}`, {
        headers: {
          'Authorization': `Bearer ${getToken()}`
        }
      });
      return await response.json();
    },

    /**
     * 取消点踩评论
     * @param {number} commentId - 评论ID
     * @returns {Promise} - 返回取消点踩结果的Promise
     */
    async cancelDislike(commentId) {
      const response = await fetch(`${getAPI_BASE_URL()}/v4/comment/cancelDislike/${commentId}`, {
        headers: {
          'Authorization': `Bearer ${getToken()}`
        }
      });
      return await response.json();
    }
  },

  // 帖子点赞/点踩相关接口
  postInteractions: {
    /**
     * 点赞帖子
     * @param {number} postId - 帖子ID
     * @returns {Promise} - 返回点赞结果的Promise
     */
    async like(postId) {
      const response = await fetch(`${getAPI_BASE_URL()}/v4/post/like/${postId}`, {
        headers: {
          'Authorization': `Bearer ${getToken()}`
        }
      });
      return await response.json();
    },

    /**
     * 取消点赞帖子
     * @param {number} postId - 帖子ID
     * @returns {Promise} - 返回取消点赞结果的Promise
     */
    async cancelLike(postId) {
      const response = await fetch(`${getAPI_BASE_URL()}/v4/post/cancelLike/${postId}`, {
        headers: {
          'Authorization': `Bearer ${getToken()}`
        }
      });
      return await response.json();
    },

    /**
     * 点踩帖子
     * @param {number} postId - 帖子ID
     * @returns {Promise} - 返回点踩结果的Promise
     */
    async dislike(postId) {
      const response = await fetch(`${getAPI_BASE_URL()}/v4/post/dislike/${postId}`, {
        headers: {
          'Authorization': `Bearer ${getToken()}`
        }
      });
      return await response.json();
    },

    /**
     * 取消点踩帖子
     * @param {number} postId - 帖子ID
     * @returns {Promise} - 返回取消点踩结果的Promise
     */
    async cancelDislike(postId) {
      const response = await fetch(`${getAPI_BASE_URL()}/v4/post/cancelDislike/${postId}`, {
        headers: {
          'Authorization': `Bearer ${getToken()}`
        }
      });
      return await response.json();
    }
  },

  // 文件上传相关接口
  upload: {
    /**
     * 上传图片
     * @param {File} file - 要上传的图片文件
     * @returns {Promise} - 返回上传结果的Promise
     */
    async image(file) {
      const formData = new FormData();
      formData.append('image', file);

      const response = await fetch(`${getAPI_BASE_URL()}/v1/image/upload`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${getToken()}`
        },
        body: formData
      });
      return await response.json();
    }
  },

  // 通知相关接口
  notifications: {
    /**
     * 获取通知列表
     * @param {Object} params - 查询参数
     * @param {number} params.page - 页码
     * @param {number} params.size - 每页数量
     * @param {boolean} params.onlyUnread - 是否只获取未读通知
     * @returns {Promise} - 返回通知列表的Promise
     */
    async getList(params = {}) {
      const { page = 1, size = 15, onlyUnread = true } = params;
      let url = `${getAPI_BASE_URL()}/api/notifications?page=${page}&size=${size}&onlyUnread=${onlyUnread}`;

      const response = await fetch(url, {
        headers: {
          'Authorization': `Bearer ${getToken()}`
        }
      });
      return await response.json();
    },

    /**
     * 标记通知为已读
     * @param {number} id - 通知ID
     * @returns {Promise} - 返回标记结果的Promise
     */
    async markAsRead(id) {
      const response = await fetch(`${getAPI_BASE_URL()}/api/notifications/${id}/read`, {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${getToken()}`
        }
      });
      return await response.json();
    },

    /**
     * 标记所有通知为已读
     * @returns {Promise} - 返回标记结果的Promise
     */
    async markAllAsRead() {
      const response = await fetch(`${getAPI_BASE_URL()}/api/notifications/read-all`, {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${getToken()}`
        }
      });
      return await response.json();
    },

    /**
     * 批量标记通知为已读
     * @param {Array<number>} ids - 通知ID数组
     * @returns {Promise} - 返回批量标记结果的Promise
     */
    async batchMarkAsRead(ids) {
      const response = await fetch(`${getAPI_BASE_URL()}/api/notifications/batch-read`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${getToken()}`
        },
        body: JSON.stringify(ids)
      });
      return await response.json();
    },

    /**
     * 获取通知跳转信息
     * @param {number} id - 通知ID
     * @returns {Promise} - 返回跳转信息的Promise
     */
    async getJumpInfo(id) {
      const response = await fetch(`${getAPI_BASE_URL()}/api/notifications/${id}/jump-info`, {
        headers: {
          'Authorization': `Bearer ${getToken()}`
        }
      });
      return await response.json();
    }
  }
};

export default API;
