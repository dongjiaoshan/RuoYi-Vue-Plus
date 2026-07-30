package org.dromara.djs.warehouse.product.util;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.warehouse.product.domain.ProductInfo;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 产品生产车间（{@code t_warehouse_product_info.product_workshop}）CSV 多值匹配 / 归一化工具。
 *
 * <p>车间自 WMS-PRODUCT-WORKSHOP-MULTI-001 起由单值 TINYINT 改为 CSV 多值 VARCHAR（如 {@code "3,5"}），
 * 因为同一猪肉成品既可在仓库「肉品打包间(3)」生产、也可在门店「门店打包间(5)」生产。</p>
 *
 * <p><b>过滤必须走本类</b>：CSV 列上用 {@code eq} / {@code in} 会漏掉多归属行
 * （{@code product_workshop='3,5'} 既不等于 {@code '3'} 也不等于 {@code '5'}），
 * 必须用 {@code FIND_IN_SET}。所有车间过滤点（燎毛 / 分割 / 门店打包 / admin 列表筛选）统一调用这里。</p>
 *
 * @author djs
 * @since WMS-PRODUCT-WORKSHOP-MULTI-001
 */
public final class WorkshopMatcher {

    private WorkshopMatcher() {
    }

    /** CSV 分隔符（与 admin 表单 join、{@code @ExcelDictFormat.separator} 默认值一致）。 */
    public static final String SEPARATOR = ",";

    /**
     * {@code FIND_IN_SET} 片段。{@code {0}} 是 MyBatis-Plus 参数占位符（预编译绑定，非字符串拼接，无注入风险）；
     * 列名用<b>数据库列名</b> {@code product_workshop}（{@code apply} 不走 Lambda 反射解析）。
     */
    private static final String FIND_IN_SET = "FIND_IN_SET({0}, product_workshop) > 0";

    /**
     * 单车间过滤：命中「归属包含该车间」的产品（多归属产品也会被选中）。
     *
     * <p>{@code code} 为空 → 不加条件。{@code code} 本身含逗号（如前端把多选值塞进了单值参数）
     * → 自动降级成 {@link #matchAny} 语义，避免 {@code FIND_IN_SET('3,5', ...)} 恒不命中的静默空结果。</p>
     *
     * @param wrapper 目标 wrapper
     * @param code    车间码（字典 {@code djs_product_workshop} 的 value，如 {@code "5"}）
     * @return 同一个 wrapper，便于链式调用
     */
    public static LambdaQueryWrapper<ProductInfo> match(LambdaQueryWrapper<ProductInfo> wrapper, String code) {
        if (StringUtils.isBlank(code)) {
            return wrapper;
        }
        if (code.contains(SEPARATOR)) {
            return matchAny(wrapper, split(code));
        }
        return wrapper.apply(FIND_IN_SET, code.trim());
    }

    /**
     * 多车间过滤：命中「归属包含其中<b>任一</b>车间」的产品（OR 语义，对齐旧 {@code IN (...)} 的筛选体感）。
     *
     * <p>生成 {@code AND ( FIND_IN_SET(a,pw)>0 OR FIND_IN_SET(b,pw)>0 )}，
     * 用 {@code and(...)} 包一层保证 OR 组不会串到同级的其它条件上。</p>
     *
     * @param wrapper 目标 wrapper
     * @param codes   车间码集合；空集合 → 不加条件
     * @return 同一个 wrapper，便于链式调用
     */
    public static LambdaQueryWrapper<ProductInfo> matchAny(LambdaQueryWrapper<ProductInfo> wrapper, Collection<String> codes) {
        List<String> cleaned = normalizeToList(codes);
        if (cleaned.isEmpty()) {
            return wrapper;
        }
        return wrapper.and(w -> {
            for (int i = 0; i < cleaned.size(); i++) {
                if (i > 0) {
                    w.or();
                }
                w.apply(FIND_IN_SET, cleaned.get(i));
            }
        });
    }

    /**
     * 判断一个 CSV 车间归属是否包含指定车间（内存侧判定，口径与 SQL {@code FIND_IN_SET} 一致）。
     *
     * @param csv  产品的 {@code product_workshop} 原值（可空）
     * @param code 待判定车间码
     * @return 包含则 true
     */
    public static boolean contains(String csv, String code) {
        if (StringUtils.isBlank(csv) || StringUtils.isBlank(code)) {
            return false;
        }
        return split(csv).contains(code.trim());
    }

    /**
     * 归一化 CSV：去首尾空格、丢空段、去重、保留原顺序。
     *
     * <p>写入路径统一调用，保证库里不出现 {@code "3, 5"} / {@code "3,,5"} / {@code "3,3"} 这类脏值
     * ——{@code FIND_IN_SET} 对带空格的段不命中（{@code FIND_IN_SET('5','3, 5')} = 0），脏值会静默丢结果。</p>
     *
     * @param csv 原始 CSV（可空）
     * @return 归一化后的 CSV；无有效段时返回 {@code null}（列语义：空归属存 NULL 不存空串）
     */
    public static String normalize(String csv) {
        if (StringUtils.isBlank(csv)) {
            return null;
        }
        List<String> cleaned = normalizeToList(split(csv));
        return cleaned.isEmpty() ? null : String.join(SEPARATOR, cleaned);
    }

    /** 拆 CSV 为段列表（不去重、不判空，仅做分隔与 trim）。 */
    private static List<String> split(String csv) {
        return Arrays.stream(csv.split(SEPARATOR)).map(String::trim).toList();
    }

    /** 集合归一化：去空白项、去重、保序。 */
    private static List<String> normalizeToList(Collection<String> codes) {
        if (CollUtil.isEmpty(codes)) {
            return List.of();
        }
        Set<String> ordered = new LinkedHashSet<>();
        for (String code : codes) {
            if (StringUtils.isNotBlank(code)) {
                ordered.add(code.trim());
            }
        }
        return List.copyOf(ordered);
    }
}
