package com.guochang.chanpicturebackend.service;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.guochang.chanpicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.guochang.chanpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.guochang.chanpicturebackend.model.dto.picture.*;
import com.guochang.chanpicturebackend.model.dto.space.analyze.SpaceSizeAnalyzeRequest;
import com.guochang.chanpicturebackend.model.entity.Picture;
import com.guochang.chanpicturebackend.model.entity.Space;
import com.guochang.chanpicturebackend.model.entity.User;
import com.guochang.chanpicturebackend.model.vo.PictureVO;
import com.guochang.chanpicturebackend.model.vo.UserVO;
import com.guochang.chanpicturebackend.model.vo.space.analyze.SpaceSizeAnalyzeResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author 31179
 * @description 针对表【picture(图片)】的数据库操作Service
 * @createDate 2025-07-21 17:25:33
 */
public interface PictureService extends IService<Picture> {

    /**
     * 上传图片
     */
    PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser);

    /**
     * 获取查询封装类
     *
     * @param pictureQueryRequest
     * @return
     */
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    public PictureVO getPictureVO(Picture picture, HttpServletRequest request);


    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    void validPicture(Picture picture);

    /**
     * 审核图片
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    void fillReviewParams(Picture picture, User loginUser);


    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(
            PictureUploadByBatchRequest pictureUploadByBatchRequest,
            User loginUser
    );

    void checkPictureAuth(User loginUser, Picture picture);

    void deletePicture(long pictureId, User loginUser);

    void clearPictureFile(Picture oldPicture);

    void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser);

    /**
     * 创建扩图任务
     */
    CreateOutPaintingTaskResponse createPaintingTask(CreatePictureOutPaintingTaskRequest picturePaintingRequest, User loginUser);

    void checkSpaceAuth(User loginUser, Space space);

}
