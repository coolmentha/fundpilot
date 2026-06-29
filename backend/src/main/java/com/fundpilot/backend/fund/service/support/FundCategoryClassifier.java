package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;

import java.util.Optional;
import java.util.Set;

/**
 * 基金类型({@link FundCategory})启发式分类器(ADR-0005):按基金名称识别宽基/行业/主动/混合。
 * <p>与 {@link FundSubTypeClassifier} 正交——后者识别数据源维度(ETF/INDEX/ACTIVE),
 * 本类识别策略参数维度(宽基/行业/主动/混合),后者决定默认档位和硬约束上限。
 *
 * <h3>识别规则(尽力填 + 可覆盖)</h3>
 * <ol>
 *   <li>指数类基金(ETF/INDEX/INDEX_ENHANCED):
 *     <ul>
 *       <li>名称含宽基指数词(沪深300/中证500/创业板/上证50/科创50/中证1000) → {@link FundCategory#BROAD_BASE}</li>
 *       <li>名称含行业词(半导体/医药/新能源 等) → {@link FundCategory#SECTOR}</li>
 *       <li>两者都没命中 → {@link FundCategory#BROAD_BASE}(兜底,指数基金宽基居多)</li>
 *     </ul>
 *   </li>
 *   <li>主动类基金(ACTIVE):
 *     <ul>
 *       <li>名称含"混合/灵活配置/平衡" → {@link FundCategory#MIXED}</li>
 *       <li>否则 → {@link FundCategory#ACTIVE}</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>识别不准时填最可能值,用户可在编辑时手动改,不留痕(沿用"override 不留痕"已确认条款)。
 * 不阻塞建仓流程,符合"所有信号都是提示"精神。
 *
 * <p>{@code rawName}(东方财富"混合型-灵活"/"指数型-股票"等资产类别描述)目前<b>不参与判定</b>——
 * 本框架按基金名称的关键词识别已够用;rawName 的价值在于能识别债券型/货币型等本框架不覆盖的基金,
 * 留作后续过滤优化的输入。
 */
public final class FundCategoryClassifier {

    /** 行业关键词(命中即判 SECTOR)。覆盖主流行业主题基金。 */
    private static final Set<String> SECTOR_KEYWORDS = Set.of(
            "半导体", "芯片", "医药", "医疗", "生物", "创新药", "中药",
            "新能源", "光伏", "锂电", "电池", "新能源车", "汽车",
            "消费", "食品", "白酒", "饮料", "家电",
            "军工", "国防", "航天",
            "银行", "证券", "金融", "保险",
            "地产", "房地产", "建材",
            "科技", "人工智能", "AI", "云计算", "5G", "通信", "传媒", "游戏",
            "有色", "钢铁", "煤炭", "化工", "石油", "环保", "电力",
            "基建", "机械", "制造"
    );

    /** 主动类基金判 MIXED 的关键词。 */
    private static final Set<String> MIXED_KEYWORDS = Set.of(
            "混合", "灵活配置", "平衡", "稳健", "配置"
    );

    private FundCategoryClassifier() {
    }

    /**
     * 按基金名称 + 子类型识别 {@link FundCategory}。
     *
     * @param fundName    基金名称
     * @param fundSubType 已识别的子类型(决定走指数类路径还是主动类路径)
     * @return 识别出的基金类型;入参为空时按主动类兜底 ACTIVE
     */
    public static FundCategory classify(String fundName, FundSubType fundSubType) {
        if (fundSubType == FundSubType.ACTIVE) {
            return classifyActive(fundName);
        }
        // ETF/INDEX/INDEX_ENHANCED 走指数类路径
        return classifyIndex(fundName);
    }

    /** 指数类基金:宽基指数词优先,其次行业词,都没命中兜底宽基。 */
    private static FundCategory classifyIndex(String fundName) {
        if (fundName == null || fundName.isBlank()) {
            return FundCategory.BROAD_BASE;
        }
        // 宽基指数词命中(复用 BenchmarkIndexTable 的 6 个核心指数)
        Optional<String> indexCode = BenchmarkIndexTable.lookup(fundName);
        if (indexCode.isPresent()) {
            return FundCategory.BROAD_BASE;
        }
        // 行业词命中
        for (String kw : SECTOR_KEYWORDS) {
            if (fundName.contains(kw)) {
                return FundCategory.SECTOR;
            }
        }
        // 兜底:指数基金宽基居多
        return FundCategory.BROAD_BASE;
    }

    /** 主动类基金:名称含"混合/灵活配置/平衡" → MIXED,否则 ACTIVE。 */
    private static FundCategory classifyActive(String fundName) {
        if (fundName == null || fundName.isBlank()) {
            return FundCategory.ACTIVE;
        }
        for (String kw : MIXED_KEYWORDS) {
            if (fundName.contains(kw)) {
                return FundCategory.MIXED;
            }
        }
        return FundCategory.ACTIVE;
    }
}
