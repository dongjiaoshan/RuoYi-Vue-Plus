package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 出栏当时冻结日龄（跨域只读投影，V6 row145）。
 *
 * <p>供非养殖域按耳号取「这头猪出栏那天多少日龄」。值取自 {@code t_farm_pig_marketing.age_days}，
 * 是出栏时冻结的快照（ADR-0017），不随之后订正出生日期而变。</p>
 *
 * <p>带上 {@code marketingDate} 是为了让调用方能按「耳号 + 出栏时间」精确配对 —— 耳标可回收复用，
 * 同一个耳号在库里可能对应多次出栏。</p>
 *
 * @author djs
 */
@Data
public class PigMarketingAgeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 猪只耳号。 */
    private String earNo;

    /** 出栏时间（与 {@code t_warehouse_bar_info.marketing_time} 同源，用于精确配对）。 */
    private LocalDateTime marketingDate;

    /** 出栏当时日龄（天）；出栏记录未冻结该值时为 null。 */
    private Integer ageDays;
}
