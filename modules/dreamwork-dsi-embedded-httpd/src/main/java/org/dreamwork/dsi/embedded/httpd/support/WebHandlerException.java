package org.dreamwork.dsi.embedded.httpd.support;

public class WebHandlerException extends Exception {
    public int code, httpStatus;

    public WebHandlerException (int code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public WebHandlerException (String message, int code, int httpStatus) {
        super (message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public WebHandlerException (String message, Throwable cause, int code, int httpStatus) {
        super (message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public WebHandlerException (Throwable cause, int code, int httpStatus) {
        super (cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public WebHandlerException (String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, int code, int httpStatus) {
        super (message, cause, enableSuppression, writableStackTrace);
        this.code = code;
        this.httpStatus = httpStatus;
    }

/*
    public WebHandlerException (int code) {
        this.code = code;
    }

    public WebHandlerException (String message, int code) {
        super (message);
        this.code = code;
    }

    public WebHandlerException (String message, Throwable cause, int code) {
        super (message, cause);
        this.code = code;
    }

    public WebHandlerException (Throwable cause, int code) {
        super (cause);
        this.code = code;
    }

    public WebHandlerException (String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, int code) {
        super (message, cause, enableSuppression, writableStackTrace);
        this.code = code;
    }
*/
}
