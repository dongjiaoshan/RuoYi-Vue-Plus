package org.dromara.djs.plant.organic.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 果蔬有机证书-关联作物导出行 VO（row148）。
 *
 * <p>行操作「导出」用：一个作物一行，输出该证书已关联的作物明细。</p>
 *
 * @author djs
 * @since row148
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ExcelIgnoreUnannotated
public class CropOrganicRelExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 1 证书编号（每行重复该证书号）。 */
    @ExcelProperty(value = "证书编号")
    private String cropCertNo;

    /** 2 作物名称。 */
    @ExcelProperty(value = "作物名称")
    private String cropName;

    /** 3 作物编号（{@code t_plant_crop_info.crop_code}）。 */
    @ExcelProperty(value = "作物编号")
    private String cropCode;
}
