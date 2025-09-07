/*
 Navicat Premium Data Transfer

 Source Server         : sbbs_prod
 Source Server Type    : PostgreSQL
 Source Server Version : 170005 (170005)
 Source Host           : 167.234.208.117:10259
 Source Catalog        : sbbs
 Source Schema         : public

 Target Server Type    : PostgreSQL
 Target Server Version : 170005 (170005)
 File Encoding         : 65001

 Date: 07/09/2025 14:41:36
*/

-- 创建数据库
CREATE DATABASE IF NOT EXISTS sbbs;

-- 连接到数据库
\c sbbs;

-- ----------------------------
-- Table structure for comment
-- ----------------------------
DROP TABLE IF EXISTS "public"."comment";
CREATE TABLE "public"."comment" (
  "id" int4 NOT NULL DEFAULT nextval('comment_id_seq'::regclass),
  "post_id" int4,
  "user_id" int4,
  "parent_id" int4,
  "content" text COLLATE "pg_catalog"."default" NOT NULL,
  "created" timestamp(0) DEFAULT CURRENT_TIMESTAMP,
  "updated" timestamp(0) DEFAULT CURRENT_TIMESTAMP,
  "deleted" int4 DEFAULT 0,
  "reply_count" int4 DEFAULT 0,
  "like_count" int4 DEFAULT 0,
  "dislike_count" int4 DEFAULT 0
)
;
COMMENT ON COLUMN "public"."comment"."id" IS '主键ID，自增';
COMMENT ON COLUMN "public"."comment"."post_id" IS '所属帖子ID';
COMMENT ON COLUMN "public"."comment"."user_id" IS '评论用户ID';
COMMENT ON COLUMN "public"."comment"."parent_id" IS '父评论ID（如果是一级评论则为null或0）';
COMMENT ON COLUMN "public"."comment"."content" IS '评论内容';
COMMENT ON COLUMN "public"."comment"."created" IS '创建时间';
COMMENT ON COLUMN "public"."comment"."updated" IS '更新时间';
COMMENT ON COLUMN "public"."comment"."deleted" IS '逻辑删除标识（0：未删除，1：已删除）';
COMMENT ON COLUMN "public"."comment"."reply_count" IS '回复数量';
COMMENT ON COLUMN "public"."comment"."like_count" IS '点赞数量';
COMMENT ON COLUMN "public"."comment"."dislike_count" IS '点踩数量';
COMMENT ON TABLE "public"."comment" IS '评论表';

-- ----------------------------
-- Table structure for like
-- ----------------------------
DROP TABLE IF EXISTS "public"."like";
CREATE TABLE "public"."like" (
  "id" int4 NOT NULL DEFAULT nextval('like_id_seq'::regclass),
  "user_id" int4,
  "post_id" int4,
  "comment_id" int4,
  "created" timestamp(0) DEFAULT CURRENT_TIMESTAMP,
  "deleted" int4 DEFAULT 0,
  "type" int4 NOT NULL DEFAULT 1,
  "updated" timestamp(0) DEFAULT CURRENT_TIMESTAMP
)
;
COMMENT ON COLUMN "public"."like"."id" IS '主键ID，自增';
COMMENT ON COLUMN "public"."like"."user_id" IS '点赞用户ID';
COMMENT ON COLUMN "public"."like"."post_id" IS '点赞的帖子ID（如果点赞的是评论则为null）';
COMMENT ON COLUMN "public"."like"."comment_id" IS '点赞的评论ID（如果点赞的是帖子则为null）';
COMMENT ON COLUMN "public"."like"."created" IS '创建时间';
COMMENT ON COLUMN "public"."like"."deleted" IS '逻辑删除标识（0：未删除，1：已删除）';
COMMENT ON COLUMN "public"."like"."type" IS '操作类型 (1: 点赞, -1: 点踩)';
COMMENT ON COLUMN "public"."like"."updated" IS '更新时间';
COMMENT ON TABLE "public"."like" IS '点赞表';

