package com.example.service;

import com.example.dto.Result;
import com.example.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IBlogService extends IService<Blog> {

    // 查询热门博客
    Result queryHotBlog(Integer current);

    // 根据id查询博客
    Result queryBlogById(Long id);

    // 给指定id的博客点赞
    Result likeBlog(Long id);
}
