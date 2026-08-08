package org.qortal.block;

import org.qortal.account.Account;
import org.qortal.api.resource.TransactionsResource;
import org.qortal.data.naming.NameData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class PrimaryNamesBlock
 */
public class PrimaryNamesBlock {

    /**
     * Process Primary Names
     *
     * @param repository
     * @throws DataException
     */
    public static void processNames(Repository repository) throws DataException {

        Set<String> addressesWithNames
            = repository.getNameRepository().getAllNames().stream()
                .map(NameData::getOwner).collect(Collectors.toSet());

        // for each address with a name, set primary name to the address
        for( String address : addressesWithNames ) {

            Account account = new Account(repository, address);
            account.resetPrimaryName(TransactionsResource.ConfirmationStatus.CONFIRMED);
        }
    }

    /**
     * Orphan the Primary Names Block
     *
     * @param repository
     * @throws DataException
     */
    public static void orphanNames(Repository repository) throws DataException {

        repository.getNameRepository().clearPrimaryNames();
    }
}