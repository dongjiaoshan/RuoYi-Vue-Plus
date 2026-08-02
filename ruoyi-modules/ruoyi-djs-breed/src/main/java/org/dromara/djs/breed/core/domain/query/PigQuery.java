package org.dromara.djs.breed.core.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 猪只列表查询入参（BRD-CORE-001）。
 *
 * @author djs
 * @since BRD-CORE-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PigQuery extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 耳号（精确匹配）。 */
    private String earNo;

    /** 性别（F/M）。 */
    private String pigSex;

    /** 类型（sow/boar/piglet/fattening）。 */
    private String pigType;

    /** 当前状态（10 lifecycle，精确）。 */
    private String currentStatus;

    /** 栋舍 ID。 */
    private Long barnId;

    /** 栋舍名称（模糊，反查 t_farm_barn_info.barn_name 命中的 barn_id 集合再 in(barnIds)）。 */
    private String barnName;

    /** 栏位 ID。 */
    private Long penId;

    /** 栏位名称（模糊，反查 t_farm_barn_pen.pen_name 命中的 pen_id 集合再 in(penIds)）。 */
    private String penName;

    /** 是否排除终态（true=不返 END 行）。 */
    private Boolean excludeEnd;

    /**
     * 最大日龄过滤（天，小程序 row251）：只返日龄 ≤ 本值的猪只。
     *
     * <p>日龄 = {@code NOW − COALESCE(birth_date, introduce_date)}，与 {@code minAgeDays} / calcAgeDays 同口径。
     * mp 疫苗药品板块猪只列表选中「育肥猪」时传「用药配置」{@code fatten_med_max_age_days}（默认 300），
     * 超龄育肥猪临近出栏不再用药 → 不进候选。{@code null}/≤0 → 不过滤（其余所有调用方行为不变）。</p>
     *
     * <p>注：无生日的猪 DATEDIFF 返 NULL、比较非真会被剔除，与 minAgeDays 相反会误杀——故本条件写成
     * 「日龄为空 或 日龄 ≤ 上限」，无生日的猪按不超龄放行。</p>
     */
    private Integer maxAgeDays;
}
