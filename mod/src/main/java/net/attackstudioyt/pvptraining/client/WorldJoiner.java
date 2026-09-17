package net.attackstudioyt.pvptraining.client;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.attackstudioyt.pvptraining.Live;
import net.attackstudioyt.pvptraining.PVPTraining;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.integrated.IntegratedServerLoader;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.rule.GameRules;

/**
 * Takes the player from wherever they are (title screen, another world, a server)
 * into the practice world. The first time, the world is created as an empty void
 * and the mod builds the arenas into it as the server starts.
 */
final class WorldJoiner {
	private WorldJoiner() {}

	private static boolean busy;

	static void enter(MinecraftClient client) {
		if (client.isIntegratedServerRunning() && Live.practiceWorld) return;
		if (busy) return;
		busy = true;
		try {
			if (client.world != null) {
				boolean local = client.isIntegratedServerRunning();
				client.world.disconnect(ClientWorld.QUITTING_MULTIPLAYER_TEXT);
				if (local) client.disconnectWithSavingScreen();
				else client.disconnectWithProgressScreen();
			}
			IntegratedServerLoader loader = client.createIntegratedServerLoader();
			if (client.getLevelStorage().levelExists(PVPTraining.WORLD_NAME)) {
				loader.start(PVPTraining.WORLD_NAME, () -> client.setScreen(new TitleScreen()));
			} else {
				LevelInfo info = new LevelInfo(PVPTraining.WORLD_NAME, GameMode.SURVIVAL, false, Difficulty.NORMAL, true,
					new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES), DataConfiguration.SAFE_MODE);
				loader.createAndStart(PVPTraining.WORLD_NAME, info, new GeneratorOptions(0L, false, false), voidDimensions(), client.currentScreen);
			}
		} catch (Exception e) {
			PVPTraining.LOG.error("Could not open the practice world", e);
			Live.error("Minecraft could not open the practice world: " + e.getMessage());
		} finally {
			busy = false;
		}
	}

	private static Function<RegistryWrapper.WrapperLookup, DimensionOptionsRegistryHolder> voidDimensions() {
		return lookup -> {
			DimensionOptionsRegistryHolder flat = lookup.getOrThrow(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createDimensionsRegistryHolder();
			FlatChunkGeneratorConfig config = new FlatChunkGeneratorConfig(Optional.empty(), lookup.getOrThrow(RegistryKeys.BIOME).getOrThrow(BiomeKeys.THE_VOID), List.of());
			return flat.with(lookup, new FlatChunkGenerator(config));
		};
	}
}
