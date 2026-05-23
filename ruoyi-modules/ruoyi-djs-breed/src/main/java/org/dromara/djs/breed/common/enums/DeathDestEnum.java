package org.dromara.djs.breed.common.enums;

import lombok.Getter;

/**
 * 死亡去向枚举。
 *
 * @author djs
 * @since BRD-MD-003
 */
@Getter
public enum DeathDestEnum {
    
    SELL("sell", "出售"),
    HARMLESS("harmless", "无害化处理"),
    DISPOSAL("disposal", "其他处理");
    
    private final String code;
    private final String desc;
    
    DeathDestEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    
    public static DeathDestEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (DeathDestEnum dest : values()) {
            if (dest.getCode().equals(code)) {
                return dest;
            }
        }
        return null;
    }
    
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
