package com.guochang.chanpicturebackend.mapper;

import com.guochang.chanpicturebackend.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author 31179
* @description 针对表【user(用户)】的数据库操作Mapper
* @createDate 2025-07-20 16:35:08
* @Entity com.guochang.chanpicturebackend.model.entity.User
*/
@Mapper
public interface UserMapper extends BaseMapper<User> {

}




