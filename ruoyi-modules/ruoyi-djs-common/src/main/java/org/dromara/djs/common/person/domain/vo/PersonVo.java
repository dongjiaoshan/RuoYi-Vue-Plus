package org.dromara.djs.common.person.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.djs.common.person.domain.Person;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 人员主数据视图对象（SYS-MD-001）。
 *
 * @author djs
 * @since SYS-MD-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = Person.class)
public class PersonVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人员 ID。
     */
    @ExcelProperty(value = "ID")
    private Long id;

    /**
     * 人员编码。
     */
    @ExcelProperty(value = "人员编码")
    private String personCode;

    /**
     * 姓名。
     */
    @ExcelProperty(value = "姓名")
    private String name;

    /**
     * 性别。
     */
    @ExcelProperty(value = "性别", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "sys_user_sex")
    private String gender;

    /**
     * 联系电话。
     */
    @ExcelProperty(value = "联系电话")
    private String phone;

    /**
     * 身份证号。
     */
    @ExcelProperty(value = "身份证号")
    private String idCard;

    /**
     * 岗位。
     */
    @ExcelProperty(value = "岗位")
    private String position;

    /**
     * 入职日期。
     */
    @ExcelProperty(value = "入职日期")
    private LocalDate hireDate;

    /**
     * 状态。
     */
    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_user_status")
    private String status;

    /**
     * 头像 URL。
     */
    private String avatarUrl;

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

}
