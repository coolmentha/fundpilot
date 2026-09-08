package com.fundpilot.backend.productcatalog.adapter.web.research;

import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.productcatalog.application.query.research.FundResearchQueryHandler;
import com.fundpilot.backend.productcatalog.application.query.research.FundResearchQueryHandler.HoldingsResult;
import com.fundpilot.backend.productcatalog.application.query.research.FundResearchQueryHandler.IndustryResult;
import com.fundpilot.backend.productcatalog.application.query.research.FundResearchQueryHandler.ProfileResult;
import com.fundpilot.backend.productcatalog.application.query.research.FundResearchQueryHandler.ScaleResult;
import com.fundpilot.backend.productcatalog.application.query.research.FundResearchQueryHandler.SnapshotResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "基金研究资料接口", description = "只读本地公开资料快照")
@RestController
@RequestMapping("/api/products/{fundCode}/research")
@RequiredArgsConstructor
public class FundResearchController {
    private final FundResearchQueryHandler queries;

    @Operation(summary = "查询基金研究资料，不触发外部采集")
    @GetMapping
    public ApiResponse<ResearchResponse> get(@PathVariable String fundCode) {
        return ApiResponse.ok(queries.find(fundCode).map(ResearchResponse::from).orElse(null));
    }

    public record ResearchResponse(String fundCode, SnapshotResult<ProfileResult> profile,
                                   SnapshotResult<ScaleResult> scale,
                                   SnapshotResult<HoldingsResult> holdings,
                                   SnapshotResult<IndustryResult> industry) {
        static ResearchResponse from(FundResearchQueryHandler.Result result) {
            return new ResearchResponse(result.fundCode(), result.profile(), result.scale(),
                    result.holdings(), result.industry());
        }
    }
}
