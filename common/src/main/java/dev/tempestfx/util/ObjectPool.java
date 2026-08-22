package dev.tempestfx.util;

import java.util.ArrayDeque;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ObjectPool<T> {
    private final ArrayDeque<T> free;
    private final Supplier<T> factory;
    private final Consumer<T> reset;
    private final int maximum;
    public ObjectPool(int initial, int maximum, Supplier<T> factory, Consumer<T> reset) {
        this.free = new ArrayDeque<>(maximum); this.factory = factory; this.reset = reset; this.maximum = maximum;
        for (int i = 0; i < initial; i++) free.add(factory.get());
    }
    public T acquire() { T item = free.pollFirst(); return item == null ? factory.get() : item; }
    public void release(T item) { reset.accept(item); if (free.size() < maximum) free.addFirst(item); }
    public int available() { return free.size(); }
}
