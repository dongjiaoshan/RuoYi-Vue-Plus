package org.dromara.djs.breed.production.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;
import org.dromara.djs.common.base.DjsBaseServiceImpl;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 精液 / 公猪配置实体（BRD-MD-003 Tab2）。
 *
 * <p>对应表 {@code t_farm_boar_config}。V1 单条"通用配置"（{@code boar_id=NULL}），
 * V2 可针对具体公猪覆盖。</p>
 *
 * <p>UNIQUE(tenant_id, boar_id, del_unique)。{@code boar_id=NULL} 时业务侧确保通用配置只有一条。</p>
 *
 * @author djs
 * @since BRD-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_boar_config")
public class BoarConfig extends TenantEntity implements DjsBaseServiceImpl.SoftDeletable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 关联公猪 ID（V2 启用）；V1 留 {@code NULL} 表示"通用配置"。
     */
    private Long boarId;

    /**
     * 精液密度阈值（亿/mL，BigDecimal 保两位精度）。
     */
    private BigDecimal spermQualityThreshold;

    /**
     * 同一公猪两次采精的最小间隔天数。
     */
    private Integer breedingIntervalDays;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 软删标记。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删唯一性辅助列。
     */
    private Long delUnique;

}
