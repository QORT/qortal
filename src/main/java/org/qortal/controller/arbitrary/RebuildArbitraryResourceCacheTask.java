package org.qortal.controller.arbitrary;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;

import java.util.TimerTask;

public class RebuildArbitraryResourceCacheTask extends TimerTask {

    private static final Logger LOGGER = LogManager.getLogger(RebuildArbitraryResourceCacheTask.class);

    public static final long MILLIS_IN_HOUR = 60 * 60 * 1000;

    public static final long MILLIS_IN_MINUTE = 60 * 1000;

    private static final String REBUILD_ARBITRARY_RESOURCE_CACHE_TASK = "Rebuild Arbitrary Resource Cache Task";

    @Override
    public void run() {

        Thread.currentThread().setName(REBUILD_ARBITRARY_RESOURCE_CACHE_TASK);

        try (final Repository repository = RepositoryManager.getRepository()) {
            ArbitraryDataCacheManager.getInstance().buildArbitraryResourcesCache(repository, true);
        }
        catch( DataException e ) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
