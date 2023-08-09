package com.dev.util;

import com.dev.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
@Component
@Slf4j
public class ProxyLoaders {
    private final Map<Class<? extends SlaveInterface>,WrapperSlave> proxySlaves = new HashMap<>();
    private ApplicationContext applicationContext;

    @Autowired
    public ProxyLoaders(ApplicationContext applicationContext){
        this.applicationContext = applicationContext;
    }
    public void load(){
        Path path = Paths.get("src/main/java/com/dev/");
        try (Stream<Path> pathsStream = Files.walk(path)){
            pathsStream.filter(file -> file.toString().endsWith(".java")).forEach(snglPath -> {
                String replace = snglPath.toString()
                        .replace("\\", ".")
                        .replace("src.main.java.", "")
                        .replace(".java", "");
                Class<? extends SlaveInterface> classLoaded = null;
                try {
                    classLoaded = (Class<? extends SlaveInterface>) Class.forName(replace);
                    if(classLoaded.getAnnotation(ProxySlave.class)!=null){
                        testAnonymous(classLoaded);
                    }
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public <C extends SlaveInterface> WrapperSlave getSlave(Class<C> clazz){
        return proxySlaves.get(clazz);
    }

    private <C extends SlaveInterface> void testAnonymous(Class<C> clazz){
        Object obj = null;
        try {
            obj = clazz.getConstructor().newInstance();
            WrapperSlave wrapperSlave = new WrapperSlave(obj);
            for(Method method:clazz.getDeclaredMethods()){
                if(method.isAnnotationPresent(Before.class)){
                    String functionCheckPath = method.getAnnotation(Before.class).pathToMethod();
                    putCheckOnWrapperSlaveMethod(functionCheckPath,wrapperSlave,method,ChecksEnum.BEFORE);
                }
                if(method.isAnnotationPresent(After.class)){
                    String functionCheckPath = method.getAnnotation(After.class).pathToMethod();
                    putCheckOnWrapperSlaveMethod(functionCheckPath,wrapperSlave,method,ChecksEnum.AFTER);
                }
            }
            proxySlaves.put(clazz,wrapperSlave);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void putCheckOnWrapperSlaveMethod(String functionCheckPath,WrapperSlave wrapperSlave, Method method,ChecksEnum checksEnum) throws ClassNotFoundException {
        Class<CheckFunctionDetainer> clazzCheck = (Class<CheckFunctionDetainer>) Class.forName(functionCheckPath);
        applicationContext.getBeansOfType(clazzCheck)
                .values()
                .stream()
                .filter(bean -> bean.getClass().equals(clazzCheck))
                .findFirst().ifPresent(bean -> {
                    CheckFunction function = bean.getFunction();
                    wrapperSlave.putChecksOnMethod(method.getName(), checksEnum,function);
                });
    }
}
