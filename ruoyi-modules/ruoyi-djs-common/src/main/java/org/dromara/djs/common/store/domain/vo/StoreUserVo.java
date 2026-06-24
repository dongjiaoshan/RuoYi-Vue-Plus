package org.dromara.djs.common.store.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 门店已绑人员 VO（STORE-PERM-001）。
 *
 * <p>门店人员绑定弹窗右侧「已绑列表」用：人员账号 / 昵称 / 电话 + 绑定关系 ID（解绑用 userId）。</p>
 *
 * @author djs
 * @since STORE-PERM-001
 */
@Data
public class StoreUserVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人员 ID（sys_user.user_id，snowflake / 自增，Jackson 序列化为 string）。
     */
    private Long userId;

    /**
     * 登录账号。
     */
    private String userName;

    /**
     * 姓名 / 昵称。
     */
    private String nickName;

    /**
     * 电话。
     */
    private String phonenumber;
}
