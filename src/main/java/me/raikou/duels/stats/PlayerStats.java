package me.raikou.duels.stats;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PlayerStats {
    private int wins;
    private int losses;
    private int kills;
    private int deaths;
}
