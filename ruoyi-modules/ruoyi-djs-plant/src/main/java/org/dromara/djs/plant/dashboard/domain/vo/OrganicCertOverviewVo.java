package org.dromara.djs.plant.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 种植看板 - 有机证书一览（区块 3，口径以「果蔬有机证书」为准）。
 *
 * <p>4 子项（{@code t_plant_crop_organic} 果蔬证书 + {@code t_plant_crop_info} 作物
 * + {@code t_plant_plant_details} 在种明细）：</p>
 * <ol>
 *   <li>果蔬有机证到期日：最新一张果蔬证书的有效期到期日（{@code MAX(crop_cert_valid)}），
 *       显示日期 {@code YYYY-MM-DD}，无证书时 null。</li>
 *   <li>果蔬有机证书到期天数：{@code DATEDIFF(MAX(crop_cert_valid), CURDATE())}，
 *       可为负（已过期），无证书时 null。</li>
 *   <li>作物无证书品类数：在种作物（{@code plant_status <> 'completed'}）中
 *       <b>不在</b>最新一张果蔬证书覆盖品类（{@code crop_id}）里的作物数量。</li>
 *   <li>果蔬有机证书品类数：在种作物中 <b>在</b>最新果蔬证书覆盖品类里的作物数量。</li>
 * </ol>
 *
 * <p>「最新证书」= 有效期最晚（{@code crop_cert_valid} MAX）的一张。</p>
 *
 * @author djs
 * @since PLT-DASH-001
 */
@Data
public class OrganicCertOverviewVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 果蔬有机证到期日（最新一张果蔬证书的到期日，{@code YYYY-MM-DD}）。
     *
     * <p>无在册果蔬证书时为 null（前端展示"-"）。</p>
     */
    private String cropCertExpiryDate;

    /**
     * 果蔬有机证书到期天数（{@code DATEDIFF(MAX(crop_cert_valid), CURDATE())}，可为负=已过期）。
     *
     * <p>无在册果蔬证书时为 null（前端展示"-"）。</p>
     */
    private Integer cropCertDaysToExpiry;

    /**
     * 作物无证书品类数（在种作物中不在最新果蔬证书覆盖品类里的数量）。
     */
    private Integer cropNoCertCount;

    /**
     * 果蔬有机证书品类数（在种作物中在最新果蔬证书覆盖品类里的数量）。
     */
    private Integer cropCertCategoryCount;

}
