package com.notpatch.nOrder.configuration;

import com.notpatch.nOrder.NOrder;
import com.notpatch.nlib.configuration.NConfiguration;

public class CustomItemConfiguration extends NConfiguration {

    public CustomItemConfiguration() {
        super(NOrder.getInstance(), "customitems.yml");
    }
}
