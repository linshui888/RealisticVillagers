package me.matsubara.realisticvillagers.entity.v1_18_r2.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import me.matsubara.realisticvillagers.data.ChangeItemType;
import me.matsubara.realisticvillagers.entity.v1_18_r2.villager.VillagerNPC;
import me.matsubara.realisticvillagers.util.PluginUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.craftbukkit.v1_18_R2.inventory.CraftItemStack;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public class Eat extends Behavior<Villager> {

    private ItemStack food;
    private ItemStack previousItem;
    private int duration;

    private final static Set<MobEffect> UNSAFE = ImmutableSet.of(MobEffects.HUNGER, MobEffects.POISON, MobEffects.CONFUSION);

    public Eat() {
        super(ImmutableMap.of(MemoryModuleType.INTERACTION_TARGET, MemoryStatus.VALUE_ABSENT), 100);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return villager instanceof VillagerNPC npc
                && npc.isDoingNothing(ChangeItemType.EATING)
                && npc.getFoodLevel() < 20
                && npc.getInventory().getContents().stream().anyMatch(item -> isSafeFood(item.getItem()));
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long time) {
        previousItem = villager.getMainHandItem().copy();

        // Get the food with higher nutrition.
        @SuppressWarnings("ConstantConditions") Optional<ItemStack> food = villager.getInventory().getContents().stream()
                .filter(item -> isSafeFood(item.getItem()))
                .max(Comparator.comparingInt(item -> item.getItem().getFoodProperties().getNutrition()));

        if (food.isEmpty()) return;

        if (villager instanceof VillagerNPC npc) npc.setEating(true);
        villager.setItemInHand(InteractionHand.MAIN_HAND, food.get());

        duration = food.get().getUseDuration();
        this.food = food.get();
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long time) {
        if (--duration == 0) villager.eat(level, food);
        handleParticles(villager);
    }

    private void spawnFoodParticles(Villager villager) {
        for (int i = 0; i < 16; i++) {
            Location location = villager.getBukkitEntity()
                    .getLocation()
                    .clone()
                    .add(0.0d, 1.42d, 0.0d);

            villager.level.getWorld().spawnParticle(
                    Particle.ITEM_CRACK,
                    location.add(PluginUtils.offsetVector(new Vector(0.0d, 0.0d, 0.3d), location.getYaw(), location.getPitch())),
                    1,
                    0.03d,
                    0.125d,
                    0.03d,
                    0.0d,
                    CraftItemStack.asBukkitCopy(food));
        }
    }

    private void handleParticles(Villager villager) {
        FoodProperties properties = food.getItem().getFoodProperties();

        boolean flag = properties != null && properties.isFastFood();
        flag |= duration <= food.getUseDuration() - 7;

        if (!(flag && duration % 4 == 0)) return;

        Random random = villager.level.random;

        spawnFoodParticles(villager);
        villager.playSound(
                villager.getEatingSound(food),
                0.5f + 0.5f * (float) random.nextInt(2),
                (random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return duration > 0;
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long time) {
        villager.setItemInHand(InteractionHand.MAIN_HAND, previousItem);
        if (villager instanceof VillagerNPC npc) npc.setEating(false);
    }

    public static boolean isSafeFood(Item item) {
        FoodProperties properties = item.getFoodProperties();
        if (properties == null) return false;

        for (Pair<MobEffectInstance, Float> effect : properties.getEffects()) {
            if (UNSAFE.contains(effect.getFirst().getEffect())) return false;
        }

        return true;
    }
}