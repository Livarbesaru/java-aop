package com.dev.model;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
@Slf4j
public class WrapperSlave implements SlaveHolder{
    private Object slave;
    private Map<String, Method> methods;
    private Map<String,Map<ChecksEnum,CheckFunction>> checksMethods;

    public WrapperSlave(Object slave){
        this.slave = slave;
        methods = new HashMap<>();
        checksMethods = new HashMap<>();
        for(Method method:slave.getClass().getDeclaredMethods()){
            methods.put(method.getName(),method);
        }
        invokeMethod("dio");
    }
    public Object invokeMethod(String methodName){
        Object returnValue = null;
        if(methods.containsKey(methodName)){
            Map<ChecksEnum,CheckFunction> checks = checksMethods.get(methodName);
            Method method = methods.get(methodName);
            try {
                if(checks.containsKey(ChecksEnum.BEFORE)){
                    checks.get(ChecksEnum.BEFORE).doFunction();
                }
                returnValue = method.invoke(slave);
                if(checks.containsKey(ChecksEnum.AFTER)){
                    checks.get(ChecksEnum.AFTER).doFunction();
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        return returnValue;
    }
    @Override
    public Object invokeMethod(String methodName,Object... params){
        Object returnValue = null;
        if(methods.containsKey(methodName)){
            Map<ChecksEnum,CheckFunction> checks = checksMethods.get(methodName);
            Method method = methods.get(methodName);
            try {
                if(checks.containsKey(ChecksEnum.BEFORE)){
                    checks.get(ChecksEnum.BEFORE).doFunction();
                }
                returnValue = method.invoke(slave,params);
                if(checks.containsKey(ChecksEnum.AFTER)){
                    checks.get(ChecksEnum.AFTER).doFunction();
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        return returnValue;
    }
    public void putChecksOnMethod(String methodName,ChecksEnum check,CheckFunction checkFunction){
        Consumer<String> checkIfPresent = (methodNameToPutChecks) ->{
            if(methods.containsKey(methodNameToPutChecks)){
                if(checksMethods.containsKey(methodNameToPutChecks)){
                    checksMethods.computeIfPresent(methodNameToPutChecks,(k,v) -> v).put(check,checkFunction);
                }else{
                    checksMethods.computeIfAbsent(methodNameToPutChecks,(k) -> new HashMap<>()).put(check,checkFunction);
                }
            }
        };
        checkIfPresent.accept(methodName);
    }
}
