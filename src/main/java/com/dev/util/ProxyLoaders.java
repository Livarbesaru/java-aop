package com.dev.util;

import com.dev.model.*;
import javassist.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
@Component
@Slf4j
public class ProxyLoaders{
    private final Map<Class<? extends SlaveInterface>,WrapperSlave> proxySlaves = new HashMap<>();
    private ApplicationContext applicationContext;

    @Autowired
    public ProxyLoaders(ApplicationContext applicationContext){
        this.applicationContext = applicationContext;
    }
    private ProxyLoaders(){
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
                        createWrapperAndMapMethods(classLoaded);
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

    private <C extends SlaveInterface> void createWrapperAndMapMethods(Class<C> clazz){
        Object obj = null;
        String wrapperClassPosition = WrapperSlave.class.getName().replace('.','/')+".class";
        InputStream wrapperInput = null;
        try {
            ClassPool pool = ClassPool.getDefault();
            wrapperInput = WrapperSlave.class.getClassLoader().getResourceAsStream(wrapperClassPosition);
            CtClass ctClazzWrapper = pool.makeClass(wrapperInput);

            addInterfacesToWrapper(ctClazzWrapper,clazz,pool);

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
                addMethodToWrapper(method,ctClazzWrapper);
            }
            proxySlaves.put(clazz,wrapperSlave);
            ctClazzWrapper.writeFile();
            ctClazzWrapper.toClass();
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        } catch (CannotCompileException e) {
            throw new RuntimeException(e);
        }
    }

    private void addInterfacesToWrapper(CtClass ctClazzWrapper,Class clazzWithInterfaces,ClassPool pool) throws IOException, NotFoundException, CannotCompileException {
        for(Class interfaz:clazzWithInterfaces.getInterfaces()){
            if(!interfaz.equals(SlaveInterface.class)){
                String interfazPath = interfaz.getName().replace('.','/')+".class";
                InputStream interfazInput = interfaz.getClassLoader().getResourceAsStream(interfazPath);
                ctClazzWrapper.addInterface(pool.makeClass(interfazInput));
            }
        }
    }

    private void addMethodToWrapper(Method method, CtClass classWrapperCt){
        try {
            String src = "";
            if(method.getReturnType().equals(Void.class)){
                src= "public void"+" "+method.getName()+"(";
            }else{
                src= "public "+method.getReturnType().getSimpleName()+" "+method.getName()+"(";
            }
            for (int i=0;i< method.getParameterCount();i++){
                Parameter param = method.getParameters()[i];
                src+=param.getType()+" "+param.getName();
                if(i<method.getParameterCount()-1){
                    src+=",";
                }
            }
            src+=")";
            if(!method.getReturnType().equals(Void.class)){
                src+="{"+"$proceed(\""+method.getName()+"\"";
            }else{
                src+="{"+"return $proceed(\""+method.getName()+"\"";
            }
            if(method.getParameterCount() > 0){
                src+=",";
            }
            for (int i=0;i< method.getParameterCount();i++){
                Parameter param = method.getParameters()[i];
                src+=param.getName();
                if(i<method.getParameterCount()-1){
                    src+=",";
                }
            }
            src+=");}";
            CtMethod implementation = CtNewMethod.make(src, classWrapperCt,"this","invokeMethod");
            classWrapperCt.addMethod(implementation);
        } catch(Throwable e) {
            throw new Error("Failed to instrument class ", e);
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

