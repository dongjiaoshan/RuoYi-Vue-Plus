package org.dromara.djs.store.operation.listener;

import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.event.AnalysisEventListener;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.excel.core.ExcelListener;
import org.dromara.common.excel.core.ExcelResult;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.store.operation.domain.vo.StoreSaleImportVo;
import org.dromara.djs.store.operation.service.IStoreSaleRecordService;

import java.util.ArrayList;
import java.util.List;

/**
 * 门店销售流水 Excel 导入监听器（STR-OP-001）。
 *
 * <p>逐行解析收集到 {@link #rows}，{@link #getExcelResult()} 触发 service
 * {@link IStoreSaleRecordService#importRows} 整批校验入库（事务原子：任一行校验失败整批回滚，
 * 错误消息回传前端 toast，不静默吞）。{@code source} 由 service 固定写 {@code excel_import}。</p>
 *
 * @author djs
 * @since STR-OP-001
 */
@Slf4j
public class StoreSaleImportListener extends AnalysisEventListener<StoreSaleImportVo>
    implements ExcelListener<StoreSaleImportVo> {

    private final IStoreSaleRecordService saleRecordService;

    private final Long storeId;

    private final Long operatorId;

    private final List<StoreSaleImportVo> rows = new ArrayList<>();

    public StoreSaleImportListener(Long storeId) {
        this.saleRecordService = SpringUtils.getBean(IStoreSaleRecordService.class);
        this.storeId = storeId;
        this.operatorId = LoginHelper.getUserId();
    }

    @Override
    public void invoke(StoreSaleImportVo row, AnalysisContext context) {
        rows.add(row);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 解析阶段不入库，由 getExcelResult() 触发整批事务校验入库
    }

    @Override
    public ExcelResult<StoreSaleImportVo> getExcelResult() {
        return new ExcelResult<>() {

            @Override
            public String getAnalysis() {
                if (storeId == null) {
                    throw new org.dromara.common.core.exception.ServiceException("请先选择目标门店");
                }
                int imported = saleRecordService.importRows(storeId, rows, operatorId);
                return "导入成功，共 " + imported + " 条销售流水（数据来源：Excel导入）";
            }

            @Override
            public List<StoreSaleImportVo> getList() {
                return rows;
            }

            @Override
            public List<String> getErrorList() {
                return List.of();
            }
        };
    }
}
