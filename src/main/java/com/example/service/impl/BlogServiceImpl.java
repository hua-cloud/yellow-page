package com.example.service.impl;

import com.example.entity.Blog;
import com.example.mapper.BlogMapper;
import com.example.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

}
