package org.eblocker.server.common.network.unix.firewall;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class Table {
    private String name;
    private Map<String, Chain> chainsByName = new LinkedHashMap<>();

    public Table(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Collection<Chain> getChains() {
        return chainsByName.values();
    }

    public Chain chain(String name) {
        if (!chainsByName.containsKey(name)) {
            chainsByName.put(name, new Chain(name));
        }
        return chainsByName.get(name);
    }
}
