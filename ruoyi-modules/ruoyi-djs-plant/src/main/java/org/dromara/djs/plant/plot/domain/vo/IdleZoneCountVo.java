package org.dromara.djs.plant.plot.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 片区空地计数 VO（FIX-PLT-MP-TILL-001 P6 翻耕筛选胶囊）。
 *
 * <p>给 mp 翻耕作业 tab 的「带计数片区胶囊」用：{@code A区(12)}。
 * {@code idleCount} = 该片区下 {@code plot_status=1} 空地地块数（LEFT JOIN 保 0 也出胶囊）。</p>
 *
 * @author djs
 * @since FIX-PLT-MP-TILL-001
 */
@Data
public class IdleZoneCountVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 片区 ID（snowflake，Jackson 全局序列化为 string）。
     */
    private Long zoneId;

    /**
     * 片区名称（如 A 区）。
     */
    private String zoneName;

    /**
     * 该片区下空地（plot_status=1）地块数。
     */
    private Integer idleCount;
}