-- ----------------------------
-- Table structure for notification
-- ----------------------------
DROP TABLE IF EXISTS "public"."notification";
CREATE TABLE "public"."notification" (
  "id" int4 NOT NULL DEFAULT nextval('notification_id_seq'::regclass),
  "receiver_id" int4 NOT NULL,
  "sender_id" int4,
  "notification_type" int4 NOT NULL,
  "related_id" int4 NOT NULL,
  "related_type" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "is_read" bool NOT NULL DEFAULT false,
  "created" timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated" timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "deleted" int4 NOT NULL DEFAULT 0,
  "trigger_entity_id" int4,
  "trigger_entity_type" int4
)
;
COMMENT ON COLUMN "public"."notification"."id" IS '主键ID，自增';
COMMENT ON COLUMN "public"."notification"."receiver_id" IS '通知接收者用户ID';
COMMENT ON COLUMN "public"."notification"."sender_id" IS '通知发送者用户ID';
COMMENT ON COLUMN "public"."notification"."notification_type" IS '通知类型 (1: 评论了我的帖子, 2: 回复了我的评论, 3: 在评论中 @了我, 4: 在回复中 @了我, 5: 点赞了我的帖子, 6: 点踩了我的帖子, 7: 点赞了我的评论/回复, 8: 点踩了我的评论/回复)';
COMMENT ON COLUMN "public"."notification"."related_id" IS '与通知相关的实体ID';
COMMENT ON COLUMN "public"."notification"."related_type" IS '1: 帖子, 2: 评论';
COMMENT ON COLUMN "public"."notification"."is_read" IS '通知是否已读';
COMMENT ON COLUMN "public"."notification"."created" IS '通知创建时间';
COMMENT ON COLUMN "public"."notification"."updated" IS '通知更新时间';
COMMENT ON COLUMN "public"."notification"."deleted" IS '逻辑删除标识';
COMMENT ON COLUMN "public"."notification"."trigger_entity_id" IS '触发此通知的具体实体ID (例如: 评论ID, 点赞记录ID)';
COMMENT ON COLUMN "public"."notification"."trigger_entity_type" IS '触发此通知的具体实体类型 (例如: 1: 评论, 2: 点赞, 3:点踩记录)';
COMMENT ON TABLE "public"."notification" IS '用户通知表';

-- ----------------------------
-- Table structure for post
-- ----------------------------
DROP TABLE IF EXISTS "public"."post";
CREATE TABLE "public"."post" (
  "id" int4 NOT NULL DEFAULT nextval('post_id_seq'::regclass),
  "user_id" int4,
  "title" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "content" text COLLATE "pg_catalog"."default" NOT NULL,
  "created" timestamp(0) DEFAULT CURRENT_TIMESTAMP,
  "updated" timestamp(0) DEFAULT CURRENT_TIMESTAMP,
  "deleted" int4 DEFAULT 0,
  "like_count" int4 NOT NULL DEFAULT 0,
  "comment_count" int4 NOT NULL DEFAULT 0,
  "dislike_count" int4 NOT NULL DEFAULT 0,
  "tag_ids_string" varchar(255) COLLATE "pg_catalog"."default" DEFAULT ''::character varying
)
;
COMMENT ON COLUMN "public"."post"."id" IS '主键ID，自增';
COMMENT ON COLUMN "public"."post"."user_id" IS '发帖用户ID';
COMMENT ON COLUMN "public"."post"."title" IS '帖子标题';
COMMENT ON COLUMN "public"."post"."content" IS '帖子内容';
COMMENT ON COLUMN "public"."post"."created" IS '创建时间';
COMMENT ON COLUMN "public"."post"."updated" IS '更新时间';
COMMENT ON COLUMN "public"."post"."deleted" IS '逻辑删除标识（0：未删除，1：已删除）';
COMMENT ON COLUMN "public"."post"."like_count" IS '点赞数量';
COMMENT ON COLUMN "public"."post"."comment_count" IS '评论数量';
COMMENT ON COLUMN "public"."post"."dislike_count" IS '点踩数量';
COMMENT ON TABLE "public"."post" IS '帖子表';

