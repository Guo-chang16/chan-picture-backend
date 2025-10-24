package com.guochang.chanpicturebackend.service.impl;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.guochang.chanpicturebackend.Manager.CosManager;
import com.guochang.chanpicturebackend.Manager.upload.FilePictureUploadImpl;
import com.guochang.chanpicturebackend.Manager.upload.PictureUploadTemplate;
import com.guochang.chanpicturebackend.Manager.upload.UrlPictureUploadImpl;
import com.guochang.chanpicturebackend.api.aliyunai.Api.AliyunAiApi;
import com.guochang.chanpicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.guochang.chanpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.guochang.chanpicturebackend.common.ErrorCode;
import com.guochang.chanpicturebackend.exception.BusinessException;
import com.guochang.chanpicturebackend.exception.ThrowUtils;
import com.guochang.chanpicturebackend.mapper.PictureMapper;
import com.guochang.chanpicturebackend.model.dto.file.UploadPictureResult;
import com.guochang.chanpicturebackend.model.dto.picture.*;
import com.guochang.chanpicturebackend.model.entity.Picture;
import com.guochang.chanpicturebackend.model.entity.Space;
import com.guochang.chanpicturebackend.model.entity.User;
import com.guochang.chanpicturebackend.model.enums.PictureReviewStatusEnum;
import com.guochang.chanpicturebackend.model.vo.PictureVO;
import com.guochang.chanpicturebackend.model.vo.UserVO;
import com.guochang.chanpicturebackend.service.PictureService;
import com.guochang.chanpicturebackend.service.SpaceService;
import com.guochang.chanpicturebackend.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


