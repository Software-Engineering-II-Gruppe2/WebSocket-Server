package model.properties;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseProperty {
    protected int id;
    protected Integer ownerId; // kann null sein
    protected String name;
    protected int purchasePrice;
    protected int mortgageValue;
    protected String image;

    @JsonProperty("isMortgaged")
    protected boolean isMortgaged;
}

