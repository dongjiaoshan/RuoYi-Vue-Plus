package org.dromara.djs.warehouse.product.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 果蔬产品展示名解析 Mapper（DENGBO-R16）。
 *
 * <p>果蔬（{@code belong_type='vegetable'}）产品的展示名由「原材料对应作物是否有<b>有效有机证书</b>」决定：
 * 有证 → 产品名（{@code product_name}）；无证 → 产品别名（{@code product_alias}，别名空则兜底产品名，避免显示空）。
 * 非果蔬产品一律显示产品名。</p>
 *
 * <h3>映射链（已用 staging 真实数据核实）</h3>
 * <pre>
 * 原材料 id = COALESCE(product_material, product.id)
 *   —— 加工成品（如「有机上海青250g」）走 product_material 指向的基础菜；
 *      基础果蔬（如「上海青」attr=2，product_material 为 NULL）即自身为原材料，回落 product.id。
 *   → t_plant_crop_info WHERE related_product = 原材料 id → 得作物 crop
 *      （crop_info.related_product 直接指向基础果蔬 product 的 id）
 *   → t_plant_crop_organic_rel（一证多作物关联表）WHERE crop_id = crop.id 且 del_flag='0'
 *   → t_plant_crop_organic WHERE id = rel.organic_id 且 crop_cert_valid >= CURDATE() 且 del_flag='0'
 *     存在即「有有效有机证书」。
 * </pre>
 *
 * <p><b>作物↔证书走关联表</b>：{@code t_plant_crop_organic} 的 {@code crop_id} 列是「一证多作物」重构前的
 * 旧单值链，已废弃置 NULL，当前关联全在 {@code t_plant_crop_organic_rel}（organic_id ↔ crop_id）。
 * 直 JOIN {@code crop_organic.crop_id} 会恒查空 → 果蔬永远显示别名（本次「顽固」根因）。</p>
 *
 * <p><b>雪花 id 整数比较</b>：{@code related_product} / {@code product_material} / {@code product.id}
 * 均为 BIGINT 雪花 id（&gt; 2^53），JOIN key 外层保留 {@code CAST(... AS SIGNED)} 锁定整数语义；
 * 一旦任一侧以字符串参与比较（如把 id 转 CHAR 或改传字符串参数），MySQL 隐式转 DOUBLE，
 * 相邻雪花 id 浮点精度丢失而误命中相邻作物（实测 {@code 9303000000000003 = '9303000000000005'} 返 TRUE）。
 * 该 CAST 不可删。</p>
 *
 * <p>跨表 {@code @Select} 显式 {@code tenant_id='1001'}（单租户 V1；多租户拦截器在跨表原生 SQL 不保证注入）。</p>
 *
 * @author djs
 * @since DENGBO-R16
 */
@Mapper
public interface VegDisplayNameMapper {

    /**
     * 单个产品的展示名（果蔬按证 / 别名，非果蔬直接产品名）。
     *
     * <p>产品不存在 → 返回 null（调用方兜底）；产品无 {@code product_material} / 查不到作物 / 查不到有效证书
     * → 无证分支（别名，别名空回落产品名）。</p>
     *
     * @param productId 产品主键 {@code t_warehouse_product_info.id}
     * @return 展示名；产品不存在时 null
     */
    @Select("""
        <script>
        SELECT CASE
                 WHEN p.belong_type != 'vegetable' OR p.belong_type IS NULL THEN p.product_name
                 WHEN EXISTS (
                        SELECT 1
                        FROM t_plant_crop_info c
                        JOIN t_plant_crop_organic_rel rel
                          ON rel.crop_id = c.id
                         AND rel.del_flag = '0'
                         AND rel.tenant_id = '1001'
                        JOIN t_plant_crop_organic o
                          ON o.id = rel.organic_id
                         AND o.del_flag = '0'
                         AND o.tenant_id = '1001'
                         AND o.crop_cert_valid &gt;= CURDATE()
                        WHERE c.del_flag = '0'
                          AND c.tenant_id = '1001'
                          AND c.related_product = CAST(COALESCE(p.product_material, p.id) AS SIGNED)
                      ) THEN p.product_name
                 ELSE COALESCE(NULLIF(p.product_alias, ''), p.product_name)
               END
        FROM t_warehouse_product_info p
        WHERE p.id = #{productId}
          AND p.del_flag = '0'
          AND p.tenant_id = '1001'
        </script>
        """)
    String resolveDisplayName(@Param("productId") Long productId);

