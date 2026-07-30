package com.fundpilot.backend.discipline.adapter.api.classification;

import com.fundpilot.backend.discipline.application.command.classification.DisciplineClassificationCommandHandler;
import com.fundpilot.backend.discipline.application.query.classification.DisciplineClassificationQueryHandler;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 其他模块读取用户最终纪律分类的公开契约。 */
@Component
@RequiredArgsConstructor
public class DisciplineClassificationApi {
    private final DisciplineClassificationCommandHandler commands;
    private final DisciplineClassificationQueryHandler queries;

    public void set(SetClassification request) {
        commands.set(request.ownerId(), request.portfolioFundId(), request.category().name(), request.source().name());
    }

    public List<Classification> findByPortfolioFundIds(long ownerId, Set<Long> portfolioFundIds) {
        return queries.findByPortfolioFundIds(ownerId, portfolioFundIds).stream()
                .map(value -> new Classification(value.portfolioFundId(), value.category()))
                .toList();
    }

    public record Classification(long portfolioFundId, String category) {
    }

    public record SetClassification(long ownerId, long portfolioFundId, Category category, Source source) {
    }

    public enum Category { BROAD_BASE, SECTOR, ACTIVE, MIXED }
    public enum Source { DEFAULT_SUGGESTION, USER_CONFIRMED, USER_CUSTOMIZED }
}
