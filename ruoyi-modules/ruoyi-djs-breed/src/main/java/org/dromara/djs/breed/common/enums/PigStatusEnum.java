package org.dromara.djs.breed.common.enums;

import lombok.Getter;

/**
 * 猪只状态枚举。
 *
 * @author djs
 * @since BRD-MD-003
 */
@Getter
public enum PigStatusEnum {
    
    /**
     * 后备
     */
    G("G", "后备"),
    
    /**
     * 配种
     */
    M("M", "配种"),
    
    /**
     * 空怀
     */
    N("N", "空怀"),
    
    /**
     * 断奶
     */
    W("W", "断奶"),
    
    /**
     * 育肥
     */
    F("F", "育肥"),
    
    /**
     * 分娩
     */
    L("L", "分娩"),
    
    /**
     * 淘汰
     */
    C("C", "淘汰"),
    
    /**
     * 死亡
     */
    D("D", "死亡"),
    
    /**
     * 出栏
     */
    S("S", "出栏"),
    
    /**
     * 返情
     */
    R("R", "返情"),
    
    /**
     * 流产
     */
    A("A", "流产"),
    
    /**
     * 公猪
     */
    B("B", "公猪");
    
    /**
     * 状态码
     */
    private final String code;
    
    /**
     * 状态描述
     */
    private final String desc;
    
    PigStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    
    /**
     * 根据状态码获取枚举
     *
     * @param code 状态码
     * @return 枚举值，不存在返回 null
     */
    public static PigStatusEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (PigStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }
    
    /**
     * 判断是否为终态（淘汰、死亡、出栏）
     *
     * @param code 状态码
     * @return true 表示终态
     */
    public static boolean isEndStatus(String code) {
        PigStatusEnum status = fromCode(code);
        return status != null && (status == C || status == D || status == S);
    }
    
    /**
     * 判断是否为有效状态
     *
     * @param code 状态码
     * @return true 表示有效状态
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
