package net.attackstudioyt.pvptraining.mixin;

import net.attackstudioyt.pvptraining.bot.BotPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * When a player hits another player, vanilla ships the knockback to the victim's client
 * and then puts the victim's server-side velocity back, because a real client moves itself.
 * A bot has no client, so that rollback would swallow every hit. Skip it for bots.
 */
@Mixin(PlayerEntity.class)
public class PlayerKnockbackMixin {
	@Redirect(method = "knockbackTarget", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;knockedBack:Z", ordinal = 0))
	private boolean pvptraining$keepBotKnockback(Entity target) {
		return target.knockedBack && !(target instanceof BotPlayer);
	}
}
