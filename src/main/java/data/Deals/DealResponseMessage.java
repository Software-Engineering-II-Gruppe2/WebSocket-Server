package data.Deals;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DealResponseMessage {
    private String type = "DEAL_RESPONSE";
    private String fromPlayerId;
    private String toPlayerId;
    private DealResponseType responseType;
    private List<Integer> counterPropertyIds; // falls Gegenvorschlag
    private int counterMoney;
}

