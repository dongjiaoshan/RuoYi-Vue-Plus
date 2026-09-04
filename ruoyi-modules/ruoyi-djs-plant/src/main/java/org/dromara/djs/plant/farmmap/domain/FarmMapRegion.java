package org.dromara.djs.plant.farmmap.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 农场地图「格子 ↔ 地块」绑定实体（PLT-FARMMAP-001）。
 *
 * <p>对应表 {@code t_plant_farm_map_region}（V202609030900）。</p>
 *
 * <p><b>表里只存已绑定的行</b>：某个 regionKey 查不到行 = 这个格子还没挂地块，
 * 解绑走软删。所以 {@code plotId} NOT NULL——不存在「有行但没挂地块」的半绑定态，
 * 那种设计会让 UNIQUE(tenant_id, plot_id, del_unique) 因多个 NULL 而失去 1:1 约束力。</p>
 *
 * <p>图上格子的**几何**不在这张表里，在前端
 * {@code src/views/djs-plant/farmmap/map/regions.generated.ts}（由描图脚本从甲方地图生成）。
 * 几何进代码是因为改画法属于改版；绑定进库是因为它是用户数据、要随时可改。</p>
 *
 * @author djs
 * @since PLT-FARMMAP-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_farm_map_region")
public class FarmMapRegion extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 图上格子业务码（regions.generated.ts 的 key，例 {@code R-001}）。
     *
     * <p>🔴 永不重编号：重跑描图脚本只允许让同一个 key 的形状变准，不允许让它指向另一个格子，
     * 否则库里所有绑定会整体错位到别的地上。</p>
     */
    private String regionKey;

    /**
     * 挂的地块 FK → {@code t_plant_plot_info.id}。
     */
    private Long plotId;

    /**
     * 软删标志。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删 token（软删时 SET del_unique=id，见 DjsBaseServiceImpl#softDelete）。
     */
    private Long delUnique;

}
