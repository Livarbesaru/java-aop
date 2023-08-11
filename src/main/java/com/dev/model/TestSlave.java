package com.dev.model;

import lombok.extern.slf4j.Slf4j;

@ProxySlave
@Slf4j
public class TestSlave implements SlaveInterface{
    @Before(pathToMethod = "com.dev.model.CheckFunctionDetainerImpl")
    @After(pathToMethod = "com.dev.model.CheckFunctionDetainerImpl")
    public void printText(){
        log.info("prova");
    }
}
