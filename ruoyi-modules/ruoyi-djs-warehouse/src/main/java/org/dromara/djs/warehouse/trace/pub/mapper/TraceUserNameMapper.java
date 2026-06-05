package org.dromara.djs.warehouse.trace.pub.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 公开追溯操作人姓名查询 Mapper（TRC-PUB-API-001）。
 *
 * <p>{@code t_warehouse_trace_event.operator_id} FK → {@code sys_user.user_id}。公开端点
 * {@code @SaIgnore} 无登录上下文，{@code USER_ID_TO_NAME} 翻译注解依赖翻译上下文（不可靠），
 * 故直查 {@code sys_user.nick_name}。仅返姓名，不暴露其他 sys_user 字段给 C 端
 * （对齐 {@code TraceFarmNameMapper} 自定义只读 SQL 范式）。</p>
 *
 * @author djs
 * @since TRC-PUB-API-001
 */
@Mapper
public interface TraceUserNameMapper {

    /**
     * 按 user_id 批量查昵称（返回 {@code [{userId, nickName}]}）。
     *
     * <p>注解 SQL 不走 MP 多租户拦截器；sys_user 是 ruoyi 全局表无 djs 业务租户隔离需求，
     * 此处仅按 user_id 取昵称。</p>
     */
    @Select("<script>"
        + "SELECT user_id AS userId, nick_name AS nickName FROM sys_user "
        + "WHERE user_id IN <foreach collection='ids' item='uid' open='(' separator=',' close=')'>#{uid}</foreach>"
        + "</script>")
    List<Map<String, Object>> selectUserNames(@Param("ids") List<Long> ids);

}
