package com.guochang.chanpicturebackend.service;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.guochang.chanpicturebackend.model.dto.space.analyze.SpaceAnalyzeRequest;
import com.guochang.chanpicturebackend.model.entity.Picture;
import com.guochang.chanpicturebackend.model.entity.Space;
import com.guochang.chanpicturebackend.model.entity.User;

public interface SpaceAnalyzeService extends IService<Space> {

     void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser);

     void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest, QueryWrapper<Picture> queryWrapper);



}
