package org.dromara.djs.warehouse.burn.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BurnInhouseAdjustMapper SQL 口径契约（V6-R43）。
 *
 * <p>两件事：</p>
 * <ol>
 *   <li>{@code <script>} 动态 SQL 能被 MyBatis 解析 —— 它在 mapper 注册阶段（应用启动）才解析，
 *       写坏了不是接口报错而是<b>整个服务起不来</b>，必须在单测阶段就拦住。</li>
 *   <li>钉住两条口径：本页数据范围 = 燎毛间入库产出行；改流水只改燎毛入库那一条。
 *       任何一条松掉都会去改到别人的数据（领用在制品 / 门店拆单行 / 领用出库流水）。</li>
 * </ol>
 */
@Tag("local")
@Tag("dev")
@DisplayName("BurnInhouseAdjustMapper SQL 口径契约")
class BurnInhouseAdjustMapperSqlContractTest {

    /** 取 {@code @Select} 原文（MyBatis 用空格拼多段 + trim，与运行期一致）。 */
    private static String selectScript() throws Exception {
        Method method = BurnInhouseAdjustMapper.class.getMethod("selectAdjustPage",
            com.baomidou.mybatisplus.core.metadata.IPage.class,
            Date.class, Date.class, String.class, Integer.class);
        return String.join(" ", method.getAnnotation(Select.class).value()).trim();
    }

    private static String normalize(String sql) {
        return sql.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    /** 用 MyBatis 真正的动态 SQL 引擎渲染一次，参数全给 → 所有 {@code <if>} 分支都进 SQL。 */
    private static String renderWithAllFilters() throws Exception {
        Configuration cfg = new Configuration();
        SqlSource source = new XMLLanguageDriver().createSqlSource(cfg, selectScript(), Object.class);
        Map<String, Object> params = new HashMap<>();
        params.put("dateFrom", new Date());
        params.put("dateTo", new Date());
        params.put("productName", "半扇");
        params.put("isAdjusted", 1);
        BoundSql boundSql = source.getBoundSql(params);
        return normalize(boundSql.getSql());
    }

    @Test
    @DisplayName("动态 SQL 可解析，且四个筛选条件都能渲染进 WHERE")
    void scriptParsesAndRendersAllFilters() throws Exception {
        assertThat(selectScript()).startsWith("<script>");

        String sql = renderWithAllFilters();

        assertThat(sql).contains("ih.produce_time >= ?");
        assertThat(sql).contains("ih.produce_time < ?");
        assertThat(sql).contains("ih.product_name like concat('%', ?, '%')");
        assertThat(sql).contains("ih.is_adjusted = ?");
        // 入库日期止用「< 次日零点」而不是「<= 当天」—— 后者会把当天有时分秒的记录全漏掉
        assertThat(sql).doesNotContain("ih.produce_time <= ?");
    }

    @Test
    @DisplayName("数据范围锁死为「燎毛间入库产出行」：领用在制品 / 门店拆单行不得混进来")
    void listScopeIsBurnRowsOnly() throws Exception {
        String sql = renderWithAllFilters();

        assertThat(sql).contains("ih.white_bar_id is not null");
        // material_id 非空 = 物资/白条领用产生的在制品行；source='store' = 门店拆单行
        assertThat(sql).contains("ih.material_id is null");
        assertThat(sql).contains("ih.source = 'warehouse'");
    }

    @Test
    @DisplayName("改流水只改「燎毛入库」那一条：同一 white_bar_no 的领用出库 / 分割流水不能被一起改")
    void flowUpdateIsScopedToBurnInbound() throws Exception {
        Method target = org.dromara.djs.warehouse.flow.mapper.StockFlowMapper.class.getMethod(
            "updateBurnInFlowWeight", String.class, Long.class, BigDecimal.class, Long.class);
        String sql = normalize(String.join(" ", target.getAnnotation(Update.class).value()));

        assertThat(sql).contains("white_bar_no = #{whitebarno}");
        assertThat(sql).contains("flow_type = 'slaughter_burn'");
        assertThat(sql).contains("inout_type = 'in'");
        assertThat(sql).contains("del_flag = '0'");
    }

    @Test
    @DisplayName("产出行 UPDATE 带旧重量乐观锁 —— 否则并发下差额会按错值推库存/流水")
    void applyAdjustCarriesOldWeightGuard() throws Exception {
        Method target = BurnInhouseAdjustMapper.class.getMethod(
            "applyAdjust", Long.class, BigDecimal.class, BigDecimal.class, Long.class);
        String sql = normalize(String.join(" ", target.getAnnotation(Update.class).value()));

        assertThat(sql).contains("product_weight = #{oldweight}");
        assertThat(sql).contains("set product_weight = #{newweight}");
        assertThat(sql).contains("is_adjusted = 1");
        assertThat(sql).contains("adjust_time = now()");
        assertThat(sql).contains("adjust_by = #{userid}");
    }

    @Test
    @DisplayName("白条窗口锁只在 pending_singe / singing 生效（= 甲方说的「未点处理完成」）")
    void barWindowLockLimitedToSingeingStates() throws Exception {
        Method target = org.dromara.djs.warehouse.cross.mapper.BarInfoMapper.class.getMethod(
            "adjustInWeightIfBurning", Long.class, BigDecimal.class, Long.class);
        String sql = normalize(String.join(" ", target.getAnnotation(Update.class).value()));

        assertThat(sql).contains("status in ('pending_singe','singing')");
        // in_weight 为 NULL（处理完成前的正常态）时保持 NULL，不要写成 0
        assertThat(sql).contains("case when in_weight is null then null else in_weight + #{delta} end");
    }

}
