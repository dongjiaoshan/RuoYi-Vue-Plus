package org.dromara.djs.common.image.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 分类默认图实体（IMG-LIB-001）。
 *
 * <p>对应表 {@code t_md_default_image}，4 层 resolver 的 L2/L3 兜底源：</p>
 * <ul>
 *   <li>L2：{@code category_key} = belong_type 值（pork / vegetable / white_bar / dry_good / egg / gift_box）</li>
 *   <li>L3：{@code category_key='global'} 且 {@code is_global=1} 全局兜底</li>
 * </ul>
 *
 * @author djs
 * @since IMG-LIB-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_md_default_image")
public class DefaultImage extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花；seed 7 行用固定 id）。
     */
    @TableId
    private Long id;

    /**
     * 分类键（belong_type 值 / vegetable / global）。
     */
    private String categoryKey;

    /**
     * 默认图 ossId（{@code sys_oss.oss_id}；未配置时 NULL）。
     */
    private String ossId;

    /**
     * 是否全局兜底（1 是 / 0 否）。
     */
    private Integer isGlobal;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 软删标记（'0' 未删 / '1' 已删）。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删唯一性辅助列。
     */
    private Long delUnique;

}
