package com.cardwise.cardwise_backend.service;

import java.math.BigDecimal;
import java.util.List;

public record SelectionPolicyView(
        int baseSelectionLimit,
        int extendedSelectionLimit,
        String extendedRequirement,
        String extendedRequirementLabel,
        Integer changeHoldDays,
        int modelledCategoryCount,
        int offeredCategoryCount,
        List<SelectableCategoryView> selectableCategories
) {

    public record SelectableCategoryView(
            String code,
            String label,
            BigDecimal selectedRate,
            BigDecimal unselectedRate
    ) {
    }
}
