package org.qortal.api.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.bitcoinj.core.Transaction;
import org.qortal.api.ApiError;
import org.qortal.api.ApiErrors;
import org.qortal.api.ApiExceptionFactory;
import org.qortal.api.Security;
import org.qortal.api.model.crosschain.BitcoinSendRequest;
import org.qortal.crosschain.Bitcoin;
import org.qortal.crosschain.ForeignBlockchainException;
import org.qortal.crosschain.SimpleTransaction;

@Path("/crosschain/btc")
@Tag(name = "Cross-Chain (Bitcoin)")
public class CrossChainBitcoinResource {

	@Context
	HttpServletRequest request;

	@POST
	@Path("/walletbalance")
	@Operation(
		summary = "Returns BTC balance for hierarchical, deterministic BIP32 wallet",
		description = "Supply BIP32 'm' private/public key in base58, starting with 'xprv'/'xpub' for mainnet, 'tprv'/'tpub' for testnet",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.TEXT_PLAIN,
				schema = @Schema(
					type = "string",
					description = "BIP32 'm' private/public key in base58",
					example = "tpubD6NzVbkrYhZ4XTPc4btCZ6SMgn8CxmWkj6VBVZ1tfcJfMq4UwAjZbG8U74gGSypL9XBYk2R2BLbDBe8pcEyBKM1edsGQEPKXNbEskZozeZc"
				)
			)
		),
		responses = {
			@ApiResponse(
				content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "string", description = "balance (satoshis)"))
			)
		}
	)
	@ApiErrors({ApiError.INVALID_PRIVATE_KEY, ApiError.FOREIGN_BLOCKCHAIN_NETWORK_ISSUE})
	public String getBitcoinWalletBalance(String key58) {
		Security.checkApiCallAllowed(request);

		Bitcoin bitcoin = Bitcoin.getInstance();

		if (!bitcoin.isValidDeterministicKey(key58))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_PRIVATE_KEY);

		Long balance = bitcoin.getWalletBalance(key58);
		if (balance == null)
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.FOREIGN_BLOCKCHAIN_NETWORK_ISSUE);

		return balance.toString();
	}

	@POST
	@Path("/wallettransactions")
	@Operation(
		summary = "Returns transactions for hierarchical, deterministic BIP32 wallet",
		description = "Supply BIP32 'm' private/public key in base58, starting with 'xprv'/'xpub' for mainnet, 'tprv'/'tpub' for testnet",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.TEXT_PLAIN,
				schema = @Schema(
					type = "string",
					description = "BIP32 'm' private/public key in base58",
					example = "tpubD6NzVbkrYhZ4XTPc4btCZ6SMgn8CxmWkj6VBVZ1tfcJfMq4UwAjZbG8U74gGSypL9XBYk2R2BLbDBe8pcEyBKM1edsGQEPKXNbEskZozeZc"
				)
			)
		),
		responses = {
			@ApiResponse(
				content = @Content(array = @ArraySchema( schema = @Schema( implementation = SimpleTransaction.class ) ) )
			)
		}
	)
	@ApiErrors({ApiError.INVALID_PRIVATE_KEY, ApiError.FOREIGN_BLOCKCHAIN_NETWORK_ISSUE})
	public List<SimpleTransaction> getBitcoinWalletTransactions(String key58) {
		Security.checkApiCallAllowed(request);

		Bitcoin bitcoin = Bitcoin.getInstance();

		if (!bitcoin.isValidDeterministicKey(key58))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_PRIVATE_KEY);

		try {
			return bitcoin.getWalletTransactions(key58);
		} catch (ForeignBlockchainException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.FOREIGN_BLOCKCHAIN_NETWORK_ISSUE);
		}
	}

	@POST
	@Path("/send")
	@Operation(
		summary = "Sends BTC from hierarchical, deterministic BIP32 wallet to specific address",
		description = "Currently only supports 'legacy' P2PKH Bitcoin addresses. Supply BIP32 'm' private key in base58, starting with 'xprv' for mainnet, 'tprv' for testnet",
		requestBody = @RequestBody(
			required = true,
			content = @Content(
				mediaType = MediaType.APPLICATION_JSON,
				schema = @Schema(
					implementation = BitcoinSendRequest.class
				)
			)
		),
		responses = {
			@ApiResponse(
				content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "string", description = "transaction hash"))
			)
		}
	)
	@ApiErrors({ApiError.INVALID_PRIVATE_KEY, ApiError.INVALID_CRITERIA, ApiError.INVALID_ADDRESS, ApiError.FOREIGN_BLOCKCHAIN_BALANCE_ISSUE, ApiError.FOREIGN_BLOCKCHAIN_NETWORK_ISSUE})
	public String sendBitcoin(BitcoinSendRequest bitcoinSendRequest) {
		Security.checkApiCallAllowed(request);

		if (bitcoinSendRequest.bitcoinAmount <= 0)
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);

		if (bitcoinSendRequest.feePerByte != null && bitcoinSendRequest.feePerByte <= 0)
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);

		Bitcoin bitcoin = Bitcoin.getInstance();

		if (!bitcoin.isValidAddress(bitcoinSendRequest.receivingAddress))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ADDRESS);

		if (!bitcoin.isValidDeterministicKey(bitcoinSendRequest.xprv58))
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_PRIVATE_KEY);

		Transaction spendTransaction = bitcoin.buildSpend(bitcoinSendRequest.xprv58,
				bitcoinSendRequest.receivingAddress,
				bitcoinSendRequest.bitcoinAmount,
				bitcoinSendRequest.feePerByte);

		if (spendTransaction == null)
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.FOREIGN_BLOCKCHAIN_BALANCE_ISSUE);

		try {
			bitcoin.broadcastTransaction(spendTransaction);
		} catch (ForeignBlockchainException e) {
			throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.FOREIGN_BLOCKCHAIN_NETWORK_ISSUE);
		}

		return spendTransaction.getTxId().toString();
	}

}