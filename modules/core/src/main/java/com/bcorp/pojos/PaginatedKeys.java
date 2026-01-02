package com.bcorp.pojos;

import java.util.List;

public record PaginatedKeys(List<DataKey> dataKeys, long pageId) {}
