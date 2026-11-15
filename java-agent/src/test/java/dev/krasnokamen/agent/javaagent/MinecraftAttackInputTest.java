package dev.krasnokamen.agent.javaagent;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class MinecraftAttackInputTest {
    @Test
    void publicEntityAttackUsesCanonicalInput() throws Exception {
        var calls = new java.util.ArrayList<String>();
        new org.objectweb.asm.ClassReader(MinecraftClientFacade.class.getName()).accept(
            new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override
                public org.objectweb.asm.MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] exceptions
                ) {
                    if (!name.startsWith("lambda$attackEntityOnce$")) return null;
                    return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                            calls.add(owner + "." + name);
                        }
                    };
                }
            }, 0);
        assertTrue(calls.contains("dev/krasnokamen/agent/javaagent/MinecraftActionAccess.attack"));
        assertFalse(calls.contains("net/minecraft/client/multiplayer/MultiPlayerGameMode.attack"));
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Minecraft client(ItemStack stack, Entity target) throws Exception {
        Minecraft minecraft = mock(Minecraft.class);
        minecraft.player = mock(LocalPlayer.class);
        var connection = LocalPlayer.class.getField("connection");
        connection.setAccessible(true);
        connection.set(minecraft.player, mock(net.minecraft.client.multiplayer.ClientPacketListener.class));
        minecraft.level = mock(ClientLevel.class);
        minecraft.gameMode = mock(MultiPlayerGameMode.class);
        when(minecraft.getCameraEntity()).thenReturn(minecraft.player);
        when(minecraft.player.raycastHitResult(1.0F, minecraft.player))
            .thenReturn(new EntityHitResult(target, Vec3.ZERO));
        when(minecraft.player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);
        when(stack.isItemEnabled(any())).thenReturn(true);
        return minecraft;
    }

    @Test
    void spearUsesVanillaPiercingInsteadOfPlainEntityAttack() throws Exception {
        ItemStack stack = mock(ItemStack.class);
        PiercingWeapon piercing = mock(PiercingWeapon.class);
        when(stack.get(DataComponents.PIERCING_WEAPON)).thenReturn(piercing);
        Entity target = mock(Entity.class);
        Minecraft minecraft = client(stack, target);
        MinecraftActionAccess.attack(minecraft);
        verify(minecraft.player).raycastHitResult(1.0F, minecraft.player);
        verify(minecraft.gameMode, never()).attack(any(), any());
        assertEquals(1, mockingDetails(minecraft.gameMode).getInvocations().stream()
            .filter(call -> call.getMethod().getName().equals("piercingAttack")).count());
    }

    @Test
    void ordinaryWeaponStillUsesVanillaEntityAttack() throws Exception {
        Entity target = mock(Entity.class);
        Minecraft minecraft = client(mock(ItemStack.class), target);
        MinecraftActionAccess.attack(minecraft);
        verify(minecraft.gameMode).attack(minecraft.player, target);
    }

    @Test
    void vanillaCooldownRefusalDoesNotSendAnAttack() throws Exception {
        Entity target = mock(Entity.class);
        ItemStack stack = mock(ItemStack.class);
        Minecraft minecraft = client(stack, target);
        when(minecraft.player.cannotAttackWithItem(stack, 0)).thenReturn(true);
        assertFalse(MinecraftActionAccess.attack(minecraft));
        verify(minecraft.gameMode, never()).attack(any(), any());
        assertTrue(mockingDetails(minecraft.gameMode).getInvocations().stream()
            .noneMatch(call -> call.getMethod().getName().equals("piercingAttack")));
    }
}
