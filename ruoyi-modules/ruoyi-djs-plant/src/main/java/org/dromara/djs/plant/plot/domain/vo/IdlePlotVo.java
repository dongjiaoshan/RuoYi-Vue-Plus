package org.dromara.djs.plant.plot.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

    /**
     * 30 天内上次同工种（翻耕/整地/施肥）农事日期（row14；按 idlePlots 入参 farmType 回填；
     * 无 farmType 或近 30 天无该工种记录时留空）。
     */
    private LocalDate lastFarmDate;

    /**
     * 该地块最近若干条灾害记录（r18；按 farm_date 倒序，最多 3 条；无灾害记录时空 list）。
     *
     * <p>卡片在地块名下逐行展示「灾害类型 损失率% 日期」。{@code disasterTypeLabel} 已由后端
     * {@code djs_disaster_type} 字典翻译好（mp 卡无字典上下文，故服务端译）。</p>
     */
    private List<DisasterRecord> disasterRecords;

    /**
     * 单条灾害记录（地块卡多行展示用）。
     */
    @Data
    public static class DisasterRecord implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 灾害类型原始字典值（{@code djs_disaster_type}）。
         */
        private String disasterType;

        /**
         * 灾害类型字典标签（后端已翻译；字典缺失时回落为原始值）。
         */
        private String disasterTypeLabel;

        /**
         * 损失率 0-100。
         */
        private BigDecimal lossRate;

        /**
         * 灾害发生日期。
         */
        private LocalDate farmDate;
    }
}
