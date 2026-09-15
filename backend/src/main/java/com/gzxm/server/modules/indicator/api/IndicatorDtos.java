package com.gzxm.server.modules.indicator.api;

import java.time.LocalDate;

public final class IndicatorDtos {
    private IndicatorDtos() {}
    public record TimeNodeView(String id, String name, LocalDate deadline, int sortOrder, boolean enabled) {}
    public record DefinitionView(String id, String code, String name, String achievementType,
                                 String unit, String category, boolean enabled) {}
}
