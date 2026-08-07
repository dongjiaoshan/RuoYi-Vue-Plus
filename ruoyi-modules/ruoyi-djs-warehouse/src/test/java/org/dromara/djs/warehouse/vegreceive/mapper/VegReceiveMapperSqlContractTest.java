package org.dromara.djs.warehouse.vegreceive.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VegReceiveMapper SQL 口径契约（row55：果蔬月台按产品聚合）。
 *
 * <p><b>为什么必须有这支测试</b>：本子域的 service 单测把 mapper 整个 mock 掉了，SQL 原文没有任何断言。
 * clean-QA 做变异测试时实测：把 {@code addStockByPlotLocation} 的 {@code AND product_id &lt;=&gt; #{productId}}
 * 删掉，404 个用例<b>一个都不红</b>——而那一行正是「同一块地收的第二个产品不会被并进第一个产品的篮子」
 * 唯一的防线。口径靠 SQL 原文守，就得断言 SQL 原文。</p>
 */
@Tag("local")
@Tag("dev")
@DisplayName("VegReceiveMapper SQL 口径契约（row55 按产品聚合）")
class VegReceiveMapperSqlContractTest {

    /**
     * 取注解里的 SQL 原文并归一：压平空白 + 转小写 + 还原 XML 实体。
     * {@code <script>} 里的比较符写成 {@code &lt;} / {@code &gt;}，不还原断言就得跟着写实体。
     */
    private static String sqlOf(String methodName, Class<?>... paramTypes) throws Exception {
        Method m = VegReceiveMapper.class.getMethod(methodName, paramTypes);
        Select select = m.getAnnotation(Select.class);
        Update update = m.getAnnotation(Update.class);
        String[] raw = select != null ? select.value() : update.value();
        return String.join(" ", raw)
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    @Test
    @DisplayName("库存篮增量必须带 product_id —— 少了它，同地块第二个产品会加到第一个产品的篮子上")
    void stockIncrementIsScopedToProduct() throws Exception {
        String sql = sqlOf("addStockByPlotLocation", Long.class, Long.class, Long.class,
            BigDecimal.class, Long.class);

        // NULL-safe 比较：作物没配关联产品时篮子 product_id 是 NULL，得能匹配上
        assertThat(sql).contains("product_id <=> #{productid}");
        assertThat(sql).contains("location_id = #{locationid}").contains("plot_id = #{plotid}");
    }

    @Test
    @DisplayName("待收货列表按 (作物,产品) 聚合，且量取自带产品维度的 handle_record 明细")
    void selfPendingGroupsByProduct() throws Exception {
        String sql = sqlOf("selectSelfPending");

        // 数据源：月台明细（record_type=2 处理 + handle_target=2 月台），不是 vegetable_handle 的汇总列
        assertThat(sql)
            .contains("from t_warehouse_handle_record")
            .contains("hr.record_type = 2")
            .contains("hr.handle_target = 2");
        assertThat(sql).doesNotContain("send_platform_weight");
        // 聚合键含产品
        assertThat(sql)
            .contains("group by hr.crop_id, hr.plot_id, coalesce(hr.product_id, cr0.related_product)")
            .contains("group by crop_id, product_id");
        // 收货侧同样按产品分组，否则已入库量会在两个产品之间串
        assertThat(sql).contains("group by vr.crop_id, vr.plot_id, coalesce(vr.product_id, cr1.related_product)");
        // 关掉租户行注入后，每层事实表 WHERE 必须自带 tenant_id
        assertThat(sql).contains("hr.tenant_id = '1001'").contains("vr.tenant_id = '1001'");

        // 三处 JOIN 都必须带产品维度的 NULL-safe 等值（recv_sum / visible / plat 与 recv 相联）。
        // 少任何一处，同一作物的多个产品会互相笛卡尔 —— 实测去掉 recv_sum 那处，红薯作物返 4 行
        //（每个产品重复两次），mp 列表直接出现重复卡，而 service 单测把 mapper 整个 mock 了、一条都不会红。
        assertThat(sql.split("product_id <=> ", -1))
            .as("selectSelfPending 里按产品的 NULL-safe JOIN 键应有 3 处")
            .hasSize(4);
    }

    @Test
    @DisplayName("地块明细 / 剩余可入量：productId 传了才收窄（<if> 可选过滤），不传 = 整作物口径")
    void plotAndQuotaNarrowOnlyWhenProductGiven() throws Exception {
        String plots = sqlOf("selectInboundPlots", Long.class, Long.class);
        String remain = sqlOf("selectRemainInboundWeight", Long.class, Long.class, Long.class);

        for (String sql : new String[]{plots, remain}) {
            assertThat(sql).contains("<script>");
            assertThat(sql).contains("<if test=\"productid != null\">");
            // 收窄时用 =（productId 已非空），不该再用 <=> —— 那会把「不传」和「传了」两种语义混起来
            assertThat(sql).contains("= #{productid}");
            assertThat(sql).contains("record_type = 2").contains("handle_target = 2");
        }

        // 地块明细两段（月台量 / 已收）各自都要按产品收窄。
        // 只做上面那种通用 contains 不够：坏掉其中一段，另一段仍能满足 `= #{productid}`，变异会存活 ——
        // 实测把月台量段换成 1=1，红薯详情页会从「1 块地 50kg」变成「3 块地 200kg」（红薯杆的地块混进红薯卡），
        // 正是 row55 要治的病，而三个用例一个都不红。故两段都点名。
        assertThat(plots)
            .as("地块明细·月台量段（handle_record）按产品收窄")
            .contains("coalesce(hr.product_id, cr0.related_product) = #{productid}")
            .as("地块明细·已收段（veg_receive）按产品收窄")
            .contains("coalesce(vr.product_id, cr1.related_product) = #{productid}");
        assertThat(plots.split("<if test=\"productid != null\">", -1))
            .as("地块明细应恰有 2 段可选收窄")
            .hasSize(3);
        // 剩余可入量三段（月台量 / 已入 / 已结算损耗）都要能按产品收窄，漏一段封顶就形同虚设。
        // 只数 <if> 外壳不够：把某一段的谓词换成 1=1，外壳计数不变、`= #{productid}` 也仍被别段满足，
        // 变异能存活。故三段各自的表别名 + 收窄谓词都要点名断言。
        assertThat(remain.split("<if test=\"productid != null\">", -1)).hasSize(4);
        assertThat(remain)
            .as("月台量段（handle_record）按产品收窄")
            .contains("coalesce(hr.product_id, cr0.related_product) = #{productid}")
            .as("已入库段（veg_receive）按产品收窄")
            .contains("coalesce(vr.product_id, cr1.related_product) = #{productid}")
            .as("已结算损耗段（veg_receive is_finish=1）按产品收窄")
            .contains("coalesce(vrl.product_id, cr2.related_product) = #{productid}");
    }
}
