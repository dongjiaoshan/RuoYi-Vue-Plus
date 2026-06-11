package org.dromara.djs.common.image.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.common.image.domain.ImageLibrary;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 公共图片库视图对象（IMG-LIB-001）。
 *
 * @author djs
 * @since IMG-LIB-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ImageLibrary.class)
public class ImageLibraryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "主名")
    private String imageName;

    @ExcelProperty(value = "别名")
    private String aliases;

    /**
     * 图 ossId（字符串）。
     */
    private String ossId;

    /**
     * 图 public URL（resolver 回填，前端预览用）。
     */
    private String imageUrl;

    @ExcelProperty(value = "排序")
    private Integer sortOrder;

    @ExcelProperty(value = "状态")
    private String status;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

    private Date updateTime;

}
