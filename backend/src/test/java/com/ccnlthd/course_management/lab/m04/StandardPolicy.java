package com.ccnlthd.course_management.lab.m04;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("standardPolicy")
@Profile("m04")
public class StandardPolicy implements PricePolicy {
    @Override
    public String code() {
        return "standard";
    }
}
