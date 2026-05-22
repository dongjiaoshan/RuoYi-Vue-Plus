package org.dromara.djs.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.common.domain.vo.UserMeVo;

import java.util.List;
import java.util.Map;

/**
 * 小程序 applet 端用户读模型 mapper（MIN-INFRA-003）。
 *
 * <p>面向"我的"页 + "通讯录"页两个读端点。所有 SQL 直接对 {@code sys_user} / {@code sys_dept} /
 * {@code sys_user_role} / {@code sys_role} 表操作（ruoyi 自带表，按强约束 #1 不动其源码 / 不写
 * 业务 wrapper，单独走 mapper interface + @Select 注解最轻量）。</p>
 *
 * <p>tenant：ruoyi 默认 sys_user 的 tenant_id 是 '000000'，djs 业务表是 '1001'，两者不互访。
 * 本 mapper 不带 tenant filter（{@code sys_user} 表在 ruoyi 多租户场景下由
 * {@code dynamic-tenant} 拦截器自动处理，djs 单农场 V1 用 default tenant 即可）。</p>
 *
 * @author djs
 * @since MIN-INFRA-003
 */
@Mapper
public interface AppletUserQueryMapper {

    /**
     * 查询当前用户 + 部门信息（{@code current_farm_id} 在 token 里已有 / 角色集合由 sa-token
     * LoginUser 给，故本 SQL 不查角色）。
     *
     * @return UserMeVo 摘要；未命中返 null（用户已删 / 禁用时会被 mapper 视作 null 返回）
     */
    @Select("""
        SELECT u.user_id          AS userId,
               u.user_name        AS username,
               u.nick_name        AS nickname,
               u.phonenumber      AS phone,
               u.email            AS email,
               u.avatar           AS avatarOssId,
               u.sex              AS sex,
               u.dept_id          AS deptId,
               d.dept_name        AS deptName,
               u.current_farm_id  AS currentFarmId
        FROM sys_user u
        LEFT JOIN sys_dept d ON u.dept_id = d.dept_id AND d.del_flag = '0'
        WHERE u.user_id = #{userId}
          AND u.del_flag = '0'
          AND u.status = '0'
        LIMIT 1
        """)
    UserMeVo selectMeByUserId(@Param("userId") Long userId);

    /**
     * 通讯录原始查询：列出所有未删除、启用、有效（dept + role 都健全的）员工。
     *
     * <p>关键约束：</p>
     * <ul>
     *   <li>排除当前用户自己（{@code user_id != #{currentUserId}}），避免在通讯录里看到自己</li>
     *   <li>{@code keyword} 不为空时按 {@code nick_name} / {@code user_name} /
     *       {@code phonenumber} 三选一模糊（前端搜索框只过滤前端可见字段）</li>
     *   <li>排序：按 dept_id, user_id 升序，前端按 dept_name 二次分组</li>
     *   <li>{@code rolesRaw} 用 {@code GROUP_CONCAT} 拼接，service 层拆成 {@code List<String>}</li>
     * </ul>
     *
     * <p>返回 {@code List<Map>} 而非 {@code List<ContactVo>}：MyBatis 对 GROUP_CONCAT 出的
     * VARCHAR 字段自动映射到 String，但 List 字段需要 typeHandler 才能直接装配。这里在
     * mapper 出口拿 Map，再让 service 层用 {@code MapstructUtils} 风格手工拷贝 + 拆 roles，
     * 显式可读。</p>
     */
    @Select("""
        <script>
        SELECT u.user_id     AS userId,
               u.user_name   AS username,
               u.nick_name   AS nickname,
               u.phonenumber AS phone,
               u.email       AS email,
               u.avatar      AS avatarOssId,
               u.dept_id     AS deptId,
               d.dept_name   AS deptName,
               GROUP_CONCAT(DISTINCT r.role_name ORDER BY r.role_sort SEPARATOR ',') AS rolesRaw
        FROM sys_user u
        LEFT JOIN sys_dept d ON u.dept_id = d.dept_id AND d.del_flag = '0'
        LEFT JOIN sys_user_role ur ON u.user_id = ur.user_id
        LEFT JOIN sys_role r ON ur.role_id = r.role_id AND r.del_flag = '0' AND r.status = '0'
        WHERE u.del_flag = '0'
          AND u.status = '0'
          AND u.user_id != #{currentUserId}
        <if test="keyword != null and keyword != ''">
          AND (u.nick_name LIKE CONCAT('%', #{keyword}, '%')
            OR u.user_name LIKE CONCAT('%', #{keyword}, '%')
            OR u.phonenumber LIKE CONCAT('%', #{keyword}, '%'))
        </if>
        GROUP BY u.user_id, u.user_name, u.nick_name, u.phonenumber, u.email, u.avatar, u.dept_id, d.dept_name
        ORDER BY u.dept_id ASC, u.user_id ASC
        LIMIT 500
        </script>
        """)
    List<Map<String, Object>> selectContactsRaw(@Param("currentUserId") Long currentUserId,
                                                @Param("keyword") String keyword);
}
