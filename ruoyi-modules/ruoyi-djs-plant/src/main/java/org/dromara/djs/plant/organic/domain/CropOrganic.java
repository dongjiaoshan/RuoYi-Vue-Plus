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
 * 果蔬有机证书实体（PLT-MD-003）。
 *
 * <p>对应表 {@code t_plant_crop_organic}（V202606051130）。</p>
 * <ul>
 *   <li>{@code cropCertNo} 证书编号用户手填，UNIQUE(tenant_id, crop_cert_no, del_unique)</li>
 *   <li>{@code cropId} FK → t_plant_crop_info.id — 一证书一作物（NOT NULL）</li>
 *   <li>{@code isWarning} 默认 2（正常），定时任务每天 0 点扫描 ≤ 60 天置 1（预警）</li>
 * </ul>
 *
 * @author djs
 * @since PLT-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_crop_organic")
public class CropOrganic extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 果蔬证书编号（用户手填，UNIQUE）。 */
    private String cropCertNo;

    /** 颁发单位。 */
    private String cropCertCompany;

    /** 证书有效期到期日。 */
    private LocalDate cropCertValid;

    /** 关联作物 ID FK → t_plant_crop_info.id。 */
    private Long cropId;

    /** 缩略图 OSS ossId（单张）。 */
    private String cropImagePreview;

    /** 原图 OSS ossIds 逗号分隔（多张）。 */
    private String cropImageUrl;

    /** 是否预警（字典 djs_yes_no：1=预警 / 2=正常，默认 2）。 */
    private Integer isWarning;

    @TableLogic
    private String delFlag;

    private Long delUnique;
}
