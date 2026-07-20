package org.dromara.djs.plant.farm.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 种植类工种作业 tab「片区胶囊」计数 VO（FIX-PLT-MP-CROPSEL-001 P12/P15·补片区筛选）。
 *
 * <p>原型：作物 selector 顶部 {@code A区(12) | B区(11) | C区(9)} 片区胶囊。计数 = 该片区下、按工种状态规则
 * 可操作的地块去重数（= 该片区下作物卡 plotCount 之和）。含 0 地块的活跃片区也返回（胶囊全量显示）。
 * 工种状态规则与 {@link FarmCropTargetCardVo} 同口径。</p>
 *
 * @author djs
 * @since FIX-PLT-MP-CROPSEL-001
 */
@Data
public class CropZoneCountVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 片区 id（雪花，前端按 string 处理）。 */
    private Long zoneId;

    /** 片区名。 */
    private String zoneName;

    /** 该片区下按工种规则可操作的地块去重数（含 0）。 */
    private Integer plotCount;
}
