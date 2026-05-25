package org.dromara.djs.common.person.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.dromara.djs.common.validation.ValidIdCard;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.common.person.domain.Person;

import java.time.LocalDate;

/**
 * 人员主数据入参 BO（SYS-MD-001）。
 *
 * <p>{@code personCode} 不接受外部传入 —— 新增由后端编码生成器产出；编辑时也不允许改编码。</p>
 *
 * @author djs
 * @since SYS-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = Person.class, reverseConvertGenerate = false)
public class PersonBo extends BaseEntity {

    /**
     * 人员 ID（编辑时必填）。
     */
    private Long id;

    /**
     * 姓名。
     */
    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过 {max} 个字符")
    private String name;

    /**
     * 性别（字典 sys_user_sex）。
     */
    @Size(max = 1, message = "性别取值非法")
    private String gender;

    /**
     * 联系电话。
     */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "请输入合法的手机号")
    private String phone;

    /**
     * 身份证号（18 位居民身份证含末位 X / 15 位旧式；ISO 7064 Mod 11-2 校验码验证）。
     */
    @ValidIdCard
    private String idCard;

    /**
     * 岗位 ID（关联 sys_post.post_id；选填）。
     */
    private Long postId;

    /**
     * 入职日期。
     */
    private LocalDate hireDate;

    /**
     * 状态（字典 djs_user_status：0 在职 / 1 离职 / 2 试用）。
     */
    @Size(max = 1, message = "状态取值非法")
    private String status;

    /**
     * 头像 URL（SYS-INFRA-002 OSS 直传后回填）。
     */
    @Size(max = 500, message = "头像 URL 过长")
    private String avatarUrl;

    /**
     * 备注。
     */
    @Size(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String remark;

}