    /**
     * 批量解析展示名（列表 enrich 防 N+1）。返回每行 {@code {productId, displayName}}。
     *
     * <p>调用方保证 {@code productIds} 非空；查不到的产品 id 不在结果集内（调用方兜底）。</p>
     *
     * @param productIds 产品主键集合（非空）
     * @return 每行 {@code productId} + {@code displayName}
     */
    @Select("""
        <script>
        SELECT p.id AS productId,
               CASE
                 WHEN p.belong_type != 'vegetable' OR p.belong_type IS NULL THEN p.product_name
                 WHEN EXISTS (
                        SELECT 1
                        FROM t_plant_crop_info c
                        JOIN t_plant_crop_organic_rel rel
                          ON rel.crop_id = c.id
                         AND rel.del_flag = '0'
                         AND rel.tenant_id = '1001'
                        JOIN t_plant_crop_organic o
                          ON o.id = rel.organic_id
                         AND o.del_flag = '0'
                         AND o.tenant_id = '1001'
                         AND o.crop_cert_valid &gt;= CURDATE()
                        WHERE c.del_flag = '0'
                          AND c.tenant_id = '1001'
                          AND c.related_product = CAST(COALESCE(p.product_material, p.id) AS SIGNED)
                      ) THEN p.product_name
                 ELSE COALESCE(NULLIF(p.product_alias, ''), p.product_name)
               END AS displayName
        FROM t_warehouse_product_info p
        WHERE p.del_flag = '0'
          AND p.tenant_id = '1001'
          AND p.id IN
          <foreach collection="productIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
        </script>
        """)
    List<Map<String, Object>> resolveDisplayNameBatch(@Param("productIds") Collection<Long> productIds);

    /**
     * 果蔬产品「当前有效」有机证书清单（追溯页有机证书块 · row149）。
     *
     * <p>走与 {@link #resolveDisplayName} 同一条映射链（原材料 → 作物 {@code related_product} →
     * 关联表 {@code t_plant_crop_organic_rel} → 证书 {@code t_plant_crop_organic}），只取
     * {@code crop_cert_valid >= CURDATE()}（有效期内）的证书。追溯码只存 {@code product_id}（无 crop_id、
     * 且 {@code crop_cert_id} 在一证多作物重构后废弃置 NULL），故追溯页有机证书必须在读时按产品→作物→关联表
     * 解析，不能读 {@code trace_code.crop_cert_id}（恒 NULL → 有机证书永不显示，本条根因）。</p>
     *
     * <p>BIGINT 雪花 id JOIN key 外层保留 {@code CAST(... AS SIGNED)} 锁定整数语义（防字符串参与比较时
     * MySQL 隐式转 DOUBLE、相邻雪花 id 精度丢失误命中相邻作物）；
     * 单租户显式 {@code tenant_id='1001'}。返每行 {@code certNo / issuer / validTo / imagePreview}；无证返空 list。</p>
     *
     * @param productId 果蔬产品主键 {@code t_warehouse_product_info.id}
     * @return 该产品对应作物的当前有效有机证书 id（{@code t_plant_crop_organic.id}，可多张 · 一证多作物）；无则空 list
     */
    @Select("""
        SELECT DISTINCT o.id
        FROM t_warehouse_product_info p
        JOIN t_plant_crop_info c
          ON c.related_product = CAST(COALESCE(p.product_material, p.id) AS SIGNED)
         AND c.del_flag = '0'
         AND c.tenant_id = '1001'
        JOIN t_plant_crop_organic_rel rel
          ON rel.crop_id = c.id
         AND rel.del_flag = '0'
         AND rel.tenant_id = '1001'
        JOIN t_plant_crop_organic o
          ON o.id = rel.organic_id
         AND o.del_flag = '0'
         AND o.tenant_id = '1001'
         AND o.crop_cert_valid >= CURDATE()
        WHERE p.id = #{productId}
          AND p.del_flag = '0'
          AND p.tenant_id = '1001'
        """)
    List<Long> selectVegOrganicCertIds(@Param("productId") Long productId);

}
