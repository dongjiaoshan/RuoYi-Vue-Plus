package org.dromara.djs.plant.plot.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 地块查询参数（PLT-MD-001）。
 *
 * @author djs
 * @since PLT-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlotInfoQuery extends BaseEntity {

    /** 所属片区（双栏页右侧 BizTable 用这个串联）。 */
    private Long zoneId;

    /** 地块编码（精确）。 */
    private String plotCode;

    /** 地块名称（模糊）。 */
    private String plotName;

    /** 地块类型（djs_plot_type）。 */
    private String plotType;

    /** 状态（1 空闲 / 2 种植 / 3 采摘）。 */
    private Integer plotStatus;

    /** 是否租赁（1 / 0）。 */
    private Integer isLease;
}
