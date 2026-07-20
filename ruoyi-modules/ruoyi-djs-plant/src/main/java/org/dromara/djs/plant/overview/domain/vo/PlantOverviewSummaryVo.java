package org.dromara.djs.plant.overview.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 种植总览汇总 VO（FIX-PLT-AD-OVERVIEW-001 图1）。
 *
 * <p>顶部一行 5 个产量类 KPI（<b>单位吨</b>，决策#7：kg→吨换算在本 VO/service 做）
 * + 作物卡片列表（{@link CropOverviewCardVo}，kg）。纯只读，无写操作。</p>
 *
 * <ul>
 *   <li>① idlePlotCount 空地块数（plot_status=1）</li>
 *   <li>② plantedPlotCount 已种植地块数（plot_status=2）</li>
 *   <li>③ harvestedTotalTon 已采摘总量（吨）= SUM(actual_yield kg) / 1000</li>
 *   <li>④ expectedTotalTon 预计总产量（吨）= SUM(expected_yield kg) / 1000</li>
 *   <li>⑤ remainingExpectedTon 剩余预计总产量（吨）= (expected - actual) / 1000</li>
 * </ul>
 *
 * @author djs
 * @since FIX-PLT-AD-OVERVIEW-001
 */
@Data
public class PlantOverviewSummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** ① 空地块数（plot_status=1）。 */
    private Integer idlePlotCount;

    /** ② 已种植地块数（plot_status=2）。 */
    private Integer plantedPlotCount;

    /** ③ 已采摘总量（吨，SUM(actual_yield)/1000，2 位小数）。 */
    private BigDecimal harvestedTotalTon;

    /** ④ 预计总产量（吨，SUM(expected_yield)/1000，2 位小数）。 */
    private BigDecimal expectedTotalTon;

    /** ⑤ 剩余预计总产量（吨，(expected-actual)/1000，2 位小数，下限 0）。 */
    private BigDecimal remainingExpectedTon;

    /** 作物卡片列表（每作物一卡，整卡可点击下钻；无数据时空列表）。 */
    private List<CropOverviewCardVo> crops;
}
