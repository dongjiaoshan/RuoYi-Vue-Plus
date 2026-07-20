package org.dromara.djs.plant.plot.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 地块 picker 轻量 VO（MP-PICKERS-001）。
 *
 * <p>给 mp {@code PlotPicker} 用——农事录入 / 采摘选地块。只暴露 id + plotCode + plotName
 * 3 字段，对应表 {@code t_plant_plot_info}。</p>
 *
 * <p>不复用 admin {@code PlotInfoVo}，避免土壤 / 经纬度 / 图片等无关重字段进 mp 流量。</p>
 *
 * @author djs
 * @since MP-PICKERS-001
 */
@Data
public class PlotPickerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 地块 ID（snowflake，Jackson 全局序列化为 string）。
     */
    private Long id;

    /**
     * 地块业务码（如 PLOT001）。
     */
    private String plotCode;

    /**
     * 地块名称。
     */
    private String plotName;

}
