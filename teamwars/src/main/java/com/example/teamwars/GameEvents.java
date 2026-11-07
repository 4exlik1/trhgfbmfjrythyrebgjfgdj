package com.example.teamwars;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GameEvents {

    public static final String KILLS = "tw_kills";
    private int tick = 0;

    /** Возвращает (или создаёт) счётчик убийств. */
    public static Objective killsObjective(MinecraftServer server) {
        Scoreboard sb = server.getScoreboard();
        Objective o = sb.getObjective(KILLS);
        if (o == null) {
            o = sb.addObjective(KILLS, ObjectiveCriteria.DUMMY, Component.literal("Киллы"),
                    ObjectiveCriteria.RenderType.INTEGER);
        }
        return o;
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) {
        TWCommands.register(e.getDispatcher());
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer victim)) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer killer)) return;
        if (killer == victim) return;

        // Убийство союзника по команде не засчитывается
        if (killer.getTeam() != null && killer.getTeam().isAlliedTo(victim.getTeam())) return;

        MinecraftServer server = killer.getServer();
        if (server == null) return;
        Objective o = killsObjective(server);
        server.getScoreboard().getOrCreatePlayerScore(killer.getScoreboardName(), o).increment();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (++tick % 20 != 0) return; // раз в секунду

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ZoneData data = ZoneData.get(server);
        Scoreboard sb = server.getScoreboard();

        for (ZoneData.Zone z : data.zones.values()) {
            ServerLevel level = server.getLevel(
                    ResourceKey.create(Registries.DIMENSION, new ResourceLocation(z.dim)));
            if (level == null) continue;

            List<ServerPlayer> inside = level.getEntitiesOfClass(ServerPlayer.class, z.box,
                    p -> p.isAlive() && !p.isSpectator());

            Set<String> teams = new HashSet<>();
            for (ServerPlayer p : inside) {
                if (p.getTeam() != null) teams.add(p.getTeam().getName());
            }

            String msg = null;
            if (teams.size() > 1) {
                msg = "§cЗона «" + z.name + "» оспаривается!";
            } else if (teams.size() == 1) {
                String t = teams.iterator().next();
                if (t.equals(z.owner)) {
                    z.progress = 0;
                    z.capturing = "";
                    msg = "§aВы удерживаете зону «" + z.name + "»";
                } else {
                    if (!t.equals(z.capturing)) {
                        z.capturing = t;
                        z.progress = 0;
                    }
                    z.progress++;
                    if (z.progress >= z.seconds) {
                        z.owner = t;
                        z.capturing = "";
                        z.progress = 0;
                        PlayerTeam team = sb.getPlayerTeam(t);
                        Component name = team != null ? team.getFormattedDisplayName() : Component.literal(t);
                        server.getPlayerList().broadcastSystemMessage(
                                Component.literal("§6Зона «" + z.name + "» захвачена командой ").append(name), false);
                        msg = "§aЗона «" + z.name + "» захвачена!";
                    } else {
                        msg = "§eЗахват «" + z.name + "»: " + z.progress + "/" + z.seconds + " c";
                    }
                }
            } else if (z.progress > 0) {
                // никого нет — прогресс постепенно откатывается
                z.progress--;
                if (z.progress == 0) z.capturing = "";
            }

            if (msg != null) {
                Component c = Component.literal(msg);
                for (ServerPlayer p : inside) p.displayClientMessage(c, true);
            }
        }
        data.setDirty();
    }
}
