package org.dromara.djs.warehouse.vegout.mapper;

import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.vegout.service.impl.VegOutServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 毛菜间出库「新增」抽屉候选行的取数口径契约。
 *
 * <p>候选范围是这个功能唯一的安全边界：它决定了工人能从哪些库、把哪一类货按 kg 直接卖出去。
 * 范围一旦放宽，扣的是真实库存、写的是真实流水，事后只能靠盘点发现。这些条件写在注解 SQL 与
 * service 常量里，普通 mock 单测打不到，只能用原文断言钉死。</p>
 *
 * <p>三条不变量：</p>
 * <ol>
 *   <li><b>只取原材料</b>（{@code product_attr=2}）—— 生产产品有各自的发货 / 门店链路。</li>
 *   <li><b>库位白名单参数化</b>（{@code foreach}）—— 白名单是 service 常量，SQL 里不许出现硬编码库位码，
 *       否则两处会分家。而白名单本身必须只含那五个可售农产品库：门店供货池 L0003/L0004、白条库 L0001、
 *       包材 / 种子 / 肥料 / 农药库都是 {@code product_attr=2}，光靠原材料过滤挡不住。</li>
 *   <li><b>存储仓库取篮子实际所在库位</b>（{@code location_info.location_name}）—— 不是产品主数据上
 *       配置的建议落点，工人照它去哪个库拿货。</li>
 * </ol>
 */
@Tag("local")
@Tag("dev")
@DisplayName("毛菜间出库候选行 SQL 口径契约（按产品取原材料库存 + 三业态 tab）")
class VegOutCandidateSqlContractTest {

    private static String candidateSql() throws Exception {
        Method method = VegOutMapper.class.getMethod("selectCandidates",
            Collection.class, Collection.class, String.class);
        Select select = method.getAnnotation(Select.class);
        assertThat(select).as("VegOutMapper#selectCandidates 应带 @Select").isNotNull();
        return normalize(String.join(" ", select.value()));
    }

    private static String normalize(String raw) {
        return raw.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    /** 读 service 里的私有静态白名单常量（它是 SQL 的实参，范围的真相在这）。 */
    @SuppressWarnings("unchecked")
    private static List<String> whitelist(String fieldName) throws Exception {
        Field field = VegOutServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (List<String>) field.get(null);
    }

    @Test
    @DisplayName("只取原材料库存：product_attr = 2 必须在 WHERE 里")
    void candidatesAreRawMaterialsOnly() throws Exception {
        String sql = candidateSql();

        assertThat(sql)
            .as("甲方「主要取原材料产品库存」的落点：少了这条，打包好的成品会被当散货按 kg 卖掉")
            .contains("p.product_attr = 2");
    }

    @Test
    @DisplayName("库位白名单参数化 foreach，SQL 里不许硬编码库位码")
    void locationWhitelistIsParameterized() throws Exception {
        String sql = candidateSql();

        assertThat(sql)
            .as("库位过滤必须走 service 传进来的白名单")
            .contains("l.location_code in")
            .contains("<foreach collection=\"locationcodes\"")
            .as("业态过滤同样参数化（前端三个 tab 按它分组）")
            .contains("p.belong_type in")
            .contains("<foreach collection=\"belongtypes\"");

        // SQL 里再写一份库位码 = 第二份白名单，改 service 常量时必然漏改一处
        for (String code : new String[]{"l0003", "l0004", "l0005", "l0006", "l0007", "l0009", "l0018"}) {
            assertThat(sql).as("SQL 里不该硬编码库位码 %s（白名单唯一真相在 service 常量）", code)
                .doesNotContain("'" + code + "'");
        }

        assertThat(sql)
            .as("空篮子不能出现在候选里")
            .contains("s.product_stock > 0");
    }

    @Test
    @DisplayName("白名单只含五个可售农产品库：门店供货池 / 白条库 / 生产投入品库一律不在内")
    void whitelistCoversSellableFarmStoresOnly() throws Exception {
        assertThat(whitelist("ALLOWED_LOCATION_CODES"))
            .as("缺 L0007 / L0018 猪肉 tab 会永远是空的")
            .containsExactlyInAnyOrder("L0006", "L0005", "L0009", "L0007", "L0018");

        assertThat(whitelist("ALLOWED_BELONG_TYPES"))
            .as("三个 tab：果蔬 vegetable / 猪肉 pork / 其他 = 剩下的业态")
            .contains("vegetable", "pork", "dry_good", "egg", "other")
            .as("白条按半只走燎毛 / 分割链路，不按 kg 散卖；包材 / 种子不是可售农产品")
            .doesNotContain("white_bar", "package", "seed", "feed", "gift_box");
    }

    @Test
    @DisplayName("存储仓库列取篮子实际所在库位名，耳号取库存行自带的 ear_no")
    void storeLocationAndEarNoAreSelected() throws Exception {
        String sql = candidateSql();

        assertThat(sql)
            .as("存储仓库 = 已 join 的 location_info.location_name（篮子真实所在库），"
                + "不是 product_info.store_location_id 那个建议落点")
            .contains("l.location_name as locationname");
        assertThat(sql)
            .as("产品主数据上的建议落点不该被当成存储仓库")
            .doesNotContain("store_location_id");

        assertThat(sql)
            .as("猪肉 tab 的第三列取库存行自带的耳号")
            .contains("s.ear_no as earno");
        assertThat(sql)
            .as("果蔬 tab 仍按地块展示，plotName 不能丢")
            .contains("pl.plot_name as plotname");
    }

    @Test
    @DisplayName("一行 = 一个库存篮：不许按产品聚合（出库要按 stockId 逐篮扣）")
    void oneRowPerStockBasket() throws Exception {
        String sql = candidateSql();

        // 合并成一行就丢了 stockId，扣减找不到具体篮子，地块 / 耳号追溯也一起没了
        assertThat(sql)
            .as("候选必须逐行返回库存篮主键")
            .contains("s.id as stockid")
            .as("按产品合并会破坏逐篮扣减链路")
            .doesNotContain("group by");
    }
}
