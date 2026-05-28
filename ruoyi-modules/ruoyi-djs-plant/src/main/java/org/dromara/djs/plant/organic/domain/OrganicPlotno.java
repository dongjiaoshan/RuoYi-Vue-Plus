package org.dromara.djs.plant.organic.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 土地证书-地块关联实体（PLT-MD-003）。
 *
 * <p>对应表 {@code t_plant_organic_plotno}（V202606051130）。</p>
 * <ul>
 *   <li>多对多关联：一张土地证书可关联多个地块；一个地块也可被多张证书覆盖（历史证书）</li>
 *   <li>UNIQUE(tenant_id, organic_id, plot_id, del_unique) — del_unique 让软删后重关联同对不冲突</li>
 *   <li>编辑保存：service 层"先软删旧关联（del_unique=id），再插入新关联"（同事务）</li>
 * </ul>
 *
 * @author djs
 * @since PLT-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_organic_plotno")
public class OrganicPlotno extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 证书 ID FK → t_plant_plot_organic.id。 */
    private Long organicId;

    /** 地块 ID FK → t_plant_plot_info.id。 */
    private Long plotId;

    @TableLogic
    private String delFlag;

    private Long delUnique;
}
