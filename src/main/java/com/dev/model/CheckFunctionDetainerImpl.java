package com.dev.model;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
@Slf4j
@Component
public class CheckFunctionDetainerImpl implements CheckFunctionDetainer {
    private CheckFunction function;
    @Autowired
    public CheckFunctionDetainerImpl(){
        function = () -> {log.info("DAJE");};
    }
    @Override
    public CheckFunction getFunction() {
        return function;
    }
}