-- ----------------------------
-- Table structure for report
-- ----------------------------
DROP TABLE IF EXISTS "public"."report";
CREATE TABLE "public"."report" (
  "id" int4 NOT NULL DEFAULT nextval('report_id_seq'::regclass),
  "reporter_id" int4 NOT NULL,
  "reported_post_id" int4 NOT NULL,
  "reason" text COLLATE "pg_catalog"."default" NOT NULL,
  "status" varchar(50) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'pending'::character varying,
  "created" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "updated" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "deleted" int4 DEFAULT 0
)
;
COMMENT ON COLUMN "public"."report"."id" IS '主键ID，自增';
COMMENT ON COLUMN "public"."report"."reporter_id" IS '举报用户ID';
COMMENT ON COLUMN "public"."report"."reported_post_id" IS '被举报帖子ID';
COMMENT ON COLUMN "public"."report"."reason" IS '举报理由';
COMMENT ON COLUMN "public"."report"."status" IS '举报处理状态';
COMMENT ON COLUMN "public"."report"."created" IS '创建时间';
COMMENT ON COLUMN "public"."report"."updated" IS '更新时间';
COMMENT ON COLUMN "public"."report"."deleted" IS '逻辑删除标识（0：未删除，1：已删除）';
COMMENT ON TABLE "public"."report" IS '举报表';

-- ----------------------------
-- Table structure for tag
-- ----------------------------
DROP TABLE IF EXISTS "public"."tag";
CREATE TABLE "public"."tag" (
  "id" int4 NOT NULL DEFAULT nextval('tag_id_seq'::regclass),
  "name" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "created" timestamp(0) DEFAULT CURRENT_TIMESTAMP,
  "updated" timestamp(0) DEFAULT CURRENT_TIMESTAMP,
  "deleted" int4 DEFAULT 0
)
;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS "public"."user";
CREATE TABLE "public"."user" (
  "id" int4 NOT NULL DEFAULT nextval('user_id_seq'::regclass),
  "username" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "password" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "group_id" varchar COLLATE "pg_catalog"."default",
  "created" timestamp(0) DEFAULT CURRENT_TIMESTAMP,
  "updated" timestamp(0) DEFAULT CURRENT_TIMESTAMP,
  "deleted" int4 DEFAULT 0,
  "email" varchar(100) COLLATE "pg_catalog"."default" NOT NULL DEFAULT ''::character varying,
  "avatar" varchar(255) COLLATE "pg_catalog"."default",
  "experience" int4 DEFAULT 0,
  "enable_like_notification" bool DEFAULT true,
  "enable_other_notification" bool DEFAULT true
)
;
COMMENT ON COLUMN "public"."user"."id" IS '主键ID，自增';
COMMENT ON COLUMN "public"."user"."username" IS '用户名，唯一';
COMMENT ON COLUMN "public"."user"."password" IS '密码（加密后）';
COMMENT ON COLUMN "public"."user"."group_id" IS '所属用户组ID';
COMMENT ON COLUMN "public"."user"."created" IS '创建时间';
COMMENT ON COLUMN "public"."user"."updated" IS '更新时间';
COMMENT ON COLUMN "public"."user"."deleted" IS '逻辑删除标识（0：未删除，1：已删除）';
COMMENT ON COLUMN "public"."user"."email" IS '用户邮箱，唯一';
COMMENT ON COLUMN "public"."user"."avatar" IS '用户头像URL或文件名';
COMMENT ON COLUMN "public"."user"."enable_like_notification" IS '是否接收点赞通知邮件，默认开启';
COMMENT ON COLUMN "public"."user"."enable_other_notification" IS '是否接收其他类型通知邮件（评论、回复、@提及、关注、升级等），默认开启';
COMMENT ON TABLE "public"."user" IS '用户表';

-- ----------------------------
-- Table structure for user_follow
-- ----------------------------
DROP TABLE IF EXISTS "public"."user_follow";
CREATE TABLE "public"."user_follow" (
  "id" int4 NOT NULL DEFAULT nextval('user_follow_id_seq'::regclass),
  "follower_id" int4 NOT NULL,
  "following_id" int4 NOT NULL,
  "created" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "updated" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "deleted" int4 DEFAULT 0
)
;
COMMENT ON COLUMN "public"."user_follow"."id" IS '主键ID，自增';
COMMENT ON COLUMN "public"."user_follow"."follower_id" IS '关注者用户ID';
COMMENT ON COLUMN "public"."user_follow"."following_id" IS '被关注者用户ID';
COMMENT ON COLUMN "public"."user_follow"."created" IS '创建时间';
COMMENT ON COLUMN "public"."user_follow"."updated" IS '更新时间';
COMMENT ON COLUMN "public"."user_follow"."deleted" IS '逻辑删除标识（0：未删除，1：已删除）';
COMMENT ON TABLE "public"."user_follow" IS '用户关注表';

