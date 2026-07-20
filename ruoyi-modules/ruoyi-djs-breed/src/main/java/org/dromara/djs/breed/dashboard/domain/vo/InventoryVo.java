package org.dromara.djs.breed.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实时库存 VO（BRD-DASH-001）。
 *
 * <p>字段名与 admin dashboard 图表 axis label 对齐（doc/12 §2.1）：
 * sow / boar / piglet / fattening / reserve 5 类。</p>
 *
 * <p>返回示例：</p>
 * <pre>
 * {
 *   "inventoryByType": { "sow": 23, "boar": 5, "piglet": 43, "fattening": 454, "reserve": 5 },
 *   "sowByLifecycle":  { "PZ": 13, "FM": 6, "DN": 2, "KH": 2 }
 * }
 * </pre>
 *
 * @author djs
 * @since BRD-DASH-001
 */
@Data
public class InventoryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** key = pig_type (sow/boar/piglet/fattening/reserve) → count；缺失 type 不写入。 */
    private Map<String, Integer> inventoryByType = new LinkedHashMap<>();

    /** key = lifecycle (PZ/FM/DN/KH/...) → count；仅活母猪。 */
    private Map<String, Integer> sowByLifecycle = new LinkedHashMap<>();
}
