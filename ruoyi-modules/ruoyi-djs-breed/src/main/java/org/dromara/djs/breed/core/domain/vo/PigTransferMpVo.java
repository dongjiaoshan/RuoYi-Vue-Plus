package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * mp 育肥/仔猪详情「转移记录」表行出参（DJS-FIX-6-14 row213，原型 转移记录 tab）。
 *
 * <p>来源 UNION {@code t_farm_pig_transfer}（普通转移）+ {@code t_farm_pig_marketing}（出栏），
 * 按日期倒序。原型表列：转移日期 / 转移栋舍 / 开始时间 / 结束时间。</p>
 *
 * <p>口径说明：</p>
 * <ul>
 *   <li>{@link #transferDate} 转移日期 / 出栏日期（'YYYY-MM-DD'）；</li>
 *   <li>{@link #targetBarn} 转移栋舍 = newBarnName + newPenName 拼接；出栏记录 → 空串，mp 端按 {@link #slaughter} 显「出栏」；</li>
 *   <li>{@link #fromDate} 开始时间 = 上一段（旧栏位）开始驻留时间，取本记录前一条记录的日期；首条无前驱 → null；</li>
 *   <li>{@link #toDate} 结束时间 = 本次转移/出栏日期（离开旧栏位的时刻）。</li>
 *   <li>{@link #slaughter} 是否出栏行（来自 marketing 表）。</li>
 * </ul>
 *
 * @author djs
 * @since DJS-FIX-6-14
 */
@Data
public class PigTransferMpVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 转移日期（'YYYY-MM-DD'）。 */
    private String transferDate;

    /** 转移栋舍（newBarnName + newPenName；出栏 → 「出栏」）。 */
    private String targetBarn;

    /** 开始时间（旧栏位驻留起始 'YYYY-MM-DD'）；缺 → null。 */
    private String fromDate;

    /** 结束时间（旧栏位驻留结束 'YYYY-MM-DD'）；缺 → null。 */
    private String toDate;

    /** 是否出栏行（newBarnId 为空，mp 端标识用）。 */
    private Boolean slaughter;
}
