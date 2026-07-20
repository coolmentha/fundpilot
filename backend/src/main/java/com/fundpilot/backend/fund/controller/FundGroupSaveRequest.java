package com.fundpilot.backend.fund.controller;

import java.util.List;

public record FundGroupSaveRequest(List<Item> groups) {
    public record Item(Long id, String name) {
    }
}
