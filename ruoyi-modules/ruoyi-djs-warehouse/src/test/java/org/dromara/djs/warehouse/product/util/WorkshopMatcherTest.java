package org.dromara.djs.warehouse.product.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WorkshopMatcher} 单测（WMS-PRODUCT-WORKSHOP-MULTI-001 生产车间单选改多选）。
 *
 * <p>核心风险是「CSV 列上误用等值比较」和「脏 CSV 让 FIND_IN_SET 静默不命中」，
 * 两者都会让产品从燎毛 / 分割 / 门店打包的取数里凭空消失且不报错，故重点覆盖这两类。</p>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
class WorkshopMatcherTest {

    @Nested
    @DisplayName("normalize：写入路径 CSV 归一化")
    class Normalize {

        @Test
        @DisplayName("空值 / 空串 / 纯分隔符 → null（列语义：空归属存 NULL 不存空串）")
        void blankToNull() {
            assertNull(WorkshopMatcher.normalize(null));
            assertNull(WorkshopMatcher.normalize(""));
            assertNull(WorkshopMatcher.normalize("   "));
            assertNull(WorkshopMatcher.normalize(","));
            assertNull(WorkshopMatcher.normalize(",,"));
        }

        @Test
        @DisplayName("单值原样保留")
        void singleValueKept() {
            assertEquals("3", WorkshopMatcher.normalize("3"));
        }

        @Test
        @DisplayName("段内空格被剥掉——'3, 5' 不归一化会让 FIND_IN_SET('5',...) 恒不命中")
        void stripsInnerSpaces() {
            assertEquals("3,5", WorkshopMatcher.normalize("3, 5"));
            assertEquals("3,5", WorkshopMatcher.normalize(" 3 , 5 "));
        }

        @Test
        @DisplayName("空段被丢弃、重复段去重、原顺序保留")
        void dropsEmptyAndDedupKeepingOrder() {
            assertEquals("3,5", WorkshopMatcher.normalize("3,,5"));
            assertEquals("3,5", WorkshopMatcher.normalize("3,5,3"));
            assertEquals("5,3", WorkshopMatcher.normalize("5,3"));
        }
    }

    @Nested
    @DisplayName("contains：内存侧判定，口径需与 SQL FIND_IN_SET 一致")
    class Contains {

        @Test
        @DisplayName("多归属 CSV 里命中任一段")
        void hitsAnySegment() {
            assertTrue(WorkshopMatcher.contains("3,5", "3"));
            assertTrue(WorkshopMatcher.contains("3,5", "5"));
        }

        @Test
        @DisplayName("不命中未挂的车间")
        void missesAbsent() {
            assertFalse(WorkshopMatcher.contains("3,5", "4"));
        }

        @Test
        @DisplayName("不做子串匹配——'5' 不能命中 '15'（等值比较退化的典型误伤）")
        void isNotSubstringMatch() {
            assertFalse(WorkshopMatcher.contains("15", "5"));
            assertFalse(WorkshopMatcher.contains("35", "3"));
        }

        @Test
        @DisplayName("空 CSV / 空 code 一律不命中")
        void blankNeverHits() {
            assertFalse(WorkshopMatcher.contains(null, "3"));
            assertFalse(WorkshopMatcher.contains("", "3"));
            assertFalse(WorkshopMatcher.contains("3", null));
            assertFalse(WorkshopMatcher.contains("3", ""));
        }
    }

    @Nested
    @DisplayName("match / matchAny：SQL 片段拼装")
    class SqlSegment {

        private LambdaQueryWrapper<ProductInfo> wrapper() {
            return new LambdaQueryWrapper<>();
        }

        @Test
        @DisplayName("单值落一个 FIND_IN_SET，车间码走参数绑定不拼字符串（防注入）")
        void singleEmitsFindInSet() {
            LambdaQueryWrapper<ProductInfo> w = WorkshopMatcher.match(wrapper(), "5");
            String sql = w.getSqlSegment();
            assertTrue(sql.contains("FIND_IN_SET"), sql);
            assertTrue(sql.contains("product_workshop"), sql);
            // 车间码不出现在 SQL 文本里 = 走了 #{} 参数占位
            assertFalse(sql.contains("'5'"), sql);
            assertTrue(w.getParamNameValuePairs().containsValue("5"), w.getParamNameValuePairs().toString());
        }

        @Test
        @DisplayName("空 / 空串不加任何条件（避免筛选项没选也过滤掉全部行）")
        void blankAddsNothing() {
            assertEquals("", WorkshopMatcher.match(wrapper(), null).getSqlSegment());
            assertEquals("", WorkshopMatcher.match(wrapper(), "").getSqlSegment());
            assertEquals("", WorkshopMatcher.matchAny(wrapper(), List.of()).getSqlSegment());
            assertEquals("", WorkshopMatcher.matchAny(wrapper(), null).getSqlSegment());
        }

        @Test
        @DisplayName("多值落 OR 组，并被 and(...) 包住不串到同级其它条件")
        void multiEmitsOrGroup() {
            String sql = WorkshopMatcher.matchAny(wrapper(), List.of("3", "5")).getSqlSegment();
            assertEquals(2, countOccurrences(sql, "FIND_IN_SET"), sql);
            assertTrue(sql.contains("OR"), sql);
            assertTrue(sql.contains("("), sql);
        }

        @Test
        @DisplayName("单值参数误传 '3,5' 自动降级成 OR 组——否则 FIND_IN_SET('3,5',..) 恒不命中、静默返空")
        void csvInSingleParamDegradesToAny() {
            String sql = WorkshopMatcher.match(wrapper(), "3,5").getSqlSegment();
            assertEquals(2, countOccurrences(sql, "FIND_IN_SET"), sql);
            assertTrue(sql.contains("OR"), sql);
        }

        @Test
        @DisplayName("多值里的空段被剔除，不产生空参数条件")
        void multiDropsBlankSegments() {
            String sql = WorkshopMatcher.matchAny(wrapper(), List.of("3", "", "  ", "5")).getSqlSegment();
            assertEquals(2, countOccurrences(sql, "FIND_IN_SET"), sql);
        }

        private int countOccurrences(String haystack, String needle) {
            int count = 0;
            int idx = haystack.indexOf(needle);
            while (idx >= 0) {
                count++;
                idx = haystack.indexOf(needle, idx + needle.length());
            }
            return count;
        }
    }
}
