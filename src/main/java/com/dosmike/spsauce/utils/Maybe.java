package com.dosmike.spsauce.utils;

import com.dosmike.spsauce.Configuration;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class Maybe<T> {
    T value;
    boolean hasValue;

    public Maybe(T t) {
        value = t;
        hasValue = true;
    }

    public Maybe() {
        hasValue = false;
    }

    public <R> Maybe<R> flatMap(Function<T, Maybe<R>> mapper) {
        return hasValue ? mapper.apply(value) : new Maybe<>();
    }

    public <R> Maybe<R> map(Function<T, R> mapper) {
        return hasValue ? new Maybe<>(mapper.apply(value)) : new Maybe<>();
    }

    public Maybe<T> flatOr(Supplier<Maybe<T>> supplier) {
        return hasValue ? this : supplier.get();
    }

    public Maybe<T> or(Supplier<T> supplier) {
        return hasValue ? this : new Maybe<>(supplier.get());
    }

    public Optional<T> optional() {
        return hasValue ? Optional.of(value) : Optional.empty();
    }

    public T unit() throws IllegalStateException {
        if (!hasValue) throw new IllegalStateException("Maybe monad has no value");
        return value;
    }

    public static <T> Maybe<T> of(T value) { return new Maybe<>(value); }

    private static final Maybe<?> _EMPTY = new Maybe<>();

    @SuppressWarnings("unchecked")
    public static <T> Maybe<T> empty() { return (Maybe<T>)_EMPTY; }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Maybe)) return false;
        Maybe<?> other = (Maybe<?>) obj;
        if (hasValue != other.hasValue) return false;
        return hasValue && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        if (!hasValue) return 0;
        return Objects.hash(value);
    }
}
