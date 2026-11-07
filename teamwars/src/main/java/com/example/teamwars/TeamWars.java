package com.example.teamwars;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

@Mod(TeamWars.MODID)
public class TeamWars {
    public static final String MODID = "teamwars";

    public TeamWars() {
        MinecraftForge.EVENT_BUS.register(new GameEvents());
    }
}
