package com.fundpilot.backend.fund.repository;

import com.fundpilot.backend.fund.entity.FundDictEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 基金字典 Repository(ADR-0005)。搜索框自动补全查这张表。
 * <p>模糊检索按 fund_code 前缀或 fund_name 包含匹配,取前 20 条(防止全量返回)。
 */
public interface FundDictRepository extends JpaRepository<FundDictEntity, Long> {

    Optional<FundDictEntity> findByFundCode(String fundCode);

    /**
     * 搜索:按 code 前缀或 name 包含匹配,最多返回 limit 条。
     * <p>用 LOWER 做 ASCII 大小写归一(基金名是中文,大小写主要影响 code 里的字母)。
     * 参数 limit 由调用方传入(搜索框传 20)。
     */
    @Query("SELECT d FROM FundDictEntity d " +
            "WHERE LOWER(d.fundCode) LIKE LOWER(CONCAT(:q, '%')) " +
            "   OR LOWER(d.fundName) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "ORDER BY d.fundCode")
    List<FundDictEntity> search(@Param("q") String q);
}
