package org.dromara.djs.plant.cropstat.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.plant.cropstat.domain.vo.CropPlotStatVo;

import java.util.List;

/**
 * 作物「在田地块」聚合 Mapper（门店需求下单页果蔬列数据源）。
 *
 * <h3>三态口径</h3>
 * {@code djs_plot_status} 只有 3 个取值：{@code 1=空闲 / 2=种植 / 3=采摘}，
 * <b>没有独立的「采摘完成」地块态</b>——采摘完成后地块仍停在 3，直到工人退茬
 * （{@code submitRotation}，要求 plot_status=3）才回 1。所以客户口径的
 * 「种植 + 采摘中 + 采摘完成」= 地块尚未退茬 = {@code plot_status IN (2,3)}；
 * 采摘中 / 采摘完成的区分落在明细的 {@code harvest_status}（picking / completed），不影响本统计的入选。
 *
 * <h3>当前轮去重</h3>
 * 同一地块历史上会种多轮（每轮一条 {@code t_plant_plant_details}），直接 JOIN 会把旧轮次也算进来。
 * 故按 <b>{@code (plot_id, crop_id)}</b> 分区、以「实际开始种植日期（缺失回落创建日期）+ id」倒序取
 * {@code rn=1} 的那条当轮明细；同时排除 {@code plant_status='pending'}（计划已排但还没播种，地块不算在田）。
 *
 * <p>分区必须带 {@code crop_id}：一块地可同时挂多个作物的在田明细，
 * 只按 {@code plot_id} 分区会让 id 较小的那个作物整条被丢掉——该作物明明还在采摘中却不计入它的剩余地块。
 * 加上 {@code crop_id} 后每个作物各取自己在该地块的当轮，既不漏算作物、也不会把同作物的历史轮次重复计数。</p>
 *
 * <p>但只按 {@code (plot_id, crop_id)} 取当轮还不够：地块退茬后 {@code plot_status} 归 1，
 * 一旦改种新作物又回到 2/3，上一茬作物的旧明细会在自己的分区里重新变成 {@code rn=1} 被「复活」成在田。
 * 故须再按 {@code t_plant_farm_records} 的退茬记录（{@code farm_type='rotation'}，同地块同作物且晚于该明细）
 * 排除已退茬的轮次——地已还回并改种，算进剩余地块 / 预计产量会让门店下单页高估可供货量。
 * 「同地块同作物退茬后再种」不受影响：新明细建于退茬之后，{@code NOT EXISTS} 成立、正常保留。</p>
 *
 * <h3>开始采摘时间</h3>
 * 每块地的「开始采摘时间」= {@code COALESCE(begin_harvestdate, earliest_harvestdate)}：
 * 已开采取实际开采日，未开采（仍在种植态）取计划最早采摘日——否则种植态地块该列恒 NULL，
 * 而客户明确要求把种植态地块纳入统计。最早 = MIN，最晚 = MAX。
 *
 * <p>跨表原生 SQL 显式写 {@code tenant_id='1001'}（V1 单租户，MP 多租户拦截器不注入 {@code @Select}）。</p>
 *
 * @author djs
 */
@Mapper
public interface CropPlotStatMapper {

    /**
     * 按作物聚合在田地块统计。
     *
     * @return 每作物一行（无在田地块的作物不出现），按剩余地块数倒序
     */
    @Select("""
        WITH dl AS (
            -- row267：灾害损失按 (地块, 作物) 预聚合，避免与 plant_details 多行左联扇出重复计数
            SELECT fr.plot_id, fr.crop_id, SUM(fr.loss_yield) AS disaster_loss
              FROM t_plant_farm_records fr
             WHERE fr.del_flag = '0' AND fr.tenant_id = '1001'
               AND fr.farm_type = 'disaster'
               AND fr.plot_id IS NOT NULL AND fr.crop_id IS NOT NULL
             GROUP BY fr.plot_id, fr.crop_id
        ),
        cur AS (
            SELECT d.plot_id,
                   d.crop_id,
                   -- row267：预计产量扣灾害损失。**逐地块**扣完再钳零，不能先求和后扣——
                   -- 某地块损失超过它自己的理论产量时，先求和会把别的地块产量一起吃掉。
                   GREATEST(COALESCE(d.expected_yield, 0) - COALESCE(dl.disaster_loss, 0), 0) AS expected_yield,
                   COALESCE(d.begin_harvestdate, d.earliest_harvestdate) AS pick_start,
                   ROW_NUMBER() OVER (
                       PARTITION BY d.plot_id, d.crop_id
                       ORDER BY COALESCE(d.begin_actualdate, DATE(d.create_time)) DESC, d.id DESC
                   ) AS rn
              FROM t_plant_plant_details d
              LEFT JOIN dl ON dl.plot_id = d.plot_id AND dl.crop_id = d.crop_id
              JOIN t_plant_plot_info p
                    ON p.id = d.plot_id
                   AND p.del_flag = '0'
                   AND p.tenant_id = '1001'
                   AND p.plot_status IN (2, 3)
             WHERE d.del_flag = '0'
               AND d.tenant_id = '1001'
               AND d.plant_status <> 'pending'
               AND NOT EXISTS (
                   SELECT 1
                     FROM t_plant_farm_records r
                    WHERE r.del_flag = '0'
                      AND r.tenant_id = '1001'
                      AND r.farm_type = 'rotation'
                      AND r.plot_id = d.plot_id
                      AND r.crop_id = d.crop_id
                      AND r.create_time > d.create_time
               )
        )
        SELECT c.id                                        AS cropId,
               c.crop_name                                 AS cropName,
               c.related_product                           AS relatedProduct,
               COUNT(DISTINCT cur.plot_id)                 AS remainPlotCount,
               SUM(cur.expected_yield)                     AS expectYield,
               DATE_FORMAT(MIN(cur.pick_start), '%Y-%m-%d') AS earliestPickDate,
               DATE_FORMAT(MAX(cur.pick_start), '%Y-%m-%d') AS latestPickDate
          FROM cur
          JOIN t_plant_crop_info c
                ON c.id = cur.crop_id
               AND c.del_flag = '0'
               AND c.tenant_id = '1001'
         WHERE cur.rn = 1
         GROUP BY c.id, c.crop_name, c.related_product
         ORDER BY remainPlotCount DESC, c.id
        """)
    List<CropPlotStatVo> selectPlotStat();
}
