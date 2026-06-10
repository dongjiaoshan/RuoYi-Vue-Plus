package org.dromara.djs.plant.plot.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 翻耕目标卡（空地）轻量 VO（FIX-PLT-MP-TILL-001 P6/P8）。
 *
 * <p>专给 mp 翻耕作业 tab 的「空地地块卡」用——只列 {@code plot_status=1} 空地，
 * 带「空地日期」（最近退茬日·派生）。不复用通用 {@link PlotPickerVo}，避免给 picker 塞 idleDate。</p>
 *
 * @author djs
 * @since FIX-PLT-MP-TILL-001
 */
@Data
public class IdlePlotVo implements Serializable {

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

    /**
     * 地块状态文案（空地恒「空闲」）。
     */
    private String plotStatusLabel;

    /**
     * 空地日期（= 该地块最近一条 rotation farm_date，派生；无退茬记录留空）。
     */
    private LocalDate idleDate;
}
