package org.dromara.djs.breed.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 育肥指标趋势折线 VO（FIX-MGMT-MP-BRD-001，原型"育肥 tab·育肥指标趋势折线"）。
 *
 * <p>周/月 selector × metric toggle（出栏 market / 出栏指标 target / 在养 onhand）。
 * {@link #labels} = x 轴时间标签（周："MM-dd"周起点；月："yyyy-MM"），{@link #values} 一一对应。</p>
 *
 * <p>另带 {@link #liveStock} 3 格当前实时库存（育肥 / 保育 / 可出栏），原型育肥 tab 顶部实时库存 3 卡，
 * 与趋势同屏返回省一次请求。</p>
 *
 * @author djs
 * @since FIX-MGMT-MP-BRD-001
 */
@Data
public class FatteningTrendVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 粒度：week / month。 */
    private String period;

    /** 指标：market（出栏头数）/ target（出栏目标）/ onhand（在养存栏）。 */
    private String metric;

    /** x 轴时间标签（升序）。 */
    private List<String> labels = new ArrayList<>();

    /** 与 labels 一一对应的指标值。 */
    private List<BigDecimal> values = new ArrayList<>();

    /** 育肥 tab 顶部实时库存 3 格（育肥存栏 / 保育存栏 / 可出栏），随趋势一并返回。 */
    private LiveStock liveStock = new LiveStock();

    /**
     * 育肥 tab 实时库存 3 格。
     */
    @Data
    public static class LiveStock implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 育肥存栏头数（pig_type=fattening 且未终止）。 */
        private Integer fattening = 0;

        /** 保育存栏头数（育肥猪中日龄 &lt;43 天，按 #7.6 边界）。 */
        private Integer nursery = 0;

        /** 可出栏头数（育肥猪中日龄 ≥ 出栏起算 211 天，#7.6 育肥后期段起）。 */
        private Integer marketable = 0;
    }
}
