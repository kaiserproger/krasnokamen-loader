package dev.krasnokamen.agent.javaagent;

import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class MinecraftClientObservationTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static LivingEntity soldier() throws Exception {
        LivingEntity entity = mock(LivingEntity.class);
        doReturn(BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:husk"))).when(entity).getType();
        when(entity.getUUID()).thenReturn(UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"));
        when(entity.getName()).thenReturn(Component.literal("soldier"));
        when(entity.getPose()).thenReturn(Pose.STANDING);
        when(entity.getPassengers()).thenReturn(List.of());
        when(entity.level()).thenReturn(mock(Level.class));
        when(entity.getBoundingBox()).thenReturn(new AABB(0, 0, 0, 1, 2, 1));
        when(entity.getDeltaMovement()).thenReturn(Vec3.ZERO);
        var walk = LivingEntity.class.getField("walkAnimation");
        walk.setAccessible(true);
        walk.set(entity, new WalkAnimationState());
        when(entity.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        when(entity.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        return entity;
    }

    private static JsonObject observe(Entity entity) throws Exception {
        var method = MinecraftClientObservation.class.getDeclaredMethod("entity", Entity.class, double.class, Entity.class);
        method.setAccessible(true);
        return (JsonObject) method.invoke(null, entity, 0.0, null);
    }

    private static ItemStack stack(Item item, String model) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getItem()).thenReturn(item);
        when(stack.getCount()).thenReturn(1);
        when(stack.getHoverName()).thenReturn(Component.literal("received equipment"));
        when(stack.get(DataComponents.ITEM_MODEL)).thenReturn(Identifier.parse(model));
        when(stack.getEnchantments()).thenReturn(ItemEnchantments.EMPTY);
        return stack;
    }

    @Test
    void serializesReceivedModelsShieldStateAndSignedVehicleId() throws Exception {
        LivingEntity entity = soldier();
        ItemStack weapon = stack(Items.IRON_SWORD, "krasnokamen_battle:iron_halberd");
        ItemStack shield = stack(Items.SHIELD, "krasnokamen_battle:blue_heater_shield");
        when(entity.getMainHandItem()).thenReturn(weapon);
        when(entity.getOffhandItem()).thenReturn(shield);
        when(entity.isBlocking()).thenReturn(true);
        Entity horse = mock(Entity.class);
        when(horse.getId()).thenReturn(-42);
        when(entity.getVehicle()).thenReturn(horse);

        JsonObject row = observe(entity);
        assertTrue(row.has("main_hand"), "received main hand missing");
        assertEquals("minecraft:iron_sword", row.getAsJsonObject("main_hand").get("item_id").getAsString());
        assertEquals("krasnokamen_battle:iron_halberd", row.getAsJsonObject("main_hand").get("item_model").getAsString());
        assertEquals("krasnokamen_battle:blue_heater_shield", row.getAsJsonObject("off_hand").get("item_model").getAsString());
        assertEquals(-42, row.get("vehicle_id").getAsInt());
        assertTrue(row.get("blocking").getAsBoolean());
    }

    @Test
    void emptyHandsUnmountedAndLoweredShieldAreExplicit() throws Exception {
        JsonObject row = observe(soldier());
        assertTrue(row.has("main_hand"), "empty main hand missing");
        for (String hand : List.of("main_hand", "off_hand")) {
            assertEquals("minecraft:air", row.getAsJsonObject(hand).get("item_id").getAsString());
            assertEquals(0, row.getAsJsonObject(hand).get("count").getAsInt());
            assertFalse(row.getAsJsonObject(hand).has("item_model"));
        }
        assertTrue(row.get("vehicle_id").isJsonNull());
        assertFalse(row.get("blocking").getAsBoolean());
    }
}
