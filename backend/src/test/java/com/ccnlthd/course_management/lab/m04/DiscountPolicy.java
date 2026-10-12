package com.ccnlthd.course_management.lab.m04;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("discountPolicy")
@Profile("m04")
public class DiscountPolicy implements PricePolicy {
    @Override
    public String code() {
        return "discount";
    }
}
