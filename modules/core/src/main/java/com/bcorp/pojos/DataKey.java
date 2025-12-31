package com.bcorp.pojos;

public record DataKey(String key) {
    public static DataKey fromString(String str) {
        return new DataKey(str);
    }
}

