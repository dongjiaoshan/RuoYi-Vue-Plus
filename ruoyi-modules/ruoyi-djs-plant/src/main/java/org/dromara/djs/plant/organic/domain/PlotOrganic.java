package org.dromara.djs.plant.organic.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 土地有机证书实体（PLT-MD-003）。
 *
 * <p>对应表 {@code t_plant_plot_organic}（V202606051130）。</p>
 * <ul>
 *   <li>{@code organicNo} 证书编号用户手填，UNIQUE(tenant_id, organic_no, del_unique)</li>
 *   <li>关联地块走 {@code t_plant_organic_plotno}（多对多，service 层维护）</li>
 *   <li>{@code isWarning} 默认 2（正常），定时任务每天 0 点扫描 ≤ 60 天置 1（预警）</li>
 *   <li>OSS 字段同 plot：{@code organicImagePreview}（缩略单张 ossId）+ {@code organicImageUrl}（原图多张 ossIds 逗号分隔）</li>
 * </ul>
 *
 * @author djs
 * @since PLT-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_plot_organic")
public class PlotOrganic extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（雪花）。 */
    @TableId
    private Long id;

    /** 证书编号（用户手填，UNIQUE）。 */
    private String organicNo;

    /** 颁发单位（例 南京国环）。 */
    private String organicCompany;

    /** 证书有效期到期日。 */
    private LocalDate organicValid;

    /** 缩略图 OSS ossId（单张）。 */
    private String organicImagePreview;

    /** 原图 OSS ossIds 逗号分隔（多张）。 */
    private String organicImageUrl;

    /** 是否预警（字典 djs_yes_no：1=预警 / 2=正常，默认 2）。 */
    private Integer isWarning;

    /** 软删标记。 */
    @TableLogic
    private String delFlag;

    /** 软删唯一性辅助列。 */
    private Long delUnique;
}
