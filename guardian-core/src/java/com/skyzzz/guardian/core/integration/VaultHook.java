package com.skyzzz.guardian.core.integration;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Optional economy fines and clean-play rewards. Entirely inert when Vault or an
 * economy provider is missing.
 */
public final class VaultHook {

    private Object economy;
    private boolean available;

    public VaultHook() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                return;
            }
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager()
                    .getRegistration(economyClass);
            if (provider != null) {
                economy = provider.getProvider();
                available = true;
            }
        } catch (Throwable ignored) {
            available = false;
        }
    }

    public boolean available() {
        return available;
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!available) {
            return false;
        }
        try {
            Object result = economy.getClass()
                    .getMethod("withdrawPlayer", OfflinePlayer.class, double.class)
                    .invoke(economy, player, amount);
            return result != null && (boolean) result.getClass().getMethod("transactionSuccess").invoke(result);
        } catch (Throwable throwable) {
            return false;
        }
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (!available) {
            return false;
        }
        try {
            Object result = economy.getClass()
                    .getMethod("depositPlayer", OfflinePlayer.class, double.class)
                    .invoke(economy, player, amount);
            return result != null && (boolean) result.getClass().getMethod("transactionSuccess").invoke(result);
        } catch (Throwable throwable) {
            return false;
        }
    }
}