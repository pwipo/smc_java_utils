package ru.smcsystem.smc.utils;

@FunctionalInterface
public interface CheckedConsumer<T> {
    void accept(int srcId, T t) throws Exception;
}
