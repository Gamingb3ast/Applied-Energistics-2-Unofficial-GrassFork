/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts.automation;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFirework;
import net.minecraft.item.ItemReed;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.BlockEvent;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartRenderHelper;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.IConfigManager;
import appeng.client.texture.CableBusTextures;
import appeng.core.AEConfig;
import appeng.core.sync.GuiBridge;
import appeng.helpers.IPriorityHost;
import appeng.me.GridAccessException;
import appeng.me.storage.MEInventoryHandler;
import appeng.parts.PartBasicState;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.Platform;
import appeng.util.prioitylist.FuzzyPriorityList;
import appeng.util.prioitylist.PrecisePriorityList;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class PartFormationPlane extends PartUpgradeable
        implements ICellContainer, IPriorityHost, IMEInventory<IAEItemStack> {

    private final MEInventoryHandler myHandler = new MEInventoryHandler(this, StorageChannel.ITEMS);
    private final AppEngInternalAEInventory Config = new AppEngInternalAEInventory(this, 63);
    private EntityPlayer owner = null;
    private int priority = 0;
    private boolean wasActive = false;
    private boolean blocked = false;

    public PartFormationPlane(final ItemStack is) {
        super(is);

        this.getConfigManager().registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.getConfigManager().registerSetting(Settings.PLACE_BLOCK, YesNo.YES);
        this.updateHandler();
    }

    @Override
    public void onPlacement(EntityPlayer player, ItemStack held, ForgeDirection side) {
        super.onPlacement(player, held, side);
        this.owner = player;
    }

    private void updateHandler() {
        this.myHandler.setBaseAccess(AccessRestriction.WRITE);
        this.myHandler.setWhitelist(
                this.getInstalledUpgrades(Upgrades.INVERTER) > 0 ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);
        this.myHandler.setPriority(this.priority);

        final IItemList<IAEItemStack> priorityList = AEApi.instance().storage().createItemList();

        final int slotsToUse = 18 + this.getInstalledUpgrades(Upgrades.CAPACITY) * 9;
        for (int x = 0; x < this.Config.getSizeInventory() && x < slotsToUse; x++) {
            final IAEItemStack is = this.Config.getAEStackInSlot(x);
            if (is != null) {
                priorityList.add(is);
            }
        }

        if (this.getInstalledUpgrades(Upgrades.FUZZY) > 0) {
            this.myHandler.setPartitionList(
                    new FuzzyPriorityList(
                            priorityList,
                            (FuzzyMode) this.getConfigManager().getSetting(Settings.FUZZY_MODE)));
        } else {
            this.myHandler.setPartitionList(new PrecisePriorityList(priorityList));
        }

        try {
            this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
        } catch (final GridAccessException e) {
            // :P
        }
    }

    @Override
    protected int getUpgradeSlots() {
        return 5;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        this.updateHandler();
        this.getHost().markForSave();
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        super.onChangeInventory(inv, slot, mc, removedStack, newStack);

        if (inv == this.Config) {
            this.updateHandler();
        }
    }

    @Override
    public void upgradesChanged() {
        this.updateHandler();
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.Config.readFromNBT(data, "config");
        this.priority = data.getInteger("priority");
        this.updateHandler();
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.Config.writeToNBT(data, "config");
        data.setInteger("priority", this.priority);
    }

    @Override
    public IInventory getInventoryByName(final String name) {
        if (name.equals("config")) {
            return this.Config;
        }

        return super.getInventoryByName(name);
    }

    @Override
    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        final boolean currentActive = this.getProxy().isActive();
        if (this.wasActive != currentActive) {
            this.wasActive = currentActive;
            this.updateHandler(); // proxy.getGrid().postEvent( new MENetworkCellArrayUpdate() );
            this.getHost().markForUpdate();
        }
    }

    @MENetworkEventSubscribe
    public void updateChannels(final MENetworkChannelsChanged changedChannels) {
        final boolean currentActive = this.getProxy().isActive();
        if (this.wasActive != currentActive) {
            this.wasActive = currentActive;
            this.updateHandler(); // proxy.getGrid().postEvent( new MENetworkCellArrayUpdate() );
            this.getHost().markForUpdate();
        }
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        int minX = 1;
        int minY = 1;
        int maxX = 15;
        int maxY = 15;

        final IPartHost host = this.getHost();
        if (host != null) {
            final TileEntity te = host.getTile();

            final int x = te.xCoord;
            final int y = te.yCoord;
            final int z = te.zCoord;

            final ForgeDirection e = bch.getWorldX();
            final ForgeDirection u = bch.getWorldY();

            if (this.isTransitionPlane(
                    te.getWorldObj().getTileEntity(x - e.offsetX, y - e.offsetY, z - e.offsetZ),
                    this.getSide())) {
                minX = 0;
            }

            if (this.isTransitionPlane(
                    te.getWorldObj().getTileEntity(x + e.offsetX, y + e.offsetY, z + e.offsetZ),
                    this.getSide())) {
                maxX = 16;
            }

            if (this.isTransitionPlane(
                    te.getWorldObj().getTileEntity(x - u.offsetX, y - u.offsetY, z - u.offsetZ),
                    this.getSide())) {
                minY = 0;
            }

            if (this.isTransitionPlane(
                    te.getWorldObj().getTileEntity(x + u.offsetX, y + u.offsetY, z + u.offsetZ),
                    this.getSide())) {
                maxY = 16;
            }
        }

        bch.addBox(5, 5, 14, 11, 11, 15);
        bch.addBox(minX, minY, 15, maxX, maxY, 16);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderInventory(final IPartRenderHelper rh, final RenderBlocks renderer) {
        rh.setTexture(
                CableBusTextures.PartPlaneSides.getIcon(),
                CableBusTextures.PartPlaneSides.getIcon(),
                CableBusTextures.PartTransitionPlaneBack.getIcon(),
                this.getItemStack().getIconIndex(),
                CableBusTextures.PartPlaneSides.getIcon(),
                CableBusTextures.PartPlaneSides.getIcon());

        rh.setBounds(1, 1, 15, 15, 15, 16);
        rh.renderInventoryBox(renderer);

        rh.setBounds(5, 5, 14, 11, 11, 15);
        rh.renderInventoryBox(renderer);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderStatic(final int x, final int y, final int z, final IPartRenderHelper rh,
            final RenderBlocks renderer) {
        int minX = 1;

        final ForgeDirection e = rh.getWorldX();
        final ForgeDirection u = rh.getWorldY();

        final TileEntity te = this.getHost().getTile();

        if (this.isTransitionPlane(
                te.getWorldObj().getTileEntity(x - e.offsetX, y - e.offsetY, z - e.offsetZ),
                this.getSide())) {
            minX = 0;
        }

        int maxX = 15;
        if (this.isTransitionPlane(
                te.getWorldObj().getTileEntity(x + e.offsetX, y + e.offsetY, z + e.offsetZ),
                this.getSide())) {
            maxX = 16;
        }

        int minY = 1;
        if (this.isTransitionPlane(
                te.getWorldObj().getTileEntity(x - u.offsetX, y - u.offsetY, z - u.offsetZ),
                this.getSide())) {
            minY = 0;
        }

        int maxY = 15;
        if (this.isTransitionPlane(
                te.getWorldObj().getTileEntity(x + u.offsetX, y + u.offsetY, z + u.offsetZ),
                this.getSide())) {
            maxY = 16;
        }

        final boolean isActive = (this.getClientFlags() & (PartBasicState.POWERED_FLAG | PartBasicState.CHANNEL_FLAG))
                == (PartBasicState.POWERED_FLAG | PartBasicState.CHANNEL_FLAG);

        this.setRenderCache(rh.useSimplifiedRendering(x, y, z, this, this.getRenderCache()));
        rh.setTexture(
                CableBusTextures.PartPlaneSides.getIcon(),
                CableBusTextures.PartPlaneSides.getIcon(),
                CableBusTextures.PartTransitionPlaneBack.getIcon(),
                isActive ? CableBusTextures.BlockFormPlaneOn.getIcon() : this.getItemStack().getIconIndex(),
                CableBusTextures.PartPlaneSides.getIcon(),
                CableBusTextures.PartPlaneSides.getIcon());

        rh.setBounds(minX, minY, 15, maxX, maxY, 16);
        rh.renderBlock(x, y, z, renderer);

        rh.setTexture(
                CableBusTextures.PartMonitorSidesStatus.getIcon(),
                CableBusTextures.PartMonitorSidesStatus.getIcon(),
                CableBusTextures.PartTransitionPlaneBack.getIcon(),
                isActive ? CableBusTextures.BlockFormPlaneOn.getIcon() : this.getItemStack().getIconIndex(),
                CableBusTextures.PartMonitorSidesStatus.getIcon(),
                CableBusTextures.PartMonitorSidesStatus.getIcon());

        rh.setBounds(5, 5, 14, 11, 11, 15);
        rh.renderBlock(x, y, z, renderer);

        this.renderLights(x, y, z, rh, renderer);
    }

    @Override
    public void onNeighborChanged() {
        final TileEntity te = this.getHost().getTile();
        final World w = te.getWorldObj();
        final ForgeDirection side = this.getSide();

        final int x = te.xCoord + side.offsetX;
        final int y = te.yCoord + side.offsetY;
        final int z = te.zCoord + side.offsetZ;

        this.blocked = !w.getBlock(x, y, z).isReplaceable(w, x, y, z);
    }

    @Override
    public int cableConnectionRenderTo() {
        return 1;
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final Vec3 pos) {
        if (!player.isSneaking()) {
            if (Platform.isClient()) {
                return true;
            }

            Platform.openGUI(player, this.getHost().getTile(), this.getSide(), GuiBridge.GUI_FORMATION_PLANE);
            return true;
        }

        return false;
    }

    private boolean isTransitionPlane(final TileEntity blockTileEntity, final ForgeDirection side) {
        if (blockTileEntity instanceof IPartHost) {
            final IPart p = ((IPartHost) blockTileEntity).getPart(side);
            return p instanceof PartFormationPlane;
        }
        return false;
    }

    @Override
    public List<IMEInventoryHandler> getCellArray(final StorageChannel channel) {
        if (this.getProxy().isActive() && channel == StorageChannel.ITEMS) {
            final List<IMEInventoryHandler> Handler = new ArrayList<>(1);
            Handler.add(this.myHandler);
            return Handler;
        }
        return new ArrayList<>();
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(final int newValue) {
        this.priority = newValue;
        this.getHost().markForSave();
        this.updateHandler();
    }

    @Override
    public IAEItemStack injectItems(final IAEItemStack input, final Actionable type, final BaseActionSource src) {
        if (this.blocked || input == null || input.getStackSize() <= 0) {
            return input;
        }

        final YesNo placeBlock = (YesNo) this.getConfigManager().getSetting(Settings.PLACE_BLOCK);

        final ItemStack is = input.getItemStack();
        final Item i = is.getItem();

        long maxStorage = Math.min(input.getStackSize(), is.getMaxStackSize());
        boolean worked = false;

        final TileEntity te = this.getHost().getTile();
        final World w = te.getWorldObj();
        final ForgeDirection side = this.getSide();

        final int x = te.xCoord + side.offsetX;
        final int y = te.yCoord + side.offsetY;
        final int z = te.zCoord + side.offsetZ;

        if (w.getBlock(x, y, z).isReplaceable(w, x, y, z)) {
            if (placeBlock == YesNo.YES && (i instanceof ItemBlock || i instanceof IPlantable
                    || i instanceof ItemSkull
                    || i instanceof ItemFirework
                    || i instanceof ItemReed)) {
                final EntityPlayer player = Platform.getPlayer((WorldServer) w);
                Platform.configurePlayer(player, side, this.getTile());

                if (i instanceof ItemFirework) {
                    final Chunk c = w.getChunkFromBlockCoords(x, z);
                    int sum = 0;
                    for (final List Z : c.entityLists) {
                        sum += Z.size();
                    }
                    if (sum > 32) {
                        return input;
                    }
                }
                maxStorage = is.stackSize;
                worked = true;
                if (type == Actionable.MODULATE) {
                    if (i instanceof IPlantable || i instanceof ItemSkull || i instanceof ItemReed) {
                        boolean Worked = false;

                        if (side.offsetX == 0 && side.offsetZ == 0) {
                            Worked = i.onItemUse(
                                    is,
                                    player,
                                    w,
                                    x + side.offsetX,
                                    y + side.offsetY,
                                    z + side.offsetZ,
                                    side.getOpposite().ordinal(),
                                    side.offsetX,
                                    side.offsetY,
                                    side.offsetZ);
                        }

                        if (!Worked && side.offsetX == 0 && side.offsetZ == 0) {
                            Worked = i.onItemUse(
                                    is,
                                    player,
                                    w,
                                    x - side.offsetX,
                                    y - side.offsetY,
                                    z - side.offsetZ,
                                    side.ordinal(),
                                    side.offsetX,
                                    side.offsetY,
                                    side.offsetZ);
                        }

                        if (!Worked && side.offsetY == 0) {
                            Worked = i.onItemUse(
                                    is,
                                    player,
                                    w,
                                    x,
                                    y - 1,
                                    z,
                                    ForgeDirection.UP.ordinal(),
                                    side.offsetX,
                                    side.offsetY,
                                    side.offsetZ);
                        }

                        if (!Worked) {
                            i.onItemUse(
                                    is,
                                    player,
                                    w,
                                    x,
                                    y,
                                    z,
                                    side.getOpposite().ordinal(),
                                    side.offsetX,
                                    side.offsetY,
                                    side.offsetZ);
                        }

                        maxStorage -= is.stackSize;
                    } else if (i instanceof ItemFirework) {
                        i.onItemUse(
                                is,
                                player,
                                w,
                                x,
                                y,
                                z,
                                side.getOpposite().ordinal(),
                                side.offsetX,
                                side.offsetY,
                                side.offsetZ);
                        maxStorage -= is.stackSize;
                    } else {
                        player.setCurrentItemOrArmor(0, is.copy());
                        BlockSnapshot blockSnapshot = new BlockSnapshot(
                                w,
                                x,
                                y,
                                z,
                                ((ItemBlock) i).field_150939_a,
                                i.getMetadata(is.getItemDamage()));
                        BlockEvent.PlaceEvent event = new BlockEvent.PlaceEvent(
                                blockSnapshot,
                                w.getBlock(x, y, z),
                                owner == null ? player : owner);
                        MinecraftForge.EVENT_BUS.post(event);
                        if (!event.isCanceled()) {
                            i.onItemUse(
                                    is,
                                    player,
                                    w,
                                    x,
                                    y,
                                    z,
                                    side.getOpposite().ordinal(),
                                    side.offsetX,
                                    side.offsetY,
                                    side.offsetZ);
                            maxStorage -= is.stackSize;
                        } else {
                            worked = false;
                        }
                    }
                } else {
                    maxStorage = 1;
                }
            } else {
                worked = true;
                final Chunk c = w.getChunkFromBlockCoords(x, z);
                int sum = 0;
                for (final List Z : c.entityLists) {
                    sum += Z.size();
                }

                if (sum < AEConfig.instance.formationPlaneEntityLimit) {
                    if (type == Actionable.MODULATE) {

                        is.stackSize = (int) maxStorage;
                        final EntityItem ei = new EntityItem(
                                w,
                                ((side.offsetX != 0 ? 0.0 : 0.7) * (Platform.getRandomFloat() - 0.5f)) + 0.5
                                        + side.offsetX * -0.3
                                        + x,
                                ((side.offsetY != 0 ? 0.0 : 0.7) * (Platform.getRandomFloat() - 0.5f)) + 0.5
                                        + side.offsetY * -0.3
                                        + y,
                                ((side.offsetZ != 0 ? 0.0 : 0.7) * (Platform.getRandomFloat() - 0.5f)) + 0.5
                                        + side.offsetZ * -0.3
                                        + z,
                                is.copy());

                        Entity result = ei;

                        ei.motionX = side.offsetX * 0.2;
                        ei.motionY = side.offsetY * 0.2;
                        ei.motionZ = side.offsetZ * 0.2;

                        if (is.getItem().hasCustomEntity(is)) {
                            result = is.getItem().createEntity(w, ei, is);
                            if (result != null) {
                                ei.setDead();
                            } else {
                                result = ei;
                            }
                        }

                        if (!w.spawnEntityInWorld(result)) {
                            if (((EntityItem) result).getEntityItem().getItem()
                                    == Item.getItemFromBlock(Blocks.dragon_egg)) { // Ducttape fix for HEE replacing the
                                                                                   // Dragon Egg
                                // HEE does cancel the event but does not mark passed entity as dead
                                worked = true;
                            } else {
                                // e.g. ExU item collector cancels item spawn, but takes the item inside
                                worked = result.isDead;
                                result.setDead();
                            }
                        }
                    }
                } else {
                    worked = false;
                }
            }
        }

        this.blocked = !w.getBlock(x, y, z).isReplaceable(w, x, y, z);

        if (worked) {
            final IAEItemStack out = input.copy();
            out.decStackSize(maxStorage);
            if (out.getStackSize() == 0) {
                return null;
            }
            return out;
        }

        return input;
    }

    @Override
    public IAEItemStack extractItems(final IAEItemStack request, final Actionable mode, final BaseActionSource src) {
        return null;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(final IItemList<IAEItemStack> out, int iteration) {
        return out;
    }

    @Override
    public IAEItemStack getAvailableItem(@Nonnull IAEItemStack request, int iteration) {
        return null;
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.ITEMS;
    }

    @Override
    public void saveChanges(final IMEInventory cellInventory) {
        // nope!
    }
}
