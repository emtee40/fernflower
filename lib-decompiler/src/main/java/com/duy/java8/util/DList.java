package com.duy.java8.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class DList {
    public static <E> void forEach(List<E> list, Consumer<? super E> action) {
        for (E e : list) {
            action.accept(e);
        }
    }

    public static <E> List<E> filter(List<E> input, Predicate<E> predicate) {
        List<E> list = new ArrayList<>();
        for (E e : input) {
            if (predicate.test(e)) {
                list.add(e);
            }
        }
        return list;
    }
}
