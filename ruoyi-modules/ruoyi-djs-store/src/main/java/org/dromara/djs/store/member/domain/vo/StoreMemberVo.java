package org.dromara.djs.store.member.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.store.member.domain.StoreMember;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 会员档案 VO（STR-MEMBER-001）。
 *
 * <p>{@code storeName} 由 service 层 JOIN {@code t_md_store} 回填；
 * {@code createName} 走 ruoyi {@code USER_ID_TO_NAME} 翻译（建档人；不要写 {@code USER_ID_TO_NICKNAME}，
 * 5.5.x 无 impl）。{@code memberLevel} 用字典 {@code djs_member_level}，前端 dict-tag 渲染。</p>
 *
 * @author djs
 * @since STR-MEMBER-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = StoreMember.class)
public class StoreMemberVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "会员ID")
    private Long id;

    @ExcelProperty(value = "会员编号")
    private String memberNo;

    @ExcelProperty(value = "会员姓名")
    private String memberName;

    @ExcelProperty(value = "手机号")
    private String phone;

    /**
     * 会员等级（字典 {@code djs_member_level}：normal / vip / keep）。
     */
    @ExcelProperty(value = "会员等级")
    private String memberLevel;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @ExcelProperty(value = "入会日期")
    private Date joinDate;

    private Long storeId;

    /**
     * 门店名称（service 层 JOIN 回填）。
     */
    @ExcelProperty(value = "所属门店")
    private String storeName;

    @ExcelProperty(value = "会员标签")
    private String memberTags;

    /**
     * 状态（1=正常 / 0=停用）。
     */
    @ExcelProperty(value = "状态")
    private Integer memberStatus;

    private Long createBy;

    /**
     * 建档人姓名（注解翻译，VO 序列化时填）。
     */
    @ExcelProperty(value = "建档人")
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "createBy")
    private String createName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "创建时间")
    private Date createTime;

    private String remark;

}
