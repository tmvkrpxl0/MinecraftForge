/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.debug.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.extensions.IForgeBlock;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Mod(BlockCapabilityTest.MOD_ID)
public class BlockCapabilityTest {
    public static final String MOD_ID = "block_capability_test";

    public BlockCapabilityTest() {
        MinecraftForge.EVENT_BUS.addGenericListener(IForgeBlock.PlacedBlockInstance.class, this::attachToIronBlock);
        MinecraftForge.EVENT_BUS.addGenericListener(IForgeBlock.PlacedBlockInstance.class, this::attachToLava);
    }

    private void attachToLava(AttachCapabilitiesEvent<IForgeBlock.PlacedBlockInstance> event) {
        if (event.getObject().blockState.getBlock() == Blocks.LAVA) {
            LevelAccessor level = event.getObject().level;
            BlockPos blockPos = event.getObject().blockPos;

            IItemHandler handler = new IItemHandler() {
                @Override
                public int getSlots() {
                    return 1;
                }

                @Override
                public @NotNull ItemStack getStackInSlot(int slot) {
                    return ItemStack.EMPTY;
                }

                @Override
                public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                    if (!simulate) level.playSound(null, blockPos, SoundEvents.GENERIC_BURN, SoundSource.BLOCKS, 0.4F, 2.0F + level.getRandom().nextFloat() * 0.4F);
                    return ItemStack.EMPTY;
                }

                @Override
                public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                    return ItemStack.EMPTY;
                }

                @Override
                public int getSlotLimit(int slot) {
                    return 64;
                }

                @Override
                public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                    return true;
                }
            };

            ICapabilityProvider lava = new ICapabilityProvider() {

                @Override
                public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
                    if (cap == ForgeCapabilities.ITEM_HANDLER) return LazyOptional.of(() -> handler).cast();

                    return LazyOptional.empty();
                }
            };
            event.addCapability(new ResourceLocation(MOD_ID, "void"), lava);
        }
    }

    private void attachToIronBlock(AttachCapabilitiesEvent<IForgeBlock.PlacedBlockInstance> event) {
        if (event.getObject().blockState.getBlock() == Blocks.IRON_BLOCK) {
            ICapabilityProvider provider = new ICapabilityProvider() {
                LazyOptional<IItemHandler> infiniteIron = LazyOptional.of(() -> new IItemHandler() {

                    @Override
                    public int getSlots() {
                        return 1;
                    }

                    @Override
                    public @NotNull ItemStack getStackInSlot(int slot) {
                        if (slot == 0) return new ItemStack(Items.IRON_INGOT, 64);
                        return ItemStack.EMPTY;
                    }

                    @Override
                    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                        return stack;
                    }

                    @Override
                    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                        if (slot == 0) return new ItemStack(Items.IRON_INGOT, amount);
                        return ItemStack.EMPTY;
                    }

                    @Override
                    public int getSlotLimit(int slot) {
                        return 64;
                    }

                    @Override
                    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                        return false;
                    }
                });
                @Override
                public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
                    if (cap == ForgeCapabilities.ITEM_HANDLER) return infiniteIron.cast();

                    return LazyOptional.empty();
                }
            };
            event.addCapability(new ResourceLocation(MOD_ID, "infinite_iron"), provider);
        }
    }
}
