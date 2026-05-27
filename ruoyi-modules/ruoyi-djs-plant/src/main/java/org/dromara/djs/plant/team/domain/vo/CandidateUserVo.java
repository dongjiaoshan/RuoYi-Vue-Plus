package org.dromara.djs.plant.team.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 班组候选员工 VO（PLT-MD-002）。
 *
 * <p>用于成员管理弹窗右栏：种植部下未分配到任何 active 班组的 sys_user。</p>
 *
 * @author djs
 * @since PLT-MD-002
 */
@Data
public class CandidateUserVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * sys_user.user_id。
     */
    private Long userId;

    /**
     * 登录账号。
     */
    private String userName;

    /**
     * 显示名。
     */
    private String nickName;

    /**
     * 手机号。
     */
    private String phonenumber;

    /**
     * 部门名称。
     */
    private String deptName;
}
