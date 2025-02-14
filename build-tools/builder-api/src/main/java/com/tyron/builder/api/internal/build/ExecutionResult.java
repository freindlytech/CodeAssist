package com.tyron.builder.api.internal.build;

import com.google.common.collect.ImmutableList;
import com.tyron.builder.api.execution.MultipleBuildFailures;
import com.tyron.builder.api.internal.Cast;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class ExecutionResult<T> {
    private static final Success<Void> SUCCESS = new Success<Void>() {
        @Override
        public Void getValue() {
            return null;
        }
    };

    /**
     * Returns the value, if available.
     */
    public abstract T getValue();

    /**
     * Returns the failures in this result, or an empty list if the operation was successful.
     */
    public abstract List<Throwable> getFailures();

    /**
     * Returns a single exception object that contains all failures in this result, if available.
     */
    public abstract RuntimeException getFailure();

    /**
     * Returns the value or rethrows the failures of this result.
     */
    public abstract T getValueOrRethrow();

    /**
     * Rethrows the failures in this result, if any, otherwise does nothing.
     */
    public abstract void rethrow();

    /**
     * Returns a copy of this result, adding any failures from the given result object.
     */
    public abstract ExecutionResult<T> withFailures(ExecutionResult<Void> otherResult);

    /**
     * Casts a failed result.
     */
    public abstract <S> ExecutionResult<S> asFailure();

    public static <T> ExecutionResult<T> succeeded(T value) {
        return new Success<T>() {
            @Override
            public T getValue() {
                return value;
            }
        };
    }

    public static ExecutionResult<Void> succeeded() {
        return SUCCESS;
    }

    public static <T> ExecutionResult<T> failed(Throwable failure) {
        return new Failure<>(ImmutableList.of(failure));
    }

    public static ExecutionResult<Void> maybeFailed(List<? extends Throwable> failures) {
        if (failures.isEmpty()) {
            return SUCCESS;
        } else {
            return new Failure<>(ImmutableList.copyOf(failures));
        }
    }

    public static ExecutionResult<Void> maybeFailed(@Nullable Throwable failure) {
        if (failure == null) {
            return SUCCESS;
        } else {
            return new Failure<>(ImmutableList.of(failure));
        }
    }

    private static abstract class Success<T> extends ExecutionResult<T> {
        @Override
        public List<Throwable> getFailures() {
            return Collections.emptyList();
        }

        @Override
        public ExecutionResult<T> withFailures(ExecutionResult<Void> otherResult) {
            if (otherResult.getFailures().isEmpty()) {
                return this;
            }
            return otherResult.asFailure();
        }

        @Override
        public T getValueOrRethrow() {
            return getValue();
        }

        @Override
        public void rethrow() {
        }

        @Override
        public RuntimeException getFailure() {
            throw new IllegalArgumentException("Cannot get the failure of a successful result.");
        }

        @Override
        public <S> ExecutionResult<S> asFailure() {
            throw new IllegalArgumentException("Cannot cast a successful result to a failed result.");
        }
    }

    private static class Failure<T> extends ExecutionResult<T> {
        private final ImmutableList<Throwable> failures;

        public Failure(ImmutableList<Throwable> failures) {
            this.failures = failures;
        }

        @Override
        public T getValue() {
            throw new IllegalArgumentException("Cannot get the value of a failed result.");
        }

        @Override
        public T getValueOrRethrow() {
            rethrow();
            return null;
        }

        @Override
        public List<Throwable> getFailures() {
            return failures;
        }

        @Override
        public RuntimeException getFailure() {
            if (failures.size() == 1 && failures.get(0) instanceof RuntimeException) {
                return (RuntimeException) failures.get(0);
            }
            return new MultipleBuildFailures(failures);
        }

        @Override
        public void rethrow() {
            throw getFailure();
        }

        @Override
        public ExecutionResult<T> withFailures(ExecutionResult<Void> otherResult) {
            if (otherResult.getFailures().isEmpty()) {
                return this;
            }
            ImmutableList.Builder<Throwable> builder = ImmutableList.builder();
            builder.addAll(failures);
            builder.addAll(otherResult.getFailures());
            return new Failure<>(builder.build());
        }

        @Override
        public <S> ExecutionResult<S> asFailure() {
            return Cast.uncheckedCast(this);
        }
    }
}
