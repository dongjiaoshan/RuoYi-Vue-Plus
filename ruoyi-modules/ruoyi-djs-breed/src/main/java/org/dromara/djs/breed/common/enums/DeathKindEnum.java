package org.dromara.djs.breed.common.enums;

import lombok.Getter;

/**
 * 死亡分类枚举。
 *
 * @author djs
 * @since BRD-MD-003
 */
@Getter
public enum DeathKindEnum {

    NORMAL("1", "正常死亡"),
    ABNORMAL("2", "非正常死亡");

    private final String code;
    private final String desc;

    DeathKindEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static DeathKindEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (DeathKindEnum kind : values()) {
            if (kind.getCode().equals(code)) {
                return kind;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
