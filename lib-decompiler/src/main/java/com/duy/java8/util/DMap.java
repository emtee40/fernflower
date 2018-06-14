package com.duy.java8.util;

import java.util.Map;
import java.util.function.Function;

public class DMap {
    public static <K, V> V putIfAbsent(Map<K, V> map, K key, V value) {
        V v = map.get(key);
        if (v == null) {
            v = map.put(key, value);
        }

        return v;
    }

    public static <K, V> V computeIfAbsent(Map<K, V> map, K key,
                                           Function<? super K, ? extends V> mappingFunction) {
        V v;
        if ((v = map.get(key)) == null) {
            V newValue;
            if ((newValue = mappingFunction.apply(key)) != null) {
                map.put(key, newValue);
                return newValue;
            }
        }

        return v;
    }
}
