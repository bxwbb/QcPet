package org.bxwbb.qcpet.command;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bxwbb.qcpet.QcPet;
import org.bxwbb.qcpet.pet.Pet;
import org.bxwbb.qcpet.pet.PetConfig;
import org.bxwbb.qcpet.utils.TextComponentUtil;
import org.bxwbb.qcpet.utils.saveUtil.SaveException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class QcPetCommand implements TabExecutor {

    private static final List<String> SUB_COMMANDS = List.of(
            "help",
            "list",
            "select",
            "show",
            "hide",
            "showdisplay",
            "hidedisplay",
            "bath",
            "feed",
            "info",
            "reload",
            "give",
            "remove",
            "addexp",
            "addlevel",
            "storage"
    );
    private static final List<String> TARGET_PLAYER_COMMANDS = List.of(
            "give",
            "remove",
            "addexp",
            "addlevel",
            "show",
            "hide",
            "info",
            "showdisplay",
            "hidedisplay"
    );

    private final QcPet plugin;

    public QcPetCommand(QcPet plugin) {
        this.plugin = plugin;
        PluginCommand command = plugin.getCommand("qcpet");
        if (command == null) {
            throw new IllegalStateException("Command qcpet is not defined in plugin.yml");
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        try {
            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                return handleHelp(sender);
            }

            String subCommand = args[0].toLowerCase(Locale.ROOT);
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            return switch (subCommand) {
                case "list" -> handleList(sender);
                case "select" -> handleSelect(sender);
                case "show" -> handleShow(sender, subArgs);
                case "hide" -> handleHide(sender, subArgs);
                case "showdisplay" -> handleShowDisplay(sender, subArgs);
                case "hidedisplay" -> handleHideDisplay(sender, subArgs);
                case "bath" -> handleBath(sender, subArgs);
                case "feed" -> handleFeed(sender, subArgs);
                case "info" -> handleInfo(sender, subArgs);
                case "reload" -> handleReload(sender);
                case "give" -> handleGive(sender, subArgs);
                case "remove" -> handleRemove(sender, subArgs);
                case "addexp" -> handleAddExp(sender, subArgs);
                case "addlevel" -> handleAddLevel(sender, subArgs);
                case "storage" -> handleStorage(sender);
                default -> {
                    sender.sendMessage(error("未知子命令，请使用 /" + label + " help 查看帮助。"));
                    yield true;
                }
            };
        } catch (IllegalArgumentException | SaveException exception) {
            plugin.getLogger().warning("执行命令 /" + label + " 失败: " + exception.getMessage());
            sender.sendMessage(error("命令执行失败: " + exception.getMessage()));
            return true;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filterByPrefix(getAllowedSubCommands(sender), args[0]);
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (TARGET_PLAYER_COMMANDS.contains(subCommand) && sender.hasPermission("qcpet.command." + subCommand)) {
                List<String> suggestions = new ArrayList<>(getOnlinePlayerNames());
                if (isPetIdCommand(subCommand) && sender instanceof Player player) {
                    suggestions.addAll(getPetIdSelectors(player));
                }
                return filterByPrefix(suggestions, args[1]);
            }
            if (isPetIdCommand(subCommand) && sender instanceof Player player) {
                return filterByPrefix(getPetIdSelectors(player), args[1]);
            }
            if (isPetTemplateCommand(subCommand)) {
                return filterByPrefix(new ArrayList<>(plugin.getPetConfigManger().pets.keySet()), args[1]);
            }
        }

        if (args.length == 3) {
            if (subCommand.equals("give")) {
                return filterByPrefix(new ArrayList<>(plugin.getPetConfigManger().pets.keySet()), args[2]);
            }
            if ((subCommand.equals("remove")
                    || subCommand.equals("addexp")
                    || subCommand.equals("addlevel")
                    || subCommand.equals("info")
                    || subCommand.equals("show")
                    || subCommand.equals("hide")
                    || subCommand.equals("showdisplay")
                    || subCommand.equals("hidedisplay"))
                    && sender.hasPermission("qcpet.command." + subCommand)) {
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target != null) {
                    return filterByPrefix(getPetIdSelectors(target), args[2]);
                }
            }
        }

        return List.of();
    }

    private boolean handleHelp(CommandSender sender) {
        if (!hasPermission(sender, "qcpet.command.help")) {
            return true;
        }
        send(sender, "&6QcPet 命令:");
        send(sender, "&e/qcpet list &7- 查看自己的宠物列表");
        send(sender, "&e/qcpet select &7- 打开宠物选择界面");
        send(sender, "&e/qcpet show [玩家] <宠物ID|*> &7- 让宠物出战并显示");
        send(sender, "&e/qcpet hide [玩家] <宠物ID|*> &7- 收起宠物并取消出战显示");
        send(sender, "&e/qcpet showdisplay [玩家] <宠物ID|*> &7- 仅恢复显示，不改变出战状态");
        send(sender, "&e/qcpet hidedisplay [玩家] <宠物ID|*> &7- 仅隐藏显示，不改变出战状态");
        send(sender, "&e/qcpet bath <宠物ID|*> &7- 给自己的宠物洗澡");
        send(sender, "&e/qcpet feed <宠物ID|*> &7- 给自己的宠物喂食");
        send(sender, "&e/qcpet info [玩家] <宠物ID|*> &7- 查看宠物信息");
        send(sender, "&e/qcpet give [玩家] <模板名> &7- 发放宠物");
        send(sender, "&e/qcpet remove [玩家] <宠物ID|*> &7- 删除宠物");
        send(sender, "&e/qcpet addexp [玩家] <宠物ID|*> <数值> &7- 增加宠物经验");
        send(sender, "&e/qcpet addlevel [玩家] <宠物ID|*> <数值> &7- 增加宠物等级");
        send(sender, "&e/qcpet reload &7- 重载配置");
        send(sender, "&e/qcpet storage &7- 查看存储状态");
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!hasPermission(sender, "qcpet.command.list")) {
            return true;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        List<Pet> pets = plugin.getPetManger().getPets(player);
        if (pets.isEmpty()) {
            send(sender, "&7你当前没有宠物。");
            return true;
        }
        send(sender, "&6你的宠物列表:");
        for (Pet pet : pets) {
            String type = plugin.getPetManger().isBlindBoxPet(pet) ? "???" : pet.type();
            String level = plugin.getPetManger().isBlindBoxPet(pet) ? "???" : String.valueOf(pet.level());
            send(sender, "&e- #" + pet.id() + " &r" + plugin.getPetManger().getDisplayName(pet, player) + " &7[" + type + "] &bLv." + level);
        }
        return true;
    }

    private boolean handleSelect(CommandSender sender) {
        if (!hasPermission(sender, "qcpet.command.select")) {
            return true;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        plugin.getGuiManager().openPetSelectMenu(player);
        return true;
    }

    private boolean handleShow(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "qcpet.command.show")) {
            return true;
        }
        TargetPetSelection selection = resolveTargetPetSelection(sender, args, "show");
        if (selection == null) {
            return true;
        }
        if (selection.allPets()) {
            handleToggleAllPets(sender, selection.target(), true);
            return true;
        }

        long petId = selection.petId();
        plugin.getPetManger().showPetAsync(selection.target(), petId)
                .whenComplete((shown, throwable) -> {
                    if (throwable != null) {
                        sender.sendMessage(error("命令执行失败: " + throwable.getMessage()));
                        return;
                    }
                    if (!shown) {
                        sender.sendMessage(error("未找到可显示的宠物，ID: " + petId));
                        return;
                    }
                    sender.sendMessage(success("已显示 " + selection.target().getName() + " 的宠物，ID: " + petId));
                });
        return true;
    }

    private boolean handleHide(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "qcpet.command.hide")) {
            return true;
        }
        TargetPetSelection selection = resolveTargetPetSelection(sender, args, "hide");
        if (selection == null) {
            return true;
        }
        if (selection.allPets()) {
            handleToggleAllPets(sender, selection.target(), false);
            return true;
        }

        long petId = selection.petId();
        plugin.getPetManger().hidePetAsync(selection.target(), petId)
                .whenComplete((hidden, throwable) -> {
                    if (throwable != null) {
                        sender.sendMessage(error("命令执行失败: " + throwable.getMessage()));
                        return;
                    }
                    if (!hidden) {
                        sender.sendMessage(error("未找到可隐藏的宠物，ID: " + petId));
                        return;
                    }
                    sender.sendMessage(success("已隐藏 " + selection.target().getName() + " 的宠物，ID: " + petId));
                });
        return true;
    }

    private boolean handleShowDisplay(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "qcpet.command.showdisplay")) {
            return true;
        }
        TargetPetSelection selection = resolveTargetPetSelection(sender, args, "showdisplay");
        if (selection == null) {
            return true;
        }
        if (selection.allPets()) {
            handleToggleAllPetDisplays(sender, selection.target(), true);
            return true;
        }

        long petId = selection.petId();
        plugin.getPetManger().showPetDisplayAsync(selection.target(), petId)
                .whenComplete((shown, throwable) -> {
                    if (throwable != null) {
                        sender.sendMessage(error("命令执行失败: " + throwable.getMessage()));
                        return;
                    }
                    if (!shown) {
                        sender.sendMessage(error("未找到可恢复显示的出战宠物，ID: " + petId));
                        return;
                    }
                    sender.sendMessage(success("已恢复 " + selection.target().getName() + " 的宠物显示，ID: " + petId));
                });
        return true;
    }

    private boolean handleHideDisplay(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "qcpet.command.hidedisplay")) {
            return true;
        }
        TargetPetSelection selection = resolveTargetPetSelection(sender, args, "hidedisplay");
        if (selection == null) {
            return true;
        }
        if (selection.allPets()) {
            handleToggleAllPetDisplays(sender, selection.target(), false);
            return true;
        }

        long petId = selection.petId();
        plugin.getPetManger().hidePetDisplayAsync(selection.target(), petId)
                .whenComplete((hidden, throwable) -> {
                    if (throwable != null) {
                        sender.sendMessage(error("命令执行失败: " + throwable.getMessage()));
                        return;
                    }
                    if (!hidden) {
                        sender.sendMessage(error("未找到可隐藏显示的出战宠物，ID: " + petId));
                        return;
                    }
                    sender.sendMessage(success("已隐藏 " + selection.target().getName() + " 的宠物显示，ID: " + petId));
                });
        return true;
    }

    private boolean handleBath(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "qcpet.command.bath")) {
            return true;
        }
        TargetPetSelection selection = resolveSelfPetSelection(sender, args);
        if (selection == null) {
            return true;
        }
        if (selection.allPets()) {
            List<Pet> candidates = plugin.getPetManger().getPets(selection.target()).stream()
                    .filter(plugin.getPetManger()::needsBath)
                    .sorted(Comparator.comparingLong(Pet::id))
                    .toList();
            handleAsyncPetBatch(sender, selection.target(), candidates,
                    pet -> plugin.getPetManger().bathPetAsync(selection.target(), pet.id())
                            .thenApply(updatedPet -> updatedPet != null),
                    selection.target().getName() + " 当前没有需要洗澡的宠物。",
                    "未能给 " + selection.target().getName() + " 的任何宠物洗澡。",
                    "已为 " + selection.target().getName() + " 的宠物洗澡，共 ");
            return true;
        }
        long petId = selection.petId();
        Player player = selection.target();
        Pet current = plugin.getPetManger().getPet(player, petId);
        if (current == null) {
            player.sendMessage(error("未找到宠物，ID: " + petId));
            return true;
        }
        if (!plugin.getPetManger().needsBath(current)) {
            player.sendMessage(error("这只宠物现在还不需要洗澡。"));
            return true;
        }
        plugin.getPetManger().bathPetAsync(player, petId)
                .whenComplete((pet, throwable) -> {
                    if (throwable != null) {
                        player.sendMessage(error("命令执行失败: " + throwable.getMessage()));
                        return;
                    }
                    if (pet == null) {
                        player.sendMessage(error("未找到宠物，ID: " + petId));
                        return;
                    }
                    player.sendMessage(success("宠物已洗澡，获得 " + plugin.getPetManger().getBathRewardExp(pet) + " 经验。"));
                });
        return true;
    }

    private boolean handleFeed(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "qcpet.command.feed")) {
            return true;
        }
        TargetPetSelection selection = resolveSelfPetSelection(sender, args);
        if (selection == null) {
            return true;
        }
        if (selection.allPets()) {
            List<Pet> candidates = plugin.getPetManger().getPets(selection.target()).stream()
                    .filter(plugin.getPetManger()::needsFeed)
                    .sorted(Comparator.comparingLong(Pet::id))
                    .toList();
            handleAsyncPetBatch(sender, selection.target(), candidates,
                    pet -> plugin.getPetManger().feedPetAsync(selection.target(), pet.id())
                            .thenApply(updatedPet -> updatedPet != null),
                    selection.target().getName() + " 当前没有需要喂食的宠物。",
                    "未能给 " + selection.target().getName() + " 的任何宠物喂食。",
                    "已为 " + selection.target().getName() + " 的宠物喂食，共 ");
            return true;
        }
        long petId = selection.petId();
        Player player = selection.target();
        Pet current = plugin.getPetManger().getPet(player, petId);
        if (current == null) {
            player.sendMessage(error("未找到宠物，ID: " + petId));
            return true;
        }
        if (!plugin.getPetManger().needsFeed(current)) {
            player.sendMessage(error("这只宠物现在还不需要喂食。"));
            return true;
        }
        plugin.getPetManger().feedPetAsync(player, petId)
                .whenComplete((pet, throwable) -> {
                    if (throwable != null) {
                        player.sendMessage(error("命令执行失败: " + throwable.getMessage()));
                        return;
                    }
                    if (pet == null) {
                        player.sendMessage(error("未找到宠物，ID: " + petId));
                        return;
                    }
                    player.sendMessage(success("宠物已经吃饱了，获得 " + plugin.getPetManger().getFeedRewardExp(pet) + " 经验。"));
                });
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "qcpet.command.info")) {
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(error("请提供宠物 ID 或 *。"));
            return true;
        }
        if (args.length >= 2) {
            if (!hasPermission(sender, "qcpet.command.info.other")) {
                return true;
            }
            Player target = requireOnlinePlayer(sender, args[0]);
            if (target == null) {
                return true;
            }
            if ("*".equals(args[1])) {
                List<Pet> pets = plugin.getPetManger().getPets(target).stream()
                        .sorted(Comparator.comparingLong(Pet::id))
                        .toList();
                if (pets.isEmpty()) {
                    sender.sendMessage(error(target.getName() + " 当前没有宠物。"));
                    return true;
                }
                for (Pet pet : pets) {
                    sendPetInfo(sender, pet);
                }
                return true;
            }
            Long petId = requirePetId(sender, args, 1);
            if (petId == null) {
                return true;
            }
            Pet pet = plugin.getPetManger().getPet(target, petId);
            if (pet == null) {
                sender.sendMessage(error("未找到 " + target.getName() + " 的宠物，ID: " + petId));
                return true;
            }
            sendPetInfo(sender, pet);
            return true;
        }
        if ("*".equals(args[0])) {
            Player player = requirePlayer(sender);
            if (player == null) {
                return true;
            }
            List<Pet> pets = plugin.getPetManger().getPets(player).stream()
                    .sorted(Comparator.comparingLong(Pet::id))
                    .toList();
            if (pets.isEmpty()) {
                sender.sendMessage(error(player.getName() + " 当前没有宠物。"));
                return true;
            }
            for (Pet pet : pets) {
                sendPetInfo(sender, pet);
            }
            return true;
        }
        Long petId = requirePetId(sender, args, 0);
        if (petId == null) {
            return true;
        }
        plugin.getPetUtil().getPetAsync(petId)
                .whenComplete((pet, throwable) -> {
                    if (throwable != null) {
                        sender.sendMessage(error("命令执行失败: " + throwable.getMessage()));
                        return;
                    }
                    if (pet == null) {
                        sender.sendMessage(error("未找到宠物，ID: " + petId));
                        return;
                    }
                    sendPetInfo(sender, pet);
                });
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!hasPermission(sender, "qcpet.command.reload")) {
            return true;
        }
        plugin.reloadPluginState();
        sender.sendMessage(success("QcPet 配置已重载。"));
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "qcpet.command.give")) {
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(error("请提供宠物模板名称，或使用 /qcpet give <玩家> <模板名>。"));
            return true;
        }

        Player target;
        String petKey;
        Integer initialLevel;
        if (args.length >= 2) {
            Player targetPlayer = Bukkit.getPlayerExact(args[0]);
            if (targetPlayer != null) {
                target = targetPlayer;
                petKey = args[1];
                initialLevel = args.length >= 3 ? requireNonNegativeAmount(sender, args[2], "绛夌骇") : 0;
            } else {
                target = requirePlayer(sender);
                petKey = args[0];
                initialLevel = requireNonNegativeAmount(sender, args[1], "绛夌骇");
            }
        } else {
            target = requirePlayer(sender);
            petKey = args[0];
            initialLevel = 0;
        }
        if (target == null) {
            return true;
        }
        if (initialLevel == null) {
            return true;
        }

        PetConfig petConfig = plugin.getPetConfigManger().pets.get(petKey);
        if (petConfig == null) {
            sender.sendMessage(error("宠物模板不存在: " + petKey));
            return true;
        }

        int giveLevel = initialLevel;
        plugin.getPetManger().givePetAsync(target, petConfig, giveLevel)
                .whenComplete((pet, throwable) -> {
                    if (throwable != null) {
                        sender.sendMessage(error("命令执行失败: " + throwable.getMessage()));
                        return;
                    }
                    sender.sendMessage(success("已向 " + target.getName() + " 发放宠物，ID: " + pet.id()));
                    if (!sender.getName().equalsIgnoreCase(target.getName())) {
                        target.sendMessage(success("你获得了一只新宠物，ID: " + pet.id()));
                    }
                });
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "qcpet.command.remove")) {
            return true;
        }
        TargetPetSelection selection = resolveTargetPetSelection(sender, args, "remove");
        if (selection == null) {
            return true;
        }
        if (selection.allPets()) {
            List<Pet> candidates = plugin.getPetManger().getPets(selection.target()).stream()
                    .sorted(Comparator.comparingLong(Pet::id))
                    .toList();
            handleAsyncPetBatch(sender, selection.target(), candidates,
                    pet -> plugin.getPetManger().removePetAsync(selection.target(), pet.id()),
                    selection.target().getName() + " 当前没有可删除的宠物。",
                    "未能删除 " + selection.target().getName() + " 的任何宠物。",
                    "已删除 " + selection.target().getName() + " 的宠物，共 ");
            return true;
        }
        long petId = selection.petId();
        plugin.getPetManger().removePetAsync(selection.target(), petId)
                .whenComplete((removed, throwable) -> {
                    if (throwable != null) {
                        sender.sendMessage(error("命令执行失败: " + throwable.getMessage()));
                        return;
                    }
                    if (!removed) {
                        sender.sendMessage(error("未找到可删除的宠物，ID: " + petId));
                        return;
                    }
                    sender.sendMessage(success("已删除 " + selection.target().getName() + " 的宠物，ID: " + petId));
                });
        return true;
    }

    private boolean handleAddExp(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "qcpet.command.addexp")) {
            return true;
        }

        TargetPetSelection selection = resolveTargetPetSelection(sender, args, "addexp", 1);
        Integer amount = requireAmount(sender, args, args.length >= 3 ? 2 : 1, "经验");
        if (selection == null || amount == null) {
            return true;
        }
        if (selection.allPets()) {
            List<Pet> candidates = plugin.getPetManger().getPets(selection.target()).stream()
                    .sorted(Comparator.comparingLong(Pet::id))
                    .toList();
            handleAsyncPetBatch(sender, selection.target(), candidates,
                    pet -> plugin.getPetManger().addPetExperienceAsync(selection.target(), pet.id(), amount)
                            .thenApply(updatedPet -> updatedPet != null),
                    selection.target().getName() + " 当前没有可增加经验的宠物。",
                    "未能为 " + selection.target().getName() + " 的任何宠物增加经验。",
                    "已为 " + selection.target().getName() + " 的宠物增加经验，共 ");
            return true;
        }
        long petId = selection.petId();
        plugin.getPetManger().addPetExperienceAsync(selection.target(), petId, amount)
                .whenComplete((pet, throwable) -> {
                    if (throwable != null) {
                        sender.sendMessage(error("命令执行失败: " + throwable.getMessage()));
                        return;
                    }
                    if (pet == null) {
                        sender.sendMessage(error("未找到宠物，ID: " + petId));
                        return;
                    }
                    sender.sendMessage(success("已为 " + selection.target().getName() + " 的宠物增加 " + amount + " 经验。"));
                });
        return true;
    }

    private boolean handleAddLevel(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "qcpet.command.addlevel")) {
            return true;
        }

        TargetPetSelection selection = resolveTargetPetSelection(sender, args, "addlevel", 1);
        Integer amount = requireAmount(sender, args, args.length >= 3 ? 2 : 1, "等级");
        if (selection == null || amount == null) {
            return true;
        }
        if (selection.allPets()) {
            List<Pet> candidates = plugin.getPetManger().getPets(selection.target()).stream()
                    .sorted(Comparator.comparingLong(Pet::id))
                    .toList();
            handleAsyncPetBatch(sender, selection.target(), candidates,
                    pet -> plugin.getPetManger().addPetLevelsAsync(selection.target(), pet.id(), amount)
                            .thenApply(updatedPet -> updatedPet != null),
                    selection.target().getName() + " 当前没有可增加等级的宠物。",
                    "未能为 " + selection.target().getName() + " 的任何宠物增加等级。",
                    "已为 " + selection.target().getName() + " 的宠物增加等级，共 ");
            return true;
        }
        long petId = selection.petId();
        plugin.getPetManger().addPetLevelsAsync(selection.target(), petId, amount)
                .whenComplete((pet, throwable) -> {
                    if (throwable != null) {
                        sender.sendMessage(error("命令执行失败: " + throwable.getMessage()));
                        return;
                    }
                    if (pet == null) {
                        sender.sendMessage(error("未找到宠物，ID: " + petId));
                        return;
                    }
                    sender.sendMessage(success("已为 " + selection.target().getName() + " 的宠物增加 " + amount + " 等级。"));
                });
        return true;
    }

    private boolean handleStorage(CommandSender sender) {
        if (!hasPermission(sender, "qcpet.command.storage")) {
            return true;
        }
        send(sender, "&eMySQL 存储状态: " + (plugin.getMySqlSaveUtil().isAvailable() ? "&a正常" : "&c异常"));
        return true;
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage(error("你没有权限执行该命令。"));
        return false;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(error("该命令只能由玩家执行。"));
        return null;
    }

    private Player requireOnlinePlayer(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target != null) {
            return target;
        }
        sender.sendMessage(error("未找到在线玩家: " + playerName));
        return null;
    }

    private Long requirePetId(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            sender.sendMessage(error("请提供宠物 ID。"));
            return null;
        }
        try {
            long petId = Long.parseLong(args[index]);
            if (petId < 1) {
                sender.sendMessage(error("宠物 ID 必须大于 0。"));
                return null;
            }
            return petId;
        } catch (NumberFormatException exception) {
            sender.sendMessage(error("宠物 ID 必须是数字。"));
            return null;
        }
    }

    private Integer requireAmount(CommandSender sender, String[] args, int index, String name) {
        if (args.length <= index) {
            sender.sendMessage(error("请提供" + name + "数量。"));
            return null;
        }
        try {
            int amount = Integer.parseInt(args[index]);
            if (amount <= 0) {
                sender.sendMessage(error(name + "数量必须大于 0。"));
                return null;
            }
            return amount;
        } catch (NumberFormatException exception) {
            sender.sendMessage(error(name + "数量必须是数字。"));
            return null;
        }
    }

    private Integer requireNonNegativeAmount(CommandSender sender, String value, String name) {
        if (value == null || value.isBlank()) {
            sender.sendMessage(error(name + " is required."));
            return null;
        }
        try {
            int amount = Integer.parseInt(value);
            if (amount < 0) {
                sender.sendMessage(error(name + " must be greater than or equal to 0."));
                return null;
            }
            return amount;
        } catch (NumberFormatException exception) {
            sender.sendMessage(error(name + " must be a number."));
            return null;
        }
    }

    private TargetPetSelection resolveTargetPetSelection(CommandSender sender, String[] args, String action) {
        return resolveTargetPetSelection(sender, args, action, 0);
    }

    private TargetPetSelection resolveTargetPetSelection(CommandSender sender, String[] args, String action, int trailingArgs) {
        Player target;
        String petSelector;
        if (args.length >= trailingArgs + 2) {
            if (!hasPermission(sender, "qcpet.command." + action + ".other")) {
                return null;
            }
            target = requireOnlinePlayer(sender, args[0]);
            petSelector = args[1];
        } else {
            target = requirePlayer(sender);
            petSelector = args.length <= trailingArgs ? null : args[0];
        }
        if (target == null) {
            return null;
        }
        return resolvePetSelectionForTarget(sender, target, petSelector);
    }

    private TargetPetSelection resolveSelfPetSelection(CommandSender sender, String[] args) {
        Player target = requirePlayer(sender);
        if (target == null) {
            return null;
        }
        String petSelector = args.length == 0 ? null : args[0];
        return resolvePetSelectionForTarget(sender, target, petSelector);
    }

    private TargetPetSelection resolvePetSelectionForTarget(CommandSender sender, Player target, String petSelector) {
        if (petSelector == null || petSelector.isBlank()) {
            sender.sendMessage(error("请提供宠物 ID 或 *。"));
            return null;
        }
        if ("*".equals(petSelector)) {
            return new TargetPetSelection(target, 0L, true);
        }
        Long petId = requirePetId(sender, new String[]{petSelector}, 0);
        if (petId == null) {
            return null;
        }
        return new TargetPetSelection(target, petId, false);
    }

    private void handleToggleAllPets(CommandSender sender, Player target, boolean show) {
        List<Pet> candidates = plugin.getPetManger().getPets(target).stream()
                .filter(pet -> show ? !pet.show() : pet.show())
                .sorted(Comparator.comparingLong(Pet::id))
                .toList();
        if (candidates.isEmpty()) {
            sender.sendMessage(error(show
                    ? target.getName() + " 当前没有可显示的宠物。"
                    : target.getName() + " 当前没有已出战的宠物。"));
            return;
        }

        CompletableFuture<Integer> future = CompletableFuture.completedFuture(0);
        for (Pet pet : candidates) {
            future = future.thenCompose(count -> {
                CompletableFuture<Boolean> actionFuture = show
                        ? plugin.getPetManger().showPetAsync(target, pet.id())
                        : plugin.getPetManger().hidePetAsync(target, pet.id());
                return actionFuture.handle((changed, throwable) -> count + (throwable == null && Boolean.TRUE.equals(changed) ? 1 : 0));
            });
        }

        int total = candidates.size();
        future.whenComplete((changedCount, throwable) -> {
            if (throwable != null) {
                sender.sendMessage(error("命令执行失败: " + throwable.getMessage()));
                return;
            }
            if (changedCount == null || changedCount <= 0) {
                sender.sendMessage(error(show
                        ? "未能显示 " + target.getName() + " 的任何宠物。"
                        : "未能隐藏 " + target.getName() + " 的任何宠物。"));
                return;
            }
            sender.sendMessage(success((show ? "已显示 " : "已隐藏 ")
                    + target.getName() + " 的宠物，共 " + changedCount + "/" + total + " 只。"));
        });
    }

    private void handleToggleAllPetDisplays(CommandSender sender, Player target, boolean showDisplay) {
        List<Pet> candidates = plugin.getPetManger().getPets(target).stream()
                .filter(Pet::show)
                .sorted(Comparator.comparingLong(Pet::id))
                .toList();
        if (candidates.isEmpty()) {
            sender.sendMessage(error(target.getName() + " 当前没有出战中的宠物。"));
            return;
        }

        CompletableFuture<Integer> future = CompletableFuture.completedFuture(0);
        for (Pet pet : candidates) {
            future = future.thenCompose(count -> {
                CompletableFuture<Boolean> actionFuture = showDisplay
                        ? plugin.getPetManger().showPetDisplayAsync(target, pet.id())
                        : plugin.getPetManger().hidePetDisplayAsync(target, pet.id());
                return actionFuture.handle((changed, throwable) -> count + (throwable == null && Boolean.TRUE.equals(changed) ? 1 : 0));
            });
        }

        int total = candidates.size();
        future.whenComplete((changedCount, throwable) -> {
            if (throwable != null) {
                sender.sendMessage(error("命令执行失败: " + throwable.getMessage()));
                return;
            }
            if (changedCount == null || changedCount <= 0) {
                sender.sendMessage(error(showDisplay
                        ? "未能恢复 " + target.getName() + " 的任何宠物显示。"
                        : "未能隐藏 " + target.getName() + " 的任何宠物显示。"));
                return;
            }
            sender.sendMessage(success((showDisplay ? "已恢复 " : "已隐藏 ")
                    + target.getName() + " 的宠物显示，共 " + changedCount + "/" + total + " 只。"));
        });
    }

    private void handleAsyncPetBatch(CommandSender sender, Player target, List<Pet> candidates,
                                     Function<Pet, CompletableFuture<Boolean>> action,
                                     String emptyMessage, String failureMessage, String successPrefix) {
        if (candidates.isEmpty()) {
            sender.sendMessage(error(emptyMessage));
            return;
        }

        CompletableFuture<Integer> future = CompletableFuture.completedFuture(0);
        for (Pet pet : candidates) {
            future = future.thenCompose(count ->
                    action.apply(pet).handle((changed, throwable) ->
                            count + (throwable == null && Boolean.TRUE.equals(changed) ? 1 : 0)));
        }

        int total = candidates.size();
        future.whenComplete((changedCount, throwable) -> {
            if (throwable != null) {
                sender.sendMessage(error("命令执行失败: " + throwable.getMessage()));
                return;
            }
            if (changedCount == null || changedCount <= 0) {
                sender.sendMessage(error(failureMessage));
                return;
            }
            sender.sendMessage(success(successPrefix + changedCount + "/" + total + " 只。"));
        });
    }

    private void sendPetInfo(CommandSender sender, Pet pet) {
        boolean blindBox = plugin.getPetManger().isBlindBoxPet(pet);
        send(sender, "&6宠物信息:");
        send(sender, "&eID: &f" + pet.id());
        send(sender, "&e名称: &r" + plugin.getPetManger().getDisplayName(pet, pet.owner()));
        send(sender, "&e类型: &f" + (blindBox ? "???" : pet.type()));
        send(sender, "&e等级: &f" + (blindBox ? "???" : pet.level()));
        send(sender, "&e经验: &f" + pet.exp());
        send(sender, "&e显示: &f" + (pet.show() ? "是" : "否"));
    }

    private List<String> getAllowedSubCommands(CommandSender sender) {
        List<String> result = new ArrayList<>();
        for (String subCommand : SUB_COMMANDS) {
            if (sender.hasPermission("qcpet.command." + subCommand)) {
                result.add(subCommand);
            }
        }
        return result;
    }

    private static boolean isPetTemplateCommand(String subCommand) {
        return subCommand.equalsIgnoreCase("give");
    }

    private static boolean isPetIdCommand(String subCommand) {
        return subCommand.equalsIgnoreCase("show")
                || subCommand.equalsIgnoreCase("hide")
                || subCommand.equalsIgnoreCase("showdisplay")
                || subCommand.equalsIgnoreCase("hidedisplay")
                || subCommand.equalsIgnoreCase("remove")
                || subCommand.equalsIgnoreCase("addexp")
                || subCommand.equalsIgnoreCase("addlevel")
                || subCommand.equalsIgnoreCase("bath")
                || subCommand.equalsIgnoreCase("feed")
                || subCommand.equalsIgnoreCase("info");
    }

    private List<String> getPetIds(Player player) {
        List<String> petIds = new ArrayList<>();
        for (Pet pet : plugin.getPetManger().getPets(player)) {
            petIds.add(String.valueOf(pet.id()));
        }
        return petIds;
    }

    private List<String> getPetIdSelectors(Player player) {
        List<String> selectors = new ArrayList<>();
        selectors.add("*");
        selectors.addAll(getPetIds(player));
        return selectors;
    }

    private List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static List<String> filterByPrefix(List<String> values, String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
                .toList();
    }

    private static Component error(String message) {
        return TextComponentUtil.legacy("&c" + message);
    }

    private static Component success(String message) {
        return TextComponentUtil.legacy("&a" + message);
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(TextComponentUtil.legacy(message));
    }

    private record TargetPetSelection(Player target, long petId, boolean allPets) {
    }
}
