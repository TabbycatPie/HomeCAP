package com.cap.plugin.model;

/**
 * 通用操作结果。
 */
public class PluginResult {

    private boolean success;
    private String message;

    public PluginResult() {}

    public PluginResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static PluginResult ok(String message) {
        return new PluginResult(true, message);
    }

    public static PluginResult fail(String message) {
        return new PluginResult(false, message);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
