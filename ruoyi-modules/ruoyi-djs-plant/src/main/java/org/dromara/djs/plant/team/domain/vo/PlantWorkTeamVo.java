package org.dromara.djs.plant.team.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.djs.common.excel.DictOrRawConvert;
import org.dromara.djs.plant.team.domain.PlantWorkTeam;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 班组视图对象（PLT-MD-002）。
 *
 * <p>{@code leaderName} / {@code memberCount} 是 LEFT JOIN sys_user + 子查询 count 的冗余列，
 * 由 {@code PlantWorkTeamMapper#selectVoPageWithLeader} 一次 SQL 取齐。</p>
 *
 * @author djs
 * @since PLT-MD-002
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PlantWorkTeam.class)
public class PlantWorkTeamVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 班组 ID。
     */
    @ExcelProperty(value = "ID")
    private Long id;

    /**
     * 班组名称。
     */
    @ExcelProperty(value = "班组名称")
    private String teamName;

    /**
     * 负责人 sys_user.user_id。
     */
    private Long leaderId;

    /**
     * 负责人姓名（冗余，LEFT JOIN sys_user.nick_name）。
     */
    @ExcelProperty(value = "负责人")
    private String leaderName;

    /**
     * 负责人手机号（enrich：LEFT JOIN sys_user.phonenumber by leader_id，非落库）。
     */
    @ExcelProperty(value = "负责人联系方式")
    private String leaderPhone;

    /**
     * 班组状态（字典 {@code djs_common_status}）。
     */
    @ExcelProperty(value = "状态", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_common_status")
    private Integer teamStatus;

    /**
     * 当前成员数（冗余，子查询 count）。
     */
    @ExcelProperty(value = "成员数")
    private Integer memberCount;

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
