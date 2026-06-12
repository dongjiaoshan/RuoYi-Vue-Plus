package org.dromara.djs.plant.organic.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 果蔬证书-作物关联实体（FIX-PLT-AD-INFO-LIST-001 一证多作物）。
 *
 * <p>对应表 {@code t_plant_crop_organic_rel}（V202606290930）。</p>
 * <ul>
 *   <li>多对多关联：一张果蔬证书可关联多个作物；一个作物也可被多张证书覆盖（历史证书）</li>
 *   <li>UNIQUE(tenant_id, organic_id, crop_id, del_unique) — del_unique 让软删后重关联同对不冲突</li>
 *   <li>编辑保存：service 层"先软删旧关联（del_unique=id），再插入新关联"（同事务），
 *       与 {@link OrganicPlotno} 土地证书-地块关联同范式</li>
 * </ul>
 *
 * @author djs
 * @since FIX-PLT-AD-INFO-LIST-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_crop_organic_rel")
public class OrganicCropno extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 证书 ID FK → t_plant_crop_organic.id。 */
    private Long organicId;

    /** 作物 ID FK → t_plant_crop_info.id。 */
    private Long cropId;

    @TableLogic
    private String delFlag;

    private Long delUnique;
}
