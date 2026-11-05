package zone.moddev.mc.skysbuildingpiecesbop.probe;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import zone.moddev.mc.skysbuildingpieces.probe.NativeProbeItems;

public final class BopProbeItems {
    static CompoundTag overworldUpgradeContext() {
        CompoundTag context = new CompoundTag();
        context.putString("dimension", "minecraft:overworld");
        context.putString("generator", "minecraft:noise");
        return context;
    }

    static void loadPlayer(ServerPlayer player) {
        var data = player.level().getServer().getPlayerList().getPlayerIo()
                .load(new net.minecraft.server.players.NameAndId(player.getGameProfile()))
                .orElseThrow(() -> new AssertionError("Saved player data is missing"));
        player.load(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess(), data));
    }

    static net.minecraft.network.chat.Component readSavedText(String json, ServerLevel world) {
        return net.minecraft.network.chat.ComponentSerialization.CODEC.parse(
                world.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
                com.google.gson.JsonParser.parseString(json)).getOrThrow();
    }

    private BopProbeItems() {
    }

    public static HolderLookup.Provider registries() {
        return ServerLifecycleHooks.getCurrentServer().registryAccess();
    }

    public static ServerPlayer player(ServerLevel world) {
        return player(world, new GameProfile(UUID.fromString("e55e930f-3729-4b29-b92f-902046f457e1"), "BasketProbe"));
    }

    public static ServerPlayer player(ServerLevel world, GameProfile profile) {
        return new ServerPlayer(world.getServer(), world, profile, ClientInformation.createDefault());
    }

    public static CompoundTag legacy(ItemStack stack) {
        CompoundTag saved = new CompoundTag();
        saved.putString("id", ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
        saved.putByte("Count", (byte) stack.getCount());
        return saved;
    }

    public static CompoundTag save(ItemStack stack) {
        return NativeProbeItems.save(stack);
    }

    public static ItemStack read(CompoundTag saved) {
        return NativeProbeItems.read(saved);
    }

    public static void name(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
    }

    public static void setCustom(ItemStack stack, CompoundTag data) {
        NativeProbeItems.setCustom(stack, data);
    }

    public static CompoundTag custom(ItemStack stack) {
        return NativeProbeItems.custom(stack);
    }

    public static boolean hasCustom(ItemStack stack) {
        return NativeProbeItems.hasCustom(stack);
    }
}
