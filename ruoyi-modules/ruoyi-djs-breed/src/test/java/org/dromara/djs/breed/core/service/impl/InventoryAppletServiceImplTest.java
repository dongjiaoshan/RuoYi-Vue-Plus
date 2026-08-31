package org.dromara.djs.breed.core.service.impl;

import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.vo.InventoryBarnMatrixVo;
import org.dromara.djs.breed.core.domain.vo.InventoryDistItemVo;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.breeding.mapper.PigBreedingMapper;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.production.domain.vo.FattenAgeStageVo;
import org.dromara.djs.breed.production.service.IFattenAgeStageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 库存看板聚合（mp「猪只库存信息」+ admin「育肥猪信息」共用）happy path 单测。
 *
 * <p>覆盖 V6-R150 admin 页依赖的两个方法：{@code ageDist} 日龄分桶、{@code barnMatrix} 栋舍 × 日龄段矩阵，
 * 断言两者段标签与段序完全一致（甲方要求 admin 柱状图与列表列一一对应）。</p>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryAppletServiceImpl 日龄分布 / 栋舍矩阵测试")
class InventoryAppletServiceImplTest {

    @Mock
    private PigMapper pigMapper;
    @Mock
    private BarnMapper barnMapper;
    @Mock
    private IFattenAgeStageService fattenAgeStageService;
    @Mock
    private PigBreedingMapper pigBreedingMapper;
    @Mock
    private IPigCoreService pigCoreService;

