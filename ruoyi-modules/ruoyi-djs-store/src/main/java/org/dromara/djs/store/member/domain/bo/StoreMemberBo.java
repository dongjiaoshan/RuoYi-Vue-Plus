package org.dromara.djs.store.member.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 会员档案新增 / 编辑 BO（STR-MEMBER-001）。
 *
 * <p>admin 店长 + mp 店员共用：姓名 + 手机号 必填，等级 / 入会日期 / 标签 / 门店 可选。
 * {@code memberNo} 由 service {@code generate(MEMBER_NO)} 生成，不收前端传值。</p>
 *
 * @author djs
 * @since STR-MEMBER-001
 */
@Data
public class StoreMemberBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会员 ID（编辑场景传，新增留空）。
     */
    private Long id;

    /**
     * 会员姓名。
     */
    @NotBlank(message = "会员姓名不能为空")
    private String memberName;

    /**
     * 手机号（service 层 add/edit 前查重）。
     */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /**
     * 会员等级（字典 {@code djs_member_level}）。
     */
    private String memberLevel;

    /**
     * 入会日期。
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date joinDate;

    /**
     * 所属门店 ID。
     */
    private Long storeId;

    /**
     * 会员标签（逗号分隔）。
     */
    private String memberTags;

    /**
     * 状态（1=正常 / 0=停用，不传默认正常）。
     */
    private Integer memberStatus;

    /**
     * 备注。
     */
    private String remark;

}
