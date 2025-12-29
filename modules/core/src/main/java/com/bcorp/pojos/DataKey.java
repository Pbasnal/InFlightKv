package com.bcorp.pojos;

public record DataKey(String key) {
    public static DataKey from(String str) {
        return new DataKey(str);
    }
}

