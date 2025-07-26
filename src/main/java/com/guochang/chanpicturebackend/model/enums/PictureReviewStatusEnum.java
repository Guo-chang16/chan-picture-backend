package com.guochang.chanpicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;


@Getter
public enum PictureReviewStatusEnum {
    REVIEWING("待审核", 0),
    PASS("管理员", 1),
    REJECT("管理员", 2);;

    private final String text;

    private final Integer value;

    PictureReviewStatusEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     */
    public static PictureReviewStatusEnum getEnumByValue(Integer value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        // 遍历当前枚举类中所有的枚举实例（REVIEWING、PASS、REJECT）
        for (PictureReviewStatusEnum pictureReviewStatusEnum : PictureReviewStatusEnum.values()) {
            // 比较当前枚举实例的value与传入的value是否相等
            if (pictureReviewStatusEnum.value == value) {
                // 如果相等，返回当前枚举实例
                return pictureReviewStatusEnum;
            }
        }
// 遍历完所有实例都没找到匹配的，返回null
        return null;
    }
}


