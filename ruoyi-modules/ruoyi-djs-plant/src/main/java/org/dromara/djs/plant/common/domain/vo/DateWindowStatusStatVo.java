package org.dromara.djs.plant.common.domain.vo;

import lombok.Data;
import org.dromara.djs.plant.common.util.DateWindowStatusCalculator;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 「日期窗口五档状态」计数 VO：列表顶部统计版块的五个数字。
 *
 * <p>果蔬上市计划与采摘计划共用（同一套 {@link DateWindowStatusCalculator} 判定，只是中文名不同），
 * 字段名沿用状态码，中文由各自前端 i18n 给。</p>
 *
 * <p><b>统计范围</b>：除「状态」本身以外的其余筛选条件照常生效，状态条件被忽略 ——
 * 否则选中一档后另外四档必然是 0，版块就没有意义了。</p>
 *
 * <p>开始日期为空（没排明细）的行状态为空，五档都不计入，因此五个数之和可能小于列表总行数。</p>
 *
 * @author djs
 */
@Data
public class DateWindowStatusStatVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 待上市 / 待采摘。 */
    private Integer pending;

    /** 即将上市 / 即将采摘。 */
    private Integer upcoming;

    /** 上市中 / 采摘中。 */
    private Integer onSale;

    /** 即将下市 / 即将结束采摘。 */
    private Integer ending;

    /** 已下架 / 完成采摘。 */
    private Integer offShelf;

    /**
     * 按「状态码 → 计数」装配；缺席的档补 0（前端五个格子恒显数字，不出现 {@code -}）。
     *
     * @param counts 状态码计数（可空）
     * @return 装配好的 VO
     */
    public static DateWindowStatusStatVo of(Map<String, Integer> counts) {
        Map<String, Integer> src = counts == null ? Map.of() : counts;
        DateWindowStatusStatVo vo = new DateWindowStatusStatVo();
        vo.setPending(src.getOrDefault(DateWindowStatusCalculator.PENDING, 0));
        vo.setUpcoming(src.getOrDefault(DateWindowStatusCalculator.UPCOMING, 0));
        vo.setOnSale(src.getOrDefault(DateWindowStatusCalculator.ON_SALE, 0));
        vo.setEnding(src.getOrDefault(DateWindowStatusCalculator.ENDING, 0));
        vo.setOffShelf(src.getOrDefault(DateWindowStatusCalculator.OFF_SHELF, 0));
        return vo;
    }
}
