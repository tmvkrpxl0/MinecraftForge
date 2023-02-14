/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.extensions.IForgeBlock;
import net.minecraftforge.common.loot.LootModifierManager;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.common.util.LogicalSidedProvider;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.server.command.ForgeCommand;
import net.minecraftforge.server.command.ConfigCommand;
import org.jetbrains.annotations.NotNull;

public class ForgeInternalHandler
{
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityJoinWorld(EntityJoinLevelEvent event)
    {
        Entity entity = event.getEntity();
        if (entity.getClass().equals(ItemEntity.class))
        {
            ItemStack stack = ((ItemEntity)entity).getItem();
            Item item = stack.getItem();
            if (item.hasCustomEntity(stack))
            {
                Entity newEntity = item.createEntity(event.getLevel(), entity, stack);
                if (newEntity != null)
                {
                    entity.discard();
                    event.setCanceled(true);
                    var executor = LogicalSidedProvider.WORKQUEUE.get(event.getLevel().isClientSide ? LogicalSide.CLIENT : LogicalSide.SERVER);
                    executor.tell(new TickTask(0, () -> event.getLevel().addFreshEntity(newEntity)));
                }
            }
        }
    }


    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDimensionUnload(LevelEvent.Unload event)
    {
        if (event.getLevel() instanceof ServerLevel)
            FakePlayerFactory.unloadLevel((ServerLevel) event.getLevel());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent event)
    {
        WorldWorkerManager.tick(event.phase == TickEvent.Phase.START);
    }

    @SubscribeEvent
    public void checkSettings(ClientTickEvent event)
    {
        //if (event.phase == Phase.END)
        //    CloudRenderer.updateCloudSettings();
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event)
    {
        if (!event.getLevel().isClientSide())
            FarmlandWaterManager.removeTickets(event.getChunk());

        if (event.getLevel() instanceof Level level) {
            level.invalidateBlockCapsInChunk(event.getChunk().getPos());
        }
    }

    /*
    @SubscribeEvent
    public void playerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getPlayer() instanceof ServerPlayerEntity)
            DimensionManager.rebuildPlayerMap(((ServerPlayerEntity)event.getPlayer()).server.getPlayerList(), true);
    }
    */

    @SubscribeEvent
    public void playerLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        UsernameCache.setUsername(event.getEntity().getUUID(), event.getEntity().getGameProfile().getName());
    }

    @SubscribeEvent
    public void tagsUpdated(TagsUpdatedEvent event)
    {
        if (event.shouldUpdateStaticData())
        {
            ForgeHooks.updateBurns();
        }
    }

    @SubscribeEvent
    public void onCommandsRegister(RegisterCommandsEvent event)
    {
        new ForgeCommand(event.getDispatcher());
        ConfigCommand.register(event.getDispatcher());
    }

    private static LootModifierManager INSTANCE;

    @SubscribeEvent
    public void onResourceReload(AddReloadListenerEvent event)
    {
        INSTANCE = new LootModifierManager();
        event.addListener(INSTANCE);
    }

    static LootModifierManager getLootModifierManager()
    {
        if(INSTANCE == null)
            throw new IllegalStateException("Can not retrieve LootModifierManager until resources have loaded once.");
        return INSTANCE;
    }

    @SubscribeEvent
    public void resourceReloadListeners(AddReloadListenerEvent event)
    {
        event.addListener(TierSortingRegistry.getReloadListener());
        event.addListener(CreativeModeTabRegistry.getReloadListener());
    }

    @SubscribeEvent
    public void attachCapToComposter(AttachCapabilitiesEvent<IForgeBlock.PlacedBlockInstance> event)
    {
        var obj = event.getObject();
        var blockState = obj.blockState;
        var level = obj.level;
        var blockPos = obj.blockPos;

        if (blockState.getBlock() != Blocks.COMPOSTER) return;

        LazyOptional<IItemHandler> up = LazyOptional.of(() -> new IItemHandler()
        {
            final int composterLevel = blockState.getValue(BlockStateProperties.LEVEL_COMPOSTER);

            @Override
            public int getSlots()
            {
                return 1;
            }

            @Override
            public @NotNull ItemStack getStackInSlot(int slot)
            {
                return ItemStack.EMPTY;
            }

            @Override
            public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate)
            {
                if (!isItemValid(slot, stack)) return stack;
                if (!(level instanceof ServerLevel)) return stack;

                ItemStack copied = stack.copy();

                if (simulate)
                    return copied.split(ComposterBlock.MAX_LEVEL - composterLevel);

                BlockState current = blockState;
                while (true)
                {
                    BlockState result = ComposterBlock.insertItem(current, (ServerLevel) level, copied, blockPos);

                    if (result == current) break;
                    current = result;
                }

                return copied;

            }

            @Override
            public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate)
            {
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot)
            {
                if (slot == 0) return 1;

                return 0;
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack)
            {
                if (slot != 0) return false;
                if (!ComposterBlock.COMPOSTABLES.containsKey(stack.getItem())) return false;
                if (composterLevel >= ComposterBlock.MAX_LEVEL) return false;

                return true;
            }
        });

        LazyOptional<IItemHandler> down = LazyOptional.of(() -> new IItemHandler()
        {
            final int composterLevel = blockState.getValue(ComposterBlock.LEVEL);
            final ItemStack boneMeal = new ItemStack(Items.BONE_MEAL);

            @Override
            public int getSlots()
            {
                return 1;
            }

            @Override
            public @NotNull ItemStack getStackInSlot(int slot)
            {
                if (slot == 0 && composterLevel == 8) return new ItemStack(Items.BONE_MEAL);

                return ItemStack.EMPTY;
            }

            @Override
            public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate)
            {
                return stack;
            }

            @Override
            public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate)
            {
                if (slot != 0) return ItemStack.EMPTY;
                if (composterLevel != 8) return ItemStack.EMPTY;
                if (!(level instanceof ServerLevel)) return ItemStack.EMPTY;
                if (!simulate)
                {
                    level.setBlock(blockPos, blockState.setValue(BlockStateProperties.LEVEL_COMPOSTER, 0), 3);
                    level.playSound(null, blockPos, SoundEvents.COMPOSTER_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                }

                return boneMeal;
            }

            @Override
            public int getSlotLimit(int slot)
            {
                if (slot == 0) return 1;

                return 0;
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack)
            {
                return false;
            }
        });

        event.addCapability(
            new ResourceLocation("forge", "composter"),
            new ICapabilityProvider()
            {

                @Override
                public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @org.jetbrains.annotations.Nullable Direction side)
                {
                    if (cap == ForgeCapabilities.ITEM_HANDLER)
                    {
                        if (side == Direction.UP) return up.cast();
                        if (side == Direction.DOWN) return down.cast();
                    }

                    return LazyOptional.empty();
                }
            }
        );
    }
}

