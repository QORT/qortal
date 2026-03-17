package org.qortal.test.at.qortalfunctioncodes;

import com.google.common.primitives.Bytes;
import org.ciyam.at.CompilationException;
import org.ciyam.at.FunctionCode;
import org.ciyam.at.MachineState;
import org.ciyam.at.OpCode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.Account;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.at.QortalFunctionCode;
import org.qortal.data.at.ATStateData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.test.common.AtUtils;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TestAccount;
import org.qortal.transaction.DeployAtTransaction;
import org.qortal.utils.Base58;
import org.qortal.utils.BitTwiddling;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GetAccountLevelTests extends Common {

    private static final Random RANDOM = new Random();
    private static final long fundingAmount = 1_00000000L;

    private Repository repository = null;
    private byte[] creationBytes = null;
    private PrivateKeyAccount deployer;
    private DeployAtTransaction deployAtTransaction;
    private String atAddress;

    @Before
    public void before() throws DataException {
        Common.useDefaultSettings();

        this.repository = RepositoryManager.getRepository();
        this.deployer = Common.getTestAccount(repository, "alice");

    }

    @After
    public void after() throws DataException {
        if (this.repository != null)
            this.repository.close();

        this.repository = null;
    }

    @Test
    public void testGetAccountLevelFromAddress() throws DataException {
        Account dilbert = Common.getTestAccount(repository, "dilbert");
        byte[] accountBytes = Bytes.ensureCapacity(Base58.decode(dilbert.getAddress()), 32, 0);

        this.creationBytes = buildGetAccountLevelAT(accountBytes);

        this.deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
        this.atAddress = deployAtTransaction.getATAccount().getAddress();

        // Mint a block to allow AT to run
        BlockUtils.mintBlock(repository);

        Integer extractedAccountLevel = extractAccountLevel(repository, atAddress);
        assertEquals(dilbert.getLevel(), extractedAccountLevel);
    }

    @Test
    public void testGetAccountLevelFromPublicKey() throws DataException {
        TestAccount dilbert = Common.getTestAccount(repository, "dilbert");
        byte[] accountBytes = dilbert.getPublicKey();

        this.creationBytes = buildGetAccountLevelAT(accountBytes);

        this.deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
        this.atAddress = deployAtTransaction.getATAccount().getAddress();

        // Mint a block to allow AT to run
        BlockUtils.mintBlock(repository);

        Integer extractedAccountLevel = extractAccountLevel(repository, atAddress);
        assertEquals(dilbert.getLevel(), extractedAccountLevel);
    }

    @Test
    public void testGetUnknownAccountLevel() throws DataException {
        byte[] accountBytes = new byte[32];
        RANDOM.nextBytes(accountBytes);

        this.creationBytes = buildGetAccountLevelAT(accountBytes);

        this.deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
        this.atAddress = deployAtTransaction.getATAccount().getAddress();

        // Mint a block to allow AT to run
        BlockUtils.mintBlock(repository);

        Integer extractedAccountLevel = extractAccountLevel(repository, atAddress);
        assertNull(extractedAccountLevel);
    }

    private static byte[] buildGetAccountLevelAT(byte[] accountBytes) {
        // Labels for data segment addresses
        int addrCounter = 0;

        // Beginning of data segment for easy extraction
        final int addrAccountLevel = addrCounter++;

        // accountBytes
        final int addrAccountBytes = addrCounter;
        addrCounter += 4;

        // Pointer to accountBytes so we can load them into B
        final int addrAccountBytesPointer = addrCounter++;

        // Data segment
        ByteBuffer dataByteBuffer = ByteBuffer.allocate(addrCounter * MachineState.VALUE_SIZE);

        // Write accountBytes
        dataByteBuffer.position(addrAccountBytes * MachineState.VALUE_SIZE);
        dataByteBuffer.put(accountBytes);

        // Store pointer to addrAccountbytes at addrAccountBytesPointer
        assertEquals(addrAccountBytesPointer * MachineState.VALUE_SIZE, dataByteBuffer.position());
        dataByteBuffer.putLong(addrAccountBytes);

        ByteBuffer codeByteBuffer = ByteBuffer.allocate(512);

        // Two-pass version
        for (int pass = 0; pass < 2; ++pass) {
            codeByteBuffer.clear();

            try {
                /* Initialization */

                // Copy accountBytes from data segment into B, starting at addrAccountBytes (as pointed to by addrAccountBytesPointer)
                codeByteBuffer.put(OpCode.EXT_FUN_DAT.compile(FunctionCode.SET_B_IND, addrAccountBytesPointer));

                // Get account level and save into addrAccountLevel
                codeByteBuffer.put(OpCode.EXT_FUN_RET.compile(QortalFunctionCode.GET_ACCOUNT_LEVEL_FROM_ACCOUNT_IN_B.value, addrAccountLevel));

                // We're done
                codeByteBuffer.put(OpCode.FIN_IMD.compile());
            } catch (CompilationException e) {
                throw new IllegalStateException("Unable to compile AT?", e);
            }
        }

        codeByteBuffer.flip();

        byte[] codeBytes = new byte[codeByteBuffer.limit()];
        codeByteBuffer.get(codeBytes);

        final short ciyamAtVersion = 2;
        final short numCallStackPages = 0;
        final short numUserStackPages = 0;
        final long minActivationAmount = 0L;

        return MachineState.toCreationBytes(ciyamAtVersion, codeBytes, dataByteBuffer.array(), numCallStackPages, numUserStackPages, minActivationAmount);
    }

    private Integer extractAccountLevel(Repository repository, String atAddress) throws DataException {
        // Check AT result
        ATStateData atStateData = repository.getATRepository().getLatestATState(atAddress);
        byte[] stateData = atStateData.getStateData();

        byte[] dataBytes = MachineState.extractDataBytes(stateData);

        Long accountLevelValue = BitTwiddling.longFromBEBytes(dataBytes, 0);
        if (accountLevelValue == -1)
            return null;

        return accountLevelValue.intValue();
    }
}
