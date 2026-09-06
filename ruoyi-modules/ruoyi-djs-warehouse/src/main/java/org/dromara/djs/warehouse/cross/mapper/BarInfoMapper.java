package org.dromara.djs.warehouse.cross.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.domain.vo.TodayBarVo;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 白条 Mapper minimal（跨域共享 / D9 closing Group B 从 cut/mapper 挪入 cross/mapper）。
 *
 * @author djs
 * @since WMS-PIG-002
 */
public interface BarInfoMapper extends BaseMapperPlus<BarInfo, BarInfo> {

    /**
     * 燎毛入库阶段乐观锁：bar_info.status pending_singe/singing → in_stock（D12X-MP-BURN-IA-001）。
     *
     * <p>WHERE status IN ('pending_singe','singing') 保证并发燎毛工只有一个成功推进白条到入库态。
     * affectedRows==0 → 调用方抛"白条状态不符（已入库或不在待燎毛态）"。
     * 同步回填 in_weight（各类型入库重量合计）/ in_time / in_method=1（1=燎毛间）。</p>
     */
    // finish_time 是「处理完成」的专用不可变锚（V6-R172）：只在这一次状态推进里写，
    // 且 WHERE 的 status 守卫保证只写得进一次。in_time 不能当这个锚——它在称重、
    // 每次产品逐项入库、处理完成三处被反复覆写，日表按 DATE(in_time) 分桶因此不可复现。
    @Update("UPDATE t_warehouse_bar_info "
        + "   SET status='in_stock', in_weight=#{inWeight}, in_time=#{inTime}, in_method=1,"
        + "       finish_time=#{inTime},"
        + "       update_by=#{userId}, update_time=NOW() "
        + " WHERE id = #{id} AND status IN ('pending_singe','singing') AND del_flag = '0'")
    int updateStatusToInStock(@Param("id") Long id,
                              @Param("inWeight") BigDecimal inWeight,
                              @Param("inTime") Date inTime,
                              @Param("userId") Long userId);

    /**
     * 燎毛产品逐项入库阶段乐观锁：bar_info.status pending_singe/singing → singing（燎毛中，FIX-WMS-MP-BURN-001）。
     *
     * <p>客户 6/11 新需求：产品逐个入库时只推进到中间态 {@code singing}（不直推 {@code in_stock}），
     * 解决「多产品逐个入库到第 2 个抛白条状态不符」的现状 bug。bar 终态由「处理完成」按钮调
     * {@link #updateStatusToInStock} 推进到 {@code in_stock}。WHERE status IN ('pending_singe','singing')
     * 幂等 + 兜并发；首次入库回填 in_time/in_method=1，后续幂等推进。</p>
     */
    @Update("UPDATE t_warehouse_bar_info "
        + "   SET status='singing', in_time=#{inTime}, in_method=1,"
        + "       update_by=#{userId}, update_time=NOW() "
        + " WHERE id = #{id} AND status IN ('pending_singe','singing') AND del_flag = '0'")
    int updateStatusToSinging(@Param("id") Long id,
                              @Param("inTime") Date inTime,
                              @Param("userId") Long userId);

    /**
     * V6-R43 燎毛间产品入库重量调整的「可调整窗口」乐观锁 + 入库重量差额同步。
     *
     * <p>两件事一次做完：</p>
     * <ol>
     *   <li><b>窗口校验（业务前置条件，必须在后端拦）</b>：{@code WHERE status IN ('pending_singe','singing')}
     *       —— 只有还没点「处理完成」的猪只才允许调整。affectedRows==0 = 该白条已被并发推进到
     *       {@code in_stock}（有人点了处理完成）或已不在燎毛间在制态，调用方抛异常回滚整个调整。</li>
     *   <li><b>并发互斥</b>：这条 UPDATE 会拿住该 bar 行的排他锁，与 {@code finishBurn} 的
     *       {@link #updateStatusToInStock} 争同一行 —— 两者不可能同时通过。调整事务里把它放在
     *       最后一步（inhouse / 库存 / 流水 / 燎毛记录都写完之后）作为提交闸：这样若 finishBurn 已先
     *       拿到锁并推进状态，本次调整整体回滚，不会留下改了一半的数据。</li>
     * </ol>
     *
     * <p>{@code in_weight} 用 CASE 保护：它由 finishBurn 在「处理完成」时才写入（= 各产出行合计），
     * 调整窗口内正常为 NULL → 保持 NULL 不动；万一非空（重跑 / 历史数据）则按差额同步，
     * 不覆盖（它是整只口径的合计，不是单行重量）。</p>
     *
     * @param id     白条主键
     * @param delta  入库重量调整差额（新重量 − 旧重量，可负）
     * @param userId 操作人
     * @return affectedRows（1=仍在可调整窗口；0=已处理完成 / 状态不符 / 已软删）
     */
    @Update("UPDATE t_warehouse_bar_info "
        + "   SET in_weight = CASE WHEN in_weight IS NULL THEN NULL ELSE in_weight + #{delta} END,"
        + "       update_by = #{userId}, update_time = NOW() "
        + " WHERE id = #{id} AND status IN ('pending_singe','singing') AND del_flag = '0'")
    int adjustInWeightIfBurning(@Param("id") Long id,
                                @Param("delta") BigDecimal delta,
                                @Param("userId") Long userId);

