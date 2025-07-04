/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.core.sync;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import appeng.core.sync.packets.PacketAssemblerAnimation;
import appeng.core.sync.packets.PacketClick;
import appeng.core.sync.packets.PacketCompassRequest;
import appeng.core.sync.packets.PacketCompassResponse;
import appeng.core.sync.packets.PacketCompressedNBT;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketCraftRequest;
import appeng.core.sync.packets.PacketCraftingCPUsUpdate;
import appeng.core.sync.packets.PacketCraftingItemInterface;
import appeng.core.sync.packets.PacketCraftingRemainingOperations;
import appeng.core.sync.packets.PacketCraftingTreeData;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.core.sync.packets.PacketLightning;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.core.sync.packets.PacketMatterCannon;
import appeng.core.sync.packets.PacketMockExplosion;
import appeng.core.sync.packets.PacketMultiPart;
import appeng.core.sync.packets.PacketNEIBookmark;
import appeng.core.sync.packets.PacketNEIDragClick;
import appeng.core.sync.packets.PacketNEIRecipe;
import appeng.core.sync.packets.PacketNetworkStatusSelected;
import appeng.core.sync.packets.PacketNewStorageDimension;
import appeng.core.sync.packets.PacketOptimizePatterns;
import appeng.core.sync.packets.PacketPaintedEntity;
import appeng.core.sync.packets.PacketPartPlacement;
import appeng.core.sync.packets.PacketPartialItem;
import appeng.core.sync.packets.PacketPatternItemRenamer;
import appeng.core.sync.packets.PacketPatternMultiSet;
import appeng.core.sync.packets.PacketPatternSlot;
import appeng.core.sync.packets.PacketPatternValueSet;
import appeng.core.sync.packets.PacketPinsUpdate;
import appeng.core.sync.packets.PacketProgressBar;
import appeng.core.sync.packets.PacketSwapSlots;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketTransitionEffect;
import appeng.core.sync.packets.PacketValueConfig;
import io.netty.buffer.ByteBuf;

public class AppEngPacketHandlerBase {

    private static final Map<Class<? extends AppEngPacket>, PacketTypes> REVERSE_LOOKUP = new HashMap<>();

    public enum PacketTypes {

        PACKET_COMPASS_REQUEST(PacketCompassRequest.class),

        PACKET_COMPASS_RESPONSE(PacketCompassResponse.class),

        PACKET_INVENTORY_ACTION(PacketInventoryAction.class),

        PACKET_ME_INVENTORY_UPDATE(PacketMEInventoryUpdate.class),

        PACKET_CONFIG_BUTTON(PacketConfigButton.class),

        PACKET_MULTIPART(PacketMultiPart.class),

        PACKET_PART_PLACEMENT(PacketPartPlacement.class),

        PACKET_LIGHTNING(PacketLightning.class),

        PACKET_MATTER_CANNON(PacketMatterCannon.class),

        PACKET_MOCK_EXPLOSION(PacketMockExplosion.class),

        PACKET_VALUE_CONFIG(PacketValueConfig.class),

        PACKET_TRANSITION_EFFECT(PacketTransitionEffect.class),

        PACKET_PROGRESS_VALUE(PacketProgressBar.class),

        PACKET_CLICK(PacketClick.class),

        PACKET_NEW_STORAGE_DIMENSION(PacketNewStorageDimension.class),

        PACKET_SWITCH_GUIS(PacketSwitchGuis.class),

        PACKET_SWAP_SLOTS(PacketSwapSlots.class),

        PACKET_PATTERN_SLOT(PacketPatternSlot.class),

        PACKET_RECIPE_NEI(PacketNEIRecipe.class),

        PACKET_PARTIAL_ITEM(PacketPartialItem.class),

        PACKET_CRAFTING_REQUEST(PacketCraftRequest.class),

        PACKET_ASSEMBLER_ANIMATION(PacketAssemblerAnimation.class),

        PACKET_COMPRESSED_NBT(PacketCompressedNBT.class),

        PACKET_PAINTED_ENTITY(PacketPaintedEntity.class),

        PACKET_CRAFTING_CPUS_UPDATE(PacketCraftingCPUsUpdate.class),

        PACKET_NEI_DRAG(PacketNEIDragClick.class),

        PACKET_PATTERN_VALUE(PacketPatternValueSet.class),
        PACKET_PATTERN_MULTI(PacketPatternMultiSet.class),
        PACKET_CRAFTING_REMAINING_OPERATIONS(PacketCraftingRemainingOperations.class),
        PACKET_CRAFTING_ITEM_INTERFACE(PacketCraftingItemInterface.class),
        PACKET_CRAFTING_TREE_DATA(PacketCraftingTreeData.class),
        PACKET_NEI_BOOKMARK(PacketNEIBookmark.class),
        PACKET_INTERFACE_TERMINAL_UPDATE(PacketInterfaceTerminalUpdate.class),
        PACKET_OPTIMIZE_PATTERNS(PacketOptimizePatterns.class),
        PACKET_NETWORK_STATUS_SELECTED(PacketNetworkStatusSelected.class),
        PACKET_PATTERN_ITEM_RENAMER(PacketPatternItemRenamer.class),
        PACKET_PINS_UPDATE(PacketPinsUpdate.class);

        private final Class<? extends AppEngPacket> packetClass;
        private final Constructor<? extends AppEngPacket> packetConstructor;

        PacketTypes(final Class<? extends AppEngPacket> c) {
            this.packetClass = c;

            Constructor<? extends AppEngPacket> x = null;
            try {
                x = this.packetClass.getConstructor(ByteBuf.class);
            } catch (final NoSuchMethodException | SecurityException ignored) {}

            this.packetConstructor = x;
            REVERSE_LOOKUP.put(this.packetClass, this);

            if (this.packetConstructor == null) {
                throw new IllegalStateException(
                        "Invalid Packet Class " + c + ", must be constructable on DataInputStream");
            }
        }

        public static PacketTypes getPacket(final int id) {
            return (values())[id];
        }

        static PacketTypes getID(final Class<? extends AppEngPacket> c) {
            return REVERSE_LOOKUP.get(c);
        }

        public AppEngPacket parsePacket(final ByteBuf in) throws InstantiationException, IllegalAccessException,
                IllegalArgumentException, InvocationTargetException {
            return this.packetConstructor.newInstance(in);
        }
    }
}
