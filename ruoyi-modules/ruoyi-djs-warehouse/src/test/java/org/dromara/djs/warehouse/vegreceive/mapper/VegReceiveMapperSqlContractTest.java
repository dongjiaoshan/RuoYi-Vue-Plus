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
 *
 * <p><b>这支测试的技法与边界（被独立 QA 打穿三次后固化，别退回弱写法）</b>：它断言的是<b>SQL 文本</b>、
 * <b>不执行 SQL</b>，所以强度完全取决于断言怎么写：</p>
 * <ul>
 *   <li><b>只写裸子串 / 只数总数 = 假防线</b>：{@code contains("group by crop_id, product_id")}
 *       既挡不住删掉其中一处，也挡不住往后追加维度（{@code … product_id, plot_id} 里
 *       {@code … product_id} 仍匹配）；{@code countOf(...) == 2} 只数「共有两处」，
 *       把 {@code FROM plat} 换成 {@code FROM recv} 照样通过。</li>
 *   <li><b>锚到子句边界 = 更强的防线，但仍不够</b>：
 *       {@code contains("from plat group by crop_id, product_id )")} 一次钉住<b>来源 + 聚合键 + 边界</b>三样，
 *       上述两种改写同时被杀。<b>但它只钉「字面 + 邻近」，不绑定 CTE 身份</b>——第九轮 QA 用
 *       {@code SELF-A-full}（把<b>两处来源整体对调</b>：{@code plat_sum}←{@code FROM recv}、
 *       {@code recv_sum}←{@code FROM plat}）打穿：两个片段依然各自都在。所以来源还要和 CTE 身份绑定，
 *       见 {@code sum(platform) as platform from plat …} / {@code sum(loss_today) as loss_today from recv …}
 *       两条（{@code platform} 只在 {@code plat} 里、{@code loss_today} 只在 {@code recv} 里）。</li>
 *   <li><b>「字面还在」的语义放宽用否定式断言挡</b>：{@code AND vr.receive_type = 1} 被改成
 *       {@code AND (… = 1 OR … = 2)} 时，光数出现次数无效，但
 *       {@code doesNotContain("receive_type = 2")} + 把谓词锚到后随子句就能杀掉它，
 *       <b>并不需要真连库</b>。（第七轮作者曾把这类洞写成「只能在真连库的集成测试里才拦得住」，
 *       第八轮 QA 指正为过度悲观——那会变成漏测的借口，已按上述技法真正堵上。）</li>
 * </ul>
 * <p>剩余的真实边界：写法完全不同但语义等价的改写（如换成子查询表达同一约束）仍可能漏，
 * 那类才需要真连库集成测试。故本类的定位是「防手滑改写 / 防复制粘贴串源」，不是形式化证明。</p>
 */
@Tag("local")
@Tag("dev")
@DisplayName("VegReceiveMapper SQL 口径契约（row55 按产品聚合）")
class VegReceiveMapperSqlContractTest {

