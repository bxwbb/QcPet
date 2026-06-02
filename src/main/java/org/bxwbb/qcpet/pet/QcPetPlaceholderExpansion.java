package org.bxwbb.qcpet.pet;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bxwbb.qcpet.QcPet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class QcPetPlaceholderExpansion extends PlaceholderExpansion {

    private final QcPet plugin;

    public QcPetPlaceholderExpansion(QcPet plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "qcpet";
    }

    @Override
    public @NotNull String getAuthor() {
        return "BXWBB";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equalsIgnoreCase("key")) return "";
        return params;
    }
}