/**
 * @author 31179
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2025-07-21 17:25:33
 */
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private FilePictureUploadImpl filePictureUpload;

    @Resource
    private UrlPictureUploadImpl urlPictureUpload;

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private CosManager cosManager;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AliyunAiApi aliyunAiApi;

    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        //校验空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "个人空间不存在");

            if(space.getTotalCount()>=space.getMaxCount()){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"空间条数不足");
            }

            if(space.getTotalSize()>=space.getMaxSize()){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"空间大小不足");
            }

        }

        //判断是新增还是更新
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }

        //如果是新增，就不要传id
        if (pictureId != null) {
            Picture oldpicture = pictureService.getById(pictureId);
            ThrowUtils.throwIf(oldpicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            if (!(oldpicture.getUserId().equals(loginUser.getId())) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }

            //校验空间是否一致
            //没传spaceId,则复用原有图片的spaceId(如果原有图片有空间的话)
            if (spaceId == null) {
                if (oldpicture.getSpaceId() != null) {
                    spaceId = oldpicture.getSpaceId();
                }
            } else {
                //如果用户传spaceId
                ThrowUtils.throwIf(ObjectUtil.notEqual(spaceId, oldpicture.getSpaceId()), ErrorCode.PARAMS_ERROR, "空间id不一致");
            }
        }

        //上传图片，得到图片信息
        //按照用户id划分目录
        String uploadPathPrefix = null;
        if (spaceId == null) {
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {
            uploadPathPrefix = String.format("space/%s", spaceId);
        }


        // 根据 inputSource 类型区分上传方式
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);

        // 构造要入库的图片信息（注意要进数据库）
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        String picName = uploadPictureResult.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);

        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());
        picture.setSpaceId(spaceId);
        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }

        // 开启事务
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            // 插入数据
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败，数据库操作失败");
            if (finalSpaceId != null) {
                // 更新空间的使用额度
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return picture;
        });
        // 可自行实现，如果是更新，可以清理图片资源
        // this.clearPictureFile(oldPicture);
        return PictureVO.objToVo(picture);
    }

    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Date reviewTime = pictureQueryRequest.getReviewTime();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Long spaceId = pictureQueryRequest.getSpaceId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();

        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewTime), "reviewTime", reviewTime);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.ge(ObjectUtil.isNotEmpty(startEditTime),"startEditTime",startEditTime);
        queryWrapper.le(ObjectUtil.isNotEmpty(endEditTime),"endEditTime",endEditTime);
        queryWrapper.isNull(nullSpaceId,"nullSpaceId");


        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * 分页获取图片封装
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    /**
     * 校验图片
     *
     * @param picture
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    /**
     * 图片审核
     *
     * @param pictureReviewRequest
     * @param loginUser
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        //前端点击拒绝或通过按钮，操作放到在请求方式中，传递到后端，后端直接按照要求保存或者遗弃数据即可
        //校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        String reviewMessage = pictureReviewRequest.getReviewMessage();
        PictureReviewStatusEnum enumByValue = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        ThrowUtils.throwIf(pictureReviewRequest.getId() <= 0 || PictureReviewStatusEnum.REVIEWING.equals(enumByValue) || enumByValue == null, ErrorCode.PARAMS_ERROR);

        //进行审核
        Picture picture = pictureService.getById(pictureReviewRequest.getId());
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);

        //状态一致，即已审核过
        ThrowUtils.throwIf(picture.getReviewStatus().equals(reviewStatus), ErrorCode.PARAMS_ERROR);

        Picture picture1 = new Picture();
        picture1.setId(pictureReviewRequest.getId());
        picture1.setReviewStatus(reviewStatus);
        picture1.setReviewMessage(reviewMessage);
        picture1.setReviewerId(loginUser.getId());
        picture1.setUpdateTime(new Date());

        //插入数据库表
        boolean success = pictureService.updateById(picture1);
        ThrowUtils.throwIf(!success, ErrorCode.OPERATION_ERROR, "审核失败");
    }

    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，创建或编辑都要改为待审核
//            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        //校验参数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        //名称前缀默认为搜索关键词
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        ThrowUtils.throwIf(searchText == null || count > 10, ErrorCode.PARAMS_ERROR, "请求参数为空或超出最大数目");

        //抓取内容
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;

        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new RuntimeException(e);
        }
        //解析内容
        Element div = document.getElementsByClass("dgControl").first();
        ThrowUtils.throwIf(ObjectUtil.isEmpty(div), ErrorCode.OPERATION_ERROR, "获取元素失败");
//        Elements imgElementList = div.select("img.mimg");
        Elements imgElementList = div.select(".iusc");  // 修改选择器，获取包含完整数据的元素
        //遍历元素，依次上传图片
        int uploadCount = 0;
        for (Element element : imgElementList) {
//String fileUrl = element.attr("src");
            String dataM = element.attr("m");
            String fileUrl;
            try {
                // 解析JSON字符串
                JSONObject jsonObject = JSONUtil.parseObj(dataM);
                // 获取murl字段（原始图片URL）
                fileUrl = jsonObject.getStr("murl");
            } catch (Exception e) {
                log.error("解析图片数据失败", e);
                continue;
            }

            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前连接为空，已跳过:{}", fileUrl);
                continue;
            }
            //处理图片地址，防止转义或和对象存储冲突问题
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            //上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(fileUrl);
            pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            try {
                PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                uploadCount++;
                log.info("图片上传成功，id={}", pictureVO.getId());
            } catch (Exception e) {
                log.info("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }


/*    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!picture.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }*/

    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判断改图片是否被多条记录使用
        String pictureUrl = oldPicture.getUrl();
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        // 有不止一条记录用到了该图片，不清理
        if (count > 1) {
            return;
        }
        // 删除图片
        cosManager.deleteObject(pictureUrl);
    }

    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        // 校验参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        ThrowUtils.throwIf(pictureIdList == null || pictureIdList.isEmpty(), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        //校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(!space.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);

        //查询指定图片（仅返回需要的字段）
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .in(Picture::getId, pictureIdList)
                .eq(Picture::getSpaceId, spaceId)
                .list();
        if (pictureList.isEmpty()) {
            return;
        }
        //更新分类和标签
        pictureList.forEach(picture -> {
            picture.setCategory(category);
            picture.setTags(JSONUtil.toJsonStr(tags));
        });

        //批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        if (StrUtil.isNotBlank(nameRule)) {
            for (int i = 0; i < pictureList.size(); i++) {
                Picture picture = pictureList.get(i);
                String newName = nameRule.replace("{index}", String.valueOf(i + 1));
                picture.setName(newName);
            }
        }

        //更新数据库
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    @Override
    public CreateOutPaintingTaskResponse createPaintingTask(CreatePictureOutPaintingTaskRequest picturePaintingRequest, User loginUser) {
        ThrowUtils.throwIf(picturePaintingRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = picturePaintingRequest.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR));

        //checkPictureAuth(loginUser, picture);
        //构造请求参数
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input=new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        createOutPaintingTaskRequest.setInput(input);
        createOutPaintingTaskRequest.setParameters(picturePaintingRequest.getParameters());
        //创建任务
        return aliyunAiApi.createPaintingTask(createOutPaintingTaskRequest);
    }

    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 更新空间的使用额度，释放额度
            /*boolean update = spaceService.lambdaUpdate()
                    .eq(Space::getId, oldPicture.getSpaceId())
                    .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                    .setSql("totalCount = totalCount - 1")
                    .update();*/
            //ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            return true;
        });
        // 异步清理文件
        this.clearPictureFile(oldPicture);
    }

/*    public void checkSpaceAuth(User loginUser, Space space) {
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }*/

}






