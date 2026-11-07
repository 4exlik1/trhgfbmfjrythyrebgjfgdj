package com.example.teamwars;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.ColorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;

import java.util.*;
import java.util.function.Predicate;

public class TWCommands {

    private static final Predicate<CommandSourceStack> OP = s -> s.hasPermission(2);

    private static final SuggestionProvider<CommandSourceStack> TEAMS = (c, b) ->
            SharedSuggestionProvider.suggest(c.getSource().getServer().getScoreboard().getTeamNames(), b);

    private static final SuggestionProvider<CommandSourceStack> ZONES = (c, b) ->
            SharedSuggestionProvider.suggest(ZoneData.get(c.getSource().getServer()).zones.keySet(), b);

    private static final ChatFormatting[] COLORS = {
            ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.YELLOW,
            ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD, ChatFormatting.WHITE
    };

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("tw")

                // ---------- КОМАНДЫ ----------
                .then(Commands.literal("team")
                        .then(Commands.literal("list").executes(TWCommands::listTeams))

                        .then(Commands.literal("create").requires(OP)
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(c -> createTeam(c, ChatFormatting.WHITE))
                                        .then(Commands.argument("color", ColorArgument.color())
                                                .executes(c -> createTeam(c, ColorArgument.getColor(c, "color"))))))

                        .then(Commands.literal("delete").requires(OP)
                                .then(Commands.argument("name", StringArgumentType.word()).suggests(TEAMS)
                                        .executes(TWCommands::deleteTeam)))

                        .then(Commands.literal("join").requires(OP)
                                .then(Commands.argument("name", StringArgumentType.word()).suggests(TEAMS)
                                        .executes(c -> join(c, List.of(c.getSource().getPlayerOrException())))
                                        .then(Commands.argument("players", EntityArgument.players())
                                                .executes(c -> join(c, EntityArgument.getPlayers(c, "players"))))))

                        .then(Commands.literal("leave").requires(OP)
                                .executes(c -> leave(c, List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(c -> leave(c, EntityArgument.getPlayers(c, "players")))))

                        // автоматическое деление всех игроков онлайн на N команд
                        .then(Commands.literal("split").requires(OP)
                                .then(Commands.argument("count", IntegerArgumentType.integer(2, COLORS.length))
                                        .executes(TWCommands::split))))

                // ---------- КИЛЛЫ ----------
                .then(Commands.literal("kills")
                        .executes(TWCommands::showKills)
                        .then(Commands.literal("reset").requires(OP).executes(TWCommands::resetKills))
                        .then(Commands.literal("sidebar").requires(OP)
                                .then(Commands.literal("on").executes(c -> sidebar(c, true)))
                                .then(Commands.literal("off").executes(c -> sidebar(c, false)))))

