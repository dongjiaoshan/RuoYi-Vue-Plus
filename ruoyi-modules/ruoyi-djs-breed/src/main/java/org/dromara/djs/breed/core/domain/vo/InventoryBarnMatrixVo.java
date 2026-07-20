package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

import java.util.Map;

/**
 * 库存看板 - 栋舍 × 状态矩阵项（BRD-INVENTORY-001，mp 端）。
 *
 * <p>单个栋舍下，按状态维度的计数明细。母猪段 byStatus 维度 = current_status；
 * 其它段无细分状态时 byStatus 为空，mp 仅展示该栋头数。</p>
 */
@Data
public class InventoryBarnMatrixVo {

    /** 栋舍ID（string，全链路禁 Number） */
    private String barnId;

    /** 栋舍名称 */
    private String barnName;

    /** 该栋舍总头数 */
    private Integer count;

    /** 按状态分组计数：{ PZ: 11, DN: 23 } —— key 走前端字典翻译，不上屏原始 code */
    private Map<String, Integer> byStatus;

    /**
     * 按日龄/周龄段分组计数（肥猪/仔猪/后备段）：{ "1-5日": 2, "6-10日": 3 }。
     *
     * <p>key 已是中文分段 label（与 {@code ageDist} 同一套分桶逻辑），前端直接 key:value 上屏，
     * 不再翻译。母猪段为空（母猪走 byStatus 状态维度）。LinkedHashMap 保证段顺序与图表一致。</p>
     */
    private Map<String, Integer> byAge;
}
