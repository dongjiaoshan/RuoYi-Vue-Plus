package org.dromara.djs.breed.breeding.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.djs.common.excel.DictOrRawConvert;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.breed.breeding.domain.BreedConfig;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 育种配置（配种关系）出参 VO（BRD-MD-001）。
 *
 * @author djs
 * @since BRD-MD-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BreedConfig.class)
public class BreedConfigVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键。
     */
    @ExcelProperty(value = "ID")
    private Long id;

    /**
     * 类型（1=品种配种 / 2=品系配种）。
     */
    @ExcelProperty(value = "类型", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_breed_strain_type")
    private Integer breedStrain;

    /**
     * 母系编码。
     */
    @ExcelProperty(value = "母系编码")
    private String motherCode;

    /**
     * 母系名称（由 {@code motherCode} JOIN t_farm_breed_info 取 breed_strain_name 富集，非持久化列）。
     */
    @ExcelProperty(value = "母系名称")
    private String motherName;

    /**
     * 父系编码。
     */
    @ExcelProperty(value = "父系编码")
    private String fatherCode;

    /**
     * 父系名称（由 {@code fatherCode} JOIN t_farm_breed_info 取 breed_strain_name 富集，非持久化列）。
     */
    @ExcelProperty(value = "父系名称")
    private String fatherName;

    /**
     * 仔代编码。
     */
    @ExcelProperty(value = "仔代编码")
    private String cubCode;

    /**
     * 仔代名称（由 {@code cubCode} JOIN t_farm_breed_info 取 breed_strain_name 富集，非持久化列）。
     */
    @ExcelProperty(value = "仔代名称")
    private String offspringName;

    /**
     * 备注。
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 创建时间。
     */
    @ExcelProperty(value = "创建时间")
    private Date createTime;

    /**
     * 创建人（sys_user.user_id）。
     */
    @ExcelProperty(value = "创建人")
    private Long createBy;

    /**
     * 创建人姓名（USER_ID_TO_NICKNAME 翻译 sys_user.nick_name 显中文名）。
     */
    @ExcelProperty(value = "创建人姓名")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "createBy")
    private String createByName;

}
