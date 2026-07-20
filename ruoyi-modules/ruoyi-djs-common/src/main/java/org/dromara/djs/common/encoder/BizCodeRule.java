package org.dromara.djs.common.encoder;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 业务编码规则配置实体（SYS-INFRA-004）。
 *
 * <p>表对应 {@code t_md_biz_code_rule}，由 {@link IBizCodeGenerator} 实现按
 * {@code (tenantId, codeType)} 查规则，决定如何拼装编码。一行规则对应一种业务编码类型
 * （EAR_NO / TRACE_CODE / DEMAND_NO / SHIP_NO / STOCK_FLOW_NO / PRODUCE_NO / RETURN_NO ...，
 * 全量见 {@link BizCodeType}）。</p>
 *
 * @author djs
 * @since SYS-INFRA-004
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_md_biz_code_rule")
public class BizCodeRule extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键。
     */
    @TableId
    private Long id;

    /**
     * 编码类型，对应 {@link BizCodeType} 枚举名（EAR_NO / TRACE_CODE / DEMAND_NO ...）。
     */
    private String codeType;

    /**
     * 编码格式串，支持占位符
     * {@code {farmCode2}{barnCode2}{yyMM}{yyMMdd}{yyyy}{yyyyMMdd}{prefix}{dailySeq4}{seq3}{seq4}{seq5}{seq6}{ioCode2}{bizCode2}{productCode2}}。
     * <p>{@code {prefix}} 为业态前缀占位符（PRODUCE_NO 使用），从 {@code context.prefix} 取原样字符串。</p>
     */
    private String pattern;

    /**
     * 序号重置策略：
     * <ul>
     *   <li>{@code 1} = 每日重置（seq_date = {@code yyyyMMdd}）</li>
     *   <li>{@code 2} = 按年重置（seq_date = {@code yyyy}，PLAN_NO 使用）</li>
     *   <li>{@code 3} = 每日重置 + 按业态前缀分桶（seq_date = {@code yyMMdd + context.prefix}，
     *       PRODUCE_NO 使用：每业态前缀当日各自独立从 0001 起算）</li>
     *   <li>{@code 0} / {@code null} = 终生递增（seq_date = {@code ""}）</li>
     * </ul>
     * 字段名沿用 {@code daily_reset}（向后兼容已落地 seed），语义已超出"是否每日"。
     */
    private Integer dailyReset;

    /**
     * 固定前缀（如追溯码 "T" / 需求单 "D" / 流水号 "F"），仅用于人工识别，已包含在 {@code pattern} 中。
     */
    private String prefix;

    /**
     * 序号位数（4 或 6），用于占位符 {@code {seq4}} / {@code {seq6}} / {@code {dailySeq4}} 补零长度提示。
     */
    private Integer seqLength;

    /**
     * 状态：0=启用 1=停用。停用时 generator 抛 ServiceException。
     */
    private String status;

    /**
     * 软删除标志（0=正常 1=已删除）。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删除唯一性辅助列（应用层在 {@code DjsMetaObjectHandler.updateFill} 同步赋 id）。
     */
    private Long delUnique;
}
