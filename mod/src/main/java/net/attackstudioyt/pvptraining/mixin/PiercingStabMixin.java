package net.attackstudioyt.pvptraining.mixin;

import net.attackstudioyt.pvptraining.PVPTraining;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Spear jabs are how Lunge fires, and the lunge drills need to count them. */
@Mixin(PiercingWeaponComponent.class)
public class PiercingStabMixin {
	@Inject(method = "stab", at = @At("HEAD"))
	private void pvptraining$countStab(LivingEntity attacker, EquipmentSlot slot, CallbackInfo ci) {
		if (attacker instanceof ServerPlayerEntity player) PVPTraining.onStab(player, player.getEquippedStack(slot));
	}
}