    private InventoryAppletServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InventoryAppletServiceImpl(pigMapper, barnMapper, fattenAgeStageService,
            pigBreedingMapper, pigCoreService);
    }

    private static FattenAgeStageVo stage(int start, int end) {
        FattenAgeStageVo vo = new FattenAgeStageVo();
        vo.setStartAge(start);
        vo.setEndAge(end);
        return vo;
    }

    private static Pig fatteningPig(long id, Long barnId, int ageDays) {
        Pig p = new Pig();
        p.setId(id);
        p.setPigType("fattening");
        p.setBarnId(barnId);
        p.setBirthDate(LocalDate.now().minusDays(ageDays));
        p.setDelFlag("0");
        return p;
    }

    private static Pig withStatus(Pig p, String status) {
        p.setCurrentStatus(status);
        return p;
    }

    private static Pig sowPig(long id, Long barnId, int parity, String status) {
        Pig p = new Pig();
        p.setId(id);
        p.setPigType("sow");
        p.setBarnId(barnId);
        p.setParity(parity);
        p.setCurrentStatus(status);
        p.setDelFlag("0");
        return p;
    }

    /** tab 头数 / 表格合计行「合计」列 = 各栋在栏数之和（前端两处都是这个公式）。 */
    private static int totalOf(List<InventoryBarnMatrixVo> matrix) {
        return matrix.stream().mapToInt(InventoryBarnMatrixVo::getCount).sum();
    }

    /**
     * 锁死甲方验收时并排比对的两条恒等式（V6-R150 row150 缺陷：柱图 952 / 表格 929）：
     * ① 柱状图各柱之和 = 表格各栋在栏数之和；
     * ② 逐段：柱高 = 表格该列各栋之和（点柱过滤后柱上数字与列合计并排出现）。
     */
    private static void assertConsistent(List<InventoryDistItemVo> dist, List<InventoryBarnMatrixVo> matrix) {
        assertThat(dist.stream().mapToInt(InventoryDistItemVo::getCount).sum())
            .as("柱状图合计应等于栋舍表格合计")
            .isEqualTo(totalOf(matrix));
        for (InventoryDistItemVo item : dist) {
            int columnSum = matrix.stream()
                .mapToInt(vo -> vo.getByAge().getOrDefault(item.getLabel(), 0))
                .sum();
            assertThat(columnSum)
                .as("日龄段「%s」的柱高应等于表格该列合计", item.getLabel())
                .isEqualTo(item.getCount());
        }
    }

    private static Barn barn(long id, String name) {
        Barn b = new Barn();
        b.setId(id);
        b.setBarnName(name);
        return b;
    }

    @Test
    @DisplayName("育肥猪日龄分布按后台阶段配置分桶，末段为「X日以上」兜底")
    void ageDist_fattening_uses_configured_stages_plus_overflow_bucket() {
        when(fattenAgeStageService.queryList()).thenReturn(List.of(stage(26, 42), stage(43, 70)));
        List<Pig> pigs = new ArrayList<>();
        pigs.add(fatteningPig(1L, 10L, 30));   // 26-42日
        pigs.add(fatteningPig(2L, 10L, 40));   // 26-42日
        pigs.add(fatteningPig(3L, 11L, 50));   // 43-70日
        pigs.add(fatteningPig(4L, 11L, 200));  // 70日以上
        when(pigMapper.selectList(any())).thenReturn(pigs);

        List<InventoryDistItemVo> dist = service.ageDist("fattening");

        assertThat(dist).extracting(InventoryDistItemVo::getLabel)
            .containsExactly("26-42日", "43-70日", "70日以上");
        assertThat(dist).extracting(InventoryDistItemVo::getCount)
            .containsExactly(2, 1, 1);
    }

    @Test
    @DisplayName("育肥猪栋舍矩阵 byAge 段标签与段序和日龄分布图完全一致，且逐栋计数正确")
    void barnMatrix_fattening_byAge_matches_ageDist_labels() {
        when(fattenAgeStageService.queryList()).thenReturn(List.of(stage(26, 42), stage(43, 70)));
        List<Pig> pigs = new ArrayList<>();
        pigs.add(fatteningPig(1L, 10L, 30));
        pigs.add(fatteningPig(2L, 10L, 40));
        pigs.add(fatteningPig(3L, 11L, 50));
        pigs.add(fatteningPig(4L, 11L, 200));
        when(pigMapper.selectList(any())).thenReturn(pigs);
        when(barnMapper.selectBatchIds(any())).thenReturn(List.of(barn(10L, "1栋"), barn(11L, "2栋")));

        List<InventoryBarnMatrixVo> matrix = service.barnMatrix("fattening");

        assertThat(matrix).hasSize(2);
        assertThat(matrix).extracting(InventoryBarnMatrixVo::getBarnName)
            .containsExactlyInAnyOrder("1栋", "2栋");
        // 段序与 ageDist 一致（LinkedHashMap 保序），且 0 段也保留，保证 admin 表格各栋列位对齐
        for (InventoryBarnMatrixVo vo : matrix) {
            assertThat(vo.getByAge().keySet())
                .containsExactly("26-42日", "43-70日", "70日以上");
        }
        InventoryBarnMatrixVo b1 = matrix.stream().filter(v -> "1栋".equals(v.getBarnName())).findFirst().orElseThrow();
        assertThat(b1.getCount()).isEqualTo(2);
        assertThat(b1.getByAge()).containsEntry("26-42日", 2).containsEntry("43-70日", 0).containsEntry("70日以上", 0);
        InventoryBarnMatrixVo b2 = matrix.stream().filter(v -> "2栋".equals(v.getBarnName())).findFirst().orElseThrow();
        assertThat(b2.getCount()).isEqualTo(2);
        assertThat(b2.getByAge()).containsEntry("26-42日", 0).containsEntry("43-70日", 1).containsEntry("70日以上", 1);
    }

    @Test
    @DisplayName("离场 END 猪在日龄分布与栋舍矩阵里同时被排除（柱图合计 = 表格合计 = tab 头数）")
    void ageDist_and_barnMatrix_exclude_end_pigs_identically() {
        when(fattenAgeStageService.queryList()).thenReturn(List.of(stage(26, 42), stage(43, 70)));
        List<Pig> pigs = new ArrayList<>();
        pigs.add(fatteningPig(1L, 10L, 30));
        pigs.add(fatteningPig(2L, 10L, 40));
        pigs.add(fatteningPig(3L, 11L, 50));
        pigs.add(fatteningPig(4L, 11L, 200));
        // 出栏/死亡/淘汰：全落末段（与 staging row150 实测一致，23 头 END 全在「250日以上」）
        pigs.add(withStatus(fatteningPig(5L, 11L, 210), "END"));
        pigs.add(withStatus(fatteningPig(6L, 11L, 220), "END"));
        when(pigMapper.selectList(any())).thenReturn(pigs);
        when(barnMapper.selectBatchIds(any())).thenReturn(List.of(barn(10L, "1栋"), barn(11L, "2栋")));

        List<InventoryDistItemVo> dist = service.ageDist("fattening");
        List<InventoryBarnMatrixVo> matrix = service.barnMatrix("fattening");

        // END 不进柱图：末段仍是 1（4 号），不是 3
        assertThat(dist).extracting(InventoryDistItemVo::getLabel)
            .containsExactly("26-42日", "43-70日", "70日以上");
        assertThat(dist).extracting(InventoryDistItemVo::getCount)
            .containsExactly(2, 1, 1);
        assertThat(totalOf(matrix)).isEqualTo(4);
        assertConsistent(dist, matrix);
    }

    @Test
    @DisplayName("缺栋舍 / 缺出生日期 / 出生日期在未来的脏数据，两处口径一致地排除")
    void ageDist_and_barnMatrix_exclude_unplaceable_pigs_identically() {
        when(fattenAgeStageService.queryList()).thenReturn(List.of(stage(26, 42), stage(43, 70)));
        List<Pig> pigs = new ArrayList<>();
        pigs.add(fatteningPig(1L, 10L, 30));
        pigs.add(fatteningPig(2L, 11L, 50));
        pigs.add(fatteningPig(3L, null, 60));                       // 未分栋舍
        Pig noBirth = fatteningPig(4L, 10L, 30);
        noBirth.setBirthDate(null);                                 // 无出生日期
        pigs.add(noBirth);
        Pig future = fatteningPig(5L, 10L, 30);
        future.setBirthDate(LocalDate.now().plusDays(3));           // 出生日期在未来
        pigs.add(future);
        when(pigMapper.selectList(any())).thenReturn(pigs);
        when(barnMapper.selectBatchIds(any())).thenReturn(List.of(barn(10L, "1栋"), barn(11L, "2栋")));

        List<InventoryDistItemVo> dist = service.ageDist("fattening");
        List<InventoryBarnMatrixVo> matrix = service.barnMatrix("fattening");

        assertThat(totalOf(matrix)).isEqualTo(2);
        assertConsistent(dist, matrix);
    }

    @Test
    @DisplayName("母猪胎次分布与栋舍矩阵同一套在栏口径：离场母猪不进饼图也不进在栏数")
    void parityDist_and_barnMatrix_exclude_end_sows_identically() {
        List<Pig> pigs = new ArrayList<>();
        pigs.add(sowPig(1L, 10L, 2, "PZ"));
        pigs.add(sowPig(2L, 10L, 2, "FM"));
        pigs.add(sowPig(3L, 11L, 5, "PZ"));
        pigs.add(sowPig(4L, 11L, 5, "END"));
        when(pigMapper.selectList(any())).thenReturn(pigs);
        when(barnMapper.selectBatchIds(any())).thenReturn(List.of(barn(10L, "1栋"), barn(11L, "2栋")));

        List<InventoryDistItemVo> parity = service.parityDist("sow");
        List<InventoryBarnMatrixVo> matrix = service.barnMatrix("sow");

        assertThat(parity).extracting(InventoryDistItemVo::getLabel).containsExactly("2胎", "5胎");
        assertThat(parity).extracting(InventoryDistItemVo::getCount).containsExactly(2, 1);
        // 饼图各扇之和 = 卡头「种母猪在栏数」
        assertThat(parity.stream().mapToInt(InventoryDistItemVo::getCount).sum())
            .isEqualTo(totalOf(matrix));
    }

    @Test
    @DisplayName("仔猪日龄分布走固定 6 段口径，不读育肥阶段配置")
    void ageDist_piglet_uses_fixed_six_buckets() {
        List<Pig> pigs = new ArrayList<>();
        Pig p1 = fatteningPig(1L, 10L, 3);
        p1.setPigType("piglet");
        Pig p2 = fatteningPig(2L, 10L, 30);
        p2.setPigType("piglet");
        pigs.add(p1);
        pigs.add(p2);
        when(pigMapper.selectList(any())).thenReturn(pigs);

        List<InventoryDistItemVo> dist = service.ageDist("piglet");

        assertThat(dist).extracting(InventoryDistItemVo::getLabel)
            .containsExactly("1-5日", "6-10日", "11-15日", "16-20日", "21-25日", "25日以上");
        assertThat(dist).extracting(InventoryDistItemVo::getCount)
            .containsExactly(1, 0, 0, 0, 0, 1);
    }
}
