package org.dromara.djs.breed.death.domain.query;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 猪只死亡记录查询条件。
 *
 * @author djs
 * @since BRD-MD-003
 */
@Data
public class PigDeathQuery {

    /**
     * 耳号（模糊匹配）。
     */
    private String earNo;

    /**
     * 开始日期。
     */
    private LocalDateTime startDate;

    /**
     * 结束日期。
     */
    private LocalDateTime endDate;

    /**
     * 死亡猪只类型（字典 pig_type）。
     */
    private String deathPigType;

    /**
     * 死亡分类（字典 death_type）。
     */
    private String deathKind;

    /**
     * 死亡原因（字典 death_reason）。
     */
    private String deathReason;

}