-- ----------------------------
-- Table structure for user_level
-- ----------------------------
DROP TABLE IF EXISTS "public"."user_level";
CREATE TABLE "public"."user_level" (
  "id" int4 NOT NULL DEFAULT nextval('user_level_id_seq'::regclass),
  "level" int4 NOT NULL,
  "name" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "min_experience" int4 NOT NULL DEFAULT 0,
  "max_experience" int4,
  "created" timestamp(0) DEFAULT CURRENT_TIMESTAMP,
  "updated" timestamp(0) DEFAULT CURRENT_TIMESTAMP,
  "deleted" int4 DEFAULT 0
)
;
COMMENT ON COLUMN "public"."user_level"."id" IS '主键ID';
COMMENT ON COLUMN "public"."user_level"."level" IS '等级数值';
COMMENT ON COLUMN "public"."user_level"."name" IS '等级名称（包含图标）';
COMMENT ON COLUMN "public"."user_level"."min_experience" IS '该等级最低经验值';
COMMENT ON COLUMN "public"."user_level"."max_experience" IS '该等级最高经验值（NULL表示无上限）';
COMMENT ON TABLE "public"."user_level" IS '用户等级配置表';

-- ----------------------------
-- Indexes structure for table comment
-- ----------------------------
CREATE INDEX "idx_comment_parent_content" ON "public"."comment" USING btree (
  "parent_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0;
CREATE INDEX "idx_comment_position_optimized" ON "public"."comment" USING btree (
  "post_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "parent_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "created" "pg_catalog"."timestamp_ops" ASC NULLS LAST,
  "id" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0 AND parent_id IS NULL;
CREATE INDEX "idx_comment_post_pagination" ON "public"."comment" USING btree (
  "post_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "parent_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "created" "pg_catalog"."timestamp_ops" ASC NULLS LAST,
  "id" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0 AND parent_id IS NULL;
CREATE INDEX "idx_comment_replies" ON "public"."comment" USING btree (
  "parent_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "post_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "created" "pg_catalog"."timestamp_ops" ASC NULLS LAST,
  "id" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0 AND parent_id IS NOT NULL;
CREATE INDEX "idx_comment_replies_optimized" ON "public"."comment" USING btree (
  "parent_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "created" "pg_catalog"."timestamp_ops" ASC NULLS LAST,
  "id" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0 AND parent_id IS NOT NULL;
CREATE INDEX "idx_comment_user_activity" ON "public"."comment" USING btree (
  "user_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "created" "pg_catalog"."timestamp_ops" DESC NULLS FIRST,
  "id" "pg_catalog"."int4_ops" DESC NULLS FIRST
) WHERE deleted = 0;
CREATE INDEX "idx_comment_user_count" ON "public"."comment" USING btree (
  "user_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0;
CREATE INDEX "idx_comment_user_updated_desc" ON "public"."comment" USING btree (
  "user_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "updated" "pg_catalog"."timestamp_ops" DESC NULLS FIRST,
  "id" "pg_catalog"."int4_ops" DESC NULLS FIRST
) WHERE deleted = 0;

-- ----------------------------
-- Primary Key structure for table comment
-- ----------------------------
ALTER TABLE "public"."comment" ADD CONSTRAINT "comment_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table like
-- ----------------------------
CREATE INDEX "idx_like_post_user_status" ON "public"."like" USING btree (
  "post_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "user_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "type" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0 AND post_id IS NOT NULL;
CREATE INDEX "idx_like_user_comment_batch" ON "public"."like" USING btree (
  "user_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "comment_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "type" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0 AND comment_id IS NOT NULL AND post_id IS NULL;
CREATE INDEX "idx_like_user_comment_batch_optimized" ON "public"."like" USING btree (
  "user_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "comment_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "type" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0 AND comment_id IS NOT NULL AND post_id IS NULL;

-- ----------------------------
-- Primary Key structure for table like
-- ----------------------------
ALTER TABLE "public"."like" ADD CONSTRAINT "like_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table notification
-- ----------------------------
CREATE INDEX "idx_notification_complete" ON "public"."notification" USING btree (
  "receiver_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "is_read" "pg_catalog"."bool_ops" ASC NULLS LAST,
  "created" "pg_catalog"."timestamp_ops" DESC NULLS FIRST,
  "sender_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "notification_type" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0;
CREATE INDEX "idx_notification_complete_enhanced" ON "public"."notification" USING btree (
  "receiver_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "is_read" "pg_catalog"."bool_ops" ASC NULLS LAST,
  "created" "pg_catalog"."timestamp_ops" DESC NULLS FIRST,
  "sender_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "notification_type" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "related_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "related_type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "trigger_entity_id" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0;
CREATE INDEX "idx_notification_enhanced_list" ON "public"."notification" USING btree (
  "receiver_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "created" "pg_catalog"."timestamp_ops" DESC NULLS FIRST
) WHERE deleted = 0;
CREATE INDEX "idx_notification_sender" ON "public"."notification" USING btree (
  "sender_id" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE sender_id IS NOT NULL;
CREATE INDEX "idx_notification_sender_user" ON "public"."notification" USING btree (
  "sender_id" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE sender_id IS NOT NULL AND deleted = 0;
CREATE INDEX "idx_notification_time_range" ON "public"."notification" USING btree (
  "created" "pg_catalog"."timestamp_ops" ASC NULLS LAST,
  "receiver_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0;
CREATE INDEX "idx_notification_trigger_comment" ON "public"."notification" USING btree (
  "trigger_entity_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "notification_type" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE trigger_entity_id IS NOT NULL AND (notification_type = ANY (ARRAY[1, 2, 3, 4])) AND deleted = 0;
CREATE INDEX "idx_notification_type_stats" ON "public"."notification" USING btree (
  "notification_type" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "receiver_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0;
CREATE INDEX "idx_notification_unread_count" ON "public"."notification" USING btree (
  "receiver_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "is_read" "pg_catalog"."bool_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0 AND is_read = false;
CREATE INDEX "idx_notification_unread_stats" ON "public"."notification" USING btree (
  "receiver_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "is_read" "pg_catalog"."bool_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0 AND is_read = false;
CREATE INDEX "idx_notification_user_list" ON "public"."notification" USING btree (
  "receiver_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "created" "pg_catalog"."timestamp_ops" DESC NULLS FIRST
) WHERE deleted = 0;

-- ----------------------------
-- Primary Key structure for table notification
-- ----------------------------
ALTER TABLE "public"."notification" ADD CONSTRAINT "notification_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table post
-- ----------------------------
CREATE INDEX "idx_post_tag_deleted_updated" ON "public"."post" USING btree (
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "tag_ids_string" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "updated" "pg_catalog"."timestamp_ops" DESC NULLS FIRST,
  "id" "pg_catalog"."int4_ops" DESC NULLS FIRST
) WHERE deleted = 0;
CREATE INDEX "idx_post_tag_ids_btree" ON "public"."post" USING btree (
  "tag_ids_string" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
) WHERE deleted = 0 AND tag_ids_string IS NOT NULL AND tag_ids_string::text <> ''::text;

-- ----------------------------
-- Indexes structure for table user
-- ----------------------------
CREATE INDEX "idx_user_batch_query" ON "public"."user" USING btree (
  "id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "username" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "avatar" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "experience" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "group_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
) WHERE deleted = 0;

-- ----------------------------
-- Indexes structure for table user_follow
-- ----------------------------
CREATE INDEX "idx_user_follow_follower_in" ON "public"."user_follow" USING btree (
  "follower_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "following_id" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0;
CREATE INDEX "idx_user_follow_followers_count" ON "public"."user_follow" USING btree (
  "following_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0;
CREATE INDEX "idx_user_follow_followers_updated" ON "public"."user_follow" USING btree (
  "following_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "updated" "pg_catalog"."timestamp_ops" DESC NULLS FIRST,
  "id" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0;
CREATE INDEX "idx_user_follow_following_count" ON "public"."user_follow" USING btree (
  "follower_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0;
CREATE INDEX "idx_user_follow_following_in" ON "public"."user_follow" USING btree (
  "following_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "follower_id" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0;
CREATE INDEX "idx_user_follow_following_updated" ON "public"."user_follow" USING btree (
  "follower_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "deleted" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "updated" "pg_catalog"."timestamp_ops" DESC NULLS FIRST,
  "id" "pg_catalog"."int4_ops" ASC NULLS LAST
) WHERE deleted = 0;
