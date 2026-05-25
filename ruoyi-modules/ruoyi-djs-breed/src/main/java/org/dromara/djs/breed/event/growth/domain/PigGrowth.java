package org.dromara.djs.breed.event.growth.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 猪只生长记录实体（BRD-EVENT-005 GROWTH）。
 *
 * <p>对应表 {@code t_farm_pig_growth}。
 * mp 端饲养员录体重；admin 端补录背膘 / 背高（专业设备）。
 * <strong>不触发状态机</strong> — 仅 INSERT 测量数据，不修改 {@code pig.current_status}。</p>
 *
 * @author djs
 * @since BRD-EVENT-005
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_pig_growth")
public class PigGrowth extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 猪只 pig_info.id。 */
    private Long pigId;

    /** 猪只耳号（冗余）。 */
    private String earNo;

    /** 测量日期。 */
    private LocalDate measureDate;

    /** 体重 kg（必填）。 */
    private BigDecimal weight;

    /** 背膘厚 mm（admin 端可选，专业设备）。 */
    private BigDecimal backfatThickness;

    /** 背高 cm（admin 端可选）。 */
    private BigDecimal backHeight;

    /** 测量照片 OSS IDs（逗号分隔，bizType=grow_photo）。 */
    private String photoOssIds;

    /** 操作人 user_id（从 LoginUser 取）。 */
    private Long operatorId;

    /** 栋舍名称（冗余）。 */
    private String barnName;

    /** 栏位名称（冗余）。 */
    private String penName;

    /** 软删标记。 */
    @TableLogic
    private String delFlag;

    /** 备注。 */
    private String remark;
}
