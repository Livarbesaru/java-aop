package com.dev.util;

import com.dev.model.TestSlave;
import com.dev.model.WrapperSlave;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Slf4j
@Component
public class StarterAOP {
    private ProxyLoaders proxyLoaders;
    @Autowired
    public StarterAOP(ProxyLoaders proxyLoaders, ApplicationContext applicationContext){
        this.proxyLoaders = proxyLoaders;
        this.proxyLoaders.loadWrappers();
    }
    public void start() {
        for (Method method : WrapperSlave.class.getMethods()) {
            log.info(method.getName());
        }
        proxyLoaders.getSlave(TestSlave.class).invokeMethod("printText");
    }
}
