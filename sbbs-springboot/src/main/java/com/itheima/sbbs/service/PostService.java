package com.itheima.sbbs.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.itheima.sbbs.entity.Like;
import com.itheima.sbbs.entity.Post;
import com.itheima.sbbs.entity.PostDto;
import com.itheima.sbbs.entity.PostWithUserDto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public interface PostService extends IService<Post> {
    PostDto getPostList(Integer lastId, LocalDateTime lastUpdated, Integer pageSize);

    PostDto getPostListByTag(Integer tagId, Integer lastId, LocalDateTime lastUpdated, Integer pageSize);

    Like checkIfLiked(int loginId, Integer postId);

    List<PostWithUserDto> searchPosts(String keyword);
    
    /**
     * 根据关键词搜索帖子（旧方法，保留兼容）
     */
    List<PostWithUserDto> searchPosts(String keyword, Integer page, Integer pageSize);
    
    /**
     * 统计搜索结果总数（旧方法，保留兼容）
     */
    Integer countSearchPosts(String keyword);
    
    /**
     * 使用MyBatis-Plus分页搜索帖子
     * @param keyword 搜索关键词
     * @param page 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    IPage<PostWithUserDto> searchPostsWithPage(String keyword, Integer page, Integer pageSize);

    PostWithUserDto getPostDetailById(Integer postId);

    void clearPostDetailCache(Integer postId);

    void clearPostListCache();

    void clearTagCache();

    long getTotalPostCount();

    long getNewPostCountToday();

    /**
     * 用户删除自己的帖子
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return 是否删除成功
     */
    boolean deletePostByUserId(Integer postId, Integer userId);

    /**
     * 用户修改自己的帖子
     * @param post 待修改的帖子数据
     * @param userId 用户ID
     * @return 是否修改成功
     */
    boolean updatePostByUserId(Post post, Integer userId);

    /**
     * 管理员删除帖子（会发送通知和邮件）
     * @param postId 帖子ID
     * @return 是否删除成功
     */
    boolean removeByIdWithNotification(java.io.Serializable postId);

    /**
     * 获取7天内最火的5个帖子
     * @return 热门帖子列表
     */
    List<PostWithUserDto> getHotPostsInSevenDays();
    
    /**
     * 🚀 设置首页置顶帖子
     * @param postId 帖子ID，null表示取消置顶
     * @return 操作是否成功
     */
    boolean setPinnedPost(Integer postId);
    
    /**
     * 🚀 获取当前置顶帖子ID
     * @return 置顶帖子ID，null表示无置顶
     */
    Integer getPinnedPostId();
}
