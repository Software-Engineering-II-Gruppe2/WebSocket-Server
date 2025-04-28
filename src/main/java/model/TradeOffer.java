package model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class TradeOffer {
        private String offeringPlayerId;
        private String receivingPlayerId;
        private List<Integer> offeredPropertyIds;
        private int offeredMoney;
        private List<Integer> requestedPropertyIds;
        private int requestedMoney;
    }
