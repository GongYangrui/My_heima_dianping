package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private final String BLOG_LIKED_KEY = "blog:liked:";

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = lambdaQuery().eq(Blog::getId, id).one();
        if (blog == null) {
            return Result.fail("获取博客信息失败");
        }
        User user = userMapper.selectById(blog.getUserId());
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(BLOG_LIKED_KEY + id, blog.getUserId().toString());
        blog.setIsLike(Boolean.TRUE.equals(isMember));
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        String key = BLOG_LIKED_KEY + id;
        Long userId = UserHolder.getUser().getId();
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        if (Boolean.TRUE.equals(isMember)) {
            // 如果是其中的一部分，就相当于是取消点赞
            boolean update = lambdaUpdate().setSql("liked = liked - 1").eq(Blog::getId, id).update();
            if (update) {
                stringRedisTemplate.opsForSet().remove(key, userId.toString());
            }
        } else {
            // 如果不是其中的一部分，就相当于是点赞了
            boolean update = lambdaUpdate().setSql("liked = liked + 1").eq(Blog::getId, id).update();
            if (update) {
                stringRedisTemplate.opsForSet().add(key, userId.toString());
            }
        }
        return Result.ok();
    }
}
