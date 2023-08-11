package com.dev.util;

import com.dev.model.TestSlave;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
@Slf4j
@Component
public class StarterAOP {
    private ProxyLoaders proxyLoaders;
    @Autowired
    public StarterAOP(ProxyLoaders proxyLoaders, ApplicationContext applicationContext){
        this.proxyLoaders = proxyLoaders;
        this.proxyLoaders.load();
    }
    public void start() {
        proxyLoaders.getSlave(TestSlave.class).invokeMethod("printText");
    }
}
