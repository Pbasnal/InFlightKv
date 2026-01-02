package com.bcorp.pojos;

import java.util.List;

public record DataKey(String key) implements Comparable {
    public static DataKey fromString(String str) {
        return new DataKey(str);
    }

    @Override
    public int compareTo(Object other) {
        if (other instanceof DataKey otherDataKey) {
            // Handle nulls if necessary, otherwise delegate to String
            if (other == null) return 1;
            return this.key.compareTo(otherDataKey.key);
        }

        return 1;
    }
}

