package org.dromara.djs.breed.core.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 母猪最近一条分娩日期 VO（D12X-MP-FARROW-WEANING-001 断奶选猪到期窗口）。
 *
 * <p>仅内部使用：{@code PigMapper.selectLastFarrowDateByPigIds} 批量查给定母猪集合各自的
 * 最近分娩日期，供 {@code searchByEarKeyword(dueType=WEANING)} 计算「分娩日 + 哺乳天数 ≤ today」
 * 的可断奶窗口。不直接出参给前端。</p>
 *
 * @author djs
 * @since D12X-MP-FARROW-WEANING-001
 */
@Data
public class PigLastFarrowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 母猪 id（t_farm_pig_info.id）。 */
    private Long pigId;

    /** 该母猪最近一条分娩日期（DATE(MAX(farrow_date))）。 */
    private LocalDate lastFarrowDate;
}
