package org.dromara.djs.store.member.domain.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 会员档案查询参数（STR-MEMBER-001 admin / mp 列表筛选）。
 *
 * @author djs
 * @since STR-MEMBER-001
 */
@Data
public class StoreMemberQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 手机号模糊匹配。
     */
    private String phone;

    /**
     * 会员姓名模糊匹配。
     */
    private String memberName;

    /**
     * 会员等级字典 {@code djs_member_level}（normal / vip / keep）精确匹配。
     */
    private String memberLevel;

    /**
     * 所属门店 ID 精确匹配。
     */
    private Long storeId;

}
