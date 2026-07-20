package org.dromara.djs.breed.event.eartag.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 仔猪耳号打标记录实体（BRD-EVENT-003）。
 *
 * <p>对应表 {@code t_farm_pig_pigletno}（SYS-INIT-001 V202605200901 L447-473 建表）。
 * 每条记录表示一头仔猪被批量贴耳标的业务日志，反向指针 {@link #pigId} 指向新插入的
 * {@code t_farm_pig_info} 行（pig_type='piglet'）。</p>
 *
 * <p><b>设计要点</b>：仔猪不参与状态机（默认 current_status='HB'），本表只记业务日志；
 * 真实 pig 主表行由 service 同事务 INSERT，不调 {@code fireEvent} / {@code createPig}。</p>
 *
 * @author djs
 * @since BRD-EVENT-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_pig_pigletno")
public class PigPigletno extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 仔猪耳号（由 SYS-INFRA-004 generateBatch 一次连续生成 N 个）。 */
    private String pigletEarNo;

    /** 母猪耳号（来自 farrow.ear_no）。 */
    private String motherEarNo;

    /** 父猪耳号（来自 farrow.breeding_id → t_farm_pig_breeding.boar_ear_no，可空）。 */
    private String fatherEarNo;

    /** 关联分娩记录 ID（t_farm_pig_farrow.id）。 */
    private Long farrowId;

    /** 打标日期。 */
    private LocalDateTime tagDate;

    /** 仔猪性别（F=母 M=公，对齐 DDL piglet_sex CHAR(1)）。 */
    private String pigletSex;

    /** 出生重 kg（可选）。 */
    private BigDecimal birthWeight;

    /** 反向指针：生成的 t_farm_pig_info.id。 */
    private Long pigId;

    /** 操作人 user_id（由 DjsMetaObjectHandler 自动注入）。 */
    private Long operatorId;

    /** 备注。 */
    private String remark;

    /** 软删标记（'0' 未删 / '1' 已删）。 */
    @TableLogic
    private String delFlag;

    /** 软删唯一性辅助列。 */
    private Long delUnique;
}
