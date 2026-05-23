package org.dromara.djs.breed.common.enums;

import lombok.Getter;

/**
 * 死亡原因枚举。
 *
 * @author djs
 * @since BRD-MD-003
 */
@Getter
public enum DeathReasonEnum {
    
    AFRICAN_SWINE_FEVER("african_swine_fever", "非洲猪瘟"),
    DISEASE("disease", "疾病"),
    ACCIDENT("accident", "意外"),
    OLD_AGE("old_age", "老死"),
    OTHER("other", "其他");
    
    private final String code;
    private final String desc;
    
    DeathReasonEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    
    public static DeathReasonEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (DeathReasonEnum reason : values()) {
            if (reason.getCode().equals(code)) {
                return reason;
            }
        }
        return null;
    }
    
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
