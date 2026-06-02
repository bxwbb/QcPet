package org.bxwbb.qcpet.gui;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public interface SelectPetGui {

    Map<Player, Integer> page = new HashMap<>();

    void open(Player player);

    void update(Player player, int page);

}
