package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.controller.FundGroupSaveRequest;
import com.fundpilot.backend.fund.controller.FundGroupView;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundGroupEntity;
import com.fundpilot.backend.fund.repository.FundGroupRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FundGroupService {
    private static final int MAX_NAME_LENGTH = 20;

    private final FundGroupRepository fundGroupRepository;
    private final FundRepository fundRepository;

    @Transactional(readOnly = true)
    public List<FundGroupView> list() {
        return toViews(fundGroupRepository.findAllByOrderBySortOrderAscIdAsc(), fundRepository.findAll());
    }

    @Transactional
    public List<FundGroupView> save(FundGroupSaveRequest request) {
        if (request == null || request.groups() == null) {
            throw new BusinessException(ErrorCode.FUND_GROUP_NAME_INVALID, "分组列表不能为空");
        }
        List<FundGroupSaveRequest.Item> items = request.groups();
        List<String> names = items.stream().map(FundGroupSaveRequest.Item::name).map(this::normalizeName).toList();
        requireUniqueNames(names);

        List<FundGroupEntity> existing = fundGroupRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, FundGroupEntity> byId = existing.stream()
                .collect(Collectors.toMap(FundGroupEntity::getId, Function.identity()));
        Set<Long> retainedIds = new HashSet<>();
        for (FundGroupSaveRequest.Item item : items) {
            if (item.id() != null && (!retainedIds.add(item.id()) || !byId.containsKey(item.id()))) {
                throw new BusinessException(ErrorCode.FUND_GROUP_NOT_FOUND, "分组不存在或重复提交: " + item.id());
            }
        }

        Set<Long> deletedIds = new HashSet<>(byId.keySet());
        deletedIds.removeAll(retainedIds);
        if (!deletedIds.isEmpty()) {
            List<FundEntity> funds = fundRepository.findAll();
            funds.forEach(fund -> fund.getGroups().removeIf(group -> deletedIds.contains(group.getId())));
            fundRepository.saveAll(funds);
            fundRepository.flush();
            fundGroupRepository.deleteAll(existing.stream().filter(group -> deletedIds.contains(group.getId())).toList());
            fundGroupRepository.flush();
        }

        List<FundGroupEntity> saved = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            FundGroupSaveRequest.Item item = items.get(index);
            FundGroupEntity group = item.id() == null ? new FundGroupEntity() : byId.get(item.id());
            group.setName(names.get(index));
            group.setSortOrder(index);
            saved.add(fundGroupRepository.save(group));
        }
        fundGroupRepository.flush();
        return toViews(saved, fundRepository.findAll());
    }

    @Transactional
    public Set<FundGroupEntity> resolveNames(List<String> requestedNames) {
        if (requestedNames == null) {
            return null;
        }
        List<String> names = requestedNames.stream().map(this::normalizeName).toList();
        requireUniqueNames(names);
        List<FundGroupEntity> existing = fundGroupRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<String, FundGroupEntity> byName = existing.stream()
                .collect(Collectors.toMap(group -> key(group.getName()), Function.identity()));
        int nextOrder = existing.size();
        Set<FundGroupEntity> groups = new LinkedHashSet<>();
        for (String name : names) {
            FundGroupEntity group = byName.get(key(name));
            if (group == null) {
                group = new FundGroupEntity();
                group.setName(name);
                group.setSortOrder(nextOrder++);
                group = fundGroupRepository.save(group);
                byName.put(key(name), group);
            }
            groups.add(group);
        }
        return groups;
    }

    private List<FundGroupView> toViews(List<FundGroupEntity> groups, List<FundEntity> funds) {
        Map<Long, Long> counts = new HashMap<>();
        funds.forEach(fund -> fund.getGroups().forEach(group -> counts.merge(group.getId(), 1L, Long::sum)));
        return groups.stream().map(group -> new FundGroupView(
                group.getId(), group.getName(), group.getSortOrder(), counts.getOrDefault(group.getId(), 0L))).toList();
    }

    private String normalizeName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty() || value.length() > MAX_NAME_LENGTH
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.FUND_GROUP_NAME_INVALID, "分组名称长度必须为 1-20 个字符");
        }
        return value;
    }

    private void requireUniqueNames(List<String> names) {
        if (names.stream().map(FundGroupService::key).distinct().count() != names.size()) {
            throw new BusinessException(ErrorCode.FUND_GROUP_NAME_DUPLICATE, "分组名称不能重复");
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
