/*
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License.  You may obtain a copy
 *  of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */

package grondag.frex;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;
import io.vram.frex.api.renderer.Renderer;
import io.vram.frex.compat.fabric.FabricRenderer;
import io.vram.frex.impl.RendererInitializerImpl;
import io.vram.frex.impl.config.FlawlessFramesImpl;
import io.vram.frex.impl.light.ItemLightLoader;
import io.vram.frex.impl.material.MaterialMapLoader;
import io.vram.frex.impl.model.FluidModelImpl;
import io.vram.frex.impl.model.SimpleFluidSpriteProvider;
import io.vram.frex.impl.texture.SpriteFinderImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.InvalidateRenderStateCallback;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

public class Frex implements ClientModInitializer {
	public static Logger LOG = LogManager.getLogger("FREX");

	private static final boolean isAvailable;

	static {
		boolean result = false;

		for (final ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			if (mod.getMetadata().containsCustomValue("frex:contains_frex_renderer")) {
				result = true;
				break;
			}
		}

		isAvailable = result;
	}

	@Deprecated
	@ScheduledForRemoval
	public static boolean isAvailable() {
		return isAvailable;
	}

	/**
	 * All Fabric-specific hooks needed for core API should be here for now.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void onInitializeClient() {
		if (RendererInitializerImpl.hasCandidate()) {
			RendererAccess.INSTANCE.registerRenderer(FabricRenderer.of(Renderer.get()));
		}

		SpriteFinderImpl.init(a -> (io.vram.frex.api.texture.SpriteFinder) SpriteFinder.get(null));

		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(materialMapListener);

		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(lightListener);

		FabricLoader.getInstance().getEntrypoints("frex", FrexInitializer.class).forEach(
			api -> api.onInitalizeFrex());

		final Function<String, Consumer<Boolean>> provider = FlawlessFramesImpl.providerFactory();
		FabricLoader.getInstance().getEntrypoints("frex_flawless_frames", Consumer.class).forEach(api -> api.accept(provider));

		// WIP: should be on resource reload
		InvalidateRenderStateCallback.EVENT.register(() -> {
			FluidModelImpl.reload();
			SimpleFluidSpriteProvider.reload();
		});
	}

	private final SimpleSynchronousResourceReloadListener materialMapListener = new SimpleSynchronousResourceReloadListener() {
		private final List<ResourceLocation> deps = ImmutableList.of(ResourceReloadListenerKeys.MODELS, ResourceReloadListenerKeys.TEXTURES);
		private final ResourceLocation id = new ResourceLocation("frex:material_map");

		@Override
		public ResourceLocation getFabricId() {
			return id;
		}

		@Override
		public Collection<ResourceLocation> getFabricDependencies() {
			return deps;
		}

		@Override
		public void onResourceManagerReload(ResourceManager resourceManager) {
			MaterialMapLoader.INSTANCE.reload(resourceManager);
		}
	};

	private final SimpleSynchronousResourceReloadListener lightListener = new SimpleSynchronousResourceReloadListener() {
		private final List<ResourceLocation> deps = ImmutableList.of();
		private final ResourceLocation id = new ResourceLocation("frex:item_light");

		@Override
		public ResourceLocation getFabricId() {
			return id;
		}

		@Override
		public Collection<ResourceLocation> getFabricDependencies() {
			return deps;
		}

		@Override
		public void onResourceManagerReload(ResourceManager resourceManager) {
			ItemLightLoader.INSTANCE.reload(resourceManager);
		}
	};
}
