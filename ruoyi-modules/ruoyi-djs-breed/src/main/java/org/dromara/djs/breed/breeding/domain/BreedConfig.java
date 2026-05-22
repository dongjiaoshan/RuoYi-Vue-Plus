package org.dromara.djs.breed.breeding.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;
import org.dromara.djs.common.base.DjsBaseServiceImpl;

import java.io.Serial;

/**
 * 育种配置（配种关系合表）实体（BRD-MD-001 / CR-20260519-06）。
 *
 * <p>对应表 {@code t_farm_breed_config}：品种配种 + 品系配种合表，{@code breedStrain} 字段区分
 * （{@code 1=品种配种 / 2=品系配种}）。三个 {@code *Code} 字段引用 {@link BreedInfo#getBreedStrainCode()}。</p>
 *
 * <p>建模决策（CR-20260519-06 文档）：用 {@code motherCode / fatherCode / cubCode} 字符串字段而非
 * FK ID，优势是历史记录改名不污染。仔代品种必须**先**在 {@code t_farm_breed_info} 里建好后才能在此引用。</p>
 *
 * <p>软删走 {@code del_flag} + {@code del_unique}，UNIQUE(tenant_id, breed_strain, mother_code, father_code, del_unique)。</p>
 *
 * @author djs
 * @since BRD-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_breed_config")
public class BreedConfig extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 类型（{@code 1=品种配种 / 2=品系配种}，字典 {@code djs_breed_strain_type}）。
     */
    private Integer breedStrain;

    /**
     * 母本编码（引用 {@link BreedInfo#getBreedStrainCode()}）。
     */
    private String motherCode;

    /**
     * 父本编码。
     */
    private String fatherCode;

    /**
     * 仔代编码（必须先在 {@code t_farm_breed_info} 建好后才能引用）。
     */
    private String cubCode;

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
