package org.dromara.djs.plant.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 种植看板 - 有机证书情况一览（区块 3，PLT-DASH-001）。
 *
 * <p>原型右上卡 4 个数字：</p>
 * <ul>
 *   <li>土地有机证书最早到期天数：{@code MIN(DATEDIFF(organic_valid, CURDATE()))}（t_plant_plot_organic）。</li>
 *   <li>作物有机证书最早到期天数：{@code MIN(DATEDIFF(crop_cert_valid, CURDATE()))}（t_plant_crop_organic）。</li>
 *   <li>作物无证书品类数：{@code t_plant_crop_info} 中无对应 {@code t_plant_crop_organic} 记录的作物数。</li>
 *   <li>预留证书品类数：{@code t_plant_crop_info} 总作物数（原型"预留证书品类数"= 已建档可挂证的作物品类总数）。</li>
 * </ul>
 *
 * <p>无证书时到期天数返回 null（前端展示"-"）；计数返回 0。</p>
 *
 * @author djs
 * @since PLT-DASH-001
 */
@Data
public class OrganicCertOverviewVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 土地有机证书最早到期天数（{@code MIN(DATEDIFF(organic_valid, CURDATE()))}，可为负=已过期）。
     *
     * <p>无在册土地证书时为 null。</p>
     */
    private Integer plotCertMinDays;

    /**
     * 作物有机证书最早到期天数（{@code MIN(DATEDIFF(crop_cert_valid, CURDATE()))}，可为负=已过期）。
     *
     * <p>无在册作物证书时为 null。</p>
     */
    private Integer cropCertMinDays;

    /**
     * 作物无证书品类数（已建档作物中尚无有机证书的作物品类数）。
     */
    private Integer cropNoCertCount;

    /**
     * 预留证书品类数（已建档作物品类总数，可挂有机证书的候选品类）。
     */
    private Integer cropReservedCount;

}
