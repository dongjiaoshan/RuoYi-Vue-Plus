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
 * 育种信息（品种 + 品系合表）实体（BRD-MD-001 / CR-20260519-06）。
 *
 * <p>对应表 {@code t_farm_breed_info}：品种与品系合表，{@code breedStrain} 字段区分
 * （{@code 1=品种 / 2=品系}）。配种关系通过 {@code breedStrainCode} 字符串引用，不走 FK ID。</p>
 *
 * <p>软删走 {@code del_flag} + {@code del_unique}（{@link org.dromara.djs.common.handler.DjsMetaObjectHandler}
 * 在 delFlag='1' 时把 id 写入 delUnique，保证 UNIQUE(tenant_id, breed_strain, breed_strain_code, del_unique)
 * 不阻塞同编码复用）。服务层走 {@link DjsBaseServiceImpl#softDelete}。</p>
 *
 * @author djs
 * @since BRD-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_breed_info")
public class BreedInfo extends TenantEntity implements DjsBaseServiceImpl.SoftDeletable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 类型（{@code 1=品种 / 2=品系}，字典 {@code djs_breed_strain_type}）。
     */
    private Integer breedStrain;

    /**
     * 品种/品系编码（业务码，字符串引用 — 被 {@link BreedConfig} 的
     * {@code motherCode / fatherCode / cubCode} 引用）。
     */
    private String breedStrainCode;

    /**
     * 品种/品系名称。
     */
    private String breedStrainName;

    /**
     * 父级编码（品系归属品种时填，引用同表 {@code breedStrainCode}）。
     */
    private String parentCode;

    /**
     * 描述。
     */
    private String description;

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
     * 软删唯一性辅助列：未删时 0；软删时由 {@link org.dromara.djs.common.handler.DjsMetaObjectHandler}
     * 写入 id，保证 UNIQUE(tenant_id, breed_strain, breed_strain_code, del_unique) 不阻塞同编码复用。
     */
    private Long delUnique;

}
