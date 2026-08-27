package com.example.fixmock.dto;

/** 예외 발생 시 클라이언트에게 내려주는 표준 에러 응답. */
public class ApiErrorResponse {
    private final String error;
    private final String message;

    public ApiErrorResponse(String error, String message) {
        this.error = error;
        this.message = message;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }
}
