package org.qortal.controller.hsqldb;

import org.qortal.data.arbitrary.ArbitraryResourceCache;
import org.qortal.repository.RepositoryManager;
import org.qortal.repository.hsqldb.HSQLDBCacheUtils;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.settings.Settings;

public class HSQLDBDataCacheManager extends Thread{

    public HSQLDBDataCacheManager() {}

    @Override
    public void run() {
        Thread.currentThread().setName("HSQLDB Data Cache Manager");

        HSQLDBCacheUtils.startCaching(
            Settings.getInstance().getDbCacheThreadPriority(),
            Settings.getInstance().getDbCacheFrequency()
        );
    }
}
