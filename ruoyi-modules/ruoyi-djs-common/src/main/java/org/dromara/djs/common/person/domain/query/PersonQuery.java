package org.dromara.djs.common.person.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 人员主数据列表查询入参（SYS-MD-001）。
 *
 * <p>分页参数继承 {@link org.dromara.common.mybatis.core.page.PageQuery}（在 Controller 单独接收，
 * 与本 BO 通过 {@code @RequestParam} 同时绑定，参考 ruoyi {@code SysPostController#list}）。</p>
 *
 * @author djs
 * @since SYS-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonQuery extends BaseEntity {

    /**
     * 姓名（模糊匹配）。
     */
    private String name;

    /**
     * 人员编码（精确匹配）。
     */
    private String personCode;

    /**
     * 手机号（模糊匹配）。
     */
    private String phone;

    /**
     * 状态（字典 djs_user_status）。
     */
    private String status;

    /**
     * 岗位 ID。
     */
    private Long postId;

}
