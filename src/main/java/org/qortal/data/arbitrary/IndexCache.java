package org.qortal.data.arbitrary;

import org.qortal.controller.arbitrary.ArbitraryTransactionDataHashWrapper;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class IndexCache {

    public static class IndexResourceDetails {
        public ArbitraryResourceData resource;
        public List<ArbitraryDataIndexDetail> indexDetails;

        public IndexResourceDetails(ArbitraryResourceData resource, List<ArbitraryDataIndexDetail> indexDetails) {
            this.resource = resource;
            this.indexDetails = indexDetails;
        }
    }

    public static final IndexCache SINGLETON = new IndexCache();
    private ConcurrentHashMap<ArbitraryTransactionDataHashWrapper, IndexResourceDetails> indexResourceDetailsByHashWrapper = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, List<ArbitraryDataIndexDetail>> indicesByTerm = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, List<ArbitraryDataIndexDetail>> indicesByIssuer = new ConcurrentHashMap<>();

    public static IndexCache getInstance() {
        return SINGLETON;
    }

    public ConcurrentHashMap<ArbitraryTransactionDataHashWrapper, IndexResourceDetails> getIndexResourceDetailsByHashWrapper() {
        return indexResourceDetailsByHashWrapper;
    }

    public ConcurrentHashMap<String, List<ArbitraryDataIndexDetail>> getIndicesByTerm() {
        return indicesByTerm;
    }

    public ConcurrentHashMap<String, List<ArbitraryDataIndexDetail>> getIndicesByIssuer() {
        return indicesByIssuer;
    }
}
