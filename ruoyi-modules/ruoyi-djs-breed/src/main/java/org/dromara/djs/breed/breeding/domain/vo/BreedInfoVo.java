package org.dromara.djs.breed.breeding.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.breed.breeding.domain.BreedInfo;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 育种信息（品种/品系）出参 VO（BRD-MD-001）。
 *
 * @author djs
 * @since BRD-MD-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BreedInfo.class)
public class BreedInfoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键。
     */
    @ExcelProperty(value = "ID")
    private Long id;

    /**
     * 类型（1=品种 / 2=品系）。
     */
    @ExcelProperty(value = "类型")
    private Integer breedStrain;

    /**
     * 品种/品系编码。
     */
    @ExcelProperty(value = "编码")
    private String breedStrainCode;

    /**
     * 品种/品系名称。
     */
    @ExcelProperty(value = "名称")
    private String breedStrainName;

    /**
     * 父级编码。
     */
    @ExcelProperty(value = "父级编码")
    private String parentCode;

    /**
     * 描述。
     */
    @ExcelProperty(value = "描述")
    private String description;

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
