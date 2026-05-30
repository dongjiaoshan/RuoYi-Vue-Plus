package org.dromara.djs.breed.event.castrate.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 阉割记录实体（BRD-EVENT-004 CASTRATE）。
 *
 * <p>对应表 {@code t_farm_castrate_record}（SYS-INIT-001 DDL #19）。
 * 由 {@code CastrateServiceImpl} 在阉割事件录入时 INSERT；service 层校验 pig_sex='M'，
 * 状态机仅状态不变事件（NO_CHANGE_EVENTS），状态保持 BOAR_ACTIVE。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_castrate_record")
public class CastrateRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 猪只 pig_info.id。 */
    private Long pigId;

    /** 猪只耳号。 */
    private String earNo;

    /** 阉割日期。 */
    private LocalDateTime castrateDate;

    /** 操作人（系统登录态账号 ID）。 */
    private Long operatorId;

    /** 阉割人员（原型 87 字段，现场实际执刀人员名/编码；与 operatorId 并存）。 */
    private String castrater;

    /** 软删标记。 */
    @TableLogic
    private String delFlag;

    /** 备注。 */
    private String remark;
}