    /**
     * pickup 阶段乐观锁：bar_info.status in_stock → pending_cut。
     *
     * <p>WHERE status='in_stock' 保证并发分割师同时领用同一条白条只有一个成功。
     * affectedRows==0 → 抛"白条已被领用或状态不符"。</p>
     */
    @Update("UPDATE t_warehouse_bar_info "
        + "   SET status='pending_cut', update_by=#{userId}, update_time=NOW() "
        + " WHERE id = #{id} AND status = 'in_stock' AND del_flag = '0'")
    int updateStatusToPendingCut(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * cutOut 首次提交：bar_info.status pending_cut → cutting。
     *
     * <p>同事务幂等：如已是 cutting 则 affectedRows==0，调用方忽略。</p>
     */
    @Update("UPDATE t_warehouse_bar_info "
        + "   SET status='cutting', update_by=#{userId}, update_time=NOW() "
        + " WHERE id = #{id} AND status = 'pending_cut' AND del_flag = '0'")
    int updateStatusToCutting(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * cutDone 阶段：bar_info.status cutting → cut_done + 写入出库相关字段。
     *
     * <p>{@code outMethod}=2（分割间）；{@code acidRemoveTime}=分钟数；{@code acidRemoveLoss}=滴水损失。</p>
     */
    @Update("UPDATE t_warehouse_bar_info "
        + "   SET status='cut_done', out_time=#{outTime}, out_weight=#{outWeight},"
        + "       out_method=2, acid_remove_time=#{acidRemoveTime}, acid_remove_loss=#{acidRemoveLoss},"
        + "       update_by=#{userId}, update_time=NOW() "
        + " WHERE id = #{id} AND status = 'cutting' AND del_flag = '0'")
    int updateStatusToCutDone(@Param("id") Long id,
                              @Param("outTime") Date outTime,
                              @Param("outWeight") BigDecimal outWeight,
                              @Param("acidRemoveTime") Integer acidRemoveTime,
                              @Param("acidRemoveLoss") BigDecimal acidRemoveLoss,
                              @Param("userId") Long userId);

    /**
     * 分割完成结算点回写「分割产品重量 + 分割损耗」到白条表（邓博 row8：白条表加这两字段落库）。
     *
     * <p>原仅 compute-on-read（PigCutRecordVo 上算）；现在 cutDone 时持久化，口径与 loss_flow 双写一致：
     * {@code cut_product_weight = Σ cut_out_in by white_bar_id}（分割产品重量之和），
     * {@code cut_loss = 出库重量 − 分割产品重量}。无状态条件（cutDone 后白条已是 cut_done 终态），
     * 由调用点的 cutting→cut_done 乐观锁保证只走一次。</p>
     *
     * @param id               白条主键
     * @param cutProductWeight 分割产品重量（kg）
     * @param cutLoss          分割损耗（kg，= 出库重 − 分割产品重量）
     * @param userId           操作人
     * @return affectedRows
     */
    @Update("UPDATE t_warehouse_bar_info "
        + "   SET cut_product_weight=#{cutProductWeight}, cut_loss=#{cutLoss},"
        + "       update_by=#{userId}, update_time=NOW() "
        + " WHERE id = #{id} AND del_flag = '0'")
    int updateCutResult(@Param("id") Long id,
                        @Param("cutProductWeight") BigDecimal cutProductWeight,
                        @Param("cutLoss") BigDecimal cutLoss,
                        @Param("userId") Long userId);

    /**
     * 发货月台领用出库乐观锁：bar_info.status in_stock → cut_done + 写出库字段（猪肉全闭环 Part I P6）。
     *
     * <p>发货月台领用（{@code submitWhiteBarOut} 来源走燎毛白条整只时）回写 bar 出库基础数据，
     * 补齐「发货月台 inhouse → product_production」链路里 bar_info 出库字段缺失的 base-data gap。</p>
     *
     * <p>终态复用 {@code cut_done}（不动 7 态状态机/字典）；用 {@code out_method=1}（发货领用）区分于
     * 分割路径的 {@code out_method=2}（分割间，见 {@link #updateStatusToCutDone}）。
     * <b>不写 acid_remove_*</b>（排酸字段语义保留分割路径专用，Kevin 拍板）。
     * WHERE status='in_stock' 保证并发只有一个成功；affectedRows==0 → 该 bar 不在 in_stock 态
     * （已被领用/出库/不存在），调用方静默跳过（白条整只发货为基础数据补写，非主链路硬阻塞）。</p>
     *
     * @param id        白条主键
     * @param outTime   出库时间
     * @param outWeight 出库重量（kg）
     * @param userId    操作人
     * @return affectedRows（0 = bar 不在 in_stock 态，调用方跳过回写）
     */
    @Update("UPDATE t_warehouse_bar_info "
        + "   SET status='cut_done', out_time=#{outTime}, out_weight=#{outWeight}, out_method=1,"
        + "       update_by=#{userId}, update_time=NOW() "
        + " WHERE id = #{id} AND status = 'in_stock' AND del_flag = '0'")
    int updateStatusToShipOut(@Param("id") Long id,
                              @Param("outTime") Date outTime,
                              @Param("outWeight") BigDecimal outWeight,
                              @Param("userId") Long userId);

    /**
     * 仓库出库（后台出库）领用出库乐观锁：bar_info.status in_stock → cut_done + 写出库字段（邓博 row17）。
     *
     * <p>「后台出库」= 矿山/厨房等直接来仓库拿货、拿走即终结（不发货、不走门店逻辑，邓博 2026-07-02 澄清）。
     * 终态沿用既有约定复用 {@code cut_done}（不动 7 态状态机/字典）；用 {@code out_method=3}（后台出库）
     * 区分于发货领用 {@code out_method=1} / 分割间 {@code out_method=2}。出库去向（矿山/厨房…）记在
     * stock_flow.stock_out_dest + product_production.remark，bar 表只标 out_method。
     * WHERE status='in_stock' 保证并发只有一个成功；affectedRows==0 → 调用方静默跳过。</p>
     */
    @Update("UPDATE t_warehouse_bar_info "
        + "   SET status='cut_done', out_time=#{outTime}, out_weight=#{outWeight}, out_method=3,"
        + "       update_by=#{userId}, update_time=NOW() "
        + " WHERE id = #{id} AND status = 'in_stock' AND del_flag = '0'")
    int updateStatusToWarehouseOut(@Param("id") Long id,
                                   @Param("outTime") Date outTime,
                                   @Param("outWeight") BigDecimal outWeight,
                                   @Param("userId") Long userId);

    /**
     * 查「今日白条出库」的猪只耳号去重清单（V6 row132 外购猪肉产品录入的耳号候选）。
     *
     * <p>口径：{@code out_time} 落在今天的白条 —— 三条出库路径（发货领用 out_method=1 /
     * 分割间 out_method=2 / 后台出库 out_method=3）都在出库那一刻写 {@code out_time}，
     * 所以只认这个字段、不按 out_method 过滤。外购白条无耳号（{@code ear_no} 为空）自然被排除。</p>
     *
     * <p>用半开区间 {@code [今天, 明天)} 而非 {@code DATE(out_time)=CURDATE()}，让
     * {@code out_time} 上的范围条件仍可走索引。同一耳号当天多次出库（整猪两半只）只回一条，
     * 按最近一次出库时间倒序。</p>
     *
     * <p>显式 {@code tenant_id='1001' AND del_flag='0'}（V1 单租户，原生 SQL 不自动注入）。</p>
     *
     * @return 今日出库耳号（无则空列表，mp 下拉显示「今日暂无白条出库」）
     */
    @Select("""
        SELECT ear_no
          FROM t_warehouse_bar_info
         WHERE tenant_id = '1001'
           AND del_flag = '0'
           AND ear_no IS NOT NULL
           AND ear_no <> ''
           AND out_time >= CURDATE()
           AND out_time < CURDATE() + INTERVAL 1 DAY
         GROUP BY ear_no
         ORDER BY MAX(out_time) DESC
        """)
    List<String> selectTodayOutEarNos();

    /**
     * 分页查「当天确认收货白条」（FIX-STORE-TRACE-BAR-001 门店猪肉追溯 picker 口径）。
     *
     * <p>口径：{@code status='in_stock'}（已入库 / 确认收货）且 {@code DATE(in_time)=CURDATE()} 的白条，
     * 含外购（bar_info 即白条主表，不按业态过滤）。按 in_time DESC 排序（最新入库排前）。
     * 当天无白条入库 → 空结果（picker 显示「暂无当天确认收货白条」，属正常态非 bug）。</p>
     *
     * <p>显式 {@code tenant_id='1001' AND del_flag='0'}（V1 单租户，原生 SQL 不自动注入）。</p>
     *
     * @param page 分页参数（MP 分页拦截器填充 total / 切片）
     * @return 当天 in_stock 白条分页（id / barId / earNo / inWeight / inTime / status）
     */
    @Select("""
        SELECT id, bar_id AS barId, ear_no AS earNo, in_weight AS inWeight, in_time AS inTime, status
          FROM t_warehouse_bar_info
         WHERE tenant_id = '1001'
           AND del_flag = '0'
           AND status = 'in_stock'
           AND DATE(in_time) = CURDATE()
         ORDER BY in_time DESC, id DESC
        """)
    IPage<TodayBarVo> selectTodayInStockBarPage(IPage<TodayBarVo> page);

    /**
     * 按白条业务码查白条状态（外购猪只删除拦截用：录入回写的 bar_id 反查镜像白条当前态）。
     *
     * <p>外购猪只台账删除前据此判断关联白条是否已进入下游处理流程（非 {@code pending_singe}）。
     * 显式 {@code tenant_id='1001' AND del_flag='0'}（V1 单租户，原生 SQL 不自动注入）。
     * bar_id 与白条 1:1（业务码 UNIQUE），返回单值；白条不存在 → null。</p>
     *
     * @param barId 白条业务码（= {@code t_warehouse_bar_info.bar_id}）
     * @return 白条状态（{@code djs_bar_status}）；无匹配白条返 null
     */
    @Select("""
        SELECT status
          FROM t_warehouse_bar_info
         WHERE tenant_id = '1001'
           AND del_flag = '0'
           AND bar_id = #{barId}
         LIMIT 1
        """)
    String selectStatusByBarId(@Param("barId") String barId);

    /**
     * 级联软删某外购猪只镜像出的待燎毛白条（仅 pending_singe 未进下游的才删）。
     * 显式 tenant_id='1001' AND del_flag='0'（V1 单租户，原生 SQL 不自动注入）；
     * del_unique=id 对齐软删唯一约束规范。
     */
    @Update("UPDATE t_warehouse_bar_info SET del_flag='1', del_unique=id, update_by=#{userId}, update_time=NOW()"
        + " WHERE tenant_id='1001' AND del_flag='0' AND bar_id=#{barId} AND status='pending_singe'")
    int softDeleteByBarIdIfPending(@Param("barId") String barId, @Param("userId") Long userId);

    /**
     * 按白条业务码批量查燎毛称重时刻（{@code in_time}）。外购猪只列表「到场时间」据此回填
     * （FIX-WMS-OUTSOURCE-001 行38：外购到场时间 = 燎毛间称重完成时刻，精确到时分秒，
     * 取代新增表单手填日期导致整列 00:00:00）。
     *
     * <p>显式 {@code tenant_id='1001' AND del_flag='0'}（V1 单租户，原生 SQL 不自动注入）。
     * 返回 Map：barId → in_time（{@code NULL} 的白条 MyBatis 默认不放入 Map，调用方按缺失处理）。</p>
     *
     * @param barIds 白条业务码集合（{@code t_warehouse_bar_info.bar_id}）
     * @return barId → in_time（仅含 in_time 非空的白条；空集合传入由调用方前置短路）
     */
    @MapKey("barId")
    @Select("""
        <script>
        SELECT bar_id AS barId, in_time AS inTime
          FROM t_warehouse_bar_info
         WHERE tenant_id = '1001'
           AND del_flag = '0'
           AND in_time IS NOT NULL
           AND bar_id IN
           <foreach collection="barIds" item="bid" open="(" separator="," close=")">#{bid}</foreach>
        </script>
        """)
    Map<String, Map<String, Object>> selectInTimeByBarIds(@Param("barIds") Collection<String> barIds);

    /**
     * 查全部「在库」白条（mp 物资领用·白条批次卡列表 issueWhiteBarBatches）。
     *
     * <p>口径 = {@code status='in_stock'}（已入库 / 可领，一行 = 一条实物白条整只），不限当天（与
     * {@link #selectTodayInStockBarPage} 的区别 = 去掉 {@code DATE(in_time)=CURDATE()} 过滤）。
     * 复用 {@link TodayBarVo} 作行类型（id / barId / earNo / inWeight / inTime / status）；
     * 预冷时长由 service 按 {@code in_time} 实时计算，不读 {@code acid_remove_time}（cut_done 前恒 NULL）。
     * 按 in_time DESC, id DESC 排序（最新入库排前）。无在库白条 → 空 list（前端 graceful empty）。</p>
     *
     * <p>显式 {@code tenant_id='1001' AND del_flag='0'}（V1 单租户，原生 SQL 不自动注入）。</p>
     *
     * @return 在库白条 minimal 行（id / barId / earNo / inWeight / inTime / status）
     */
    @Select("""
        SELECT id, bar_id AS barId, ear_no AS earNo, in_weight AS inWeight, in_time AS inTime, status
          FROM t_warehouse_bar_info
         WHERE tenant_id = '1001'
           AND del_flag = '0'
           AND status = 'in_stock'
         ORDER BY in_time DESC, id DESC
        """)
    java.util.List<TodayBarVo> selectInStockBars();

}
