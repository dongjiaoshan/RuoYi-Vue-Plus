package org.dromara.djs.warehouse.demand.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 需求管理汇总列表「下单时间 / 下单人」聚合口径契约（admin row181）。
 *
 * <p>这一行不是一张需求单：{@code GROUP BY dm.demand_date, dm.product_id} 把同日同产品被 N 家门店下的单
 * 合并成一行，所以下单时间 / 下单人在行上不是单值。口径二选一定死为<b>组内最早一单</b>
 * （{@code MIN(create_time)} + 最早那一单的昵称），多人时后缀「等 N 人」。</p>
 *
 * <p>这些条件写在注解 SQL 里，service 单测的 mock 打不到，只能拿原文断言钉死：</p>
 * <ol>
 *   <li>分组维度不许被新聚合列改动 —— 一旦有人为了取昵称把 {@code create_by} 加进 GROUP BY，
 *       同一天同一产品会按下单人裂成多行，需求量 / 门店数 / 确认率全部跟着错。</li>
 *   <li>昵称必须走「相关子查询取一行」而不是 {@code GROUP_CONCAT + SUBSTRING_INDEX}：
 *       昵称含逗号会被切断，且 {@code group_concat_max_len} 有截断风险
 *       （同一决定已记在 {@code selectStoreDemandDayPage} 的 mp 日卡口径上）。</li>
 *   <li>子查询必须重复门店过滤条件 —— 门店筛选是唯一会改变<b>组内成员</b>的筛选，
 *       漏了会出现「按 A 店筛完，下单人显示的却是 B 店的人」。</li>
 * </ol>
 */
@Tag("local")
@Tag("dev")
@DisplayName("需求汇总「下单时间 / 下单人」SQL 口径契约（admin row181）")
class DemandGroupOrdererSqlContractTest {

    private static String groupListSql() throws Exception {
        Method method = DemandManageMapper.class.getMethod("selectDemandGroupList",
            String.class, String.class, List.class, Long.class, List.class, LocalDate.class, LocalDate.class);
        Select select = method.getAnnotation(Select.class);
        assertThat(select).as("selectDemandGroupList 应带 @Select").isNotNull();
        return String.join(" ", select.value())
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    @Test
    @DisplayName("下单时间 = 组内最早一单的 create_time")
    void orderTimeIsGroupMinCreateTime() throws Exception {
        String sql = groupListSql();

        assertThat(sql)
            .as("下单时间必须是组内 MIN(create_time)，不是 MAX / 任取一行")
            .contains("min(dm.create_time) as ordertime");
    }

    @Test
    @DisplayName("分组维度仍是「需求日期 + 产品」——新列不得把 create_by 拽进 GROUP BY")
    void groupKeyStaysDateAndProduct() throws Exception {
        String sql = groupListSql();

        assertThat(sql)
            .as("分组维度必须保持 demand_date + product_id")
            .contains("group by dm.demand_date, dm.product_id");
        assertThat(sql)
            .as("create_by 进 GROUP BY 会让同日同产品按下单人裂成多行，需求量 / 门店数 / 确认率全错")
            .doesNotContain("group by dm.demand_date, dm.product_id, dm.create_by");
    }

    @Test
    @DisplayName("下单人 = 最早一单昵称 + 多人「等 N 人」后缀，且不用 GROUP_CONCAT")
    void ordererNameTakesEarliestNickWithMultiPersonSuffix() throws Exception {
        String sql = groupListSql();

        assertThat(sql)
            .as("昵称取自 sys_user.nick_name")
            .contains("u.nick_name")
            .as("必须按 create_time 升序取第一条（= 最早一单），与 orderTime 指向同一条需求单")
            .contains("order by d2.create_time asc, d2.id asc")
            .contains("limit 1")
            .as("多人后缀按组内 distinct 下单人数拼")
            .contains("count(distinct dm.create_by) > 1")
            .contains("' 等 '");
        assertThat(sql)
            .as("昵称含逗号会被 SUBSTRING_INDEX 切断，group_concat_max_len 还会截断，不许用这条路")
            .doesNotContain("group_concat");
    }

    @Test
    @DisplayName("取昵称的子查询与主查询同口径：排除取消/删除单 + 单租户 + 未删 + 门店过滤")
    void ordererSubQuerySharesMainScope() throws Exception {
        String sql = groupListSql();

        assertThat(sql)
            .as("子查询必须锚在同一个分组上（同日期 + 同产品）")
            .contains("d2.demand_date = dm.demand_date")
            .contains("d2.product_id = dm.product_id")
            .as("取消 / 删除单不进主查询的组，也不该被选成下单人")
            .contains("d2.demand_status not in ('cancelled','deleted')")
            .contains("d2.del_flag = '0'")
            .contains("d2.tenant_id = '1001'");
        assertThat(sql)
            .as("门店过滤是唯一会改变组内成员的筛选，子查询漏了就会显示别家店的下单人")
            .contains("and d2.store_id in")
            .contains("and d2.store_id = #{storeid}");
    }
}
