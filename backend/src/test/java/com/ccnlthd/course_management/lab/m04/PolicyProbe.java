package com.ccnlthd.course_management.lab.m04;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("m04")
public class PolicyProbe {
    private final PricePolicy selected;
    private final PricePolicy qualified;

    // "selected" does not match either bean name.
    public PolicyProbe(PricePolicy selected,
            @Qualifier("discountPolicy") PricePolicy qualified) {
        this.selected = selected;
        this.qualified = qualified;
    }

    @PostConstruct
    void printSelection() {
        System.out.println("[M04] default=" + selected.code());
        System.out.println("[M04] qualified=" + qualified.code());
    }

    public PricePolicy selectedPolicy() {
        return selected;
    }

    public PricePolicy qualifiedPolicy() {
        return qualified;
    }
}
