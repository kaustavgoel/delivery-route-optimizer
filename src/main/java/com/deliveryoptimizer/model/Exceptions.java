package com.deliveryoptimizer.model;

public final class Exceptions {
    private Exceptions() {}

    public static class InvalidNetworkException extends RuntimeException {
        public InvalidNetworkException(String message) {
            super(message);
        }
    }

    public static class InfeasibleRoutingException extends RuntimeException {
        public InfeasibleRoutingException(String message) {
            super(message);
        }
    }

    public static class CapacityExceededException extends RuntimeException {
        public CapacityExceededException(String message) {
            super(message);
        }
    }
}
