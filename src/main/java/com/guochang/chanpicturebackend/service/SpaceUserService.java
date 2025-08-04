package com.guochang.chanpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.guochang.chanpicturebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.guochang.chanpicturebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.guochang.chanpicturebackend.model.entity.SpaceUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.guochang.chanpicturebackend.model.vo.SpaceUserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
* @author 31179
* @description 针对表【space_user(空间用户关联)】的数据库操作Service
* @createDate 2025-08-03 20:59:55
*/
public interface SpaceUserService extends IService<SpaceUser> {
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    void validSpaceUser(SpaceUser spaceUser, boolean add);

    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);


    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);





}