    /**
     * 取注解里的 SQL 原文并归一：压平空白 + 转小写 + 还原 XML 实体。
     * {@code <script>} 里的比较符写成 {@code &lt;} / {@code &gt;}，不还原断言就得跟着写实体。
     */
    /** 字面出现次数（不能用 String.split：它吃正则，而 {@code #{xxx}} 的花括号会被当成重复量词）。 */
    private static int countOf(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

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
        // 聚合键含产品。⚠️ 必须锚到**子句边界** ` )`，不能只锚到列清单（见本类 javadoc「为什么断言必须锚边界」）：
        // `contains("...coalesce(hr.product_id, cr0.related_product)")` 是**子串**断言，再往后追加一个维度
        // （如 `, cr0.id`）依然通过 —— 而 plat CTE 多一个维度就会把同一 (作物,产品) 拆成多行，
        // 下游 `plat_sum` 再 SUM 回去也救不回来（甲方会看到重复/翻倍）。
        assertThat(sql)
            .contains("group by hr.crop_id, hr.plot_id, coalesce(hr.product_id, cr0.related_product) )");
        // 收货侧同样按产品分组，否则已入库量会在两个产品之间串
        assertThat(sql)
            .contains("group by vr.crop_id, vr.plot_id, coalesce(vr.product_id, cr1.related_product) )");
        // 关掉租户行注入后，每层事实表 WHERE 必须自带 tenant_id
        assertThat(sql).contains("hr.tenant_id = '1001'").contains("vr.tenant_id = '1001'");

        // 三处 JOIN 都必须带产品维度的 NULL-safe 等值（recv_sum / visible / plat 与 recv 相联）。
        // 少任何一处，同一作物的多个产品会互相笛卡尔 —— 实测去掉 recv_sum 那处，红薯作物返 4 行
        //（每个产品重复两次），mp 列表直接出现重复卡，而 service 单测把 mapper 整个 mock 了、一条都不会红。
        assertThat(countOf(sql, "product_id <=> "))
            .as("selectSelfPending 里按产品的 NULL-safe JOIN 键应有 3 处")
            .isEqualTo(3);

        // productId 必须真投影出去。把它换成 NULL 是合法 SQL、跑得通、全部单测也绿，
        // 但 mp 拿到的每张卡 productId 都是空 → 收货时多产品作物一律被判「说不清是哪个产品」而 400，
        // 红薯和红薯杆双双收不了，row55 的病原样复发。
        assertThat(sql)
            .as("productId 必须取自聚合键 t.product_id，不能是常量/NULL")
            .contains("t.product_id as productid");

        // 三个展示投影与 productId 同级同命：换成 NULL 都是合法 SQL、跑得通、单测全绿，但 mp 拿到的卡会退化。
        // 实测（clean-QA 第七轮变异）：只钉 productId 时，把 productName 换成 NULL **无测试报警** ——
        // 而 mp 卡片标题取 `item.productName || item.cropName`（dock/index.vue:355/:458），productName 一空
        // 两张卡都回落显示作物名「红薯」，红薯杆那张看不出来，**row55 的病原样复发**。故与 productId 同等对待。
        assertThat(sql)
            .as("productName 必须回落到产品名（空则 mp 回落作物名 → 多产品卡同名）")
            .contains("coalesce(pr.product_name, cr.crop_name) as productname");
        assertThat(sql)
            .as("imageOssId / productCode 是卡面缩略图与产品码的来源，同样不能被抹成 NULL")
            .contains("coalesce(pr.image_oss_id, cr.image_oss_id) as imageossid")
            .contains("coalesce(pr.product_id, cr.crop_code) as productcode");

        // 两个汇总 CTE 的聚合键：plat_sum（月台量）与 recv_sum（已收）**各自**都要按产品分组。
        // 演进史（三轮独立 QA 打穿三次，别再退回「数总数」或「裸子串」）：
        //   ① 裸 `.contains("group by crop_id, product_id")` —— 两处文本相同，删任一处另一处仍满足 → 存活；
        //   ② `countOf(...) == 2` —— 只数「共有两处」，被 9d 打穿：把 plat_sum 的 `FROM plat` 改成
        //      `FROM recv`，字面仍是两处、计数不变，但月台量变成了已收量 → pendingWeight ≤ 0
        //      → mp 自产待收货列表整页空。**这是复制粘贴型自然写法，不是刻意构造。**
        //   ③ 只锚到列清单（`... product_id`）—— 被 4b 打穿：追加 `, plot_id` 后 `... product_id`
        //      仍是它的**前缀**，断言照样通过。而 plat_sum 一旦变 (crop,product,plot) 粒度，末尾那句
        //      `LEFT JOIN recv_sum rs ON (crop_id, product_id)` 会把同一 recv_sum 行按地块数**重复**，
        //      且每行都减「全地块合计已收」→ mp 同一张卡重复出现、多地块作物的 pending 变负被 `WHERE > 0`
        //      吞掉而**直接丢卡**（甲方可见）。
        //   ④ 只锚「来源 + 聚合键 + 边界」—— 被 SELF-A-full 打穿：把**两处来源整体对调**
        //      （plat_sum←`FROM recv`、recv_sum←`FROM plat`），两个字面片段依然各自都在、断言全绿。
        //      所以来源必须和 **CTE 身份**绑在一起，见下面两条断言。
        assertThat(sql)
            .as("plat_sum 必须取自月台明细 FROM plat，且聚合键恰为 (crop_id, product_id)、不多不少")
            .contains("from plat group by crop_id, product_id )");
        assertThat(sql)
            .as("recv_sum 必须取自已收明细 FROM recv，且聚合键恰为 (crop_id, product_id)、不多不少")
            .contains("from recv group by crop_id, product_id )");
        // ④ 的堵法：把「聚合列 → 来源」钉在一起。platform 这个列名只存在于 `plat` CTE
        //（recv 没有 platform），loss_today 只存在于 `recv` CTE（plat 没有 loss_today），
        // 因此这两条断言同时约束了 **CTE 身份 + 来源** —— 整体对调后两条必红。
        assertThat(sql)
            .as("plat_sum 的 platform 列只能来自 plat CTE（防两处来源整体对调 / SELF-A-full）")
            .contains("sum(platform) as platform from plat group by crop_id, product_id )");
        assertThat(sql)
            .as("recv_sum 的 loss_today 列只能来自 recv CTE（防两处来源整体对调 / SELF-A-full）")
            .contains("sum(loss_today) as loss_today from recv group by crop_id, product_id )");

        // 只算自产收货：recv CTE 漏了 receive_type=1，外购收货会被算进自产「已入库」，静默改数。
        // 这条以前没人钉（外购与自产混在同一个 SUM 里，页面上看不出来）。
        assertThat(countOf(sql, "vr.receive_type = 1"))
            .as("selectSelfPending 的 recv CTE 必须限定自产收货 receive_type=1")
            .isEqualTo(1);
        // M9c 闭合（第八轮 QA：「字面计数挡不住语义放宽」）。光数 `receive_type = 1` 出现 1 次，
        // 挡不住 `AND (vr.receive_type = 1 OR vr.receive_type = 2)` —— 字面仍在、计数仍 1，
        // 但外购已被算进自产已入库。补两条**纯文本**断言即可，不必真连库：
        //   ① 这个 CTE 里根本不该出现 receive_type = 2；
        //   ② 条件必须仍然**紧贴** GROUP BY（防止被 OR 或额外括号包住）。
        assertThat(sql)
            .as("recv CTE 不得出现外购收货 receive_type = 2（M9c：OR 放宽会把外购算进自产）")
            .doesNotContain("receive_type = 2");
        assertThat(sql)
            .as("receive_type = 1 之后必须直接接 GROUP BY，不能被 OR/括号包裹（M9c）")
            .contains("vr.receive_type = 1 group by vr.crop_id, vr.plot_id");
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
        assertThat(countOf(plots, "<if test=\"productid != null\">"))
            .as("地块明细应恰有 2 段可选收窄")
            .isEqualTo(2);
        // 剩余可入量三段（月台量 / 已入 / 已结算损耗）都要能按产品收窄，漏一段封顶就形同虚设。
        // 只数 <if> 外壳不够：把某一段的谓词换成 1=1，外壳计数不变、`= #{productid}` 也仍被别段满足，
        // 变异能存活。故三段各自的表别名 + 收窄谓词都要点名断言。
        assertThat(countOf(remain, "<if test=\"productid != null\">"))
            .as("剩余可入量应恰有 3 段可选收窄")
            .isEqualTo(3);
        assertThat(remain)
            .as("月台量段（handle_record）按产品收窄")
            .contains("coalesce(hr.product_id, cr0.related_product) = #{productid}")
            .as("已入库段（veg_receive）按产品收窄")
            .contains("coalesce(vr.product_id, cr1.related_product) = #{productid}")
            .as("已结算损耗段（veg_receive is_finish=1）按产品收窄")
            .contains("coalesce(vrl.product_id, cr2.related_product) = #{productid}");

        // 产品之外的两个轴同样得钉住：删了作物谓词 → 详情页返回所有作物的地块；
        // 删了地块谓词 → 封顶额度按整作物算、跨地块互吃。两者都是合法 SQL、单测照样全绿。
        assertThat(countOf(plots, "crop_id = #{cropid}"))
            .as("地块明细两段都要按作物收窄")
            .isEqualTo(2);
        assertThat(countOf(remain, "plot_id = #{plotid}"))
            .as("剩余可入量三段都要按地块收窄")
            .isEqualTo(3);
        // 已结算损耗那段只能算已完成行，否则未完成的损耗也被扣、额度凭空变小
        assertThat(remain).contains("vrl.is_finish = 1");
        // 只算自产收货：漏了这条，外购收货会被计进自产已入库量
        assertThat(remain).contains("vr.receive_type = 1");

        // 地块明细的 recv CTE 同样要限定自产 —— 详情页的「已收量」漏了它，外购收货会被算成自产已收。
        // 与 selectSelfPending 那条同源不同语句：旧测试只钉了 remain 一处，plots 这处漏网（变异存活）。
        assertThat(countOf(plots, "vr.receive_type = 1"))
            .as("selectInboundPlots 的 recv CTE 必须限定自产收货 receive_type=1")
            .isEqualTo(1);
    }
}
