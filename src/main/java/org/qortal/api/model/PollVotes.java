package org.qortal.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.qortal.data.voting.VoteOnPollData;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;

@Schema(description = "Poll vote info, including voters")
// All properties to be converted to JSON via JAX-RS
@XmlAccessorType(XmlAccessType.FIELD)
public class PollVotes {

    @Schema(description = "List of individual votes")
    @XmlElement(name = "votes")
    public List<VoteOnPollData> votes;

    @Schema(description = "Total number of votes")
    public Integer totalVotes;

    @Schema(description = "Total weight of votes")
    public Integer totalWeight;

    @Schema(description = "List of vote counts for each option")
    public List<OptionCount> voteCounts;

    @Schema(description = "List of vote weights for each option")
    public List<OptionWeight> voteWeights;

    // For JAX-RS
    protected PollVotes() {
    }

    public PollVotes(List<VoteOnPollData> votes, Integer totalVotes, Integer totalWeight, List<OptionCount> voteCounts, List<OptionWeight> voteWeights) {
        this.votes = votes;
        this.totalVotes = totalVotes;
        this.totalWeight = totalWeight;
        this.voteCounts = voteCounts;
        this.voteWeights = voteWeights;
    }

    @Schema(description = "Vote info")
    // All properties to be converted to JSON via JAX-RS
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class OptionCount {
        @Schema(description = "Option name")
        public String optionName;

        @Schema(description = "Vote count")
        public Integer voteCount;

        // For JAX-RS
        protected OptionCount() {
        }

        public OptionCount(String optionName, Integer voteCount) {
            this.optionName = optionName;
            this.voteCount = voteCount;
        }
    }

    @Schema(description = "Vote weights")
    // All properties to be converted to JSON via JAX-RS
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class OptionWeight {
        @Schema(description = "Option name")
        public String optionName;

        @Schema(description = "Vote weight")
        public Integer voteWeight;

        // For JAX-RS
        protected OptionWeight() {
        }

        public OptionWeight(String optionName, Integer voteWeight) {
            this.optionName = optionName;
            this.voteWeight = voteWeight;
        }
    }
}
