package com.guochang.chanpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.guochang.chanpicturebackend.model.dto.space.SpaceAddRequest;
import com.guochang.chanpicturebackend.model.dto.space.SpaceQueryRequest;
import com.guochang.chanpicturebackend.model.dto.user.UserQueryRequest;
import com.guochang.chanpicturebackend.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.guochang.chanpicturebackend.model.entity.User;
import com.guochang.chanpicturebackend.model.vo.SpaceVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * @author 31179
 * @description 针对表【space(空间)】的数据库操作Service
 * @createDate 2025-07-26 21:54:21
 */
public interface SpaceService extends IService<Space> {
    public void validSpace(Space space, boolean add);

    public void fillSpaceBySpaceLevel(Space space);

    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

}
