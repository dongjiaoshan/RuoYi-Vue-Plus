package org.dromara.djs.plant.crop.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.crop.domain.CropProduct;
import org.dromara.djs.plant.crop.domain.vo.CropProductVo;

import java.util.Collection;
import java.util.List;

/**
 * 作物关联产品配置 Mapper（V6 row16）。
 *
 * <p><b>聚合 SQL 租户隔离</b>：V1 未启全局 MP 多租户拦截器，{@code @Select} 原生 SQL 不被自动注入
 * tenant 过滤 → 每张表显式手写 {@code tenant_id='1001' AND del_flag='0'}。</p>
 *
 * @author djs
 */
public interface CropProductMapper extends BaseMapperPlus<CropProduct, CropProductVo> {

    /**
     * 按作物批量查配置行（含产品名 / 单位），一次 IN 查完不逐条查库。
     *
     * <p>产品已删除的配置行 JOIN 不上 → 直接不返回：留着只会让下拉里出现一个点不出名字的空条目。</p>
     *
     * @param cropIds 作物 id 集合（已 dedupe，非空）
     * @return 配置行，按 (crop_id, id) 升序 —— 第一行即该作物的「首选产品」，存量迁移行排在最前
     */
    @Select("""
        <script>
        SELECT cp.id, cp.crop_id AS cropId, cp.product_id AS productId,
               p.product_name AS productName, p.product_unit AS productUnit,
               cp.perf_price AS perfPrice
          FROM t_plant_crop_product cp
          JOIN t_warehouse_product_info p
                 ON p.id = cp.product_id AND p.tenant_id = '1001' AND p.del_flag = '0'
         WHERE cp.tenant_id = '1001'
           AND cp.del_flag = '0'
           AND cp.crop_id IN
           <foreach collection='cropIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach>
         ORDER BY cp.crop_id, cp.id
        </script>
        """)
    List<CropProductVo> selectByCropIds(@Param("cropIds") Collection<Long> cropIds);

    /**
     * 产品是否存在且未删（配置前置校验）。
     *
     * <p>用原生 SQL 而不是注入 {@code ProductInfoMapper}：plant 模块不依赖 warehouse，
     * 反过来依赖会把两个域的编译顺序绑死。</p>
     *
     * @param productId 产品 id
     * @return 1 = 存在且未删 / 0 = 不存在或已删
     */
    @Select("SELECT COUNT(1) FROM t_warehouse_product_info "
        + "WHERE id = #{productId} AND tenant_id = '1001' AND del_flag = '0'")
    int countLiveProduct(@Param("productId") Long productId);
}
