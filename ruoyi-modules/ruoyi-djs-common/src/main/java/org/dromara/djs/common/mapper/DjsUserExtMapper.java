package org.dromara.djs.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.djs.common.domain.vo.WechatLoginVo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * sys_user 的 djs 扩字段读写 mapper。
 *
 * <p>ruoyi 自带 {@code SysUser} entity 未包含 SYS-INIT-001 加的 farm_id / current_farm_id /
 * wx_openid / wx_unionid 字段（按强约束 #1，不动 ruoyi 自带模块），因此 djs 域单独写一个
 * mapper 处理这几列的读写。所有 SQL 都用 @TenantIgnore（通过原生 sql + 不带 tenant filter）
 * 直接对 sys_user 表操作（sys_user 自己 tenant_id 是 '1001'，跟业务表 default 一致）。</p>
 *
 * @author djs
 */
@Mapper
public interface DjsUserExtMapper {

    /**
     * 通过 openid 查找用户的登录摘要。
     *
     * @return WechatLoginVo（不带 token）；未命中返 null
     */
    @Select("""
        SELECT user_id        AS userId,
               nick_name      AS nickName,
               current_farm_id AS currentFarmId
        FROM sys_user
        WHERE wx_openid = #{openid}
          AND del_flag = '0'
          AND status = '0'
        LIMIT 1
        """)
    WechatLoginVo selectLoginVoByOpenid(@Param("openid") String openid);

    /**
     * 通过手机号查询 user_id（用于 bind-phone）。
     *
     * @return user_id；未命中返 null
     */
    @Select("""
        SELECT user_id
        FROM sys_user
        WHERE phonenumber = #{phone}
          AND del_flag = '0'
          AND status = '0'
        LIMIT 1
        """)
    Long selectUserIdByPhone(@Param("phone") String phone);

    /**
     * 更新指定用户的 wx_openid（绑定阶段使用）。
     *
     * @return 影响行数
     */
    @Update("""
        UPDATE sys_user
        SET wx_openid = #{openid},
            update_time = NOW()
        WHERE user_id = #{userId}
        """)
    int updateWxOpenid(@Param("userId") Long userId, @Param("openid") String openid);

    /**
     * 更新指定用户的 current_farm_id（农场切换使用）。
     *
     * @return 影响行数
     */
    @Update("""
        UPDATE sys_user
        SET current_farm_id = #{farmId},
            update_time = NOW()
        WHERE user_id = #{userId}
        """)
    int updateCurrentFarmId(@Param("userId") Long userId, @Param("farmId") String farmId);

    /**
     * 读取用户的 current_farm_id（用于回填 token session）。
     */
    @Select("SELECT current_farm_id FROM sys_user WHERE user_id = #{userId}")
    String selectCurrentFarmId(@Param("userId") Long userId);

    /**
     * 读取用户的 accessible_farm_ids（V2 多农场场景使用；V1 始终返单值 "1001"）。
     */
    @Select("SELECT accessible_farm_ids FROM sys_user WHERE user_id = #{userId}")
    String selectAccessibleFarmIds(@Param("userId") Long userId);

    /**
     * 读取用户名（用于颁发 token 时填充 username）。
     */
    @Select("SELECT user_name FROM sys_user WHERE user_id = #{userId}")
    String selectUserName(@Param("userId") Long userId);

    /**
     * 读取 tenant_id（用于 LoginUser 构造）。
     */
    @Select("SELECT tenant_id FROM sys_user WHERE user_id = #{userId}")
    String selectTenantId(@Param("userId") Long userId);

    /**
     * 读取用户的 wx_openid（SYS-INFRA-006 推送订阅消息时定位接收方 openid）。
     *
     * @return wx_openid；未绑定或用户不存在返 null
     */
    @Select("""
        SELECT wx_openid
        FROM sys_user
        WHERE user_id = #{userId}
          AND del_flag = '0'
          AND status = '0'
        """)
    String selectWxOpenid(@Param("userId") Long userId);

    /**
     * 批量查 user_id → nick_name（导出回填用，只查启用未删用户）。
     *
     * <p>FastExcel 导出不经 Jackson，{@code @Translation} 不触发，需 service 层显式回填人名。
     * 每行 Map 含 {@code userId}(Long) + {@code nickName}(String)；查不到的 id 不在结果集。</p>
     *
     * @param ids 去重后的非空 user_id 集合（调用方保证非空，空集时 mapper 不调用）
     * @return 命中行的 {userId, nickName} 列表
     */
    @Select("""
        <script>
        SELECT user_id AS userId, nick_name AS nickName
        FROM sys_user
        WHERE del_flag = '0' AND status = '0'
          AND user_id IN
          <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    List<Map<String, Object>> selectNickNamesByIds(@Param("ids") Collection<Long> ids);
}
