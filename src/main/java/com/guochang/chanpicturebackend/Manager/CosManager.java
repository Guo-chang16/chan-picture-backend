package com.guochang.chanpicturebackend.Manager;

import cn.hutool.core.io.FileUtil;
import com.guochang.chanpicturebackend.config.CosClientConfig;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传文件
     * @param key  唯一键, 最早的来源是我们的配置文件
     * @param file 要上传的本地文件, 导包 java.io.File
     * @return 将构造好的请求, 提高创建的客户端, 上传到 COS 对象中
     */
    public PutObjectResult putObject(String key, File file){
        // 调用 PutObjectRequest 构造方法, 文档中给出了对应 PutObjectRequest 的参数
        // 参数为 : 客户端桶的名称, 唯一键 key, 本地需要上传的文件
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);

        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 下载对象
     *
     * @param key 唯一键
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }

    /**
     * 上传对象（附带图片信息）
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        // 对图片进行处理（获取基本信息也被视作为一种处理）
        PicOperations picOperations = new PicOperations();
        //表示返回原图信息
        picOperations.setIsPicInfo(1);

        // 图片压缩（转成 webp 格式）
        List<PicOperations.Rule> rules=new ArrayList<>();
        String webKey= FileUtil.mainName(key)+".webp";
        PicOperations.Rule compressRule=new PicOperations.Rule();
        compressRule.setFileId(webKey);
        compressRule.setBucket(cosClientConfig.getBucket());
        compressRule.setRule("imageMogr2/format/webp");
        rules.add(compressRule);

        /*// 缩略图处理
        PicOperations.Rule thumbnailRule = new PicOperations.Rule();
        thumbnailRule.setBucket(cosClientConfig.getBucket());
        String thumbnailKey = FileUtil.mainName(key) + "_thumbnail." + FileUtil.getSuffix(key);
        thumbnailRule.setFileId(thumbnailKey);
        // 缩放规则 /thumbnail/<Width>x<Height>>（如果大于原图宽高，则不处理）
        thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>", 128, 128));
        rules.add(thumbnailRule);*/

        // 构造处理参数
        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 删除对象
     *
     * @param key 唯一键
     */
    public void deleteObject(String key) {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);}

}
