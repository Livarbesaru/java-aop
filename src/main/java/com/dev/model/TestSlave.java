package com.dev.model;
@ProxySlave
public class TestSlave implements SlaveInterface{
    @Before(pathToMethod = "com.dev.model.CheckFunctionDetainerImpl")
    public String getText(){
        return "PROVA";
    }
}
