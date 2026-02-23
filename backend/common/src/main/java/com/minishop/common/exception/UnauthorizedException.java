package com.minishop.common.exception;

public class UnauthorizedException extends BaseException {
    public UnauthorizedException(String message) {
        super(message, 401);
    }
}
