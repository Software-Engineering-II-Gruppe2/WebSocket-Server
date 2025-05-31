package data.Deals;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DealProposalMessage {
    private String type = "DEAL_PROPOSAL";
    private String fromPlayerId;
    private String toPlayerId;
    private List<Integer> requestedPropertyIds; // vom anderen Spieler
    private List<Integer> offeredPropertyIds;   // vom initiierenden Spieler
    private int offeredMoney;
}

