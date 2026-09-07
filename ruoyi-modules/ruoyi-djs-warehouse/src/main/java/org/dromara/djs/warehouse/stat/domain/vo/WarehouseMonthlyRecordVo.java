package org.dromara.djs.warehouse.stat.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.warehouse.stat.domain.WarehouseMonthlyRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 仓库月数据记录视图对象（WMS-STAT-001，邓博 admin row18）。
 *
 * <p>{@code @AutoMapper} 生成 entity→VO 的 MapStruct converter，供 {@code selectVoList} /
 * {@code MapstructUtils.convert} 使用。缺它 {@code listMonthly} 转换会抛 ConvertException（接口 500）。</p>
 *
 * @author djs
 * @since WMS-STAT-001
 */
@Data
@AutoMapper(target = WarehouseMonthlyRecord.class)
public class WarehouseMonthlyRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计月份 yyyy-MM。 */
    private String statMonth;

    /** 屠宰头数。 */
    private Integer slaughterCount;
    /** 屠宰率%。 */
    private BigDecimal slaughterRate;
    /** 白条出品率%。 */
    private BigDecimal barYieldRate;
    /** 分割出品率%。 */
    private BigDecimal cutYieldRate;
}
