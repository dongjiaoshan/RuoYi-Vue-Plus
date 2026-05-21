package org.dromara.djs.common.encoder;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 业务编码序号实体（SYS-INFRA-004）。
 *
 * <p>表对应 {@code t_md_biz_code_sequence}，按 {@code (tenantId, codeType, seqDate)} 三元组计数：</p>
 * <ul>
 *   <li>{@code daily_reset=1} 的类型：{@code seqDate} 写 {@code yyyyMMdd}（每日重置）</li>
 *   <li>{@code daily_reset=0} 的类型：{@code seqDate} 写空串（终生递增）</li>
 * </ul>
 *
 * <p>序号增长走 Redisson 分布式锁 + 序号表 UPDATE 兜底，DB UNIQUE 约束保证最终一致性
 * （即使 Redis 故障，DB 也不会产生重复号）。</p>
 *
 * @author djs
 * @since SYS-INFRA-004
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_md_biz_code_sequence")
public class BizCodeSequence extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键。
     */
    @TableId
    private Long id;

    /**
     * 编码类型（{@link BizCodeType} 枚举名）。
     */
    private String codeType;

    /**
     * 序号统计周期：{@code yyyyMMdd}（每日重置）/ {@code yyyyMM}（每月）/ {@code yyyy}（每年）/ 空串（终生）。
     */
    private String seqDate;

    /**
     * 当前已用最大序号；下次生成 = {@code currentSeq + 1}。批量则 = {@code currentSeq + count}。
     */
    private Long currentSeq;

    /**
     * 软删除标志。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删除唯一性辅助列。
     */
    private Long delUnique;
}
