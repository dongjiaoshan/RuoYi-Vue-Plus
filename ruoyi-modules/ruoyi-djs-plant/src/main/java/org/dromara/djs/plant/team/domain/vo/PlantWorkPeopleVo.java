package org.dromara.djs.plant.team.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.plant.team.domain.PlantWorkPeople;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 班组成员视图对象（PLT-MD-002）。
 *
 * <p>所有 sys_user 冗余字段（{@code userName} / {@code nickName} / {@code phonenumber}）
 * 由 mapper LEFT JOIN 一次取齐，避免 N+1。</p>
 *
 * @author djs
 * @since PLT-MD-002
 */
@Data
@AutoMapper(target = PlantWorkPeople.class)
public class PlantWorkPeopleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 成员关联 ID。
     */
    private Long id;

    /**
     * 班组 ID。
     */
    private Long teamId;

    /**
     * sys_user.user_id。
     */
    private Long userId;

    /**
     * sys_user.user_name 登录账号。
     */
    private String userName;

    /**
     * sys_user.nick_name 显示名。
     */
    private String nickName;

    /**
     * sys_user.phonenumber 手机号。
     */
    private String phonenumber;

    /**
     * 是否班组负责人（1=是 2=否）。
     */
    private Integer isLeader;

    /**
     * 加入日期。
     */
    private Date joinDate;
}
