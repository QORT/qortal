package org.qortal.data.arbitrary;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ArbitraryResourceCache {
    private ConcurrentHashMap<Integer, List<ArbitraryResourceData>> dataByService = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Integer> levelByName = new ConcurrentHashMap<>();

    private ArbitraryResourceCache() {}

    private static ArbitraryResourceCache SINGLETON = new ArbitraryResourceCache();

    public static ArbitraryResourceCache getInstance(){
        return SINGLETON;
    }

    public ConcurrentHashMap<String, Integer> getLevelByName() {
        return levelByName;
    }

    public ConcurrentHashMap<Integer, List<ArbitraryResourceData>> getDataByService() {
        return this.dataByService;
    }
}
