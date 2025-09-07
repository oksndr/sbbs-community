package com.itheima.sbbs.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("\"comment\"")
public class Comment extends BasePojo{
    private Integer id;
    private Integer postId;
    private Integer userId;
    private Integer parentId;
    private String content;
    private String replyCount;
    private String likeCount;
    private String dislikeCount;
}
