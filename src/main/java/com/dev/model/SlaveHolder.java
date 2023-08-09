package com.dev.model;

public interface SlaveHolder {
    Object invokeMethod(String methodName,Object... params);
}
