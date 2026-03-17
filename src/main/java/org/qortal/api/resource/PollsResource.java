package org.qortal.api.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.qortal.api.ApiError;
import org.qortal.api.ApiErrors;
import org.qortal.api.ApiException;
import org.qortal.api.ApiExceptionFactory;
import org.qortal.api.model.PollVotes;
import org.qortal.crypto.Crypto;
import org.qortal.data.account.AccountData;
import org.qortal.data.transaction.CreatePollTransactionData;
import org.qortal.data.transaction.VoteOnPollTransactionData;
import org.qortal.data.voting.PollData;
import org.qortal.data.voting.PollOptionData;
import org.qortal.data.voting.VoteOnPollData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;
import org.qortal.transaction.Transaction;
import org.qortal.transform.TransformationException;
import org.qortal.transform.transaction.CreatePollTransactionTransformer;
import org.qortal.transform.transaction.VoteOnPollTransactionTransformer;
import org.qortal.utils.Base58;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/polls")
@Tag(name = "Polls")
public class PollsResource {
    @Context
    HttpServletRequest request;

    @GET
    @Operation(
            summary = "List all polls",
            responses = {
                    @ApiResponse(
                            description = "poll info",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON,
                                    array = @ArraySchema(schema = @Schema(implementation = PollData.class))
                            )
                    )
            }
    )
    @ApiErrors({ApiError.REPOSITORY_ISSUE})
    public List<PollData> getAllPolls(@Parameter(
            ref = "limit"
    ) @QueryParam("limit") Integer limit, @Parameter(
            ref = "offset"
    ) @QueryParam("offset") Integer offset, @Parameter(
            ref = "reverse"
    ) @QueryParam("reverse") Boolean reverse) {
            try (final Repository repository = RepositoryManager.getRepository()) {
		    List<PollData> allPollData = repository.getVotingRepository().getAllPolls(limit, offset, reverse);
		    return allPollData;
            } catch (DataException e) {
		    throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
            }
    }

    @GET
    @Path("/{pollName}")
    @Operation(
            summary = "Info on poll",
            responses = {
                    @ApiResponse(
                            description = "poll info",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON,
                                    schema = @Schema(implementation = PollData.class)
                            )
                    )
            }
    )
    @ApiErrors({ApiError.REPOSITORY_ISSUE})
    public PollData getPollData(@PathParam("pollName") String pollName) {
            try (final Repository repository = RepositoryManager.getRepository()) {
                    PollData pollData = repository.getVotingRepository().fromPollName(pollName);
                    if (pollData == null)
                            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.POLL_NO_EXISTS);

                    return pollData;
            } catch (ApiException e) {
                    throw e;
            } catch (DataException e) {
                    throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
            }
    }

    @GET
    @Path("/votes/{pollName}")
    @Operation(
            summary = "Votes on poll",
            responses = {
                    @ApiResponse(
                            description = "poll votes",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON,
                                    schema = @Schema(implementation = PollVotes.class)
                            )
                    )
            }
    )
    @ApiErrors({ApiError.REPOSITORY_ISSUE})
    public PollVotes getPollVotes(@PathParam("pollName") String pollName, @QueryParam("onlyCounts") Boolean onlyCounts) {
            try (final Repository repository = RepositoryManager.getRepository()) {
                    PollData pollData = repository.getVotingRepository().fromPollName(pollName);
                    if (pollData == null)
                            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.POLL_NO_EXISTS);

                    List<VoteOnPollData> votes = repository.getVotingRepository().getVotes(pollName);

                    // Initialize map for counting votes
                    Map<String, Integer> voteCountMap = new HashMap<>();
                    for (PollOptionData optionData : pollData.getPollOptions()) {
                            voteCountMap.put(optionData.getOptionName(), 0);
                    }
                    // Initialize map for counting vote weights
                    Map<String, Integer> voteWeightMap = new HashMap<>();
                    for (PollOptionData optionData : pollData.getPollOptions()) {
                            voteWeightMap.put(optionData.getOptionName(), 0);
                    }

                    int totalVotes = 0;
                    int totalWeight = 0;
                    for (VoteOnPollData vote : votes) {
                            String voter = Crypto.toAddress(vote.getVoterPublicKey());
                            AccountData voterData = repository.getAccountRepository().getAccount(voter);
                            int voteWeight = voterData.getBlocksMinted() + voterData.getBlocksMintedPenalty();
                            if (voteWeight < 0) voteWeight = 0;
                            totalWeight += voteWeight;

                            String selectedOption = pollData.getPollOptions().get(vote.getOptionIndex()).getOptionName();
                            if (voteCountMap.containsKey(selectedOption)) {
                                    voteCountMap.put(selectedOption, voteCountMap.get(selectedOption) + 1);
                                    voteWeightMap.put(selectedOption, voteWeightMap.get(selectedOption) + voteWeight);
                                    totalVotes++;
                            }
                    }

                    // Convert map to list of VoteInfo
                    List<PollVotes.OptionCount> voteCounts = voteCountMap.entrySet().stream()
                        .map(entry -> new PollVotes.OptionCount(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList());
                    // Convert map to list of WeightInfo
                    List<PollVotes.OptionWeight> voteWeights = voteWeightMap.entrySet().stream()
                        .map(entry -> new PollVotes.OptionWeight(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList());
    
                    if (onlyCounts != null && onlyCounts) {
                        return new PollVotes(null, totalVotes, totalWeight, voteCounts, voteWeights);
                    } else {
                        return new PollVotes(votes, totalVotes, totalWeight, voteCounts, voteWeights);
                    }
            } catch (ApiException e) {
                    throw e;
            } catch (DataException e) {
                    throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
            }
    }

    @POST
    @Path("/create")
    @Operation(
            summary = "Build raw, unsigned, CREATE_POLL transaction",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(
                                    implementation = CreatePollTransactionData.class
                            )
                    )
            ),
            responses = {
                    @ApiResponse(
                            description = "raw, unsigned, CREATE_POLL transaction encoded in Base58",
                            content = @Content(
                                    mediaType = MediaType.TEXT_PLAIN,
                                    schema = @Schema(
                                            type = "string"
                                    )
                            )
                    )
            }
    )
    @ApiErrors({ApiError.NON_PRODUCTION, ApiError.TRANSACTION_INVALID, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE})
    public String CreatePoll(CreatePollTransactionData transactionData) {
        if (Settings.getInstance().isApiRestricted())
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.NON_PRODUCTION);

        try (final Repository repository = RepositoryManager.getRepository()) {
            Transaction transaction = Transaction.fromData(repository, transactionData);

            Transaction.ValidationResult result = transaction.isValidUnconfirmed();
            if (result != Transaction.ValidationResult.OK)
                throw TransactionsResource.createTransactionInvalidException(request, result);

            byte[] bytes = CreatePollTransactionTransformer.toBytes(transactionData);
            return Base58.encode(bytes);
        } catch (TransformationException e) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
        } catch (DataException e) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
        }
    }

    @POST
    @Path("/vote")
    @Operation(
            summary = "Build raw, unsigned, VOTE_ON_POLL transaction",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(
                                    implementation = VoteOnPollTransactionData.class
                            )
                    )
            ),
            responses = {
                    @ApiResponse(
                            description = "raw, unsigned, VOTE_ON_POLL transaction encoded in Base58",
                            content = @Content(
                                    mediaType = MediaType.TEXT_PLAIN,
                                    schema = @Schema(
                                            type = "string"
                                    )
                            )
                    )
            }
    )
    @ApiErrors({ApiError.NON_PRODUCTION, ApiError.TRANSACTION_INVALID, ApiError.TRANSFORMATION_ERROR, ApiError.REPOSITORY_ISSUE})
    public String VoteOnPoll(VoteOnPollTransactionData transactionData) {
        if (Settings.getInstance().isApiRestricted())
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.NON_PRODUCTION);

        try (final Repository repository = RepositoryManager.getRepository()) {
            Transaction transaction = Transaction.fromData(repository, transactionData);

            Transaction.ValidationResult result = transaction.isValidUnconfirmed();
            if (result != Transaction.ValidationResult.OK)
                throw TransactionsResource.createTransactionInvalidException(request, result);

            byte[] bytes = VoteOnPollTransactionTransformer.toBytes(transactionData);
            return Base58.encode(bytes);
        } catch (TransformationException e) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.TRANSFORMATION_ERROR, e);
        } catch (DataException e) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.REPOSITORY_ISSUE, e);
        }
    }
    
}
