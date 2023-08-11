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
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.stream.Stream;
@Component
@Slf4j
public class ProxyLoaders implements ClassFileTransformer{
    private final Map<Class<? extends SlaveInterface>,WrapperSlave> proxySlaves = new HashMap<>();
    private ApplicationContext applicationContext;
    private CtClass ctClazzWrapper;
    private List<Class<? extends SlaveInterface>> slavesClasses = new ArrayList<>();
    private ClassPool pool;
    private Set<Class> ctWrapperInterfaces = new HashSet<>();

    @Autowired
    public ProxyLoaders(ApplicationContext applicationContext){
        this.applicationContext = applicationContext;
        this.pool = ClassPool.getDefault();
        loadWrapperCtClass();
    }
    public ProxyLoaders(){
        this.pool = ClassPool.getDefault();
        loadWrapperCtClass();
    }
    public static void premain(String agentArgument, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ProxyLoaders());
    }
    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer){

        try {
            if(className.matches("com/dev/model/WrapperSlave")) {
                return load();
            } else {
                return classfileBuffer;
            }
        } catch (IOException | CannotCompileException e) {
            throw new RuntimeException(e);
        }
    }
    public byte[] load() throws IOException, CannotCompileException {
        Path path = Paths.get("src/main/java/com/dev/");
        try (Stream<Path> pathsStream = Files.walk(path)){
            pathsStream.filter(file -> file.toString().endsWith(".java")).forEach(snglPath -> {
                String replace = snglPath.toString()
                        .replace("\\", ".")
                        .replace("src.main.java.", "")
                        .replace(".java", "");
                Class classLoaded = null;
                try {
                    classLoaded = Class.forName(replace);
                    if(classLoaded.getAnnotation(ProxySlave.class)!=null){
                        Class<? extends SlaveInterface> classLoadedSlave = (Class<? extends SlaveInterface>) classLoaded;
                        slavesClasses.add(classLoadedSlave);
                    }
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        slavesClasses.forEach(this::alterWrapperClass);
        return ctClazzWrapper.toBytecode();
    }

    public void loadWrappers(){
        Path path = Paths.get("src/main/java/com/dev/");
        try (Stream<Path> pathsStream = Files.walk(path)){
            pathsStream.filter(file -> file.toString().endsWith(".java")).forEach(snglPath -> {
                String replace = snglPath.toString()
                        .replace("\\", ".")
                        .replace("src.main.java.", "")
                        .replace(".java", "");
                Class classLoaded = null;
                try {
                    classLoaded = Class.forName(replace);
                    if(classLoaded.getAnnotation(ProxySlave.class)!=null){
                        Class<? extends SlaveInterface> classLoadedSlave = (Class<? extends SlaveInterface>) classLoaded;
                        slavesClasses.add(classLoadedSlave);
                    }
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        slavesClasses.forEach(this::createWrapperAndMapMethods);
    }
    public <C extends SlaveInterface> WrapperSlave getSlave(Class<C> clazz){
        return proxySlaves.get(clazz);
    }
    private void loadWrapperCtClass(){
        String wrapperClassPosition = WrapperSlave.class.getName().replace('.','/')+".class";
        InputStream wrapperInput = null;
            try {
                wrapperInput = WrapperSlave.class.getClassLoader().getResourceAsStream(wrapperClassPosition);
                ctClazzWrapper = pool.makeClass(wrapperInput);
            }catch (IOException ex) {
                throw new RuntimeException(ex);
            }
    }

    private <C extends SlaveInterface> void alterWrapperClass(Class<C> clazz){
        try {
            addInterfacesToWrapper(ctClazzWrapper,clazz,pool);
            for(Method method:clazz.getMethods()){
                addMethodToWrapper(method,ctClazzWrapper);
            }
        }catch (NotFoundException | IOException e) {
            throw new RuntimeException(e);
        }

    }
    private void addInterfacesToWrapper(CtClass ctClazzWrapper,Class clazzWithInterfaces,ClassPool pool) throws IOException, NotFoundException {
        for(Class interfaz:clazzWithInterfaces.getInterfaces()){
            if(!interfaz.equals(SlaveInterface.class) && ctWrapperInterfaces.add(interfaz)){
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

    private <C extends SlaveInterface> void createWrapperAndMapMethods(Class<C> clazz){
        try{
            Object obj = clazz.getConstructor().newInstance();
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
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
    private void putCheckOnWrapperSlaveMethod(String functionCheckPath,WrapperSlave wrapperSlave, Method method,ChecksEnum checksEnum) throws ClassNotFoundException {
        Class clazzCheck = Class.forName(functionCheckPath);
        if(Arrays.stream(clazzCheck.getInterfaces()).anyMatch(interfacez->interfacez.equals(CheckFunctionDetainer.class))){
            applicationContext.getBeansOfType(clazzCheck)
                    .values()
                    .stream()
                    .filter(bean -> bean.getClass().equals(clazzCheck))
                    .findFirst().ifPresent(bean -> {
                        CheckFunction function = ((CheckFunctionDetainer)bean).getFunction();
                        wrapperSlave.putChecksOnMethod(method.getName(), checksEnum,function);
                    });
        }

    }
}

