package org.dromara.djs.warehouse.veg.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.veg.domain.FeedAnimal;
import org.dromara.djs.warehouse.veg.domain.FeedDailyConfirm;
import org.dromara.djs.warehouse.veg.domain.bo.FeedDailyConfirmBo;
import org.dromara.djs.warehouse.veg.domain.bo.FeedRecordQuery;
import org.dromara.djs.warehouse.veg.domain.vo.FeedDailyVo;
import org.dromara.djs.warehouse.veg.domain.vo.FeedRecordVo;
import org.dromara.djs.warehouse.veg.mapper.FeedDailyConfirmMapper;
import org.dromara.djs.warehouse.veg.mapper.FeedLogMapper;
import org.dromara.djs.warehouse.veg.service.IFeedRecordService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 有机饲喂记录 Service 实现（WMS-FEED-RECORD-001，仓库-admin 行21）。
 *
 * @author djs
 * @since WMS-FEED-RECORD-001
 */
@Service
@RequiredArgsConstructor
public class FeedRecordServiceImpl implements IFeedRecordService {

    private final FeedLogMapper feedLogMapper;
    private final FeedDailyConfirmMapper confirmMapper;

    @Override
    public TableDataInfo<FeedRecordVo> queryPage(FeedRecordQuery query, PageQuery pageQuery) {
        // query 可能为 null（无任何筛选项时），统一兜底空对象，避免 mapper <if> NPE
        FeedRecordQuery q = query != null ? query : new FeedRecordQuery();
        // V1 固定租户 '1001'；自定义 JOIN @Select 不走拦截器自动注入，显式取当前租户传入 WHERE。
        String tenantId = TenantHelper.getTenantId();
        IPage<FeedRecordVo> page = feedLogMapper.selectRecordPage(pageQuery.build(), tenantId, q);
        return TableDataInfo.build(page);
    }

    @Override
    public List<FeedRecordVo> queryList(FeedRecordQuery query) {
        FeedRecordQuery q = query != null ? query : new FeedRecordQuery();
        String tenantId = TenantHelper.getTenantId();
        return feedLogMapper.selectRecordList(tenantId, q);
    }

    @Override
    public TableDataInfo<FeedDailyVo> queryDailyPage(FeedRecordQuery query, PageQuery pageQuery) {
        FeedRecordQuery q = query != null ? query : new FeedRecordQuery();
        String tenantId = TenantHelper.getTenantId();
        IPage<FeedDailyVo> page = feedLogMapper.selectDailyPage(pageQuery.build(), tenantId, q);
        return TableDataInfo.build(page);
    }

    @Override
    public void saveDailyConfirm(FeedDailyConfirmBo bo) {
        // 每次只写一行，INSERT / UPDATE 各自原子提交；不跨查询保持事务快照。
        // 并发首次 INSERT 撞唯一键后，重新查询必须能看到另一请求刚提交的确认行。
        LocalDate date = bo.getFeedDate();
        // 不传 = 猪只饲料：mp 有机饲喂记录只管猪（V7 行18），老 mp 包也不带这个字段
        String animal = StringUtils.isBlank(bo.getFeedAnimal()) ? FeedAnimal.PIG : bo.getFeedAnimal();
        FeedDailyConfirm exist = selectConfirm(date, animal);
        if (exist == null) {
            FeedDailyConfirm row = new FeedDailyConfirm();
            row.setFeedDate(date);
            row.setFeedAnimal(animal);
            row.setBoxCount(bo.getBoxCount());
            row.setConfirmUserId(bo.getConfirmUserId());
            row.setConfirmTime(new Date());
            row.setDelUnique(0L);
            // 同日同类型并发首次录入：唯一键 uk_feed_date_animal 兜底，撞了就退化成更新那条已存在的行
            try {
                confirmMapper.insert(row);
                return;
            } catch (DuplicateKeyException e) {
                exist = selectConfirm(date, animal);
                if (exist == null) {
                    throw e;
                }
            }
        }
        // 该日已有确认行：框数 / 确认人 / 确认时间一起刷成最后一次操作的结果。
        // 正常业务走不到这里——mp 只对未录入的日期放出入口；留着是给并发首次录入兜底。
        exist.setBoxCount(bo.getBoxCount());
        exist.setConfirmUserId(bo.getConfirmUserId());
        exist.setConfirmTime(new Date());
        confirmMapper.updateById(exist);
    }

    private FeedDailyConfirm selectConfirm(LocalDate date, String animal) {
        return confirmMapper.selectOne(new LambdaQueryWrapper<FeedDailyConfirm>()
            .eq(FeedDailyConfirm::getFeedDate, date)
            .eq(FeedDailyConfirm::getFeedAnimal, animal));
    }
}
