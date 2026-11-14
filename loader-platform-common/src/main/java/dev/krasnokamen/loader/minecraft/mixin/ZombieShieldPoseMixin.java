package dev.krasnokamen.loader.minecraft.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla zombie renderers omit the ordinary shield arm pose, even while blocking. */
@Mixin(AbstractZombieRenderer.class)
abstract class ZombieShieldPoseMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/monster/zombie/Zombie;Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;F)V",
            at = @At("RETURN"))
    private void krasnokamen$shieldPose(Zombie entity, ZombieRenderState state, float partialTick,
                                       CallbackInfo callback) {
        if (entity.isBlocking()) {
            HumanoidArm usedArm = entity.getUsedItemHand() == InteractionHand.MAIN_HAND
                    ? entity.getMainArm() : entity.getMainArm().getOpposite();
            if (usedArm == HumanoidArm.LEFT) {
                state.leftArmPose = HumanoidModel.ArmPose.BLOCK;
            } else {
                state.rightArmPose = HumanoidModel.ArmPose.BLOCK;
            }
        }
    }
}
