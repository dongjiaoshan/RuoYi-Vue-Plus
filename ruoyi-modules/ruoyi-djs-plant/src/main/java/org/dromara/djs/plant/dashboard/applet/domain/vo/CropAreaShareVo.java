package org.dromara.djs.plant.dashboard.applet.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 管理·种植管理看板「当前农场果蔬分布」饼图单片（FIX-MGMT-MP-PLT-001）。
 *
 * <p>进行中计划明细（{@code plant_status='ongoing'}）按 {@code crop_id} 分组求面积，
 * 前端按 area 占比渲染饼图。</p>
 *
 * @author djs
 * @since FIX-MGMT-MP-PLT-001
 */
@Data
public class CropAreaShareVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 作物名称。 */
    private String cropName;

    /** 该作物当前种植面积（亩）。 */
    private BigDecimal area;

}