                // ---------- ЗОНЫ ЗАХВАТА ----------
                .then(Commands.literal("zone")
                        .then(Commands.literal("list").executes(TWCommands::listZones))

                        // /tw zone create <имя> <pos1> <pos2> [секунд]
                        .then(Commands.literal("create").requires(OP)
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                                .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                                        .executes(c -> createZone(c, 30))
                                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                                                .executes(c -> createZone(c, IntegerArgumentType.getInteger(c, "seconds"))))))))

                        // /tw zone here <имя> <радиус> [секунд] — зона вокруг игрока на всю высоту
                        .then(Commands.literal("here").requires(OP)
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 500))
                                                .executes(c -> zoneHere(c, 30))
                                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                                        .executes(c -> zoneHere(c, IntegerArgumentType.getInteger(c, "seconds")))))))

                        .then(Commands.literal("remove").requires(OP)
                                .then(Commands.argument("name", StringArgumentType.word()).suggests(ZONES)
                                        .executes(TWCommands::removeZone)))

                        .then(Commands.literal("reset").requires(OP)
                                .then(Commands.argument("name", StringArgumentType.word()).suggests(ZONES)
                                        .executes(TWCommands::resetZone)))));
    }

    // ===================== КОМАНДЫ =====================

    private static int createTeam(CommandContext<CommandSourceStack> c, ChatFormatting color) {
        Scoreboard sb = c.getSource().getServer().getScoreboard();
        String name = StringArgumentType.getString(c, "name");
        if (sb.getPlayerTeam(name) != null) {
            c.getSource().sendFailure(Component.literal("Команда «" + name + "» уже существует"));
            return 0;
        }
        makeTeam(sb, name, color);
        c.getSource().sendSuccess(() -> Component.literal("Команда «" + name + "» создана"), true);
        return 1;
    }

    private static PlayerTeam makeTeam(Scoreboard sb, String name, ChatFormatting color) {
        PlayerTeam t = sb.addPlayerTeam(name);
        t.setColor(color);
        t.setPlayerPrefix(Component.literal("[" + name + "] ").withStyle(color));
        t.setAllowFriendlyFire(false);
        return t;
    }

    private static int deleteTeam(CommandContext<CommandSourceStack> c) {
        Scoreboard sb = c.getSource().getServer().getScoreboard();
        String name = StringArgumentType.getString(c, "name");
        PlayerTeam t = sb.getPlayerTeam(name);
        if (t == null) {
            c.getSource().sendFailure(Component.literal("Нет такой команды"));
            return 0;
        }
        sb.removePlayerTeam(t);
        c.getSource().sendSuccess(() -> Component.literal("Команда «" + name + "» удалена"), true);
        return 1;
    }

    private static int join(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        Scoreboard sb = c.getSource().getServer().getScoreboard();
        PlayerTeam t = sb.getPlayerTeam(StringArgumentType.getString(c, "name"));
        if (t == null) {
            c.getSource().sendFailure(Component.literal("Нет такой команды"));
            return 0;
        }
        for (ServerPlayer p : players) sb.addPlayerToTeam(p.getScoreboardName(), t);
        c.getSource().sendSuccess(() -> Component.literal("Добавлено игроков: " + players.size()), true);
        return players.size();
    }

    private static int leave(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        Scoreboard sb = c.getSource().getServer().getScoreboard();
        int n = 0;
        for (ServerPlayer p : players) {
            if (p.getTeam() instanceof PlayerTeam t) {
                sb.removePlayerFromTeam(p.getScoreboardName(), t);
                n++;
            }
        }
        int fn = n;
        c.getSource().sendSuccess(() -> Component.literal("Убрано из команд: " + fn), true);
        return n;
    }

    private static int split(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        Scoreboard sb = server.getScoreboard();
        int count = IntegerArgumentType.getInteger(c, "count");

        List<PlayerTeam> teams = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ChatFormatting col = COLORS[i];
            PlayerTeam t = sb.getPlayerTeam(col.getName());
            if (t == null) t = makeTeam(sb, col.getName(), col);
            teams.add(t);
        }

        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        Collections.shuffle(players);
        for (int i = 0; i < players.size(); i++) {
            sb.addPlayerToTeam(players.get(i).getScoreboardName(), teams.get(i % count));
        }
        c.getSource().sendSuccess(() -> Component.literal(
                "Игроки (" + players.size() + ") случайно разделены на " + count + " команд(ы)"), true);
        return players.size();
    }

    private static int listTeams(CommandContext<CommandSourceStack> c) {
        Scoreboard sb = c.getSource().getServer().getScoreboard();
        if (sb.getPlayerTeams().isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("Команд пока нет"), false);
            return 0;
        }
        for (PlayerTeam t : sb.getPlayerTeams()) {
            c.getSource().sendSuccess(() -> Component.empty()
                    .append(t.getFormattedDisplayName())
                    .append(Component.literal(" (" + t.getPlayers().size() + "): " + String.join(", ", t.getPlayers()))), false);
        }
        return sb.getPlayerTeams().size();
    }

    // ===================== КИЛЛЫ =====================

    private static int showKills(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        Scoreboard sb = server.getScoreboard();
        Objective o = GameEvents.killsObjective(server);

        List<Score> scores = new ArrayList<>(sb.getPlayerScores(o));
        scores.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));

        Map<String, Integer> byName = new HashMap<>();
        for (Score s : scores) byName.put(s.getOwner(), s.getScore());

        c.getSource().sendSuccess(() -> Component.literal("§6=== Киллы: игроки ==="), false);
        int shown = 0;
        for (Score s : scores) {
            if (s.getScore() <= 0 || shown >= 10) break;
            shown++;
            int place = shown;
            c.getSource().sendSuccess(() -> Component.literal(
                    "§e" + place + ". §f" + s.getOwner() + " §7— §a" + s.getScore()), false);
        }
        if (shown == 0) c.getSource().sendSuccess(() -> Component.literal("§7Пока никто не убивал"), false);

        c.getSource().sendSuccess(() -> Component.literal("§6=== Киллы: команды ==="), false);
        for (PlayerTeam t : sb.getPlayerTeams()) {
            int sum = 0;
            for (String n : t.getPlayers()) sum += byName.getOrDefault(n, 0);
            int fsum = sum;
            c.getSource().sendSuccess(() -> Component.empty()
                    .append(t.getFormattedDisplayName())
                    .append(Component.literal(" §7— §a" + fsum)), false);
        }
        return 1;
    }

    private static int resetKills(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        Scoreboard sb = server.getScoreboard();
        Objective o = GameEvents.killsObjective(server);
        for (Score s : new ArrayList<>(sb.getPlayerScores(o))) sb.resetPlayerScore(s.getOwner(), o);
        c.getSource().sendSuccess(() -> Component.literal("Счётчик убийств сброшен"), true);
        return 1;
    }

    private static int sidebar(CommandContext<CommandSourceStack> c, boolean on) {
        MinecraftServer server = c.getSource().getServer();
        Objective o = GameEvents.killsObjective(server);
        server.getScoreboard().setDisplayObjective(1, on ? o : null); // 1 = sidebar
        c.getSource().sendSuccess(() -> Component.literal("Таблица киллов " + (on ? "включена" : "выключена")), true);
        return 1;
    }

    // ===================== ЗОНЫ =====================

    private static int createZone(CommandContext<CommandSourceStack> c, int seconds) throws CommandSyntaxException {
        BlockPos a = BlockPosArgument.getBlockPos(c, "pos1");
        BlockPos b = BlockPosArgument.getBlockPos(c, "pos2");
        AABB box = new AABB(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
        return addZone(c, box, seconds);
    }

    private static int zoneHere(CommandContext<CommandSourceStack> c, int seconds) {
        Vec3 p = c.getSource().getPosition();
        int r = IntegerArgumentType.getInteger(c, "radius");
        var level = c.getSource().getLevel();
        AABB box = new AABB(p.x - r, level.getMinBuildHeight(), p.z - r,
                p.x + r, level.getMaxBuildHeight(), p.z + r);
        return addZone(c, box, seconds);
    }

    private static int addZone(CommandContext<CommandSourceStack> c, AABB box, int seconds) {
        String name = StringArgumentType.getString(c, "name");
        ZoneData data = ZoneData.get(c.getSource().getServer());
        if (data.zones.containsKey(name)) {
            c.getSource().sendFailure(Component.literal("Зона «" + name + "» уже существует"));
            return 0;
        }
        ZoneData.Zone z = new ZoneData.Zone();
        z.name = name;
        z.dim = c.getSource().getLevel().dimension().location().toString();
        z.box = box;
        z.seconds = seconds;
        data.zones.put(name, z);
        data.setDirty();
        c.getSource().sendSuccess(() -> Component.literal(
                "Зона «" + name + "» создана (захват " + seconds + " c)"), true);
        return 1;
    }

    private static int removeZone(CommandContext<CommandSourceStack> c) {
        ZoneData data = ZoneData.get(c.getSource().getServer());
        String name = StringArgumentType.getString(c, "name");
        if (data.zones.remove(name) == null) {
            c.getSource().sendFailure(Component.literal("Нет такой зоны"));
            return 0;
        }
        data.setDirty();
        c.getSource().sendSuccess(() -> Component.literal("Зона «" + name + "» удалена"), true);
        return 1;
    }

    private static int resetZone(CommandContext<CommandSourceStack> c) {
        ZoneData data = ZoneData.get(c.getSource().getServer());
        ZoneData.Zone z = data.zones.get(StringArgumentType.getString(c, "name"));
        if (z == null) {
            c.getSource().sendFailure(Component.literal("Нет такой зоны"));
            return 0;
        }
        z.owner = "";
        z.capturing = "";
        z.progress = 0;
        data.setDirty();
        c.getSource().sendSuccess(() -> Component.literal("Зона «" + z.name + "» сброшена"), true);
        return 1;
    }

    private static int listZones(CommandContext<CommandSourceStack> c) {
        ZoneData data = ZoneData.get(c.getSource().getServer());
        if (data.zones.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("Зон пока нет"), false);
            return 0;
        }
        for (ZoneData.Zone z : data.zones.values()) {
            String owner = z.owner.isEmpty() ? "никто" : z.owner;
            c.getSource().sendSuccess(() -> Component.literal(
                    "§e" + z.name + " §7[" + z.dim + "] §fвладелец: §a" + owner
                            + " §7(" + (int) z.box.minX + "," + (int) z.box.minZ + " → "
                            + (int) z.box.maxX + "," + (int) z.box.maxZ + "), захват " + z.seconds + " c"), false);
        }
        return data.zones.size();
    }
}
