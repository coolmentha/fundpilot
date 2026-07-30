package com.fundpilot.backend.portfolio.infrastructure.persistence.fundgroup;

import com.fundpilot.backend.portfolio.domain.fundgroup.FundGroup;
import com.fundpilot.backend.portfolio.domain.fundgroup.FundGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
@RequiredArgsConstructor
class FundGroupRepositoryImpl implements FundGroupRepository {
    private final JdbcTemplate jdbc;

    @Override
    public List<FundGroup> findByOwnerId(long ownerId) {
        return jdbc.query("""
                SELECT id, owner_id, name, sort_order
                FROM fund_group
                WHERE owner_id = ? AND deleted_date IS NULL
                ORDER BY sort_order, id
                """, FundGroupRepositoryImpl::mapGroup, ownerId);
    }

    @Override
    public List<FundGroup> replace(long ownerId, List<FundGroup> requested) {
        List<FundGroup> existing = findByOwnerId(ownerId);
        Map<Long, FundGroup> existingById = new HashMap<>();
        existing.forEach(group -> existingById.put(group.id(), group));
        Set<Long> retainedIds = new HashSet<>();
        requested.stream().map(FundGroup::id).filter(java.util.Objects::nonNull)
                .forEach(retainedIds::add);

        List<Long> deletedIds = existing.stream().map(FundGroup::id)
                .filter(id -> !retainedIds.contains(id)).toList();
        deleteGroups(ownerId, deletedIds);

        List<FundGroup> saved = new ArrayList<>();
        for (FundGroup group : requested) {
            if (group.id() == null) {
                Long id = jdbc.queryForObject("""
                        INSERT INTO fund_group
                            (version, created_date, updated_date, owner_id, name, sort_order)
                        VALUES (0, now(), now(), ?, ?, ?)
                        RETURNING id
                        """, Long.class, ownerId, group.name(), group.sortOrder());
                saved.add(new FundGroup(id, ownerId, group.name(), group.sortOrder()));
            } else {
                int updated = jdbc.update("""
                        UPDATE fund_group
                        SET name = ?, sort_order = ?, updated_date = now(), version = COALESCE(version, 0) + 1
                        WHERE id = ? AND owner_id = ? AND deleted_date IS NULL
                        """, group.name(), group.sortOrder(), group.id(), ownerId);
                if (updated != 1 || !existingById.containsKey(group.id())) {
                    throw new IllegalStateException("分组在保存期间发生变化: " + group.id());
                }
                saved.add(group);
            }
        }
        return saved;
    }

    @Override
    public List<GroupSummary> summarize(long ownerId) {
        return jdbc.query("""
                SELECT g.id, g.name, g.sort_order, count(pf.id) AS portfolio_fund_count
                FROM fund_group g
                LEFT JOIN portfolio_fund_group_member member ON member.group_id = g.id
                LEFT JOIN portfolio_fund pf ON pf.id = member.portfolio_fund_id
                    AND pf.owner_id = g.owner_id AND pf.validity = 'TRACKED'
                WHERE g.owner_id = ? AND g.deleted_date IS NULL
                GROUP BY g.id, g.name, g.sort_order
                ORDER BY g.sort_order, g.id
                """, (rs, rowNum) -> new GroupSummary(
                rs.getLong("id"), rs.getString("name"), rs.getInt("sort_order"),
                rs.getLong("portfolio_fund_count")), ownerId);
    }

    @Override
    public List<GroupMembership> memberships(long ownerId) {
        return jdbc.query("""
                SELECT member.portfolio_fund_id, g.id AS group_id, g.name, g.sort_order
                FROM portfolio_fund_group_member member
                JOIN fund_group g ON g.id = member.group_id
                JOIN portfolio_fund pf ON pf.id = member.portfolio_fund_id
                WHERE pf.owner_id = ? AND g.owner_id = ? AND g.deleted_date IS NULL
                ORDER BY member.portfolio_fund_id, g.sort_order, g.id
                """, (rs, rowNum) -> new GroupMembership(rs.getLong("portfolio_fund_id"),
                rs.getLong("group_id"), rs.getString("name"), rs.getInt("sort_order")), ownerId, ownerId);
    }

    @Override
    public void assignByNames(long ownerId, long portfolioFundId, Long legacyFundId,
                              List<String> names) {
        Map<String, FundGroup> existingByKey = new HashMap<>();
        List<FundGroup> existing = findByOwnerId(ownerId);
        existing.forEach(group -> existingByKey.put(group.normalizedKey(), group));
        int nextOrder = existing.size();
        List<Long> groupIds = new ArrayList<>();
        for (String name : names) {
            String key = name.toLowerCase(java.util.Locale.ROOT);
            FundGroup group = existingByKey.get(key);
            if (group == null) {
                Long id = jdbc.queryForObject("""
                        INSERT INTO fund_group
                            (version, created_date, updated_date, owner_id, name, sort_order)
                        VALUES (0, now(), now(), ?, ?, ?)
                        RETURNING id
                        """, Long.class, ownerId, name, nextOrder++);
                group = new FundGroup(id, ownerId, name, nextOrder - 1);
                existingByKey.put(key, group);
            }
            groupIds.add(group.id());
        }

        jdbc.update("DELETE FROM portfolio_fund_group_member WHERE portfolio_fund_id = ?",
                portfolioFundId);
        for (Long groupId : groupIds) {
            jdbc.update("""
                    INSERT INTO portfolio_fund_group_member (portfolio_fund_id, group_id)
                    VALUES (?, ?)
                    ON CONFLICT DO NOTHING
                    """, portfolioFundId, groupId);
        }

        if (legacyFundId != null) {
            jdbc.update("DELETE FROM fund_group_member WHERE fund_id = ?", legacyFundId);
            for (Long groupId : groupIds) {
                jdbc.update("""
                        INSERT INTO fund_group_member (fund_id, group_id)
                        VALUES (?, ?)
                        ON CONFLICT DO NOTHING
                        """, legacyFundId, groupId);
            }
        }
    }

    private void deleteGroups(long ownerId, List<Long> deletedIds) {
        for (Long id : deletedIds) {
            jdbc.update("DELETE FROM portfolio_fund_group_member WHERE group_id = ?", id);
            jdbc.update("DELETE FROM fund_group_member WHERE group_id = ?", id);
            jdbc.update("""
                    UPDATE fund_group
                    SET deleted_date = now(), updated_date = now(), version = COALESCE(version, 0) + 1
                    WHERE id = ? AND owner_id = ? AND deleted_date IS NULL
                    """, id, ownerId);
        }
    }

    private static FundGroup mapGroup(ResultSet rs, int rowNum) throws SQLException {
        return new FundGroup(rs.getLong("id"), rs.getLong("owner_id"),
                rs.getString("name"), rs.getInt("sort_order"));
    }
}
